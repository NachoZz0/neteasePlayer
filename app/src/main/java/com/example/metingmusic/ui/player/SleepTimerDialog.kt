package com.example.metingmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.metingmusic.player.SleepTimerState
import com.example.metingmusic.ui.theme.clickableNoRipple

/** 定时播放的预设时长。 */
private val PRESETS = listOf(5, 15, 30, 45, 60, 90, 120)

/** 自定义时长的允许范围（分钟）。 */
private const val MIN_MINUTES = 1
private const val MAX_MINUTES = 600

/**
 * 定时播放设置对话框。
 *
 * 时长和「播完当前歌曲再暂停」都会被记住（通过 [onStart] 透传给控制器落盘），
 * 下次打开自动回填 —— 这是用户明确要求的行为。
 *
 * 除了预设 chip，还支持手填任意分钟数：预设覆盖不到「今晚听 47 分钟就睡」这种需求。
 */
@Composable
fun SleepTimerDialog(
    state: SleepTimerState,
    onStart: (minutes: Int, finishCurrentSong: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    // 回填上次的时长 / 开关
    var minutes by remember { mutableIntStateOf(state.minutes) }
    var finishCurrent by remember { mutableStateOf(state.finishCurrentSong) }
    // 自定义输入框的内容。用户点预设 chip 时清空，避免两处显示不一致
    var customText by remember {
        mutableStateOf(if (state.minutes in PRESETS) "" else state.minutes.toString())
    }

    val customValue = customText.toIntOrNull()
    val customInvalid = customText.isNotEmpty() &&
        (customValue == null || customValue < MIN_MINUTES || customValue > MAX_MINUTES)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("定时播放", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (state.enabled) {
                    Text(
                        text = "正在倒计时：${state.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.finishCurrentSong) {
                            "到点后会播完当前歌曲再暂停"
                        } else {
                            "到点后立即暂停"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    text = "播放时长",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // 预设时长，两行铺开
                PRESETS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { value ->
                            PresetChip(
                                text = presetLabel(value),
                                selected = value == minutes && customText.isEmpty(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    minutes = value
                                    customText = ""
                                }
                            )
                        }
                        // 补齐最后一行，保证 chip 宽度一致
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ---- 自定义时长
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw ->
                        // 只留数字，顺便限长，避免用户粘进来一坨奇怪的东西
                        val digits = raw.filter { it.isDigit() }.take(3)
                        customText = digits
                        digits.toIntOrNull()?.let { minutes = it.coerceIn(MIN_MINUTES, MAX_MINUTES) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义时长") },
                    placeholder = { Text("$MIN_MINUTES ~ $MAX_MINUTES 分钟") },
                    suffix = { Text("分钟") },
                    singleLine = true,
                    isError = customInvalid,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (customInvalid) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "请输入 $MIN_MINUTES ~ $MAX_MINUTES 之间的整数分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "将计时：${presetLabel(minutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "播完当前歌曲再暂停",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "到点时如果还在播，会等这首唱完",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = finishCurrent,
                        onCheckedChange = { finishCurrent = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(minutes, finishCurrent)
                    onDismiss()
                },
                enabled = !customInvalid
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.enabled) "重新计时" else "开始计时", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (state.enabled) {
                    TextButton(onClick = {
                        onCancelTimer()
                        onDismiss()
                    }) {
                        Text("关闭定时", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

/** 把分钟数写成人话，且尽量短（一行四个 chip，字太长会换行）。 */
private fun presetLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    // 90 -> 1.5 小时
    else -> "${minutes / 60}.${(minutes % 60) * 10 / 60} 小时"
}

@Composable
private fun PresetChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
            .padding(vertical = 9.dp)
    )
}
