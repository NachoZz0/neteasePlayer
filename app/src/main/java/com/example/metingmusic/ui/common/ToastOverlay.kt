package com.example.metingmusic.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 一条会「自我替换」的轻提示。
 *
 * 和 Material 的 Snackbar 不同：Snackbar 的 showSnackbar() 会挂起直到上一条播完，
 * 因此连点播放模式按钮时，提示会排队依次弹出，看起来又慢又卡。
 * 这里用一个短小的淡入淡出浮层替代——同一条 [ToastMessage] 再次出现时
 * 会直接覆盖上一条的计时，所以连点多少次都能立刻看到最新模式。
 */
data class ToastMessage(
    /** 每次触发都自增，用来让 LaunchedEffect 重新起算延迟。 */
    val id: Long,
    val text: String,
    val icon: ImageVector? = null
)

/**
 * 展示轻提示。[message] 为 null 时不显示。
 *
 * @param bottomPadding 距离底部的距离，由调用方按当前页面布局决定。
 */
@Composable
fun ToastOverlay(
    message: ToastMessage?,
    modifier: Modifier = Modifier,
    durationMs: Long = 1200L
) {
    // 保留最后一条消息，这样退场动画期间文字不会突然空掉。
    var visible by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<ToastMessage?>(null) }

    if (message != null) last = message

    LaunchedEffect(message?.id) {
        if (message != null) {
            visible = true
            delay(durationMs)
            visible = false
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        last?.let { ToastCard(it) }
    }
}

@Composable
private fun ToastCard(message: ToastMessage) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                // 让读屏软件在内容变化时自动播报，无需用户主动聚焦
                .semantics {
                    contentDescription = message.text
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            message.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
