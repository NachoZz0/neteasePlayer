package com.example.metingmusic

import com.example.metingmusic.data.local.mergeRefreshedSongs
import com.example.metingmusic.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 刷新歌单时的合并规则测试。
 *
 * 这是个真实的 bug 回归：早期实现直接 `songs = 远端列表`，
 * 用户手动「加入歌单」的歌在刷新后全部消失。
 */
class RefreshMergeTest {

    private fun song(id: String) = Song(
        name = "歌$id",
        artist = "歌手",
        url = "https://api.qijieya.cn/meting/?type=url&id=$id",
        pic = "https://api.qijieya.cn/meting/?type=pic&id=$id",
        lrc = "",
        server = "netease"
    )

    @Test
    fun `没有手动歌曲时直接用远端列表`() {
        val merged = mergeRefreshedSongs(
            remote = listOf(song("1"), song("2")),
            previousSongs = listOf(song("1"), song("2")),
            manualIds = emptySet()
        )
        assertEquals(listOf("1", "2"), merged.map { it.id })
    }

    @Test
    fun `手动加入的歌在刷新后保留`() {
        val merged = mergeRefreshedSongs(
            remote = listOf(song("1"), song("2")),
            previousSongs = listOf(song("1"), song("2"), song("99")),
            manualIds = setOf("99")
        )
        // 远端在前，手动加的接在后面
        assertEquals(listOf("1", "2", "99"), merged.map { it.id })
    }

    @Test
    fun `远端已包含该歌时不重复添加`() {
        val merged = mergeRefreshedSongs(
            remote = listOf(song("1"), song("99")),
            previousSongs = listOf(song("1"), song("99")),
            manualIds = setOf("99")
        )
        assertEquals(listOf("1", "99"), merged.map { it.id })
    }

    @Test
    fun `远端删掉的歌不会被当成手动歌留下`() {
        // 1 曾经在歌单里、但用户没手动加过，现在远端删了 —— 不该复活
        val merged = mergeRefreshedSongs(
            remote = listOf(song("2")),
            previousSongs = listOf(song("1"), song("2")),
            manualIds = emptySet()
        )
        assertEquals(listOf("2"), merged.map { it.id })
    }

    @Test
    fun `远端更新曲目时手动歌依然在末尾`() {
        val merged = mergeRefreshedSongs(
            remote = listOf(song("3"), song("4")),
            previousSongs = listOf(song("1"), song("99")),
            manualIds = setOf("99")
        )
        assertEquals(listOf("3", "4", "99"), merged.map { it.id })
    }

    @Test
    fun `远端为空时也不丢手动歌`() {
        val merged = mergeRefreshedSongs(
            remote = emptyList(),
            previousSongs = listOf(song("99")),
            manualIds = setOf("99")
        )
        assertEquals(listOf("99"), merged.map { it.id })
    }
}
