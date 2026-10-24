package com.example.metingmusic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metingmusic.data.remote.ReleaseInfo

/**
 * 发现新版本时的提示。
 *
 * 只负责「告诉你 + 打开浏览器」，不做应用内下载 ——
 * 自更新要处理签名校验、安装权限、断点续传，风险远大于收益，
 * 交给 GitHub Release 页面的 APK 更稳妥。
 */
@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "当前版本 $currentVersion → 最新版本 ${release.version}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (release.body.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = release.body.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenRelease) {
                Text("去下载", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("以后再说") }
        }
    )
}
