package com.example.metingmusic.data.model

/**
 * 从用户输入里提取网易云歌单 ID。
 *
 * 用户可能直接填歌单号，也可能从 App 里复制一整条分享链接，例如：
 *  - `24381616`
 *  - `https://music.163.com/playlist?id=24381616`
 *  - `https://music.163.com/#/playlist?id=24381616&userid=123`
 *  - `分享歌单: https://y.music.163.com/m/playlist?id=3778678`
 *
 * 这些全都落到「找 `id=` 后面的数字」这一条规则上，所以统一用正则处理，
 * 拿不到就返回 null 让调用方提示用户。
 */
object PlaylistIdParser {

    /** `?id=`、`&id=`、`#id=`、`/id=` 都能命中。 */
    private val idInUrl = Regex("[?&#/]id=(\\d+)")

    /** 纯数字（允许前后有空格）。 */
    private val pureNumber = Regex("^\\s*(\\d{4,})\\s*$")

    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        // 先按纯数字试，避免把 "12345" 当成 URL 的一部分误判
        pureNumber.find(text)?.let { return it.groupValues[1] }
        return idInUrl.find(text)?.groupValues?.getOrNull(1)
    }

    /** 是否是合法的歌单号（用于输入框实时校验，给用户即时反馈）。 */
    fun isValid(input: String): Boolean = parse(input) != null
}
