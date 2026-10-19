package com.example.metingmusic.data.model

import java.util.Locale

/**
 * 把 LRC 文本解析成带时间戳的行列表。
 *
 * 支持一行多个时间标签（`[00:12.00][01:30.00]一样的歌词`），
 * 支持 `[mm:ss]`、`[mm:ss.xx]`、`[mm:ss.xxx]` 三种精度。
 * 元信息标签（[ti:]、[ar:] 等）会被忽略。
 */
object LrcParser {

    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    /**
     * 制作人员行。真实接口会把「作词 / 作曲 / 编曲」等也放进 LRC，
     * 且带时间轴（如 `[00:00.00] 作词 : 方文山`），需要在展示时剔除。
     */
    private val creditPattern = Regex(
        "^\\s*(作词|作曲|编曲|制作人|混音|母带|录音|吉他|贝斯|鼓|钢琴|和声|监制|出品|发行|统筹|企划|OP|SP|词|曲)\\s*[:：]"
    )

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val lines = ArrayList<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (isMetadata(line)) return@forEach

            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            if (isCredit(text)) return@forEach

            matches.forEach { m ->
                lines += LyricLine(timeMs = toMillis(m), text = text)
            }
        }
        // 按时间排序，并去掉同一时间戳的重复行
        return lines.sortedBy { it.timeMs }.distinctBy { it.timeMs to it.text }
    }

    private fun isCredit(text: String): Boolean = creditPattern.containsMatchIn(text)

    private fun isMetadata(line: String): Boolean =
        line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
            line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[au:") ||
            line.startsWith("[re:") || line.startsWith("[ve:")

    private fun toMillis(m: MatchResult): Long {
        val min = m.groupValues[1].toLongOrNull() ?: 0L
        val sec = m.groupValues[2].toLongOrNull() ?: 0L
        val frac = m.groupValues[3]
        val millis = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.substring(0, 3).toLong()
        }
        return min * 60_000 + sec * 1_000 + millis
    }

    /** 把毫秒格式化成 mm:ss，用于 UI 展示。 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
    }
}
