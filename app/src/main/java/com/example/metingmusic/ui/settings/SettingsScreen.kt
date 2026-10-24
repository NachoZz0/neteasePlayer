package com.example.metingmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metingmusic.data.local.Appearance
import com.example.metingmusic.data.local.ThemeMode
import com.example.metingmusic.ui.theme.clickableNoRipple
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.example.metingmusic.ui.theme.rememberGlassSupported
import com.example.metingmusic.ui.theme.glassCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
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

/**
 * 设置页：深浅色、背景图、主题色、磨砂玻璃。
 *
 * 所有改动都是即时生效并持久化的，没有「保存」按钮 —— 这类外观设置改错了
 * 用户想立刻看到效果，多一步确认反而碍事。
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

    LazyColumn(
        contentPadding = contentBottomPadding,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)
            )
        }

        // ---------------- 深浅色 ----------------
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

        // ---------------- 背景 ----------------
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
                SettingRow(
                    title = "恢复默认背景",
                    subtitle = "删掉已保存的背景图"
                ) {
                    TextButton(onClick = onClearBackground) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("恢复")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------------- 主题色 ----------------
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
                // 默认色 + 预设 + 取色结果
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

        // ---------------- 底部栏 ----------------
        item {
            SectionHeader(icon = Icons.Default.Image, title = "模糊与底部栏")
            SettingRow(
                title = "全局模糊",
                subtitle = if (glassSupported) {
                    "背景整体模糊，卡片 / 歌单 / 按钮变成玻璃质感"
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
                subtitle = if (glassSupported) {
                    "仅悬浮样式生效"
                } else {
                    "当前设备不支持，只能半透明"
                }
            ) {
                Switch(
                    checked = appearance.bottomFrosted,
                    onCheckedChange = onBottomFrostedChange
                )
            }
            if (appearance.bottomFrosted) {
                // 模糊强度：滑杆比几个预设档位好用，用户能自己找到「糊得刚好」的位置
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .glassCard(corner = 14.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "模糊强度", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${appearance.blurRadiusDp.roundToInt()} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = when {
                            appearance.blurRadiusDp < 12f -> "几乎看不出模糊"
                            appearance.blurRadiusDp < 30f -> "轻微磨砂，还能看出内容轮廓"
                            appearance.blurRadiusDp < 55f -> "标准毛玻璃"
                            else -> "重度模糊，只剩色块"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = appearance.blurRadiusDp,
                        onValueChange = onBlurRadiusChange,
                        valueRange = 0f..80f,
                        steps = 15,
                        enabled = glassSupported
                    )
                }
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

        // ---------------- 关于 ----------------
        item {
            SectionHeader(icon = Icons.Default.Info, title = "关于")
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    text = "版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(text = versionName, style = MaterialTheme.typography.bodyMedium)
            }
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
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    text = "音源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(text = "Meting API", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
        }
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
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
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = ringColor,
                shape = CircleShape
            )
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
