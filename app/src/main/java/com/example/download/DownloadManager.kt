package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.content.Context
import android.os.Environment
import com.example.data.TweetDownloadEntity
import com.example.data.TweetRepository
import com.example.data.VideoQuality
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val speedKbps: Double, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    object Paused : DownloadState()
    object Success : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

data class ActiveDownloadTask(
    val id: String, // format: tweetId_videoIndex
    val tweetId: String,
    val videoIndex: Int,
    val videoUrl: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val qualityLabel: String,
    val entity: TweetDownloadEntity,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val repository: TweetRepository,
    private val outputDir: File,
    private val context: Context,
    private val onTaskUpdated: () -> Unit,
    private val onDownloadSuccess: (String) -> Unit,
    private val badgeCountProvider: () -> Int = { 0 },
    val forceRedownload: Boolean = false
) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null
    private var isPausing = false
    private var targetFileName: String = ""

    // 从本地持久化恢复的任务：初始为暂停状态，等待用户手动继续
    fun restoreAsPaused() {
        _state.value = DownloadState.Paused
    }

    fun start() {
        if (state.value is DownloadState.Downloading) return
        isPausing = false
        job = scope.launch(Dispatchers.IO) {
            downloadLoop()
        }
    }

    fun pause() {
        if (state.value !is DownloadState.Downloading) return
        isPausing = true
        job?.cancel()
        _state.value = DownloadState.Paused
        cancelProgressNotification()
        onTaskUpdated()
    }

    // ==================== 下载进度系统通知 ====================
    private val progressNotificationId = id.hashCode()
    private var cachedThumbnail: android.graphics.Bitmap? = null
    private var thumbnailLoadAttempted = false

    // 将位图截取为居中的正方形
    private fun cropToSquare(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return android.graphics.Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun createProgressNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "download_progress_channel",
                "下载进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示视频下载任务的实时进度"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun ensureThumbnailLoaded() {
        if (thumbnailLoadAttempted) return
        thumbnailLoadAttempted = true
        val thumbnailUrl = entity.thumbnailUrl
        if (thumbnailUrl.isNullOrEmpty()) return
        cachedThumbnail = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? BitmapDrawable)?.bitmap?.let { cropToSquare(it) }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun showProgressNotification(progress: Float) {
        try {
            createProgressNotificationChannel()
            ensureThumbnailLoaded()
            val percent = (progress * 100).toInt()
            val builder = NotificationCompat.Builder(context, "download_progress_channel")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("${qualityLabel.uppercase()}视频正在下载中")
                .setContentText("@${authorHandle}：$title")
                .setProgress(100, percent, percent <= 0)
                .setNumber(badgeCountProvider())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            cachedThumbnail?.let { builder.setLargeIcon(it) }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(progressNotificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelProgressNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(progressNotificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun downloadLoop() {
        // 统一保存到 X-Down 根文件夹，不再按作者创建子文件夹
        val finalFolder = outputDir
        if (!finalFolder.exists()) {
            finalFolder.mkdirs()
        }

        // Format name as "@username：caption_first_30"
        val caption = title.trim()
        val captionFirst30 = if (caption.length > 30) caption.substring(0, 30) else caption
        val rawBaseName = "@${authorHandle}：$captionFirst30"
        var sanitizedBaseName = rawBaseName.replace("[\"/\\\\:*?<>|\\r\\n\\t]".toRegex(), "_").trim()
        if (sanitizedBaseName.isEmpty()) {
            sanitizedBaseName = tweetId
        }

        val finalFileName = "${sanitizedBaseName}_${videoIndex}_${qualityLabel}"
        targetFileName = finalFileName
        var destFile = File(finalFolder, "$targetFileName.mp4")
        if (forceRedownload) {
            var checkFile = destFile
            var count = 1
            while (checkFile.exists()) {
                targetFileName = "${finalFileName}_$count"
                checkFile = File(finalFolder, "$targetFileName.mp4")
                count++
            }
            destFile = checkFile
        }
        val tempFile = File(finalFolder, "$targetFileName.tmp")

        val currentEntity = repository.getDownloadById(tweetId) ?: entity
        val videos = currentEntity.getVideos()
        val qIndex = videos.indexOfFirst { it.url == videoUrl || it.quality == qualityLabel }
        val targetKey = if (qIndex != -1) qIndex else videoIndex

        if (!forceRedownload) {
            val pathsMap = currentEntity.getLocalFilePaths()
            val existingPath = pathsMap[targetKey]
            if (existingPath != null && File(existingPath).exists()) {
                _state.value = DownloadState.Success
                onTaskUpdated()
                return
            }

            if (destFile.exists()) {
                _state.value = DownloadState.Success
                val finalPath = exportVideoToGallery(destFile, "$targetFileName.mp4")
                updateEntityCompleted(finalPath)
                onTaskUpdated()
                onDownloadSuccess(authorHandle)
                return
            }
        }

        var totalBytes = 0L
        var firstErrorTime: Long? = null
        var hasRetriedFrom416 = false

        while (currentCoroutineContext().isActive && !isPausing) {
            val currentDownloaded = tempFile.length()
            val request = Request.Builder()
                .url(videoUrl)
                .apply {
                    if (currentDownloaded > 0) {
                        addHeader("Range", "bytes=$currentDownloaded-")
                    }
                }
                .build()

            var response: okhttp3.Response? = null
            var randomAccessFile: RandomAccessFile? = null
            var inputStream: java.io.InputStream? = null

            try {
                _state.value = DownloadState.Downloading(
                    progress = if (totalBytes > 0) (currentDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else (if (currentDownloaded > 0) 0.01f else 0f),
                    speedKbps = 0.0,
                    downloadedBytes = currentDownloaded,
                    totalBytes = totalBytes
                )
                showProgressNotification(_state.value.let { (it as? DownloadState.Downloading)?.progress ?: 0f })
                onTaskUpdated()

                response = client.newCall(request).execute()
                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416) {
                        if (!hasRetriedFrom416) {
                            hasRetriedFrom416 = true
                            tempFile.delete()
                            continue
                        } else {
                            throw Exception("HTTP Range 请求失败 (416 且重试过)")
                        }
                    }
                    throw Exception("HTTP Error: ${response.code} ${response.message}")
                }

                val body = response.body ?: throw Exception("Empty payload")
                if (totalBytes == 0L) {
                    totalBytes = body.contentLength() + if (response.code == 206) currentDownloaded else 0L
                }

                randomAccessFile = RandomAccessFile(tempFile, "rw")
                if (response.code == 206) {
                    randomAccessFile.seek(currentDownloaded)
                } else {
                    randomAccessFile.setLength(0)
                }

                inputStream = body.byteStream()
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var lastUpdatedTime = System.currentTimeMillis()
                var lastDownloadedBytes = currentDownloaded
                var runningDownloaded = currentDownloaded

                // Connection is active, reset error timer
                firstErrorTime = null

                while (currentCoroutineContext().isActive && !isPausing) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    randomAccessFile.write(buffer, 0, bytesRead)
                    runningDownloaded += bytesRead

                    val now = System.currentTimeMillis()
                    val delta = now - lastUpdatedTime
                    if (delta >= 500) {
                        val bytesSinceLast = runningDownloaded - lastDownloadedBytes
                        val speedKbps = (bytesSinceLast / 1024.0) / (delta / 1000.0)
                        val progress = if (totalBytes > 0) runningDownloaded.toFloat() / totalBytes else 0f

                        _state.value = DownloadState.Downloading(
                            progress = progress.coerceIn(0f, 1f),
                            speedKbps = speedKbps,
                            downloadedBytes = runningDownloaded,
                            totalBytes = totalBytes
                        )
                        showProgressNotification(progress.coerceIn(0f, 1f))

                        lastUpdatedTime = now
                        lastDownloadedBytes = runningDownloaded
                        onTaskUpdated()
                    }
                }

                randomAccessFile.close()
                inputStream.close()
                response.close()

                if (isPausing) {
                    _state.value = DownloadState.Paused
                    cancelProgressNotification()
                    onTaskUpdated()
                    return
                }

                // Successfully finished streaming
                if (tempFile.exists() && tempFile.length() >= totalBytes) {
                    tempFile.renameTo(destFile)
                    _state.value = DownloadState.Success
                    cancelProgressNotification()
                    val finalPath = exportVideoToGallery(destFile, "$targetFileName.mp4")
                    updateEntityCompleted(finalPath)
                    onTaskUpdated()

                    // Trigger push notification with thumbnail and file size
                    val actualBytes = if (destFile.exists()) destFile.length() else totalBytes
                    sendDownloadCompleteNotification(context, authorHandle, actualBytes, entity.thumbnailUrl ?: "", title, qualityLabel)

                    onDownloadSuccess(authorHandle)
                    return
                } else {
                    throw Exception("文件下载尺寸不匹配，请重试")
                }

            } catch (e: Exception) {
                try { randomAccessFile?.close() } catch (ex: Exception) {}
                try { inputStream?.close() } catch (ex: Exception) {}
                try { response?.close() } catch (ex: Exception) {}

                if (isPausing) {
                    _state.value = DownloadState.Paused
                    cancelProgressNotification()
                    onTaskUpdated()
                    return
                }

                if (!currentCoroutineContext().isActive) {
                    cancelProgressNotification()
                    return
                }

                if (firstErrorTime == null) {
                    firstErrorTime = System.currentTimeMillis()
                }

                val timeSinceFirstError = System.currentTimeMillis() - firstErrorTime!!
                if (timeSinceFirstError > 3000) {
                    _state.value = DownloadState.Failed(e.message ?: "网络下载遇到异常")
                    cancelProgressNotification()
                    onTaskUpdated()
                    return
                } else {
                    delay(500)
                }
            }
        }
    }

    private fun exportVideoToGallery(localFile: File, fileName: String): String {
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MOVIES}/X-Down")
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    val publicDir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                        "X-Down"
                    )
                    if (!publicDir.exists()) publicDir.mkdirs()
                    val targetFile = java.io.File(publicDir, fileName)
                    put(android.provider.MediaStore.Video.Media.DATA, targetFile.absolutePath)
                }
            }

            val collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, contentValues) ?: return localFile.absolutePath

            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) {
                    resolver.delete(uri, null, null)
                    return localFile.absolutePath
                }
                java.io.FileInputStream(localFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            var realPath: String? = null
            val projection = arrayOf(android.provider.MediaStore.Video.Media.DATA)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                    realPath = cursor.getString(idx)
                }
            }

            if (realPath != null) {
                try {
                    localFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(realPath),
                    arrayOf("video/mp4")
                ) { _, _ -> }
                return realPath!!
            }
            return localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return localFile.absolutePath
        }
    }

    private suspend fun updateEntityCompleted(absolutePath: String) {
        // Fetch current entity mapping
        val currentEntity = repository.getDownloadById(tweetId) ?: entity
        val videos = currentEntity.getVideos()
        val qIndex = videos.indexOfFirst { it.url == videoUrl || it.quality == qualityLabel }
        val targetKey = if (qIndex != -1) qIndex else videoIndex

        val map = currentEntity.getLocalFilePaths().toMutableMap()
        map[targetKey] = absolutePath

        val updatedEntity = currentEntity.copy(
            downloadStatus = "Success",
            timestamp = System.currentTimeMillis(),
            localFilePathsJson = TweetDownloadEntity.createLocalFilePathsJson(map)
        )
        repository.updateDownload(updatedEntity)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "download_finished_channel"
            val channelName = "下载完成通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "当推特视频完成下载时发送此通知"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun sendDownloadCompleteNotification(
        context: Context,
        authorHandle: String,
        fileBytes: Long,
        thumbnailUrl: String,
        title: String,
        qualityLabel: String
    ) {
        try {
            createNotificationChannel(context)

            val sizeInMb = fileBytes.toDouble() / (1024.0 * 1024.0)
            val formattedSize = String.format("%.1f", sizeInMb) + "MB"
            val contentTitle = "视频已下载完成，$formattedSize，${qualityLabel.uppercase()}"

            val channelId = "download_finished_channel"
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(contentTitle)
                .setContentText("@${authorHandle}：$title")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_tab", "history")
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = android.app.PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), intent, pendingIntentFlags)
            builder.setContentIntent(pendingIntent)

            if (thumbnailUrl.isNotEmpty()) {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            (result.drawable as? BitmapDrawable)?.bitmap?.let { cropToSquare(it) }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                    builder.setStyle(NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                    )
                }
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()
            manager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class DownloadManager private constructor(
    private val context: Context,
    private val repository: TweetRepository
) {
    private val client = OkHttpClient.Builder().build()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Concurrent map of active tasks
    private val _tasks = ConcurrentHashMap<String, ActiveDownloadTask>()
    
    // Outputs flow
    private val _activeTasksList = MutableStateFlow<List<ActiveDownloadTask>>(emptyList())
    val activeTasksList: StateFlow<List<ActiveDownloadTask>> = _activeTasksList.asStateFlow()

    private val _downloadCompletions = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)
    val downloadCompletions = _downloadCompletions.asSharedFlow()

    private fun emitCompletedDownload(authorHandle: String) {
        mainScope.launch {
            _downloadCompletions.emit(authorHandle)
        }
    }

    // Base output folder inside movies directory
    private val outputDirectory: File

    // 下载队列本地持久化（APP 关闭后队列不丢失，仅手动删除或下载完成才移除）
    private val queuePrefs = context.getSharedPreferences("download_queue_prefs", Context.MODE_PRIVATE)

    init {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "Movies")
        outputDirectory = File(moviesDir, "X-Down")
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        restoreTasks()
    }

    private fun handleTaskUpdated() {
        val active = _tasks.values.filter { it.state.value !is DownloadState.Success }
        _activeTasksList.value = active
        badgeCount = active.count {
            val s = it.state.value
            s is DownloadState.Downloading || s is DownloadState.Paused || s is DownloadState.Failed
        }
        saveTasks()
    }

    // 将当前未完成的任务写入本地
    private fun saveTasks() {
        try {
            val active = _tasks.values.filter { it.state.value !is DownloadState.Success }
            val array = org.json.JSONArray()
            active.forEach { t ->
                array.put(org.json.JSONObject().apply {
                    put("id", t.id)
                    put("tweetId", t.tweetId)
                    put("videoIndex", t.videoIndex)
                    put("videoUrl", t.videoUrl)
                    put("title", t.title)
                    put("authorName", t.authorName)
                    put("authorHandle", t.authorHandle)
                    put("qualityLabel", t.qualityLabel)
                })
            }
            queuePrefs.edit().putString("tasks", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // APP 启动时恢复上次未完成的下载任务（恢复为暂停状态，用户手动继续）
    private fun restoreTasks() {
        mainScope.launch {
            try {
                val json = queuePrefs.getString("tasks", "[]") ?: "[]"
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val taskId = obj.optString("id")
                    val tweetId = obj.optString("tweetId")
                    if (taskId.isEmpty() || tweetId.isEmpty() || _tasks.containsKey(taskId)) continue
                    val entity = repository.getDownloadById(tweetId) ?: continue
                    val task = ActiveDownloadTask(
                        id = taskId,
                        tweetId = tweetId,
                        videoIndex = obj.optInt("videoIndex"),
                        videoUrl = obj.optString("videoUrl"),
                        title = obj.optString("title"),
                        authorName = obj.optString("authorName"),
                        authorHandle = obj.optString("authorHandle"),
                        qualityLabel = obj.optString("qualityLabel"),
                        entity = entity,
                        scope = mainScope,
                        client = client,
                        repository = repository,
                        outputDir = outputDirectory,
                        context = context,
                        onTaskUpdated = { handleTaskUpdated() },
                        onDownloadSuccess = { authorHandle -> emitCompletedDownload(authorHandle) },
                        badgeCountProvider = { badgeCount },
                        forceRedownload = false
                    )
                    task.restoreAsPaused()
                    _tasks[taskId] = task
                }
                handleTaskUpdated()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 当前下载队列任务数（供通知 setNumber 角标使用）
    @Volatile
    private var badgeCount = 0

    fun downloadVideo(entity: TweetDownloadEntity, video: VideoQuality, forceRedownload: Boolean = false) {
        val taskId = "${entity.tweetId}_${video.videoIndex}_${video.quality}"
        if (_tasks.containsKey(taskId)) {
            val task = _tasks[taskId]!!
            if (task.state.value is DownloadState.Success) {
                val pathsMap = entity.getLocalFilePaths()
                val videos = entity.getVideos()
                val qIndex = videos.indexOfFirst { it.url == video.url && it.quality == video.quality }
                val targetKey = if (qIndex != -1) qIndex else video.videoIndex
                val existingPath = pathsMap[targetKey]
                if (existingPath == null || !File(existingPath).exists()) {
                    _tasks.remove(taskId)
                }
            }
        }

        if (_tasks.containsKey(taskId)) {
            val task = _tasks[taskId]!!
            if (task.state.value is DownloadState.Paused || task.state.value is DownloadState.Failed) {
                task.start()
            }
            return
        }

        // Put down database state
        mainScope.launch {
            val current = repository.getDownloadById(entity.tweetId)
            if (current == null) {
                repository.insertDownload(entity.copy(downloadStatus = "Downloading"))
            } else {
                repository.updateDownload(current.copy(downloadStatus = "Downloading"))
            }
        }

        val newTask = ActiveDownloadTask(
            id = taskId,
            tweetId = entity.tweetId,
            videoIndex = video.videoIndex,
            videoUrl = video.url,
            title = entity.title,
            authorName = entity.authorName,
            authorHandle = entity.authorHandle,
            qualityLabel = video.quality,
            entity = entity,
            scope = mainScope,
            client = client,
            repository = repository,
            outputDir = outputDirectory,
            context = context,
            onTaskUpdated = { handleTaskUpdated() },
            onDownloadSuccess = { authorHandle -> emitCompletedDownload(authorHandle) },
            badgeCountProvider = { badgeCount },
            forceRedownload = forceRedownload
        )

        _tasks[taskId] = newTask
        newTask.start()
        handleTaskUpdated()
    }

    fun pauseAll() {
        _tasks.values.forEach { it.pause() }
    }

    fun startTask(taskId: String) {
        _tasks[taskId]?.start()
    }

    fun pauseTask(taskId: String) {
        _tasks[taskId]?.pause()
    }

    fun removeTask(taskId: String) {
        _tasks[taskId]?.pause()
        _tasks.remove(taskId)
        handleTaskUpdated()
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context, repository: TweetRepository): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DownloadManager(context.applicationContext, repository)
                INSTANCE = instance
                instance
            }
        }
    }
}
