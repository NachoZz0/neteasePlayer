package com.example.metingmusic

import com.example.metingmusic.data.model.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses basic timestamps`() {
        val lrc = """
            [00:12.00]第一行
            [00:15.50]第二行
            [01:02.30]第三行
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(12_000L, lines[0].timeMs)
        assertEquals("第一行", lines[0].text)
        assertEquals(15_500L, lines[1].timeMs)
        assertEquals(62_300L, lines[2].timeMs)
    }

    @Test
    fun `supports multiple tags on one line`() {
        val lrc = "[00:10.00][01:20.00]重复的副歌"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(80_000L, lines[1].timeMs)
        assertEquals("重复的副歌", lines[1].text)
    }

    @Test
    fun `ignores metadata tags`() {
        val lrc = """
            [ti:歌名]
            [ar:歌手]
            [offset:0]
            [00:05.00]真正的歌词
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("真正的歌词", lines[0].text)
    }

    @Test
    fun `handles millisecond precision of one and two digits`() {
        assertEquals(12_100L, LrcParser.parse("[00:12.1]a")[0].timeMs)
        assertEquals(12_120L, LrcParser.parse("[00:12.12]b")[0].timeMs)
        assertEquals(12_123L, LrcParser.parse("[00:12.123]c")[0].timeMs)
    }

    @Test
    fun `strips credit lines that carry timestamps`() {
        // 真实接口会把制作人员写进 LRC 且带时间轴，必须剔除
        val lrc = """
            [00:00.00] 作词 : 方文山
            [00:01.00] 作曲 : 周杰伦
            [00:02.00] 编曲 : 钟兴民
            [00:03.00] 制作人 : 周杰伦
            [00:20.80]琴键上透着光
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(20_800L, lines[0].timeMs)
        assertEquals("琴键上透着光", lines[0].text)
    }

    @Test
    fun `keeps real lyric lines that contain a colon`() {
        val lines = LrcParser.parse("[00:30.00]他说 : 你好")
        assertEquals(1, lines.size)
        assertEquals("他说 : 你好", lines[0].text)
    }

    @Test
    fun `returns empty list for blank input`() {
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse(null).isEmpty())
    }

    @Test
    fun `formats duration`() {
        assertEquals("00:00", LrcParser.formatDuration(0))
        assertEquals("00:05", LrcParser.formatDuration(5_000))
        assertEquals("01:05", LrcParser.formatDuration(65_000))
        assertEquals("10:00", LrcParser.formatDuration(600_000))
    }
}
