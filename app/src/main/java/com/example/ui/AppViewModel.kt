package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.download.DownloadManager
import com.example.download.DownloadState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    init {
        viewModelScope.launch {
            downloadManager.downloadCompletions.collect { authorHandle ->
                val handle = authorHandle.replace("@", "")
                showTopBubble("@$handle 的视频已下载")
                refreshFileStatus()
            }
        }
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
}
