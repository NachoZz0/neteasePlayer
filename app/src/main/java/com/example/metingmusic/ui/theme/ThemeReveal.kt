package com.example.metingmusic.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.PixelCopy
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 一次「圆形揭示」主题切换的素材。
 *
 * [bitmap] 是切换前那一帧的截图，[center] 是点击位置（根坐标）。
 */
@Immutable
data class ThemeReveal(val bitmap: Bitmap, val center: Offset)

/**
 * 盖在界面最上层、慢慢「挖洞」的旧主题截图。
 *
 * 用「在旧截图上挖一个不断变大的圆孔」来实现新主题从点击处扩散出来 ——
 * 比「叠一层新主题再裁剪」便宜得多：只需要一张静态图 + 一个 Path，
 * 不用把整棵 UI 树同时组合两遍（那会把 remember 状态也复制一份，滚动位置都会错）。
 */
@Composable
fun ThemeRevealOverlay(reveal: ThemeReveal, onDone: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "theme-reveal"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 用 px 算，避免 dp 换算带来的误差
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val c = reveal.center
        // 半径要覆盖到最远的那个屏幕角，否则边缘会残留一圈旧主题
        val maxRadius = listOf(
            Offset(0f, 0f),
            Offset(w, 0f),
            Offset(0f, h),
            Offset(w, h)
        ).maxOf { (it - c).getDistance() }

        // 动画和「移除覆盖层」解耦：不管动画有没有跑起来，
        // 560ms 后一定把这层摘掉，绝不会卡在「半张旧截图」的状态。
        LaunchedEffect(Unit) {
            expanded = true
            delay(560)
            onDone()
        }

        Image(
            bitmap = reveal.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleHoleShape(center = c, radius = maxRadius * progress))
        )
    }
}

/** 整块矩形挖掉一个圆；圆之外仍然显示旧截图。 */
private class CircleHoleShape(
    private val center: Offset,
    private val radius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            // EvenOdd：外矩形减去内圆，剩下的部分就是「洞以外」的区域
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (radius > 0f) addOval(Rect(center = center, radius = radius))
        }
        return Outline.Generic(path)
    }
}

/**
 * 截当前窗口。
 *
 * 用 [PixelCopy] 而不是 `view.drawToBitmap()`：后者走软件绘制，
 * 会把硬件图层（比如底栏的模糊层）画成空白，截出来是残缺的。
 */
fun captureWindow(activity: Activity, onResult: (Bitmap?) -> Unit) {
    val decor = activity.window.decorView
    if (decor.width <= 0 || decor.height <= 0) {
        onResult(null)
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onResult(null)
        return
    }
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    runCatching {
        PixelCopy.request(
            activity.window,
            bitmap,
            { result ->
                onResult(if (result == PixelCopy.SUCCESS) bitmap else null)
            },
            Handler(Looper.getMainLooper())
        )
    }.onFailure { onResult(null) }
}

/**
 * 系统是否允许动画。
 *
 * 用户在开发者选项里把「动画时长缩放」关掉时，直接切主题，不做圆形揭示 ——
 * 这类用户要的就是「别给我动」。
 */
fun animationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f
}.getOrDefault(true)
