package com.example.metingmusic.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.metingmusic.player.PlayMode

/**
 * 播放模式到图标的唯一映射。
 *
 * 之前 PlayerScreen 和 PlayerMenuSheet 各写了一份 when 分支，加轻提示时又会多出第三份，
 * 一旦漏改就会出现「按钮显示随机、提示显示循环」的错误。统一收在这里。
 */
val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.SHUFFLE -> Icons.Default.Shuffle
        PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
        PlayMode.SEQUENTIAL, PlayMode.LIST_LOOP -> Icons.Default.Repeat
    }
