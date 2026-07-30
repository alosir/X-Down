package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tag_name: String?,
    val name: String?,
    val body: String?,
    val published_at: String?,
    val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    val name: String?,
    val browser_download_url: String?
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object NoUpdate : UpdateState()
    data class UpdateAvailable(val latestVersion: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class DownloadSuccess(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class AppUpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val updateChannelId = "app_update_channel"
    private val updateNotificationId = 1001

    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                updateChannelId,
                "应用更新",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用版本更新下载进度"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun checkLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/alosir/X-Down/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("检查更新失败：HTTP ${response.code}")
        }
        val body = response.body ?: throw Exception("检查更新失败：空响应")
        val adapter = moshi.adapter(GitHubRelease::class.java)
        adapter.fromJson(body.string()) ?: throw Exception("解析更新信息失败")
    }

    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/alosir/X-Down/releases")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("获取更新记录失败：HTTP ${response.code}")
        }
        val body = response.body ?: throw Exception("获取更新记录失败：空响应")
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, GitHubRelease::class.java)
        val adapter = moshi.adapter<List<GitHubRelease>>(type)
        adapter.fromJson(body.string()) ?: emptyList()
    }

    fun compareVersions(current: String, latest: String): Boolean {
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadApk(downloadUrl: String, onProgress: (Float, Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("下载失败：HTTP ${response.code}")
        }
        val body = response.body ?: throw Exception("下载失败：空响应")
        val totalBytes = body.contentLength()
        val apkFile = File(context.cacheDir, "xdown_update.apk")
        if (apkFile.exists()) apkFile.delete()

        body.byteStream().use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloaded = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                    onProgress(progress.coerceIn(0f, 1f), downloaded, totalBytes)
                }
            }
        }
        apkFile
    }

    fun showUpdateNotification(progress: Float, downloadedBytes: Long, totalBytes: Long) {
        createUpdateNotificationChannel()
        val percent = (progress * 100).toInt()
        val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
        val totalMb = totalBytes / (1024.0 * 1024.0)
        val text = if (totalBytes > 0) {
            String.format("%.1f MB / %.1f MB", downloadedMb, totalMb)
        } else {
            String.format("%.1f MB", downloadedMb)
        }

        val builder = NotificationCompat.Builder(context, updateChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 X-Down 更新")
            .setContentText(text)
            .setProgress(100, percent, totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(updateNotificationId, builder.build())
    }

    fun showUpdateDownloadCompleteNotification(apkFile: File) {
        createUpdateNotificationChannel()

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, updateNotificationId, installIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, updateChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("X-Down 更新下载完成")
            .setContentText("点击安装新版本")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(updateNotificationId, builder.build())
    }

    fun cancelUpdateNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(updateNotificationId)
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兼容鸿蒙/旧版安卓：跳转到应用详情页
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
}
