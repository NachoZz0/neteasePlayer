package com.example.metingmusic.data.model

/**
 * 把公告纯文本切成「普通文本 / 链接」两种片段。
 *
 * 拆成纯数据而不是直接产出 `AnnotatedString`，是为了能在普通 JVM 单元测试里验证 ——
 * 公告正文的格式没法控制，链接识别错一个字符，用户就会点到一个坏地址。
 */
object NoticeTextParser {

    /** 一个片段。 */
    data class Segment(val text: String, val isLink: Boolean)

    /**
     * 链接匹配。
     *
     * 排除空白和常见的中文标点 —— 中文里 URL 后面经常紧跟「，」或「。」，
     * 如果不排除，链接会把标点一起吞进去，点开就是 404。
     */
    private val urlRegex = Regex("""https?://[^\s，。、；：！？（）()【】「」『』《》"'<>]+""")

    /** 句末可能粘上的英文标点，也要剥掉。 */
    private const val TRAILING_PUNCT = ".,;:!?"

    /**
     * 切分文本。
     *
     * 没有链接时返回单个普通片段；有链接时按出现顺序交替切分。
     * 空文本返回空列表。
     */
    fun segments(raw: String): List<Segment> {
        if (raw.isEmpty()) return emptyList()

        val result = mutableListOf<Segment>()
        var cursor = 0

        for (match in urlRegex.findAll(raw)) {
            var url = match.value
            // 剥掉粘在末尾的英文标点（例如 "详见 https://a.com/x." 里的句号）
            while (url.isNotEmpty() && url.last() in TRAILING_PUNCT) {
                url = url.dropLast(1)
            }
            // 剥完如果不像个地址了，就当普通文本处理
            if (url.length <= "https://".length) continue

            val start = match.range.first
            if (start > cursor) {
                result += Segment(raw.substring(cursor, start), isLink = false)
            }
            result += Segment(url, isLink = true)
            // 被剥掉的那几个标点要留在游标后面，作为普通文本继续处理
            cursor = start + url.length
        }

        if (cursor < raw.length) {
            result += Segment(raw.substring(cursor), isLink = false)
        }
        return result
    }

    /** 文本里是否包含至少一个链接（用于决定要不要提示「可点击」）。 */
    fun hasLink(raw: String): Boolean = segments(raw).any { it.isLink }
}
