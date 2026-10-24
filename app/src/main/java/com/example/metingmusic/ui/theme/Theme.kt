package com.example.metingmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.metingmusic.data.local.Appearance
import com.example.metingmusic.data.local.ThemeMode

/**
 * 磨砂玻璃开关。
 *
 * 只作用于底部那一整块（导航栏 + 迷你播放器）—— 列表行逐个做模糊会掉帧，
 * 而且视觉上也没必要。
 */
@Immutable
data class Frosting(
    val navBar: Boolean = false
)

val LocalFrosting = staticCompositionLocalOf { Frosting() }

/** 当前外观设置，供需要读取背景图等信息的组件使用。 */
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/**
 * 应用主题。
 *
 * 配色由「深浅色模式 + 主题色」共同决定：主题色只覆盖 primary 系列，
 * 其余中性色保持原本的灰阶 —— 这样换主题色只是换掉强调色，
 * 不会把整个界面染成一片同色。
 */
@Composable
fun MetingMusicTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val scheme = remember(dark, appearance.accentColor) {
        val base = if (dark) DarkColors else LightColors
        appearance.accentColor?.let { argb -> applyAccent(base, Color(argb), dark) } ?: base
    }

    val frosting = remember(appearance.bottomFrosted) {
        Frosting(navBar = appearance.bottomFrosted)
    }

    CompositionLocalProvider(
        LocalFrosting provides frosting,
        LocalAppearance provides appearance
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 把主题色套到基础配色上。
 *
 * 只改 primary 家族。`primaryContainer` 用「底色和主题色混合」得到，
 * 这样深浅色模式下都能保持柔和，而不是硬编码一个颜色。
 */
private fun applyAccent(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme =
    base.copy(
        primary = accent,
        onPrimary = accent.onColorFor(),
        primaryContainer = lerp(base.surface, accent, if (dark) 0.32f else 0.18f),
        onPrimaryContainer = lerp(base.onSurface, accent, if (dark) 0.45f else 0.30f),
        // 次级色跟着主色微调，避免主色很跳而次级还是原来的蓝，看着不协调
        secondary = lerp(accent, base.onSurfaceVariant, 0.45f)
    )

/**
 * 配色：中性灰打底 + 淡蓝点缀。
 *
 * 灰负责「安静」（背景、次级文字、分隔线），彩色只用在需要用户注意的地方
 * （主按钮、当前歌词、选中态），这样界面不会花，视线也能落到重点上。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B9BD5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E6F7),
    onPrimaryContainer = Color(0xFF10344F),

    secondary = Color(0xFF6B7A88),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3EA),
    onSecondaryContainer = Color(0xFF2A333B),

    tertiary = Color(0xFF7E96A8),
    onTertiary = Color(0xFFFFFFFF),

    background = Color(0xFFEEF1F4),
    onBackground = Color(0xFF1E252B),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF1E252B),
    surfaceVariant = Color(0xFFDFE5EB),
    onSurfaceVariant = Color(0xFF4C5762),

    surfaceContainer = Color(0xFFF1F4F7),
    surfaceContainerHigh = Color(0xFFE9EDF1),
    surfaceContainerLow = Color(0xFFF6F8FA),

    outline = Color(0xFF8C97A2),
    outlineVariant = Color(0xFFC6CED6),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FBCE0),
    onPrimary = Color(0xFF0C2A42),
    primaryContainer = Color(0xFF23455F),
    onPrimaryContainer = Color(0xFFD3E6F7),

    secondary = Color(0xFF9AA7B4),
    onSecondary = Color(0xFF1B242C),
    secondaryContainer = Color(0xFF333D46),
    onSecondaryContainer = Color(0xFFDCE3EA),

    tertiary = Color(0xFF93A9B9),
    onTertiary = Color(0xFF1B242C),

    background = Color(0xFF14181C),
    onBackground = Color(0xFFE2E7EC),
    surface = Color(0xFF1B2025),
    onSurface = Color(0xFFE2E7EC),
    surfaceVariant = Color(0xFF333B43),
    onSurfaceVariant = Color(0xFFB4BEC8),

    surfaceContainer = Color(0xFF1F252A),
    surfaceContainerHigh = Color(0xFF262D33),
    surfaceContainerLow = Color(0xFF191E22),

    outline = Color(0xFF7C8792),
    outlineVariant = Color(0xFF3E474F),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)
