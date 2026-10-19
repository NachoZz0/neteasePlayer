package com.example.metingmusic.ui.playlist

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.ShareLinkParser

/**
 * 歌单分享对话框。
 *
 * 生成的是网易云风格的分享文案（`分享XXX创建的歌单「名字」: https://...`），
 * 这样对方收到后可以直接粘回本应用的导入框 —— 我们的解析器认得这种格式，
 * 歌单号、名称、创建者信息都能还原出来。
 */
@Composable
fun SharePlaylistDialog(
    playlist: SavedPlaylist,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val text = ShareLinkParser.buildPlaylistShareText(
        playlistId = playlist.playlistId,
        name = playlist.name,
        userId = playlist.userId,
        creatorId = playlist.creatorId,
        creatorName = playlist.creatorName
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("分享歌单", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp)
                )
                if (playlist.creatorId.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "这条记录里没有创建者信息（导入时链接里没带），" +
                            "分享链接里也就不会有 creatorId。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onCopy(text) }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复制", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
