package com.example.metingmusic.ui.download

import androidx.compose.runtime.Immutable
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.DownloadedSong

/**
 * 下载列表里的一个分组。
 *
 * [playlistId] 为 null 表示「单曲」这一组 —— 从搜索结果、播放队列或外部单曲链接下的歌。
 */
@Immutable
data class DownloadGroup(
    val playlistId: String?,
    val name: String,
    val songs: List<DownloadedSong>
) {
    val isSingle: Boolean get() = playlistId == null
    val size: Int get() = songs.size
    val totalSize: Long get() = songs.sumOf { it.fileSize }

    /** 稳定的 LazyColumn key。 */
    val key: String get() = playlistId ?: SINGLE_KEY

    companion object {
        const val SINGLE_KEY = "__single__"
        const val SINGLE_NAME = "单曲"
    }
}

/**
 * 把下载记录按「来源歌单」分组，单曲单独一组。
 *
 * 两件事需要说明：
 *
 * 1. **优先用记录里存的来源**（[DownloadedSong.originPlaylistId]）。
 *
 * 2. **老数据没有来源信息**（这个字段是后加的），这时退化为「看它属于哪个已保存歌单」——
 *    一首歌通常只属于少数几个歌单，匹配得上就归过去，匹配不上就当单曲。
 *    这比直接把它们全丢进「单曲」要合理得多，也不用做数据迁移。
 *
 * 排序：歌单组按「最近下载时间」倒序（刚下完的排前面），单曲组固定放最后。
 */
fun groupDownloads(
    downloads: List<DownloadedSong>,
    savedPlaylists: List<SavedPlaylist>
): List<DownloadGroup> {
    if (downloads.isEmpty()) return emptyList()

    // 歌单号 -> 名称，用于补全「有 id 但没存名字」的情况
    val nameById = savedPlaylists.associate { it.playlistId to it.name }

    // 老数据的兜底索引：歌曲 id -> 它所在的第一个歌单
    val songToPlaylist = HashMap<String, String>()
    savedPlaylists.forEach { playlist ->
        playlist.songs.forEach { song ->
            songToPlaylist.putIfAbsent(song.id, playlist.playlistId)
        }
    }

    val buckets = LinkedHashMap<String?, MutableList<DownloadedSong>>()
    downloads.forEach { item ->
        val key = item.originPlaylistId
            ?: songToPlaylist[item.song.id]
        buckets.getOrPut(key) { mutableListOf() } += item
    }

    return buckets.map { (key, items) ->
        val displayName = when {
            key == null -> DownloadGroup.SINGLE_NAME
            else -> items.firstOrNull()?.originPlaylistName
                ?.takeIf { it.isNotBlank() }
                ?: nameById[key]
                ?: "歌单 $key"
        }
        DownloadGroup(playlistId = key, name = displayName, songs = items)
    }.sortedWith(
        compareBy<DownloadGroup> { it.isSingle }          // 单曲排最后
            .thenByDescending { g -> g.songs.maxOf { it.downloadedAt } }
    )
}
