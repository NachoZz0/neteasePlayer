package com.example.metingmusic.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.metingmusic.ui.theme.clickableNoRipple

/**
 * 歌单详情的二级菜单。
 *
 * 原先「分享」「刷新」是标题栏上的两个图标按钮，「下载全部」和「播放全部」并排一行，
 * 三处操作散在屏幕上、还把标题挤得很窄。现在统一收进这个底部弹层，
 * 标题栏只留一个「⋮」，播放全部独占一行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMenuSheet(
    playlistName: String,
    songCount: Int,
    refreshing: Boolean,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 2.dp)
            )
            Text(
                text = "$songCount 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            PlaylistMenuRow(
                icon = Icons.Default.Share,
                title = "分享歌单",
                subtitle = "生成网易云格式的分享文案，可复制",
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            PlaylistMenuRow(
                icon = Icons.Default.Refresh,
                title = "刷新歌单",
                subtitle = if (refreshing) "正在从网易云重新拉取…" else "重新拉取曲目，获取最新增删",
                enabled = !refreshing,
                trailing = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    onDismiss()
                    onRefresh()
                }
            )

            PlaylistMenuRow(
                icon = Icons.Default.DownloadForOffline,
                title = "下载全部",
                subtitle = "把整张歌单加入下载队列，可随时取消",
                accent = true,
                onClick = {
                    onDismiss()
                    onDownloadAll()
                }
            )
        }
    }
}

/** 菜单里的一行：图标 + 标题 + 说明 + 可选尾部控件。 */
@Composable
private fun PlaylistMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
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
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}
