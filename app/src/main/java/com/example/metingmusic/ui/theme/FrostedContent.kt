package com.example.metingmusic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 真·背景模糊（backdrop blur）。
 *
 * 只有三步，**不在布局上做任何偏移或缩放**：
 * 1. 把整屏内容图层按 `-origin` 平移到窗口原点（绘制阶段的 [translate]，不参与布局）；
 * 2. 用 [Modifier.blur] 施加 RenderEffect 高斯模糊；
 * 3. 外层 `clipToBounds()` 裁掉模糊溢出。
 *
 * 之前试过「降采样小盒子 + 放大」来拉大强度范围，但那套的缩放原点和裁剪区
 * 极难对齐，出现过模糊层整体偏移、面板里露出方块、以及某些半径下干脆不渲染
 * 的问题 —— 已经全部放弃，回到这个简单可靠的版本。
 *
 * 半径直接采用设置里的值，不做任何映射。
 */
@Composable
fun FrostedContentBackdrop(
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    tint: Color,
    radius: Dp = 60.dp
) {
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clipToBounds()
    ) {
        // 必须常驻组合：用 if (radius > 0) 包起来的话，强度从 0 调上去时
        // 这个子树重新进入组合，drawLayer 会变成空操作 —— 面板里就出现白块。
        // radius = 0 时 Modifier.blur 本身就是空效果，等价于「不模糊」。
        Box(
            Modifier
                .fillMaxSize()
                .blur(radius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                .drawBehind {
                    // 强度为 0 时上层已经跳过了录制，图层是空的 ——
                    // 这时候如果还画，栏里就只剩背景、看不到内容。
                    // 这里用「绘制阶段的判断」而不是把整个 Box 用 if 包起来，
                    // 因为条件组合会让 drawLayer 在重新进入组合后失效。
                    if (radius > 0.dp) {
                        translate(left = -origin.x, top = -origin.y) {
                            drawLayer(layer)
                        }
                    }
                }
        )

        if (tint != Color.Transparent) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint)
            )
        }
    }
}
