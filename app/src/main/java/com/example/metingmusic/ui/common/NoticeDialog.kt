package com.example.metingmusic.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.example.metingmusic.data.model.NoticeTextParser

/**
 * 启动公告弹窗。
 *
 * 正文来自远端纯文本，其中的 URL 会渲染成可点击的超链接
 * （Compose 1.7 的 `LinkAnnotation`，点击由 `Text` 内部的 UriHandler 直接交给系统浏览器）。
 *
 * 不做「不再提示」：公告通常就是「某音源挂了，请升级」这类必须看到的信息。
 */
@Composable
fun NoticeDialog(
    text: String,
    onDismiss: () -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary

    // 纯文本 -> 带链接的富文本
    val annotated = buildAnnotatedString {
        NoticeTextParser.segments(text).forEach { segment ->
            if (segment.isLink) {
                withLink(
                    LinkAnnotation.Url(
                        url = segment.text,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) {
                    append(segment.text)
                }
            } else {
                append(segment.text)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("公告", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    // 公告可能很长，限高 + 可滚动，避免把按钮挤出屏幕
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (NoticeTextParser.hasLink(text)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "链接可点击",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", fontWeight = FontWeight.Bold)
            }
        }
    )
}
