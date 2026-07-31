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
    private val newVersionNotificationId = 1002

    private fun createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 删除旧渠道再重建，确保 setShowBadge(false) 对旧安装生效
            manager.deleteNotificationChannel(updateChannelId)
            val channel = NotificationChannel(
                updateChannelId,
                "应用更新",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用版本更新下载进度"
                // 通知不参与角标，角标由 ShortcutBadger 统一写入
                setShowBadge(false)
            }
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
        // 保存到外部应用专属 Download 目录（用户可见、FileProvider 可共享）
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val apkFile = File(downloadDir, "xdown_update.apk")
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

    // 通知更新节流，避免触发系统通知频率限制导致后续通知被丢弃
    private var lastUpdateNotifyTime = 0L

    fun showUpdateNotification(progress: Float, downloadedBytes: Long, totalBytes: Long) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateNotifyTime < 500 && progress < 1f) return
        lastUpdateNotifyTime = now

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

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 先取消进度通知再发完成通知，避免部分 ROM 上 ongoing 通知不更新/被限流丢弃
        manager.cancel(updateNotificationId)

        try {
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
                .setStyle(NotificationCompat.BigTextStyle().bigText("点击安装新版本\n安装包已保存至：${apkFile.absolutePath}"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            manager.notify(updateNotificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
            // 兜底：即使安装 Intent 构造失败，也告知用户安装包位置
            val builder = NotificationCompat.Builder(context, updateChannelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("X-Down 更新下载完成")
                .setContentText("安装包已保存，请手动安装")
                .setStyle(NotificationCompat.BigTextStyle().bigText("安装包已保存至：${apkFile.absolutePath}，请使用文件管理器打开安装"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            manager.notify(updateNotificationId, builder.build())
        }
    }

    fun cancelUpdateNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(updateNotificationId)
    }

    // 静默检查发现有新版本时的系统推送
    fun showNewVersionAvailableNotification(latestVersion: String) {
        createUpdateNotificationChannel()

        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "about")
            putExtra("open_changelog", "true")
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, newVersionNotificationId, intent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(context, updateChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("发现新版本 v$latestVersion")
            .setContentText("发现新版本，前往更新>>")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(newVersionNotificationId, builder.build())
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
