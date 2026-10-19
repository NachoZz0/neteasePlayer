package com.example.metingmusic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlin.jvm.Transient

/**
 * 单首歌曲。对应 Meting API 的 `type=song` / `type=search` / `type=playlist` 返回数组中的一项。
 *
 * 注意：API 返回的 url / pic / lrc 三个字段是「接口地址」而不是最终媒体地址，
 * 它们都指向 .../meting/?server=xxx&type=url&id=... 这种会 302 跳转的链接。
 * 真正的媒体直链需要跟随重定向后拿到。
 */
@Immutable
data class Song(
    @SerializedName("name") val name: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("pic") val pic: String = "",
    @SerializedName("lrc") val lrc: String = "",
    /**
     * 这首歌来自哪个音源。
     *
     * 搜索结果里不含该字段，由 [com.example.metingmusic.data.repository.MusicRepository]
     * 在返回时统一填充；歌单持久化时也会一起存下来，避免重开后无法解析直链。
     */
    @SerializedName("server") val server: String = ""
) {
    /**
     * id 的缓存。
     *
     * 歌单动辄上千首，而 [id] 要做一次正则匹配；列表滑动时每一帧都会取若干次 key，
     * 每次都跑正则是之前掉帧的主因之一。加个缓存后只在首次访问时算一遍。
     *
     * @Transient 让 Gson 跳过这个字段（Kotlin 的 @Transient 会编译成 JVM 的 transient 修饰符，
     * 而 Gson 默认 Excluder 会忽略 transient 字段），所以不会污染持久化的 JSON。
     */
    @Transient
    private var cachedId: String? = null

    /** 从 url 接口地址里解析出的歌曲 id，用于做稳定 key 与去重。 */
    val id: String
        get() = cachedId ?: run {
            val computed = idRegex.find(url)?.groupValues?.getOrNull(1)
                ?: (name + artist).hashCode().toString()
            cachedId = computed
            computed
        }

    /** 歌手名列表，Meting 用 "/" 分隔多歌手。 */
    val artistList: List<String>
        get() = artist.split('/').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private val idRegex = Regex("[?&]id=([^&]+)")
    }
}

/** LRC 歌词中的一行。 */
@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String
)
