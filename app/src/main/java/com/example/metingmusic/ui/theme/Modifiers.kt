package com.example.metingmusic.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 无涟漪效果的点击。
 *
 * Compose 里 `clickable` 是 [Modifier] 的扩展函数，不能当顶级函数调用；
 * 这里封装成 [Modifier] 扩展，供歌词行、音质标签这类「不需要水波纹」的元素复用。
 *
 * @param enabled 传 false 时既不响应点击，也不会在语义树里表现为可点。
 */
@Composable
fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * 无涟漪的「点击 + 长按」。
 *
 * 列表里的多选模式靠长按进入，而普通模式下的点击是播放 / 打开 ——
 * 两种手势落在同一个元素上，必须用 [combinedClickable] 才都能收到。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longPressableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
