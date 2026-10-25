package com.example.metingmusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metingmusic.data.local.Appearance
import com.example.metingmusic.data.local.ThemeMode
import com.example.metingmusic.ui.theme.clickableNoRipple
import com.example.metingmusic.ui.theme.glassCard
import com.example.metingmusic.ui.theme.rememberGlassSupported
import kotlin.math.roundToInt

/** 检查更新的界面状态。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/** 预设主题色。第一项是默认的淡蓝。 */
private val PRESET_COLORS = listOf(
    0xFF5B9BD5.toInt(),
    0xFF7E57C2.toInt(),
    0xFF2E9E7E.toInt(),
    0xFFE08A3C.toInt(),
    0xFFD4576E.toInt(),
    0xFF4C6EF5.toInt(),
    0xFF8C6D4F.toInt(),
    0xFF3E4C59.toInt()
)

/** 开源仓库地址。 */
private const val REPO_URL = "https://github.com/NachoZz0/neteasePlayer"

/**
 * 设置页的三个层级。
 *
 * 用枚举而不是几个布尔量：层级是互斥的，用布尔量会出现「同时打开两个子页」的非法状态。
 */
private enum class SettingsPage { Main, Theme, Background, Player }

/**
 * 设置页。
 *
 * 一级只放入口（主题设置 / 背景设置 / 关于），具体选项都收进二级 ——
 * 之前一屏二十多项，找「背景模糊度」要滚半天。
 *
 * 所有改动即时生效并持久化，没有「保存」按钮：这类外观设置用户想立刻看到效果，
 * 多一步确认反而碍事。
 */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    /** 当前背景图算出来的主色；没有背景图时为 null。 */
    extractedAccent: Int?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onAccentChange: (Int?) -> Unit,
    onAccentFromBackgroundChange: (Boolean) -> Unit,
    onGlobalBlurChange: (Boolean) -> Unit,
    onBottomFrostedChange: (Boolean) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onFloatingNavBarChange: (Boolean) -> Unit,
    onPlayerImmersiveChange: (Boolean) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundAlphaChange: (Float) -> Unit,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    contentBottomPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val glassSupported = rememberGlassSupported()
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    var page by remember { mutableStateOf(SettingsPage.Main) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // 子页从右边推进来，回退时反向 —— 和系统设置的手感一致
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (
                slideInHorizontally(tween(260)) { width -> dir * width / 6 } +
                    fadeIn(tween(220))
                ) togetherWith (
                slideOutHorizontally(tween(260)) { width -> -dir * width / 6 } +
                    fadeOut(tween(160))
                )
        },
        label = "settings-page"
    ) { current ->
        when (current) {
            SettingsPage.Main -> MainPage(
                appearance = appearance,
                versionName = versionName,
                updateState = updateState,
                onCheckUpdate = onCheckUpdate,
                onOpenTheme = { page = SettingsPage.Theme },
                onOpenBackground = { page = SettingsPage.Background },
                onOpenPlayer = { page = SettingsPage.Player },
                onOpenRepo = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                        )
                    }
                },
                contentBottomPadding = contentBottomPadding
            )

            SettingsPage.Theme -> ThemePage(
                appearance = appearance,
                glassSupported = glassSupported,
                onThemeModeChange = onThemeModeChange,
                onGlobalBlurChange = onGlobalBlurChange,
                onBottomFrostedChange = onBottomFrostedChange,
                onBlurRadiusChange = onBlurRadiusChange,
                onFloatingNavBarChange = onFloatingNavBarChange,
                onBack = { page = SettingsPage.Main },
                contentBottomPadding = contentBottomPadding
            )

            SettingsPage.Player -> PlayerSettingsPage(
                appearance = appearance,
                onPlayerImmersiveChange = onPlayerImmersiveChange,
                onBack = { page = SettingsPage.Main },
                contentBottomPadding = contentBottomPadding
            )

            SettingsPage.Background -> BackgroundPage(
                appearance = appearance,
                extractedAccent = extractedAccent,
                onPickBackground = onPickBackground,
                onClearBackground = onClearBackground,
                onAccentChange = onAccentChange,
                onAccentFromBackgroundChange = onAccentFromBackgroundChange,
                onBackgroundBlurChange = onBackgroundBlurChange,
                onBackgroundAlphaChange = onBackgroundAlphaChange,
                onBack = { page = SettingsPage.Main },
                contentBottomPadding = contentBottomPadding
            )
        }
    }
}

// ---------------------------------------------------------------- 一级

@Composable
private fun MainPage(
    appearance: Appearance,
    versionName: String,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenRepo: () -> Unit,
    contentBottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = contentBottomPadding,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item { PageTitle("设置") }

        item {
            EntryRow(
                icon = Icons.Default.DarkMode,
                title = "主题设置",
                subtitle = "深浅色 · 磨砂质感 · 底栏模糊",
                onClick = onOpenTheme
            )
            EntryRow(
                icon = Icons.Default.Wallpaper,
                title = "背景设置",
                subtitle = "背景图 · 模糊度 · 透明度 · 主题色",
                onClick = onOpenBackground
            )
            EntryRow(
                icon = Icons.Default.PlayArrow,
                title = "播放设置",
                subtitle = "播放页视觉与交互",
                onClick = onOpenPlayer
            )
            Spacer(Modifier.height(22.dp))
        }

        // ---------------- 关于 ----------------
        item {
            SectionHeader(icon = Icons.Default.Info, title = "关于")
            InfoRow(label = "版本", value = versionName)
            SettingRow(
                title = "检查更新",
                subtitle = when (updateState) {
                    UpdateUiState.Idle -> "从 GitHub Release 获取最新版本"
                    UpdateUiState.Checking -> "正在检查…"
                    UpdateUiState.UpToDate -> "已经是最新版本"
                    is UpdateUiState.Failed -> updateState.message
                }
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(onClick = onCheckUpdate) { Text("检查") }
                }
            }
            InfoRow(label = "音源", value = "Meting API")
            SettingRow(
                title = "开源地址",
                subtitle = "github.com/NachoZz0/neteasePlayer"
            ) {
                TextButton(onClick = onOpenRepo) { Text("打开") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------------------------------------------------------------- 主题设置

@Composable
private fun ThemePage(
    appearance: Appearance,
    glassSupported: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onGlobalBlurChange: (Boolean) -> Unit,
    onBottomFrostedChange: (Boolean) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onFloatingNavBarChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = contentBottomPadding,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item {
            SubPageTitle(title = "主题设置", onBack = onBack)
        }

        // ---- 深浅色
        item {
            SectionHeader(icon = Icons.Default.DarkMode, title = "深浅色")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = appearance.themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickableNoRipple { onThemeModeChange(mode) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // ---- 磨砂与底栏
        item {
            SectionHeader(icon = Icons.Default.BlurOn, title = "磨砂与底栏")
            SettingRow(
                title = "全局磨砂质感",
                subtitle = if (glassSupported) {
                    "卡片 / 歌单行 / 按钮变成半透明玻璃（不改变背景）"
                } else {
                    "当前设备不支持（需要 Android 13+ 且内存足够）"
                }
            ) {
                Switch(
                    checked = appearance.globalBlur,
                    onCheckedChange = onGlobalBlurChange,
                    enabled = glassSupported
                )
            }
            SettingRow(
                title = "底栏高级模糊",
                subtitle = if (glassSupported) "仅悬浮样式生效" else "当前设备不支持，只能半透明"
            ) {
                Switch(
                    checked = appearance.bottomFrosted,
                    onCheckedChange = onBottomFrostedChange
                )
            }
            if (appearance.bottomFrosted) {
                SliderRow(
                    title = "模糊强度",
                    valueLabel = "${appearance.blurRadiusDp.roundToInt()} dp",
                    description = when {
                        appearance.blurRadiusDp <= 0f -> "完全透明，只保留描边"
                        appearance.blurRadiusDp < 12f -> "几乎看不出模糊"
                        appearance.blurRadiusDp < 30f -> "轻微磨砂，还能看出内容轮廓"
                        appearance.blurRadiusDp < 55f -> "标准毛玻璃"
                        else -> "重度模糊，只剩色块"
                    },
                    value = appearance.blurRadiusDp,
                    range = 0f..80f,
                    steps = 15,
                    enabled = glassSupported,
                    onValueChange = onBlurRadiusChange
                )
            }
            SettingRow(
                title = "悬浮样式",
                subtitle = if (appearance.floatingNavBar) {
                    "胶囊浮在内容之上，列表可以滚到最底"
                } else {
                    "贴底整条导航栏，和内容分界更明确"
                }
            ) {
                Switch(
                    checked = appearance.floatingNavBar,
                    onCheckedChange = onFloatingNavBarChange
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

// ---------------------------------------------------------------- 播放设置

@Composable
private fun PlayerSettingsPage(
    appearance: Appearance,
    onPlayerImmersiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = contentBottomPadding,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item { SubPageTitle(title = "播放设置", onBack = onBack) }

        item {
            SectionHeader(icon = Icons.Default.PlayArrow, title = "播放页")
            SettingRow(
                title = "沉浸式视觉",
                subtitle = if (appearance.playerImmersive) {
                    "底部控制区变成圆角玻璃面板"
                } else {
                    "朴素的纯色控制区"
                }
            ) {
                Switch(
                    checked = appearance.playerImmersive,
                    onCheckedChange = onPlayerImmersiveChange
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

// ---------------------------------------------------------------- 背景设置

@Composable
private fun BackgroundPage(
    appearance: Appearance,
    extractedAccent: Int?,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onAccentChange: (Int?) -> Unit,
    onAccentFromBackgroundChange: (Boolean) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundAlphaChange: (Float) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = contentPaddingSafe(contentBottomPadding),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item { SubPageTitle(title = "背景设置", onBack = onBack) }

        // ---- 背景图
        item {
            SectionHeader(icon = Icons.Default.Wallpaper, title = "背景图")
            SettingRow(
                title = if (appearance.backgroundPath == null) "使用内置插画" else "已设置自定义背景",
                subtitle = if (appearance.backgroundPath == null) {
                    "从相册选一张图，会复制到应用内保存"
                } else {
                    "图片已保存在应用目录，换图会替换掉旧的"
                }
            ) {
                TextButton(onClick = onPickBackground) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (appearance.backgroundPath == null) "选择" else "更换")
                }
            }
            if (appearance.backgroundPath != null) {
                SettingRow(title = "恢复默认背景", subtitle = "删掉已保存的背景图") {
                    TextButton(onClick = onClearBackground) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- 模糊度 / 透明度
        item {
            SectionHeader(icon = Icons.Default.BlurOn, title = "背景效果")
            SliderRow(
                title = "模糊度",
                valueLabel = "${appearance.bgBlurDp.roundToInt()} dp",
                description = when {
                    appearance.bgBlurDp <= 0f -> "不模糊"
                    appearance.bgBlurDp < 10f -> "轻微虚化，细节还在"
                    appearance.bgBlurDp < 24f -> "明显虚化，适合花色复杂的图"
                    else -> "重度虚化，只剩色块"
                },
                value = appearance.bgBlurDp,
                range = 0f..40f,
                steps = 19,
                onValueChange = onBackgroundBlurChange
            )
            SliderRow(
                title = "透明度",
                valueLabel = "${(appearance.bgAlpha * 100).roundToInt()}%",
                description = when {
                    appearance.bgAlpha >= 0.99f -> "完全不透明"
                    appearance.bgAlpha >= 0.6f -> "稍微透出一点底色"
                    else -> "很淡，几乎只剩底色（最低 10%）"
                },
                value = appearance.bgAlpha,
                range = 0.1f..1f,
                steps = 16,
                onValueChange = onBackgroundAlphaChange
            )
            Spacer(Modifier.height(22.dp))
        }

        // ---- 主题色
        item {
            SectionHeader(icon = Icons.Default.Palette, title = "主题色")
            SettingRow(
                title = "从背景图取色",
                subtitle = if (appearance.backgroundPath == null) {
                    "先设置一张背景图才能取色"
                } else {
                    "自动挑出背景里最显眼的颜色当主题色"
                }
            ) {
                Switch(
                    checked = appearance.accentFromBackground,
                    onCheckedChange = onAccentFromBackgroundChange,
                    enabled = appearance.backgroundPath != null
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val swatches = buildList {
                    add(null)
                    if (extractedAccent != null) add(extractedAccent)
                    addAll(PRESET_COLORS)
                }
                swatches.forEach { argb ->
                    ColorSwatch(
                        argb = argb,
                        selected = appearance.accentColor == argb,
                        onClick = { onAccentChange(argb) }
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

// ---------------------------------------------------------------- 公共组件

/** 二级页底部多留一点，避免最后一项贴着底栏。 */
private fun contentPaddingSafe(base: PaddingValues): PaddingValues = base

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)
    )
}

/** 二级页标题栏：返回箭头 + 标题。 */
@Composable
private fun SubPageTitle(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** 一级页里的「进入二级」入口。 */
@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .glassCard(corner = 16.dp)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 一行「标题 + 说明 + 右侧控件」。 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .glassCard(corner = 14.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 一行只读信息。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 带标题、数值、说明的滑杆。 */
@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .glassCard(corner = 14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

/**
 * 一个色块。
 *
 * [argb] 为 null 表示「默认色」—— 用一个带斜线的圆表示，而不是硬塞一个蓝色，
 * 免得用户以为「默认」就是某个具体颜色。
 */
@Composable
private fun ColorSwatch(
    argb: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val size = 30.dp
    val ringColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(argb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(width = if (selected) 2.dp else 1.dp, color = ringColor, shape = CircleShape)
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            argb == null -> Text(
                text = "默认",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            selected -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
