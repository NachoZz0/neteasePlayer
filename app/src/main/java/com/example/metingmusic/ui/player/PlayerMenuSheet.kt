package com.example.metingmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.metingmusic.ui.common.icon
import com.example.metingmusic.player.PlayerUiState
import com.example.metingmusic.ui.theme.clickableNoRipple

private val BITRATES = listOf(
    128 to "128K",
    192 to "192K",
    320 to "320K",
    2000 to "FLAC"
)

/**
 * 播放页的二级菜单。
 *
 * 播放页本身只留下最常用的几个按钮，其余功能（音质、下载、定时、队列）都收进这里，
 * 用底部弹层呈现 —— 既满足「全部功能都能找到」，又不会让主界面变成一排图标墙。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuSheet(
    state: PlayerUiState,
    onCyclePlayMode: () -> Unit,
    onSelectBitrate: (Int) -> Unit,
    onDownload: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val song = state.currentSong

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "更多功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
            )
            song?.let {
                Text(
                    text = "${it.name} · ${it.artist}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ---- 播放模式
            MenuRow(
                icon = state.playMode.icon,
                title = "播放模式",
                subtitle = "当前：${state.playMode.label}",
                accent = true,
                onClick = onCyclePlayMode
            )

            // ---- 音质
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "音质",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BITRATES.forEach { (value, label) ->
                        val selected = value == state.bitrate
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickableNoRipple { onSelectBitrate(value) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                                .padding(vertical = 9.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // ---- 下载
            MenuRow(
                icon = if (state.isCurrentDownloaded) Icons.Default.DownloadDone
                else Icons.Default.Download,
                title = if (state.isCurrentDownloaded) "已下载" else "下载当前歌曲",
                subtitle = if (state.isCurrentDownloaded) "这首歌已在本地，可离线播放"
                else "保存到本地，之后无网也能听",
                accent = state.isCurrentDownloaded,
                enabled = song != null,
                onClick = onDownload
            )

            // ---- 定时播放
            MenuRow(
                icon = Icons.Default.Timer,
                title = "定时播放",
                subtitle = if (state.sleepTimer.enabled) {
                    "剩余 ${state.sleepTimer.label}" +
                        if (state.sleepTimer.finishCurrentSong) "（播完当前曲）" else ""
                } else {
                    "设置时长，到点自动暂停"
                },
                accent = state.sleepTimer.enabled,
                onClick = onOpenSleepTimer
            )

            // ---- 播放队列
            MenuRow(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "播放队列",
                subtitle = "${state.queue.size} 首",
                enabled = state.queue.isNotEmpty(),
                onClick = onOpenQueue
            )
        }
    }
}

/** 二级菜单里的一行。 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
    }
}
