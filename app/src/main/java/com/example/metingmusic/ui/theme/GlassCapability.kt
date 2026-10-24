package com.example.metingmusic.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 这台设备能不能跑「真·背景模糊」。
 *
 * 两道门：
 * 1. **Android 13（API 33）及以上** —— `RenderEffect` 虽然 12 就有了，
 *    但 12 上的模糊合成质量和性能都明显差一截；NeriPlayer 的实时模糊也是
 *    `@RequiresApi(TIRAMISU)` 卡在 13，这里跟它保持一致
 * 2. 不是低内存设备 —— 背景模糊每帧要多渲染一遍内容，低端机上会掉到十几帧，
 *    这种情况退回半透明底比卡着强
 */
@Composable
fun rememberGlassSupported(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            false
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice != true
        }
    }
}
