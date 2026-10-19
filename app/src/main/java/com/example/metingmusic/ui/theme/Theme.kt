package com.example.metingmusic.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 配色：中性灰打底 + 淡蓝点缀。
 *
 * 灰负责「安静」（背景、次级文字、分隔线），淡蓝只用在需要用户注意的地方
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

/** 应用主题。固定使用上面的灰蓝配色，不跟随系统取色。 */
@androidx.compose.runtime.Composable
fun MetingMusicTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
