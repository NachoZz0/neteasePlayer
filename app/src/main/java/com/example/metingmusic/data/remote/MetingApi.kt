package com.example.metingmusic.data.remote

import com.example.metingmusic.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Meting API 客户端。
 *
 * 接口文档：https://api.qijieya.cn/meting/
 *
 * 所有接口都是 GET，公共参数：
 *  - server: netease(默认) / tencent
 *  - type  : name | artist | url | pic | lrc | song | search | playlist
 *  - id    : 对应类型的 ID（search 时是关键词）
 *
 * 关键点：type=url 与 type=pic 返回的是 302 跳转，需要用 OkHttp 跟随重定向拿到最终直链。
 */
class MetingApi(
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    private val gson = Gson()

    /** 搜索结果 / 单曲 / 歌单，统一是 Song 数组。 */
    suspend fun search(
        keyword: String,
        server: String = SERVER_NETEASE,
        page: Int = 1,
        limit: Int = 30,
        searchType: Int = 1
    ): List<Song> = withContext(Dispatchers.IO) {
        val url = buildUrl {
            addQueryParameter("server", server)
            addQueryParameter("type", "search")
            addQueryParameter("id", keyword)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", limit.toString())
            addQueryParameter("search_type", searchType.toString())
        }
        parseSongList(getText(url))
    }

    suspend fun song(
        id: String,
        server: String = SERVER_NETEASE
    ): Song? = withContext(Dispatchers.IO) {
        val url = buildUrl {
            addQueryParameter("server", server)
            addQueryParameter("type", "song")
            addQueryParameter("id", id)
        }
        parseSongList(getText(url)).firstOrNull()
    }

    /**
     * 拉取歌单。
     *
     * ⚠️ 接口的坑：歌单号无效时它**不返回 404**，而是 HTTP 200 + `[{"error":"unknown playlist id"}]`。
     * 如果直接交给 Gson 反序列化成 List<Song>，会得到一条所有字段为空字符串的「幽灵歌曲」，
     * 界面上表现为「导入成功但歌单里有一首空歌」。所以这里必须显式探测 error 字段。
     */
    suspend fun playlist(
        id: String,
        server: String = SERVER_NETEASE
    ): List<Song> = withContext(Dispatchers.IO) {
        val url = buildUrl {
            addQueryParameter("server", server)
            addQueryParameter("type", "playlist")
            addQueryParameter("id", id)
        }
        val json = getText(url)
        detectApiError(json)?.let { throw IOException(it) }
        parseSongList(json)
    }

    /**
     * 解析歌曲的真实播放直链。
     * 传入的 [songUrl] 必须是 API 给出的 ...&type=url&id=xxx 接口地址，
     * 这里会跟随 302 拿到 mp3/flac 的最终地址。
     */
    suspend fun resolvePlayUrl(
        songUrl: String,
        br: Int = BR_320
    ): String? = withContext(Dispatchers.IO) {
        val final = followRedirect(withBr(songUrl, br)) ?: return@withContext null
        // 有些音源会返回一个 404 的占位地址，过滤掉
        if (final.contains("404") && !final.endsWith(".mp3")) null else final
    }

    /** 解析封面图真实直链，可指定分辨率。 */
    suspend fun resolvePicUrl(picUrl: String, size: Int = 500): String? =
        withContext(Dispatchers.IO) {
            var url = picUrl
            url = url.replace(Regex("[?&]cover=\\d+"), "")
            url = url.replace(Regex("[?&]size=\\d+"), "")
            val sep = if (url.contains("?")) "&" else "?"
            url = "$url${sep}cover=$size"
            followRedirect(url)
        }

    /** 获取 LRC 歌词文本。 */
    suspend fun lyrics(lrcUrl: String): String = withContext(Dispatchers.IO) {
        getText(lrcUrl)
    }

    // ---------------------------------------------------------------- 内部工具

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            resp.body?.string().orEmpty()
        }
    }

    /** 跟随重定向，拿到 Location 最终指向的地址（不下载 body）。 */
    private suspend fun followRedirect(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            when {
                resp.isRedirect -> resp.header("Location")
                resp.isSuccessful -> resp.request.url.toString()
                else -> null
            }
        }
    }

    private fun withBr(songUrl: String, br: Int): String = streamUrl(songUrl, br)

    private fun buildUrl(block: HttpUrl.Builder.() -> Unit): String {
        val base = baseUrl.toHttpUrlOrNull() ?: throw IllegalStateException("非法 baseUrl")
        return base.newBuilder().apply(block).build().toString()
    }

    private fun parseSongList(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        return runCatching { gson.fromJson<List<Song>>(json, type) }.getOrNull().orEmpty()
    }

    /**
     * 探测 Meting 的「HTTP 200 但是错误」响应。
     *
     * 形如 `[{"error":"unknown playlist id"}]`，状态码是 200，body 也是个合法 JSON 数组。
     * 直接反序列化会得到一条空歌曲，所以要先把这个 error 字段捞出来。
     */
    private fun detectApiError(json: String): String? {
        if (json.isBlank()) return null
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) return null
        return runCatching {
            val obj = gson.fromJson(trimmed, com.google.gson.JsonElement::class.java)
            val errorMember = when {
                obj.isJsonArray -> obj.asJsonArray.firstOrNull()?.takeIf { it.isJsonObject }
                    ?.asJsonObject?.get("error")
                obj.isJsonObject -> obj.asJsonObject.get("error")
                else -> null
            }
            errorMember?.takeIf { it.isJsonPrimitive }?.asString
        }.getOrNull()?.let { friendlyError(it) }
    }

    /** 把接口的英文错误翻成用户能看懂的话。 */
    private fun friendlyError(raw: String): String = when {
        raw.contains("unknown playlist", ignoreCase = true) ->
            "歌单不存在或未公开，请检查歌单号"
        raw.contains("unknown", ignoreCase = true) ->
            "没有找到该内容，请检查 ID 是否正确"
        else -> raw
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.qijieya.cn/meting/"

        const val SERVER_NETEASE = "netease"
        /** 保留常量仅为兼容 Meting 协议；QQ 音乐（tencent）经实测绝大多数歌曲解析失败，已下线。 */
        const val SERVER_TENCENT = "tencent"

        const val BR_FLAC = 2000
        const val BR_320 = 320
        const val BR_192 = 192
        const val BR_128 = 128

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        /**
         * 网易云 CDN 会校验 UA，播放器拉流时必须带上同一个 UA，
         * 否则会拿到 403。所以这里把它暴露出去给 ExoPlayer 用。
         */
        const val MEDIA_USER_AGENT = USER_AGENT

        /**
         * 给 `type=url` 接口地址补上 br 参数，得到「可以直接交给播放器」的地址。
         *
         * 这个地址本身仍然是个 302 跳转端点 —— 但 ExoPlayer 自己会跟随重定向，
         * 所以不必先解析出真实直链再播放。这样整个播放队列都能一次性塞进播放器，
         * 通知栏的上一首/下一首才有东西可切。
         */
        fun streamUrl(songUrl: String, br: Int): String {
            // 去掉已存在的 br 再拼，避免重复参数
            val cleaned = songUrl.replace(Regex("[?&]br=\\d+"), "")
            val sep = if (cleaned.contains("?")) "&" else "?"
            return "$cleaned${sep}br=$br"
        }

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

/** 复用 OkHttp 的 Call 取消能力（预留给未来做请求取消）。 */
internal fun Call.cancelQuietly() = runCatching { cancel() }
