package com.example.metingmusic.data.repository

import com.example.metingmusic.data.model.LrcParser
import com.example.metingmusic.data.model.LyricLine
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.data.remote.MetingApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 音乐仓库：把 API 的多步调用（搜索 -> 解析直链 -> 解析封面 -> 拉歌词）编排成一个原子操作，
 * UI 层只面对「可播放的歌曲」这一概念。
 */
class MusicRepository(private val api: MetingApi = MetingApi()) {

    /** 播放器需要的完整数据。 */
    data class PlayableTrack(
        val song: Song,
        val streamUrl: String,
        val coverUrl: String?,
        val lyrics: List<LyricLine>
    )

    suspend fun search(
        keyword: String,
        server: String = MetingApi.SERVER_NETEASE,
        page: Int = 1,
        limit: Int = 30
    ): Result<List<Song>> = runCatching {
        require(keyword.isNotBlank()) { "搜索关键词不能为空" }
        api.search(keyword = keyword, server = server, page = page, limit = limit)
            .map { it.copy(server = server) }
    }

    suspend fun playlist(
        id: String,
        server: String = MetingApi.SERVER_NETEASE
    ): Result<List<Song>> = runCatching {
        api.playlist(id, server).map { it.copy(server = server) }
    }

    /**
     * 按歌曲 id 取单曲信息。
     *
     * 「从外部导入歌曲」用它 —— 用户粘的是一条单曲分享链接，只有一个 id，
     * 需要拿歌名/歌手/封面才能进下载库。找不到（已下架）返回 null。
     */
    suspend fun songById(
        id: String,
        server: String = MetingApi.SERVER_NETEASE
    ): Song? = runCatching {
        api.song(id, server)?.copy(server = server)
    }.getOrNull()

    /**
     * 只取歌词。
     *
     * 播放本地已下载的歌曲时用 —— 音频不用解析了，但歌词没存在音频里，
     * 仍需要单独拉一次（失败就返回空列表，不影响播放）。
     */
    suspend fun lyricsOnly(song: Song): List<LyricLine> = runCatching {
        LrcParser.parse(api.lyrics(song.lrc))
    }.getOrDefault(emptyList())

    /**
     * 把一首 [Song] 解析成可播放的 [PlayableTrack]。
     * 三个解析请求并发执行，减少等待时间。
     */
    suspend fun resolve(song: Song, br: Int = MetingApi.BR_320): Result<PlayableTrack> =
        runCatching {
            coroutineScope {
                val streamDeferred = async { api.resolvePlayUrl(song.url, br) }
                val coverDeferred = async { api.resolvePicUrl(song.pic) }
                val lyricDeferred = async {
                    runCatching { api.lyrics(song.lrc) }.getOrDefault("")
                }

                val stream = streamDeferred.await()
                    ?: error("无法解析播放地址（可能为 VIP 或已下架）：${song.name}")

                PlayableTrack(
                    song = song,
                    streamUrl = stream,
                    coverUrl = coverDeferred.await(),
                    lyrics = LrcParser.parse(lyricDeferred.await())
                )
            }
        }
}
