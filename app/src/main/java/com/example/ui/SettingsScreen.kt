package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ThemeController
import com.example.ui.theme.ThemePreset

// 色块预览色（上半浅色主题 / 下半深色主题），与 AppTheme 配色一致
private data class SwatchPreview(val preset: ThemePreset, val light: Color?, val dark: Color?)

private val SWATCHES = listOf(
    SwatchPreview(ThemePreset.SYSTEM, null, null),
    SwatchPreview(ThemePreset.X_BLACK, Color(0xFF000000), Color(0xFFE7E9EA)),
    SwatchPreview(ThemePreset.FACEBOOK, Color(0xFF1877F2), Color(0xFFA8C7FA)),
    SwatchPreview(ThemePreset.WHATSAPP, Color(0xFF25D366), Color(0xFF7BE0A0)),
    SwatchPreview(ThemePreset.REDDIT, Color(0xFFFF4500), Color(0xFFFFA385)),
    SwatchPreview(ThemePreset.TITANIUM, Color(0xFF64748B), Color(0xFFA9B7C9)),
    SwatchPreview(ThemePreset.MEITUAN, Color(0xFFF6A200), Color(0xFFFCC763)),
    SwatchPreview(ThemePreset.COFFEE, Color(0xFF6F4E37), Color(0xFFB08968)),
    SwatchPreview(ThemePreset.TIFFANY, Color(0xFF0ABAB5), Color(0xFF63E0DC)),
    SwatchPreview(ThemePreset.SAKURA, Color(0xFFF783AC), Color(0xFFFBB6D0)),
    SwatchPreview(ThemePreset.LAVENDER, Color(0xFF7C6BD1), Color(0xFFB3A8EA)),
    SwatchPreview(ThemePreset.CUSTOM, null, null)
)

private val DEFAULT_PATH_TEXT = "内部存储/Movies/X-Down"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val saveEnabled by viewModel.saveLocationEnabled.collectAsStateWithLifecycle()
    val saveUri by viewModel.saveTreeUri.collectAsStateWithLifecycle()

    // 系统目录选择器（SAF，支持新建文件夹）
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setSaveTreeUri(uri.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更多设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ==================== 自定义主题 ====================
            Text("自定义主题", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            SWATCHES.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { preview ->
                        ThemeSwatch(
                            preview = preview,
                            selected = themeConfig.preset == preview.preset,
                            customColor = themeConfig.customColor,
                            onClick = {
                                viewModel.selectThemePreset(preview.preset)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 自定义取色器
            if (themeConfig.preset == ThemePreset.CUSTOM) {
                HsvColorPicker(
                    initialColor = themeConfig.customColor,
                    onApply = { color -> viewModel.selectCustomTheme(color) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // ==================== 自定义保存位置 ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自定义保存位置", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = saveEnabled,
                    onCheckedChange = { viewModel.setSaveLocationEnabled(it) }
                )
            }

            val displayPath = when {
                saveEnabled && !saveUri.isNullOrEmpty() -> decodeTreePath(saveUri!!)
                else -> DEFAULT_PATH_TEXT
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = saveEnabled) {
                        treeLauncher.launch(null)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = displayPath,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (saveEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (saveEnabled) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun decodeTreePath(uriStr: String): String {
    return try {
        val seg = Uri.parse(uriStr).lastPathSegment ?: return DEFAULT_PATH_TEXT
        seg.replace("primary:", "内部存储/").replace(":", "/")
    } catch (e: Exception) {
        DEFAULT_PATH_TEXT
    }
}

@Composable
private fun ThemeSwatch(
    preview: SwatchPreview,
    selected: Boolean,
    customColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(
                width = if (selected) 2.5.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        when (preview.preset) {
            ThemePreset.SYSTEM -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B),
                                    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0),
                                    Color(0xFFF44336)
                                )
                            )
                        )
                )
            }
            ThemePreset.CUSTOM -> {
                val base = customColor ?: Color(0xFF1D9BF0)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF16181C)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize(0.72f)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                        Color(0xFFFF0000)
                                    )
                                )
                            )
                            .border(3.dp, Color.White, CircleShape)
                    )
                    // 当前自定义色小圆点
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(base)
                            .border(2.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(preview.light ?: Color.Transparent)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(preview.dark ?: Color.Transparent)
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** HSV 取色器：色相条 + 饱和度/明度面板 + 应用按钮 */
@Composable
private fun HsvColorPicker(initialColor: Color?, onApply: (Color) -> Unit) {
    val density = LocalDensity.current
    val initHsv = remember(initialColor) {
        FloatArray(3).also { arr ->
            val argb = (initialColor ?: Color(0xFF1D9BF0)).toArgb()
            android.graphics.Color.colorToHSV(argb, arr)
        }
    }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var value by remember { mutableStateOf(initHsv[2]) }

    val currentColor = Color.hsv(hue, sat, value)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 饱和度/明度面板
            var svW by remember { mutableStateOf(0) }
            var svH by remember { mutableStateOf(0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f)))
                    )
                    .onSizeChanged { svW = it.width; svH = it.height }
                    .pointerInput(hue) {
                        fun update(offset: Offset) {
                            sat = (offset.x / size.width).coerceIn(0f, 1f)
                            value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        }
                        detectTapGestures { update(it) }
                    }
                    .pointerInput(hue) {
                        fun update(offset: Offset) {
                            sat = (offset.x / size.width).coerceIn(0f, 1f)
                            value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        }
                        detectDragGestures { change, _ ->
                            update(change.position)
                            change.consume()
                        }
                    }
            ) {
                // 黑色遮罩（明度）
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )
                )
                // 指示点
                if (svW > 0) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (sat * svW).toDp() } - 8.dp,
                                y = with(density) { ((1f - value) * svH).toDp() } - 8.dp
                            )
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, Color.White, CircleShape)
                    )
                }
            }

            // 色相条
            var hueW by remember { mutableStateOf(0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            (0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) }
                        )
                    )
                    .onSizeChanged { hueW = it.width }
                    .pointerInput(Unit) {
                        fun update(offset: Offset) {
                            hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                        }
                        detectTapGestures { update(it) }
                    }
                    .pointerInput(Unit) {
                        fun update(offset: Offset) {
                            hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                        }
                        detectDragGestures { change, _ ->
                            update(change.position)
                            change.consume()
                        }
                    }
            ) {
                if (hueW > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { (hue / 360f * hueW).toDp() } - 9.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, Color.White, CircleShape)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = String.format("#%06X", 0xFFFFFF and currentColor.toArgb()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { onApply(currentColor) }) {
                    Text("应用", fontSize = 12.sp)
                }
            }
        }
    }
}
