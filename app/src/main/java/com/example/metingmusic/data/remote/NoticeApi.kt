package com.example.metingmusic.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 公告的远端数据源。
 *
 * 公告是一份**纯文本**，放在一个静态地址上，由作者随时改内容。
 * 这里刻意不做本地缓存：公告通常是时效性的（比如「某音源挂了」），
 * 拿不到就不显示，比显示一条过期公告更安全。
 */
class NoticeApi(
    private val client: OkHttpClient = MetingApi.defaultClient
) {

    /**
     * 拉取公告正文。
     *
     * @return 正文（已 trim）；网络失败、HTTP 错误或内容为空时返回 null。
     */
    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(NOTICE_URL)
                .header("User-Agent", MetingApi.MEDIA_USER_AGENT)
                // 公告可能被 CDN 缓存，加个时间戳避免一直拿到旧内容
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.trim()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val NOTICE_URL = "https://nachozz.netlify.app/others/music_notice.txt"
    }
}
