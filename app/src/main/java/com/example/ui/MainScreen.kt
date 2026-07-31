package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.AuthorRanking
import com.example.data.TweetDownloadEntity
import com.example.data.VideoQuality
import com.example.download.ActiveDownloadTask
import com.example.download.DownloadState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AppViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle(initialValue = "home")
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // Video Player Overlay State
    var activePlaybackUrl by remember { mutableStateOf<String?>(null) }
    var activePlaybackTitle by remember { mutableStateOf<String?>(null) }
    var activePlaybackDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Webview overlay state
    var activeWebViewUrl by remember { mutableStateOf<String?>(null) }

    // Changelog overlay state
    var showChangelog by remember { mutableStateOf(false) }

    // 系统通知点击后请求打开《更新记录》页
    val openChangelogEvent by viewModel.openChangelogEvent.collectAsStateWithLifecycle()
    LaunchedEffect(openChangelogEvent) {
        if (openChangelogEvent) {
            showChangelog = true
            viewModel.consumeOpenChangelog()
        }
    }

    val playVideo: (String, String, (() -> Unit)?) -> Unit = { url, title, downloadAction ->
        activePlaybackUrl = url
        activePlaybackTitle = title
        activePlaybackDownloadAction = downloadAction
    }

    // Top Bubble notification state
    val topBubbleMessage by viewModel.topBubbleMessage.collectAsStateWithLifecycle()

    // Clipboard auto-parse state
    val clipboardDetected by viewModel.clipboardDetectedEntity.collectAsStateWithLifecycle()
    val downloadWarning by viewModel.downloadWarningState.collectAsStateWithLifecycle()

    // Refresh file existence checks when tabs change
    LaunchedEffect(selectedTab) {
        viewModel.refreshFileStatus()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == "home",
                    onClick = { viewModel.setSelectedTab("home") },
                    label = { Text("下载", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Parse") }
                )
                NavigationBarItem(
                    selected = selectedTab == "history",
                    onClick = { viewModel.setSelectedTab("history") },
                    label = { Text("历史", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") }
                )
                NavigationBarItem(
                    selected = selectedTab == "rankings",
                    onClick = { viewModel.setSelectedTab("rankings") },
                    label = { Text("排行", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Rank") }
                )
                NavigationBarItem(
                    selected = selectedTab == "about",
                    onClick = { viewModel.setSelectedTab("about") },
                    label = { Text("关于", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Info, contentDescription = "About") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
        ) {
            when (selectedTab) {
                "home" -> DownloaderTab(viewModel)
                "history" -> HistoryTab(
                    viewModel = viewModel,
                    onPlayVideo = playVideo,
                    onOpenUrl = { url -> activeWebViewUrl = url }
                )
                "rankings" -> RankingsTab(
                    viewModel = viewModel,
                    onPlayVideo = playVideo,
                    onOpenUrl = { url -> activeWebViewUrl = url }
                )
                "about" -> AboutTab(
                    viewModel = viewModel,
                    onOpenUrl = { url -> activeWebViewUrl = url },
                    onNavigateToChangelog = { showChangelog = true }
                )
            }

            // Top Bubble Toast Presentation Overlay (animating based on status)
            topBubbleMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Full Screen Video Player Dialog
    activePlaybackUrl?.let { url ->
        VideoPlayerDialog(
            videoUrl = url,
            title = activePlaybackTitle ?: "视频点播",
            onDismiss = {
                activePlaybackUrl = null
                activePlaybackTitle = null
                activePlaybackDownloadAction = null
            },
            onDownloadClick = activePlaybackDownloadAction
        )
    }

    // Full Screen WebView Overlay Dialog
    activeWebViewUrl?.let { url ->
        InAppWebViewDialog(
            url = url,
            onDismiss = { activeWebViewUrl = null }
        )
    }

    // Already Downloaded Warning Dialog
    downloadWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDownloadWarning() },
            title = { Text("提示") },
            text = { Text("您选中的分辨率版本可能已下载过") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmRedownload {
                            Toast.makeText(context, "已加入下载队列并开始下载", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("重新下载")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelDownloadWarning() }
                ) {
                    Text("好的")
                }
            }
        )
    }

    // Dialog for Autoparsed Clipboard Content
    clipboardDetected?.let { entity ->
        AlertDialog(
            onDismissRequest = { viewModel.clearClipboardDetected() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Link Detected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检测到视频链接", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("系统检测到您的剪贴板包含有效的 X 视频分享链接：", fontSize = 13.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entity.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = entity.thumbnailUrl,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.authorName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = entity.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Text("是否立即进入首页查看视频详情并加入下载队列？", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acceptClipboardParsedEntity(entity)
                        viewModel.setSelectedTab("home")
                    }
                ) {
                    Text("开始下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearClipboardDetected() }) {
                    Text("取消", color = MaterialTheme.colorScheme.outline)
                }
            }
        )
    }

    // Changelog Dialog
    if (showChangelog) {
        Dialog(
            onDismissRequest = { showChangelog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ChangelogScreen(viewModel = viewModel, onBack = { showChangelog = false })
            }
        }
    }
}

// ==========================================
// Tab 1: Parser and Downloader Tab
// ==========================================
@Composable
fun DownloaderTab(viewModel: AppViewModel) {
    var urlText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val parseState by viewModel.parseState.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header Description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.ic_launcher_image),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "X-Down",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Input Box Row
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        placeholder = { Text("粘贴 Twitter/X 推文链接...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            viewModel.parseUrl(urlText)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Paste Button
                    IconButton(
                        onClick = {
                            clipboardManager.getText()?.let {
                                urlText = it.text
                                Toast.makeText(context, "已粘贴剪贴板链接", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste Link",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Parse Button Trigger
        item {
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.parseUrl(urlText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !parseState.isParsing,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (parseState.isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解析资源中...")
                } else {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Parse")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解析推文视频", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Parse Result Display Card
        if (parseState.parsedEntity != null) {
            val entity = parseState.parsedEntity!!
            val videos = entity.getVideos()

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Author Metadata row（与历史卡片一致：头像 + 作者信息 + 红色删除按钮）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = entity.authorAvatarUrl,
                                contentDescription = "Author Avatar",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entity.authorName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "@${entity.authorHandle}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.clearParseResult() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除解析结果",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 缩略图左、帖文文案右（与历史卡片一致）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (entity.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = entity.thumbnailUrl,
                                    contentDescription = "Video Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = entity.title,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic grouping and pre-selection
                        // Supported multiples: grouping by videoIndex
                        val grouped = videos.groupBy { it.videoIndex }
                        grouped.forEach { (videoIndex, qualityList) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                if (entity.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = entity.thumbnailUrl,
                                        contentDescription = "Video Paragraph Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = "视频段落 #${videoIndex + 1} 选择清晰度:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Resolution Buttons Selection Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                qualityList.forEach { video ->
                                    val isSelected = parseState.selectedResolutions[videoIndex] == video
                                    Box(
                                        modifier = Modifier
                                            .widthIn(min = 90.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                viewModel.updateSelectedResolution(videoIndex, video)
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = video.quality,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Trigger Download Button
                        Button(
                            onClick = {
                                viewModel.checkAndTriggerDownloads(
                                    entity = entity,
                                    selections = parseState.selectedResolutions,
                                    onSuccessDirectDownload = {
                                        Toast.makeText(context, "已加入下载队列并开始下载", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                coroutineScope.launch {
                                    delay(150)
                                    val count = listState.layoutInfo.totalItemsCount
                                    if (count > 0) {
                                        listState.animateScrollToItem(count - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Download")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("立即下载", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Parsing error feedback
        if (parseState.parseError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = parseState.parseError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Active Range Queue Header
        if (activeTasks.isNotEmpty()) {
            item {
                Text(
                    text = "实时下载队列 (${activeTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(activeTasks) { task ->
                ActiveQueueItemCard(task, viewModel)
            }
        }
    }
}

@Composable
fun ActiveQueueItemCard(task: ActiveDownloadTask, viewModel: AppViewModel) {
    val state by task.state.collectAsStateWithLifecycle()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!task.entity.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = task.entity.thumbnailUrl,
                        contentDescription = "Video Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(52.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Movie File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "作者: @${task.authorHandle} • 清晰度: ${task.qualityLabel}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Control Triggers (Pause, Resume, Stop)
                when (state) {
                    is DownloadState.Downloading -> {
                        IconButton(onClick = { viewModel.pauseTask(task.id) }) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause")
                        }
                    }
                    is DownloadState.Paused, is DownloadState.Failed -> {
                        IconButton(onClick = { viewModel.startTask(task.id) }) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }
                    else -> {}
                }

                IconButton(onClick = { viewModel.removeTask(task.id) }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar and feedback metrics
            var progress = 0f
            var progressText = "准备下载..."
            when (val s = state) {
                is DownloadState.Downloading -> {
                    progress = s.progress
                    val speedDisplay = if (s.speedKbps >= 1024.0) {
                        "${String.format("%.2f", s.speedKbps / 1024.0)} MB/s"
                    } else {
                        "${String.format("%.1f", s.speedKbps)} KB/s"
                    }
                    progressText = "进行中: ${String.format("%.1f", progress * 100)}% ($speedDisplay)"
                }
                is DownloadState.Paused -> {
                    progressText = "已暂停"
                }
                is DownloadState.Success -> {
                    progress = 1f
                    progressText = "成功归档"
                }
                is DownloadState.Failed -> {
                    progressText = "失败: ${s.error}"
                }
                else -> {}
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = progressText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// ==========================================
// Tab 2: offline History List Tab
// ==========================================
@Composable
fun HistoryTab(
    viewModel: AppViewModel,
    onPlayVideo: (String, String, (() -> Unit)?) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val historyList by viewModel.allHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "历史",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "No History",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "空空如也",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyList) { item ->
                    HistoryItemCard(item, viewModel, onPlayVideo, onOpenUrl)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: TweetDownloadEntity,
    viewModel: AppViewModel,
    onPlayVideo: (String, String, (() -> Unit)?) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val paths = item.getLocalFilePaths()
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Author Title Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.authorAvatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onOpenUrl("https://x.com/${item.authorHandle}") },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenUrl("https://x.com/${item.authorHandle}") }
                ) {
                    Text(text = item.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "@${item.authorHandle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }

                // Delete Button
                IconButton(onClick = {
                    viewModel.deleteHistoryItem(item)
                    Toast.makeText(context, "已从本地及历史中移除", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Video Thumbnail on left, description text on right (Clicking opens post URL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpenUrl(item.url) },
                verticalAlignment = Alignment.Top
            ) {
                if (item.thumbnailUrl.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = "Video Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Icon",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = item.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Play / Online Offline Previews
            val videos = item.getVideos()
            val grouped = videos.groupBy { it.videoIndex }

            grouped.forEach { (idx, qualityList) ->
                Text(
                    text = "视频部分 #${idx + 1}:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // List Resolutions buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityList.forEach { video ->
                        val videoIndexInAll = videos.indexOf(video)
                        val localPath = paths[videoIndexInAll]
                        val isDownloaded = localPath != null && File(localPath).exists()
                        val greenColor = Color(0xFF2E7D32)
                        val greenBg = Color(0xFFE8F5E9)
                        val greenText = Color(0xFF1B5E20)

                        Box(
                            modifier = Modifier
                                .widthIn(min = 100.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDownloaded) greenBg else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = if (isDownloaded) greenColor else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (isDownloaded && localPath != null) {
                                        // Offline file Playback (no download button on dialog browser)
                                        onPlayVideo(localPath, "本地离线: ${item.title}", null)
                                    } else {
                                        // Streaming played online (equipped with click download key)
                                        if (video.url.isNotEmpty()) {
                                            onPlayVideo(video.url, "在线缓存: ${item.title}") {
                                                viewModel.startDownload(item, video)
                                            }
                                        } else {
                                            Toast.makeText(context, "解析地址缺失，无法播放", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Wifi,
                                    contentDescription = "Play",
                                    tint = if (isDownloaded) greenText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${video.quality} ${if (isDownloaded) "(已下载)" else "(预览)"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDownloaded) greenText else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Tab 3: Author Rankings Dashboard Tab
// ==========================================
@Composable
fun RankingsTab(
    viewModel: AppViewModel,
    onPlayVideo: (String, String, (() -> Unit)?) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val rankingList by viewModel.rankings.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyList by viewModel.allHistory.collectAsStateWithLifecycle(initialValue = emptyList())

    // Keep track of expanded author handles
    var expandedHandles by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "排行",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "根据下载成功归档的视频数量自动排名",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (rankingList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Empty Stats",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "空空如也",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(rankingList) { index, author ->
                    val isExpanded = expandedHandles.contains(author.authorHandle)
                    val authorDownloads = historyList.filter { 
                        it.authorHandle == author.authorHandle && it.downloadStatus == "Success" 
                    }

                    RankCardItem(
                        index = index,
                        author = author,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedHandles = if (isExpanded) {
                                expandedHandles - author.authorHandle
                            } else {
                                expandedHandles + author.authorHandle
                            }
                        },
                        authorDownloads = authorDownloads,
                        onPlayVideo = onPlayVideo,
                        onOpenUrl = onOpenUrl,
                        onDownloadClick = { targetEntity, videoQuality ->
                            viewModel.startDownload(targetEntity, videoQuality)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RankCardItem(
    index: Int,
    author: AuthorRanking,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    authorDownloads: List<TweetDownloadEntity>,
    onPlayVideo: (String, String, (() -> Unit)?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadClick: (TweetDownloadEntity, VideoQuality) -> Unit
) {
    val context = LocalContext.current
    val medalColor = when (index) {
        0 -> Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFB700))) // Gold
        1 -> Brush.horizontalGradient(listOf(Color(0xFFC0C0C0), Color(0xFF9E9E9E))) // Silver
        2 -> Brush.horizontalGradient(listOf(Color(0xFFCD7F32), Color(0xFFB86F28))) // Bronze
        else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Medal Rank number Box representation
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (index < 3) Modifier.background(medalColor)
                            else Modifier.background(MaterialTheme.colorScheme.outlineVariant)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (index < 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Author Avatar Image
                AsyncImage(
                    model = author.authorAvatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onOpenUrl("https://x.com/${author.authorHandle}") },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = author.authorName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onOpenUrl("https://x.com/${author.authorHandle}")
                        }
                    ) {
                        Text(
                            text = "@${author.authorHandle}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open X Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Download Volume statistics
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "${author.downloadCount} 个视频",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Expand Collapse Indicator
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand details",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expanded downloads column
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (authorDownloads.isEmpty()) {
                        Text(
                            text = "暂无成功下载记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        authorDownloads.forEach { dlItem ->
                            ExpandedDownloadItem(
                                dlItem = dlItem,
                                onPlayVideo = onPlayVideo,
                                onOpenUrl = onOpenUrl,
                                onDownloadClick = { video -> onDownloadClick(dlItem, video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedDownloadItem(
    dlItem: TweetDownloadEntity,
    onPlayVideo: (String, String, (() -> Unit)?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadClick: (VideoQuality) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val paths = dlItem.getLocalFilePaths()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onOpenUrl(dlItem.url) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dlItem.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = dlItem.thumbnailUrl,
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dlItem.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val videos = dlItem.getVideos()
            val downloadedVideos = videos.filter { video ->
                val videoIndexInAll = videos.indexOf(video)
                val localPath = paths[videoIndexInAll]
                localPath != null && File(localPath).exists()
            }
            
            if (downloadedVideos.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    downloadedVideos.forEach { video ->
                        val videoIndexInAll = videos.indexOf(video)
                        val localPath = paths[videoIndexInAll]
                        val greenColor = Color(0xFF2E7D32)
                        val greenBg = Color(0xFFE8F5E9)
                        val greenText = Color(0xFF1B5E20)
                        
                        Box(
                            modifier = Modifier
                                .widthIn(min = 80.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(greenBg)
                                .border(
                                    width = 1.dp,
                                    color = greenColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    if (localPath != null) {
                                        onPlayVideo(localPath, "本地离线: ${dlItem.title}", null)
                                    } else {
                                        Toast.makeText(context, "本地文件不存在", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded Icon",
                                    tint = greenText,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = video.quality,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = greenText
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "无本地下载的版本",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ==========================================
// Full Screen Overlay Dialog Player (ExoPlayer)
// ==========================================
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    title: String,
    onDismiss: () -> Unit,
    onDownloadClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val exoPlayer = remember(videoUrl) {
        try {
            if (videoUrl.isBlank()) {
                null
            } else {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    if (exoPlayer == null) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "视频文件不存在或播放地址已失效", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(top = 28.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Floating Download Button for online streaming previews
            if (onDownloadClick != null) {
                Button(
                    onClick = {
                        onDownloadClick()
                        Toast.makeText(context, "已添加到下载队列并开始下载", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Video",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下载当前分辨率视频", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// In-App WebView Browser Dialog Component
// ==========================================
@Composable
fun InAppWebViewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    var canGoBack by remember { mutableStateOf(false) }
    var webViewRefByLambda by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var showMenu by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("内页浏览") }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canGoBack) {
                        IconButton(onClick = { webViewRefByLambda?.goBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "后退",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制链接") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("URL", currentUrl)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("用浏览器打开") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "分享链接"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                
                                // Compatibility setting for mixed secure/insecure scripts if needed
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                
                                // Evade custom/webview detection by using a desktop-grade/premium mobile User-Agent
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                                
                                // Accept cookie and third-party cookies for cross-domain federated login (Google/Apple login support)
                                try {
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageStarted(view: android.webkit.WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, pageUrl, favicon)
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: android.webkit.WebView?, pageUrl: String?) {
                                        super.onPageFinished(view, pageUrl)
                                        isLoading = false
                                        canGoBack = view?.canGoBack() ?: false
                                        if (pageUrl != null) {
                                            currentUrl = pageUrl
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: android.webkit.WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        isLoading = false
                                    }
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
                                        super.onReceivedTitle(view, title)
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }
                                loadUrl(url)
                                webViewRefByLambda = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "页面加载中, 请稍候...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
