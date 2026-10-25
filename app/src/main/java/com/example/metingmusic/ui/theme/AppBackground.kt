package com.example.metingmusic.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.metingmusic.R
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * 全局背景。
 *
 * 两种情况：
 * - 用户设置了自定义背景图 → 铺满，并压一层半透明底色保证文字可读
 * - 没设置 → 内置插画（30% 透明度）
 *
 * 用 [Image] 的 alpha 而不是给图片叠遮罩，是因为前者只影响图片自身、
 * 不会连带把上层内容也变暗。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current

    // 只在路径变化时解码一次；否则每次重组都解码一张大图会直接卡死
    val custom = remember(appearance.backgroundPath) {
        BackgroundImage.load(appearance.backgroundPath)?.toImage()
    }

    // 背景模糊由「背景设置」里的滑杆单独控制，和「全局磨砂质感」互不影响
    val blurModifier = if (appearance.bgBlurDp > 0f && supportsRealBlur) {
        Modifier.blur(appearance.bgBlurDp.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (custom != null) {
            Image(
                bitmap = custom,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = appearance.bgAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
            // 压一层底色：自定义图明暗不可控，不压的话文字经常糊在图上看不清
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.66f)
                            )
                        )
                    )
            )
        } else {
            Image(
                painter = painterResource(R.drawable.app_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.3f * appearance.bgAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        }
    }
}
