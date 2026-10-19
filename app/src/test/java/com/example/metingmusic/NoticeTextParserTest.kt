package com.example.metingmusic

import com.example.metingmusic.data.model.NoticeTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公告文本的链接切分测试。
 *
 * 公告正文由作者随时改，格式不可控。链接识别错一个字符，用户就会点到一个坏地址，
 * 所以把「中文标点紧跟在 URL 后面」「英文句号粘在末尾」这些实际会遇到的情况都固定下来。
 */
class NoticeTextParserTest {

    @Test
    fun `没有链接时是单个普通片段`() {
        val segs = NoticeTextParser.segments("本项目依赖公开 api，随时失效。")
        assertEquals(1, segs.size)
        assertFalse(segs[0].isLink)
        assertEquals("本项目依赖公开 api，随时失效。", segs[0].text)
    }

    @Test
    fun `链接被单独切出来`() {
        val segs = NoticeTextParser.segments("开源地址：https://github.com/foo/bar 欢迎 star")
        assertEquals(3, segs.size)
        assertEquals("开源地址：", segs[0].text)
        assertFalse(segs[0].isLink)
        assertEquals("https://github.com/foo/bar", segs[1].text)
        assertTrue(segs[1].isLink)
        assertEquals(" 欢迎 star", segs[2].text)
        assertFalse(segs[2].isLink)
    }

    @Test
    fun `中文标点不会被吞进链接`() {
        // 中文语境里 URL 后面经常紧跟「，」「。」，不能把它们算进链接
        val segs = NoticeTextParser.segments("详见 https://a.com/x，谢谢")
        val link = segs.first { it.isLink }
        assertEquals("https://a.com/x", link.text)
        assertTrue(segs.last().text.contains("，"))
    }

    @Test
    fun `末尾英文句号会被剥掉`() {
        val segs = NoticeTextParser.segments("详见 https://a.com/x.")
        val link = segs.first { it.isLink }
        assertEquals("https://a.com/x", link.text)
        assertEquals(".", segs.last().text)
    }

    @Test
    fun `多个链接都能识别`() {
        val segs = NoticeTextParser.segments(
            "A https://a.com/1 B https://b.com/2 C"
        )
        val links = segs.filter { it.isLink }.map { it.text }
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), links)
    }

    @Test
    fun `纯链接文本不会丢字符`() {
        val raw = "https://example.com/a?b=1&c=2"
        val segs = NoticeTextParser.segments(raw)
        assertEquals(1, segs.size)
        assertTrue(segs[0].isLink)
        assertEquals(raw, segs[0].text)
    }

    @Test
    fun `空文本返回空列表`() {
        assertTrue(NoticeTextParser.segments("").isEmpty())
    }

    @Test
    fun `hasLink 判断正确`() {
        assertFalse(NoticeTextParser.hasLink("没有链接的一段话"))
        assertTrue(NoticeTextParser.hasLink("有链接 https://a.com"))
    }
}
