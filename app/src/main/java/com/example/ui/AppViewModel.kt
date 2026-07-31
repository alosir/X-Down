package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.download.BadgeHelper
import com.example.download.DownloadManager
import com.example.download.DownloadState
import com.example.update.AppUpdateManager
import com.example.update.UpdateState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParseUiState(
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val parsedEntity: TweetDownloadEntity? = null,
    val selectedResolutions: Map<Int, VideoQuality> = emptyMap() // maps videoIndex to selected VideoQuality
)

data class DownloadWarningState(
    val entity: TweetDownloadEntity,
    val selectedVideos: List<VideoQuality>
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = TweetRepository(db.tweetDownloadDao())
    private val downloadManager = DownloadManager.getInstance(application, repository)
    private val appUpdateManager = AppUpdateManager(application)
    private val updatePrefs = application.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _selectedTab = MutableStateFlow("home")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    private val _refreshTrigger = MutableStateFlow(0)

    fun refreshFileStatus() {
        _refreshTrigger.value += 1
    }

    // Flow states
    val allHistory = combine(repository.allDownloads, _refreshTrigger) { downloads, _ ->
        downloads
    }

    val rankings = allHistory.map { history ->
        history
            .groupBy { it.authorHandle }
            .map { (handle, items) ->
                val firstItem = items.first()
                AuthorRanking(
                    authorName = firstItem.authorName,
                    authorHandle = firstItem.authorHandle,
                    authorAvatarUrl = firstItem.authorAvatarUrl,
                    downloadCount = items.size
                )
            }
            .sortedByDescending { it.downloadCount }
    }

    val activeDownloads = downloadManager.activeTasksList

    private val _topBubbleMessage = MutableStateFlow<String?>(null)
    val topBubbleMessage: StateFlow<String?> = _topBubbleMessage.asStateFlow()

    fun showTopBubble(message: String) {
        viewModelScope.launch {
            _topBubbleMessage.value = message
            kotlinx.coroutines.delay(2500)
            if (_topBubbleMessage.value == message) {
                _topBubbleMessage.value = null
            }
        }
    }

    // ==================== 应用更新状态 ====================
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // "有新版本"橘色标识
    private val _hasNewVersion = MutableStateFlow(false)
    val hasNewVersion: StateFlow<Boolean> = _hasNewVersion.asStateFlow()

    // 请求打开《更新记录》页（供系统通知点击跳转）
    private val _openChangelogEvent = MutableStateFlow(false)
    val openChangelogEvent: StateFlow<Boolean> = _openChangelogEvent.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.downloadCompletions.collect { authorHandle ->
                val handle = authorHandle.replace("@", "")
                showTopBubble("@$handle 的视频已下载")
                refreshFileStatus()
            }
        }

        // 桌面角标：实时显示下载队列中（下载中/暂停/失败）的任务数
        viewModelScope.launch {
            var lastBadgeCount = -1
            activeDownloads.collect { tasks ->
                val count = tasks.count {
                    val s = it.state.value
                    s is DownloadState.Downloading || s is DownloadState.Paused || s is DownloadState.Failed
                }
                if (count != lastBadgeCount) {
                    lastBadgeCount = count
                    try {
                        BadgeHelper.applyBadgeCount(getApplication(), count)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 若用户已更新到存储的最新版本（或更高），清除"有新版本"标识
        val storedLatest = updatePrefs.getString(KEY_LATEST_VERSION, null)
        if (updatePrefs.getBoolean(KEY_HAS_NEW_VERSION, false) && storedLatest != null &&
            !appUpdateManager.compareVersions(getAppVersionName(), storedLatest)
        ) {
            updatePrefs.edit().putBoolean(KEY_HAS_NEW_VERSION, false).apply()
        }
        _hasNewVersion.value = updatePrefs.getBoolean(KEY_HAS_NEW_VERSION, false)

        // 每天至少一次的静默检查更新
        maybeAutoCheckUpdate()
    }

    private val _parseState = MutableStateFlow(ParseUiState())
    val parseState: StateFlow<ParseUiState> = _parseState.asStateFlow()

    private val _downloadWarningState = MutableStateFlow<DownloadWarningState?>(null)
    val downloadWarningState: StateFlow<DownloadWarningState?> = _downloadWarningState.asStateFlow()

    // Clipboard auto-parse state
    private var lastParsedClipboardUrl: String = ""
    private val _clipboardDetectedEntity = MutableStateFlow<TweetDownloadEntity?>(null)
    val clipboardDetectedEntity: StateFlow<TweetDownloadEntity?> = _clipboardDetectedEntity.asStateFlow()

    fun parseUrl(url: String) {
        if (url.isBlank()) {
            _parseState.value = ParseUiState(parseError = "请输入 Twitter / X 视频分享链接")
            return
        }
        viewModelScope.launch {
            _parseState.value = ParseUiState(isParsing = true)
            try {
                val entity = repository.parseTweetUrl(url)
                
                // Get pre-selection: for each videoIndex, pre-select the highest quality
                val availableVideos = entity.getVideos()
                val grouped = availableVideos.groupBy { it.videoIndex }
                val selections = mutableMapOf<Int, VideoQuality>()
                grouped.forEach { (idx, list) ->
                    if (list.isNotEmpty()) {
                        selections[idx] = list[0] // first item is the highest quality
                    }
                }

                _parseState.value = ParseUiState(
                    isParsing = false,
                    parsedEntity = entity,
                    selectedResolutions = selections
                )
            } catch (e: Exception) {
                _parseState.value = ParseUiState(
                    isParsing = false,
                    parseError = e.message ?: "解析视频链接失败，请稍后重试"
                )
            }
        }
    }

    fun updateSelectedResolution(videoIndex: Int, video: VideoQuality) {
        val currentSelections = _parseState.value.selectedResolutions.toMutableMap()
        currentSelections[videoIndex] = video
        _parseState.value = _parseState.value.copy(selectedResolutions = currentSelections)
    }

    fun checkAndTriggerDownloads(
        entity: TweetDownloadEntity,
        selections: Map<Int, VideoQuality>,
        onSuccessDirectDownload: () -> Unit
    ) {
        viewModelScope.launch {
            val current = repository.getDownloadById(entity.tweetId)
            val videos = entity.getVideos()
            val alreadyDownloaded = mutableListOf<VideoQuality>()
            
            selections.forEach { (_, video) ->
                val qIndex = videos.indexOfFirst { it.url == video.url && it.quality == video.quality }
                if (current != null && qIndex != -1 && current.getLocalFilePaths().containsKey(qIndex)) {
                    alreadyDownloaded.add(video)
                }
            }
            
            if (alreadyDownloaded.isNotEmpty()) {
                _downloadWarningState.value = DownloadWarningState(entity, selections.values.toList())
            } else {
                if (current == null) {
                    repository.insertDownload(entity.copy(downloadStatus = "Downloading"))
                } else {
                    repository.updateDownload(current.copy(downloadStatus = "Downloading"))
                }
                selections.forEach { (_, video) ->
                    downloadManager.downloadVideo(entity, video)
                }
                onSuccessDirectDownload()
                refreshFileStatus()
            }
        }
    }

    fun confirmRedownload(onStartDownload: () -> Unit) {
        val warning = _downloadWarningState.value ?: return
        _downloadWarningState.value = null
        viewModelScope.launch {
            val current = repository.getDownloadById(warning.entity.tweetId)
            if (current == null) {
                repository.insertDownload(warning.entity.copy(downloadStatus = "Downloading"))
            } else {
                repository.updateDownload(current.copy(downloadStatus = "Downloading"))
            }
            warning.selectedVideos.forEach { video ->
                downloadManager.downloadVideo(warning.entity, video, forceRedownload = true)
            }
            onStartDownload()
            refreshFileStatus()
        }
    }

    fun cancelDownloadWarning() {
        _downloadWarningState.value = null
    }

    fun startDownload(entity: TweetDownloadEntity, video: VideoQuality, forceRedownload: Boolean = false) {
        viewModelScope.launch {
            val current = repository.getDownloadById(entity.tweetId)
            if (current == null) {
                repository.insertDownload(entity.copy(downloadStatus = "Downloading"))
            } else {
                repository.updateDownload(current.copy(downloadStatus = "Downloading"))
            }
            downloadManager.downloadVideo(entity, video, forceRedownload)
            refreshFileStatus()
        }
    }

    fun deleteHistoryItem(entity: TweetDownloadEntity) {
        viewModelScope.launch {
            repository.deleteDownloadById(entity.tweetId)
            refreshFileStatus()
        }
    }

    fun startTask(taskId: String) {
        downloadManager.startTask(taskId)
    }

    fun pauseTask(taskId: String) {
        downloadManager.pauseTask(taskId)
    }

    fun removeTask(taskId: String) {
        downloadManager.removeTask(taskId)
    }

    // Clipboard auto-parse methods
    fun clearClipboardDetected() {
        _clipboardDetectedEntity.value = null
    }

    fun checkAndAutoParseClipboard(clipboardText: String) {
        val detectedUrl = extractTwitterUrl(clipboardText) ?: return
        if (detectedUrl == lastParsedClipboardUrl) return

        viewModelScope.launch {
            try {
                // Parse silently in background, do not show any error to minimize UI distraction
                val entity = repository.parseTweetUrl(detectedUrl)
                lastParsedClipboardUrl = detectedUrl
                _clipboardDetectedEntity.value = entity
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun acceptClipboardParsedEntity(entity: TweetDownloadEntity) {
        val availableVideos = entity.getVideos()
        val grouped = availableVideos.groupBy { it.videoIndex }
        val selections = mutableMapOf<Int, VideoQuality>()
        grouped.forEach { (idx, list) ->
            if (list.isNotEmpty()) {
                selections[idx] = list[0]
            }
        }

        _parseState.value = ParseUiState(
            isParsing = false,
            parsedEntity = entity,
            selectedResolutions = selections
        )
        _clipboardDetectedEntity.value = null
    }

    private fun extractTwitterUrl(text: String): String? {
        val regex = "https?://(mobile\\.)?(vx|fx)?(twitter|x)\\.com/[a-zA-Z0-9_]+/status/\\d+".toRegex()
        val match = regex.find(text)
        return match?.value
    }

    // ==================== 应用更新 ====================
    fun requestOpenChangelog() {
        _openChangelogEvent.value = true
    }

    fun consumeOpenChangelog() {
        _openChangelogEvent.value = false
    }

    // 每天最多自动触发一次检查（APP 启动时与每天首次进入更新记录页时调用）
    fun maybeAutoCheckUpdate() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (updatePrefs.getString(KEY_LAST_CHECK_DATE, "") == today) return
        updatePrefs.edit().putString(KEY_LAST_CHECK_DATE, today).apply()
        checkForUpdate(silent = true)
    }

    fun checkForUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                showTopBubble("正在检查更新…")
            }
            _updateState.value = UpdateState.Checking
            try {
                val release = appUpdateManager.checkLatestRelease()
                val latestTag = release.tag_name ?: throw Exception("未找到版本信息")
                val latestVersion = latestTag.removePrefix("v")
                val currentVersion = getAppVersionName()
                val hasUpdate = appUpdateManager.compareVersions(currentVersion, latestTag)
                if (hasUpdate) {
                    val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    val downloadUrl = apkAsset?.browser_download_url ?: throw Exception("未找到安装包下载地址")
                    updatePrefs.edit()
                        .putBoolean(KEY_HAS_NEW_VERSION, true)
                        .putString(KEY_LATEST_VERSION, latestVersion)
                        .apply()
                    _hasNewVersion.value = true
                    _updateState.value = UpdateState.UpdateAvailable(latestVersion, downloadUrl)
                    if (silent) {
                        appUpdateManager.showNewVersionAvailableNotification(latestVersion)
                    }
                } else {
                    updatePrefs.edit().putBoolean(KEY_HAS_NEW_VERSION, false).apply()
                    _hasNewVersion.value = false
                    _updateState.value = UpdateState.Idle
                    if (!silent) {
                        showTopBubble("当前已是最新版本")
                    }
                }
            } catch (e: Exception) {
                if (silent) {
                    // 静默检查：任何失败都不打扰用户，等待下次触发
                    _updateState.value = UpdateState.Idle
                } else if (isNetworkError(e)) {
                    _updateState.value = UpdateState.Idle
                    showTopBubble("网络异常，请检查网络连接")
                } else {
                    _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
                }
            }
        }
    }

    private fun isNetworkError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is java.net.UnknownHostException ||
                t is java.net.ConnectException ||
                t is java.net.SocketTimeoutException ||
                t is java.net.NoRouteToHostException ||
                t is javax.net.ssl.SSLException
            ) {
                return true
            }
            t = t.cause
        }
        return false
    }

    fun downloadAndInstallUpdate(downloadUrl: String) {
        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0f, 0L, 0L)
                val apkFile = appUpdateManager.downloadApk(downloadUrl) { progress, downloaded, total ->
                    _updateState.value = UpdateState.Downloading(progress, downloaded, total)
                    appUpdateManager.showUpdateNotification(progress, downloaded, total)
                }
                _updateState.value = UpdateState.DownloadSuccess(apkFile)
                appUpdateManager.showUpdateDownloadCompleteNotification(apkFile)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "下载更新失败")
                appUpdateManager.cancelUpdateNotification()
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun getAppVersionName(): String {
        return try {
            val pm = getApplication<Application>().packageManager
            val packageName = getApplication<Application>().packageName
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: "1.3"
        } catch (e: Exception) {
            "1.3"
        }
    }

    fun openAppNotificationSettings(context: android.content.Context) {
        appUpdateManager.openNotificationSettings(context)
    }

    // 手动关闭首页解析结果卡片
    fun clearParseResult() {
        _parseState.value = ParseUiState()
    }

    companion object {
        private const val KEY_LAST_CHECK_DATE = "last_check_date"
        private const val KEY_HAS_NEW_VERSION = "has_new_version"
        private const val KEY_LATEST_VERSION = "latest_version"
    }
}
