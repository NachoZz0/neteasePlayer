package com.example.metingmusic

import com.example.metingmusic.data.model.ShareLinkParser
import com.example.metingmusic.data.model.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享文本解析测试。
 *
 * 用例全部取自用户真实粘贴过的两种网易云分享文案 ——
 * 这两种格式差异很大（一种名称在「」里、一种用空格分隔），
 * 是最容易解析错的地方，所以固定下来当回归测试。
 */
class ShareLinkParserTest {

    /** 格式一：名称在「」里，带 userid / creatorId。 */
    private val shareWithBracket =
        "分享镜子的反面照不出你创建的歌单「Blog」: " +
            "https://y.music.163.com/m/playlist?id=14117428052&userid=9046537786" +
            "&creatorId=9046537786 (来自 @网易云音乐)"

    /** 格式二：没有「」，名称与创建者用空格隔开，只有 creatorId。 */
    private val shareWithoutBracket =
        "分享歌单: 镜子的反面照不出你喜欢的音乐 镜子的反面照不出你 " +
            "https://music.163.com/m/playlist?id=9104755614&creatorId=9046537786"

    @Test
    fun `解析带书名号的分享文案`() {
        val t = ShareLinkParser.parse(shareWithBracket)
        assertTrue(t is ShareTarget.Playlist)
        t as ShareTarget.Playlist
        assertEquals("14117428052", t.playlistId)
        assertEquals("Blog", t.name)
        assertEquals("9046537786", t.userId)
        assertEquals("9046537786", t.creatorId)
        assertEquals("镜子的反面照不出你", t.creatorName)
    }

    @Test
    fun `解析不带书名号的分享文案`() {
        val t = ShareLinkParser.parse(shareWithoutBracket)
        assertTrue(t is ShareTarget.Playlist)
        t as ShareTarget.Playlist
        assertEquals("9104755614", t.playlistId)
        assertEquals("镜子的反面照不出你喜欢的音乐 镜子的反面照不出你", t.name)
        assertNull(t.userId)
        assertEquals("9046537786", t.creatorId)
    }

    @Test
    fun `解析单曲分享链接`() {
        val t = ShareLinkParser.parse(
            "分享单曲: 一縷 大原ゆい子 https://y.music.163.com/m/song?id=1234567&userid=123"
        )
        assertTrue(t is ShareTarget.Song)
        assertEquals("1234567", (t as ShareTarget.Song).songId)
    }

    @Test
    fun `单曲链接不会被当成歌单`() {
        // song?id= 与 playlist?id= 的 id 数字不同，必须落到 Song 而不是 Playlist
        val t = ShareLinkParser.parse("https://music.163.com/#/song?id=999")
        assertTrue(t is ShareTarget.Song)
        assertEquals("999", (t as ShareTarget.Song).songId)
    }

    @Test
    fun `纯数字按歌单处理`() {
        val t = ShareLinkParser.parse("24381616")
        assertTrue(t is ShareTarget.Playlist)
        assertEquals("24381616", (t as ShareTarget.Playlist).playlistId)
    }

    @Test
    fun `无意义的输入返回 null`() {
        assertNull(ShareLinkParser.parse("随便写点什么"))
        assertNull(ShareLinkParser.parse(""))
    }

    // ---------------------------------------------------------- 分享文本生成

    @Test
    fun `生成的分享文本能被自己解析回去`() {
        val text = ShareLinkParser.buildPlaylistShareText(
            playlistId = "14117428052",
            name = "Blog",
            userId = "9046537786",
            creatorId = "9046537786",
            creatorName = "镜子的反面照不出你"
        )
        // 形状要和网易云一致
        assertTrue(text.startsWith("分享镜子的反面照不出你创建的歌单「Blog」: https://y.music.163.com/m/playlist?id=14117428052"))
        assertTrue(text.endsWith("(来自 @网易云音乐)"))
        assertTrue(text.contains("userid=9046537786"))
        assertTrue(text.contains("creatorId=9046537786"))

        // 关键：往返一致 —— 用户复制出去再粘回导入框，信息不能丢
        val back = ShareLinkParser.parse(text) as ShareTarget.Playlist
        assertEquals("14117428052", back.playlistId)
        assertEquals("Blog", back.name)
        assertEquals("9046537786", back.userId)
        assertEquals("9046537786", back.creatorId)
    }

    @Test
    fun `缺少创建者信息时不编造参数`() {
        val text = ShareLinkParser.buildPlaylistShareText(
            playlistId = "9104755614",
            name = "我喜欢的音乐",
            userId = null,
            creatorId = null,
            creatorName = null
        )
        // 没有的信息就不写进链接，而不是填占位数字（那样点开是错的）
        assertTrue(!text.contains("userid="))
        assertTrue(!text.contains("creatorId="))
        assertTrue(text.startsWith("分享歌单「我喜欢的音乐」: "))
    }
}
