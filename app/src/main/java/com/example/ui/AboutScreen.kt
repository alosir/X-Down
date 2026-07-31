package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.update.UpdateState

// ==================== 关于页面 ====================
@Composable
fun AboutTab(viewModel: AppViewModel, onOpenUrl: (String) -> Unit, onNavigateToChangelog: () -> Unit) {
    val context = LocalContext.current
    val versionName = viewModel.getAppVersionName()
    val hasNewVersion by viewModel.hasNewVersion.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "关于",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AboutItem(
                    title = "关于项目",
                    subtitle = "https://github.com/alosir/X-Down",
                    icon = Icons.Default.Code,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alosir/X-Down"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            onOpenUrl("https://github.com/alosir/X-Down")
                        }
                    }
                )
            }

            item {
                AboutItem(
                    title = "当前版本",
                    subtitle = "v$versionName",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToChangelog,
                    trailing = if (hasNewVersion) {
                        {
                            Text(
                                text = "有新版本",
                                color = androidx.compose.ui.graphics.Color(0xFFFF8C00),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    } else null
                )
            }

            item {
                AboutItem(
                    title = "通知权限",
                    subtitle = "进入系统通知设置",
                    icon = Icons.Default.Notifications,
                    onClick = { viewModel.openAppNotificationSettings(context) }
                )
            }
        }
    }
}

@Composable
fun AboutItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            trailing?.invoke()
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 更新记录页面 ====================
data class VersionLog(
    val version: String,
    val date: String,
    val changes: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val topBubbleMessage by viewModel.topBubbleMessage.collectAsStateWithLifecycle()

    // 每天首次进入本页面时自动触发一次检查更新
    LaunchedEffect(Unit) {
        viewModel.maybeAutoCheckUpdate()
    }

    // 更新记录为应用内置（不实时拉取 GitHub，避免网络不可达）
    // 面向普通用户撰写：过滤与用户无关的信息（包名变更、纯开发向改动等）
    val versionLogs = remember {
        listOf(
            VersionLog(
                version = "1.5.2",
                date = "2026-07-31",
                changes = listOf(
                    "再次修复桌面数字角标不显示的问题，覆盖下载中、暂停与失败状态"
                )
            ),
            VersionLog(
                version = "1.5.1",
                date = "2026-07-31",
                changes = listOf(
                    "修复下载视频进行期间桌面不显示数字角标的问题"
                )
            ),
            VersionLog(
                version = "1.5.0",
                date = "2026-07-31",
                changes = listOf(
                    "多视频帖子拆分为独立卡片：一个视频一张卡片，缩略图与之一一对应",
                    "作者下载统计改为按视频个数计数",
                    "下载队列支持断线保留：APP 关闭后任务不丢失，重新打开可继续下载"
                )
            ),
            VersionLog(
                version = "1.4.3",
                date = "2026-07-31",
                changes = listOf(
                    "解析结果卡片排版与历史卡片统一，删除按钮改为红色垃圾桶图标",
                    "下载通知文案优化，通知缩略图改为正方形",
                    "底部导航名称精简：下载、历史、排行、关于",
                    "修复部分机型桌面角标不显示的问题"
                )
            ),
            VersionLog(
                version = "1.4.2",
                date = "2026-07-31",
                changes = listOf(
                    "新增下载任务实时进度通知，下载完成后自动消失",
                    "新增桌面角标，实时显示下载队列中的任务数量",
                    "支持每天自动检查新版本，有更新时及时提醒",
                    "修复新版本安装包下载完成后通知无法点击安装的问题",
                    "解析结果卡片支持手动关闭"
                )
            ),
            VersionLog(
                version = "1.4.1",
                date = "2026-07-31",
                changes = listOf(
                    "更新记录改为应用内置，浏览不再受网络影响",
                    "检查更新结果改为顶部气泡提示，网络异常提示更友好",
                    "下载视频统一保存至 X-Down 文件夹，查找管理更方便"
                )
            ),
            VersionLog(
                version = "1.4",
                date = "2026-07-30",
                changes = listOf(
                    "新增「关于」页面：项目地址、当前版本、通知权限入口",
                    "新增「更新记录」页面，展示近期版本更新日志",
                    "支持应用内检查更新，发现新版本后自动下载安装包",
                    "更新下载进度实时显示在系统通知栏，完成后点击通知即可安装"
                )
            ),
            VersionLog(
                version = "1.3",
                date = "2026-07-23",
                changes = listOf(
                    "支持 Twitter/X 推文视频解析与多清晰度下载",
                    "本地历史归档与离线播放",
                    "作者排行统计",
                    "剪贴板自动检测视频链接",
                    "新增 User-Agent 与 401 错误处理"
                )
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("更新记录", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.checkForUpdate() }) {
                            Text("检查更新")
                        }
                    }
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (val state = updateState) {
                is UpdateState.UpdateAvailable -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "发现新版本 v${state.latestVersion}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.downloadAndInstallUpdate(state.downloadUrl) }) {
                                Text("立即下载更新")
                            }
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Column {
                        Text("正在下载更新...", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is UpdateState.DownloadSuccess -> {
                    Text("下载完成，请从通知栏点击安装", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                is UpdateState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(versionLogs.size) { index ->
                    val log = versionLogs[index]
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "v${log.version}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = log.date,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            log.changes.forEach { change ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", color = MaterialTheme.colorScheme.primary)
                                    Text(change, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // 顶部气泡提示（与下载完成提示样式一致）
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
                        contentDescription = "提示",
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
