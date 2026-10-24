package com.example.metingmusic.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.Dp

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

/**
 * 全局模糊下的「玻璃卡片」。
 *
 * 开启全局模糊时，给列表行 / 面板铺一层半透明底色 + 细描边；
 * 背景已经被 [AppBackground] 糊过了，所以这里只做半透明就够了 ——
 * 每个卡片单独做模糊会让一屏几十个卡片各建一个模糊层，必卡。
 */
@Composable
fun Modifier.glassCard(corner: Dp = 12.dp): Modifier {
    if (!LocalAppearance.current.globalBlur) return this
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
        .border(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            shape = shape
        )
}

/** 全局模糊下按钮的半透明配色；关掉时返回 Material 默认配色。 */
@Composable
fun glassButtonColors(): ButtonColors =
    if (LocalAppearance.current.globalBlur) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        )
    } else {
        ButtonDefaults.buttonColors()
    }
