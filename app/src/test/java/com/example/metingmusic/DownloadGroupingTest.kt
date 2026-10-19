package com.example.metingmusic

import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.DownloadedSong
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.ui.download.DownloadGroup
import com.example.metingmusic.ui.download.groupDownloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载按歌单分组的测试。
 *
 * 重点是「老数据没有来源字段」这条回退路径 —— 上线前的下载记录都没有
 * originPlaylistId，如果不做推断，用户会看到几十首歌全掉进「单曲」里。
 */
class DownloadGroupingTest {

    private fun song(id: String, name: String = "歌$id") = Song(
        name = name,
        artist = "歌手",
        url = "https://api.qijieya.cn/meting/?type=url&id=$id",
        pic = "https://api.qijieya.cn/meting/?type=pic&id=$id",
        lrc = "",
        server = "netease"
    )

    private fun downloaded(
        id: String,
        playlistId: String? = null,
        playlistName: String? = null,
        at: Long = 0L
    ) = DownloadedSong(
        song = song(id),
        fileName = "$id.mp3",
        fileSize = 1024L,
        downloadedAt = at,
        originPlaylistId = playlistId,
        originPlaylistName = playlistName
    )

    private fun saved(id: String, name: String, songIds: List<String>) = SavedPlaylist(
        playlistId = id,
        name = name,
        songs = songIds.map { song(it) }
    )

    @Test
    fun `有来源信息的按歌单分组`() {
        val groups = groupDownloads(
            downloads = listOf(
                downloaded("1", "p1", "Blog", at = 10),
                downloaded("2", "p1", "Blog", at = 11),
                downloaded("3", "p2", "我喜欢的音乐", at = 12)
            ),
            savedPlaylists = emptyList()
        )
        assertEquals(2, groups.size)
        // 分组顺序按最近下载时间倒序，所以这里按名字取，不依赖顺序
        val blog = groups.first { it.name == "Blog" }
        assertEquals(2, blog.size)
        assertEquals("p1", blog.playlistId)
        val liked = groups.first { it.name == "我喜欢的音乐" }
        assertEquals(1, liked.size)
        assertEquals("p2", liked.playlistId)
    }

    @Test
    fun `没有来源且不在任何歌单里的算单曲`() {
        val groups = groupDownloads(
            downloads = listOf(downloaded("9", at = 5)),
            savedPlaylists = emptyList()
        )
        assertEquals(1, groups.size)
        assertTrue(groups[0].isSingle)
        assertEquals(DownloadGroup.SINGLE_NAME, groups[0].name)
    }

    @Test
    fun `老数据用已保存歌单反推来源`() {
        // 老记录没有 originPlaylistId，但歌在某个已保存歌单里 —— 应该归到那个歌单
        val groups = groupDownloads(
            downloads = listOf(
                downloaded("1"),           // 在 p1 里
                downloaded("2"),           // 在 p1 里
                downloaded("99")           // 哪个歌单都没有
            ),
            savedPlaylists = listOf(saved("p1", "Blog", listOf("1", "2")))
        )
        assertEquals(2, groups.size)
        val blog = groups.first { it.playlistId == "p1" }
        assertEquals("Blog", blog.name)
        assertEquals(2, blog.size)
        assertTrue(groups.last().isSingle)
        assertEquals(1, groups.last().size)
    }

    @Test
    fun `单曲组永远排在最后`() {
        val groups = groupDownloads(
            downloads = listOf(
                downloaded("9", at = 999),                       // 单曲，时间最新
                downloaded("1", "p1", "Blog", at = 1)            // 歌单，时间最旧
            ),
            savedPlaylists = emptyList()
        )
        assertEquals(2, groups.size)
        assertEquals("Blog", groups[0].name)
        assertTrue(groups.last().isSingle)
    }

    @Test
    fun `歌单组按最近下载时间倒序`() {
        val groups = groupDownloads(
            downloads = listOf(
                downloaded("1", "p1", "旧歌单", at = 10),
                downloaded("2", "p2", "新歌单", at = 100)
            ),
            savedPlaylists = emptyList()
        )
        assertEquals(listOf("新歌单", "旧歌单"), groups.map { it.name })
    }

    @Test
    fun `有 id 但没有名字时回退到已保存歌单名`() {
        val groups = groupDownloads(
            downloads = listOf(downloaded("1", "p1", playlistName = null)),
            savedPlaylists = listOf(saved("p1", "Blog", listOf("1")))
        )
        assertEquals("Blog", groups[0].name)
    }

    @Test
    fun `完全没有名字时用歌单号兜底`() {
        val groups = groupDownloads(
            downloads = listOf(downloaded("1", "p1", playlistName = null)),
            savedPlaylists = emptyList()
        )
        assertEquals("歌单 p1", groups[0].name)
        assertEquals("p1", groups[0].playlistId)
    }

    @Test
    fun `空输入返回空列表`() {
        assertTrue(groupDownloads(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `分组后不丢歌也不重复`() {
        val items = listOf(
            downloaded("1", "p1", "Blog"),
            downloaded("2", null),
            downloaded("3", "p2", "另一个")
        )
        val groups = groupDownloads(items, emptyList())
        val flattened = groups.flatMap { it.songs }.map { it.song.id }.sorted()
        assertEquals(listOf("1", "2", "3"), flattened)
    }
}
