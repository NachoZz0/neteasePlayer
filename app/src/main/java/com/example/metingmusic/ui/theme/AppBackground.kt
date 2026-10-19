package com.example.metingmusic.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.metingmusic.R

/**
 * 全局背景：主题底色 + 一张 30% 透明度的插画。
 *
 * 用 [Image] 的 alpha 参数而不是给图片叠一层半透明遮罩，
 * 是因为前者只影响图片自身、不会连带把上层内容也变暗。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Image(
            painter = painterResource(R.drawable.app_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.3f,
            modifier = Modifier.fillMaxSize()
        )
    }
}
