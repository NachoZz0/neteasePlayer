package com.example.metingmusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
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
 * 把歌单导出成纯文本。
 *
 * 格式刻意做得很朴素（`序号. 歌名 - 作者`），因为这份 txt 的用途是
 * 「发给别人 / 存到网盘 / 贴进记事本」，任何多余的花样都会让它不好用。
 */
fun buildPlaylistText(playlist: SavedPlaylist): String = buildString {
    appendLine(playlist.name)
    appendLine("共 ${playlist.songs.size} 首")
    appendLine()
    playlist.songs.forEachIndexed { index, song ->
        val order = (index + 1).toString().padStart(2, '0')
        appendLine("$order. ${song.name} - ${song.artist}")
    }
}

/** 建议的文件名：去掉路径非法字符。 */
fun buildPlaylistTextFileName(playlist: SavedPlaylist): String {
    val safe = playlist.name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    return "${safe.ifBlank { "歌单" }}.txt"
}

/**
 * 歌单导出 / 分享对话框。
 *
 * 两种歌单是两件事，不能混：
 * - **网易云歌单**（从链接导入的）→ 「分享」：生成网易云格式的分享文案，
 *   对方粘回本应用就能导入。它本来就有源歌单号，分享链接才有意义。
 * - **本地歌单**（自己建的）→ 「导出」：没有歌单号，分享链接是空的，
 *   所以导出成 txt（歌名 + 作者），对方用什么都能打开。
 */
@Composable
fun SharePlaylistDialog(
    playlist: SavedPlaylist,
    onSave: (fileName: String, content: String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (playlist.isLocalPlaylist) {
        ExportPlaylistDialog(
            playlist = playlist,
            onSave = onSave,
            onCopy = onCopy,
            onDismiss = onDismiss
        )
    } else {
        ShareLinkDialog(
            playlist = playlist,
            onCopy = onCopy,
            onDismiss = onDismiss
        )
    }
}

/** 本地歌单：导出成 txt。 */
@Composable
private fun ExportPlaylistDialog(
    playlist: SavedPlaylist,
    onSave: (fileName: String, content: String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val text = buildPlaylistText(playlist)
    val fileName = buildPlaylistTextFileName(playlist)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("导出歌单", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "${playlist.name} · ${playlist.songs.size} 首",
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
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
                if (playlist.songs.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "这个歌单还没有歌，导出的文件里只有标题。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSave(fileName, text) }) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("保存 txt", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onCopy(text) }) { Text("复制") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/** 网易云歌单：分享可被本应用识别的导入文案。 */
@Composable
private fun ShareLinkDialog(
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "对方把这段文字粘进「歌单 → 从网易云导入」就能打开这张歌单。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(text) }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
