package com.example.metingmusic.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metingmusic.data.remote.ReleaseInfo

/** 应用内更新包的下载状态。 */
sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState

    /** [progress] 为 0f~1f；-1f 表示服务端没给长度、进度未知。 */
    data class Downloading(val progress: Float) : ApkDownloadState

    data object Ready : ApkDownloadState
    /** 下载好了，但缺「安装未知应用」授权（已帮用户跳到授权页）。 */
    data object NeedPermission : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

/**
 * 发现新版本时的提示。
 *
 * 有两种升级路径：
 * - **直接下载**：拉 Release 里的 APK，下完唤起系统安装器（需要「安装未知应用」授权）
 * - **去网页**：Release 没附 APK，或者用户想自己看说明
 */
@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    downloadState: ApkDownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit
) {
    val asset = release.apkAsset
    val downloading = downloadState is ApkDownloadState.Downloading

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
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
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                Spacer(Modifier.height(14.dp))

                when (downloadState) {
                    is ApkDownloadState.Downloading -> {
                        if (downloadState.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier.fillMaxWidth(),
                                drawStopIndicator = {}
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "正在下载 ${(downloadState.progress * 100).toInt()}%" +
                                    asset?.let { " · ${formatSize(it.size)}" }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "正在下载…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is ApkDownloadState.Ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "下载完成，点「立即安装」继续",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    ApkDownloadState.NeedPermission -> {
                        Text(
                            text = "已打开「安装未知应用」设置，允许本应用后回来点「立即安装」即可，不用重新下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is ApkDownloadState.Failed -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    ApkDownloadState.Idle -> {
                        Text(
                            text = if (asset != null) {
                                "安装包 ${formatSize(asset.size)}，下载后会自动打开系统安装器"
                            } else {
                                "这个版本没有附带安装包，请到 Release 页面下载"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadState is ApkDownloadState.Ready ||
                    downloadState is ApkDownloadState.NeedPermission -> TextButton(onClick = onInstall) {
                    Text("立即安装", fontWeight = FontWeight.Bold)
                }
                downloading -> TextButton(onClick = {}, enabled = false) { Text("下载中…") }
                asset != null -> TextButton(onClick = onDownload) {
                    Text("下载更新", fontWeight = FontWeight.Bold)
                }
                else -> TextButton(onClick = onOpenRelease) {
                    Text("去网页下载", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenRelease, enabled = !downloading) {
                Text("查看详情")
            }
        }
    )
}

/** 字节数转成「12.3 MB」这种可读形式。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
