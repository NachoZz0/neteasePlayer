package com.example.metingmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 当前设备是否支持真正的实时模糊。
 *
 * `RenderEffect` 是 Android 12（API 31）引入的；Android 13 上系统对它的合成做了优化，
 * 所以 13+ 的模糊更省电、边缘也更干净。低于 12 只能退回半透明底色 ——
 * 是假磨砂，但至少不会崩。
 */
val supportsRealBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 真·磨砂玻璃底。
 *
 * 做法不是「盖一层半透明白」，而是**把背景按窗口坐标再画一遍、然后模糊**，
 * 再裁到自身范围。这样透出来的是背景真实的颜色和形状，而不是一张平均色。
 *
 * 为什么不直接 `Modifier.blur` 糊自己：`blur` 模糊的是组件**自身的内容**，
 * 拿它去糊一个空白面板只会得到一片灰。要糊「背后的东西」，就得自己再画一份背景。
 *
 * 性能上，模糊层是窗口大小的图层 —— 模糊本身不便宜，但背景是静态的，
 * 系统会缓存这一层，只在背景变化（换图 / 换深浅色）时重算。
 * 所以它适合导航栏、底部弹层这类**固定位置**的元素；
 * 列表里每一行都套一层会明显掉帧，因此歌曲卡片仍用半透明底色。
 */
@Composable
fun FrostedBackdrop(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    /** 叠在模糊之上的着色，用来压暗或提亮，保证文字可读。 */
    tint: Color? = null
) {
    // 用 Configuration 拿窗口尺寸：LocalWindowInfo.containerSize 在 1.7 上还没有
    val config = LocalConfiguration.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    val overlay = tint ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clipToBounds()
    ) {
        // 把整屏背景按「自己在窗口里的位置」反向偏移，再模糊 ——
        // 这样模糊层里看到的像素，正好是这块区域背后真实的背景。
        AppBackground(
            Modifier
                .requiredSize(config.screenWidthDp.dp, config.screenHeightDp.dp)
                .offset {
                    IntOffset(
                        x = -origin.x.roundToInt(),
                        y = -origin.y.roundToInt()
                    )
                }
                .blur(radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(overlay)
        )
    }
}
