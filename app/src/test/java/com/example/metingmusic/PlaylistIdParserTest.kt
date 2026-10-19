package com.example.metingmusic

import com.example.metingmusic.data.model.PlaylistIdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌单号解析测试。
 *
 * 用户会从各种地方粘贴歌单信息（纯数字、完整链接、带 # 的分享链接、
 * 带中文前缀的分享文案），这里把实际遇到过的形态都覆盖住。
 */
class PlaylistIdParserTest {

    @Test
    fun `解析纯数字歌单号`() {
        assertEquals("24381616", PlaylistIdParser.parse("24381616"))
    }

    @Test
    fun `解析数字两侧空白`() {
        assertEquals("2618726551", PlaylistIdParser.parse("  2618726551  "))
    }

    @Test
    fun `解析标准歌单链接`() {
        assertEquals(
            "24381616",
            PlaylistIdParser.parse("https://music.163.com/playlist?id=24381616")
        )
    }

    @Test
    fun `解析带井号的分享链接`() {
        assertEquals(
            "24381616",
            PlaylistIdParser.parse("https://music.163.com/#/playlist?id=24381616&userid=123")
        )
    }

    @Test
    fun `解析移动端短链`() {
        assertEquals(
            "3778678",
            PlaylistIdParser.parse("https://y.music.163.com/m/playlist?id=3778678")
        )
    }

    @Test
    fun `解析带中文前缀的分享文案`() {
        assertEquals(
            "3778678",
            PlaylistIdParser.parse("分享歌单: https://music.163.com/playlist?id=3778678")
        )
    }

    @Test
    fun `空输入返回 null`() {
        assertNull(PlaylistIdParser.parse(""))
        assertNull(PlaylistIdParser.parse("   "))
    }

    @Test
    fun `无法识别的内容返回 null`() {
        assertNull(PlaylistIdParser.parse("abc"))
        assertNull(PlaylistIdParser.parse("https://music.163.com/playlist"))
    }

    @Test
    fun `过短的纯数字不算歌单号`() {
        // 3 位数字多半是用户误输，不应被当成歌单号
        assertNull(PlaylistIdParser.parse("123"))
    }

    @Test
    fun `isValid 与 parse 保持一致`() {
        assertTrue(PlaylistIdParser.isValid("24381616"))
        assertTrue(PlaylistIdParser.isValid("https://music.163.com/playlist?id=3778678"))
        assertFalse(PlaylistIdParser.isValid("nope"))
    }
}
