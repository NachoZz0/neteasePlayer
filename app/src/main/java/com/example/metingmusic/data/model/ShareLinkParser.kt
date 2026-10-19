package com.example.metingmusic.data.model

/**
 * 从网易云分享文本里解析出来的目标。
 *
 * 网易云的分享文本有好几种写法，实测长这样：
 *
 * 1. 歌单（带创建者昵称，名称在「」里）
 *    `分享镜子的反面照不出你创建的歌单「Blog」: https://y.music.163.com/m/playlist?id=14117428052&userid=9046537786&creatorId=9046537786 (来自 @网易云音乐)`
 * 2. 歌单（没有「」，名称和创建者用空格隔开）
 *    `分享歌单: 镜子的反面照不出你喜欢的音乐 镜子的反面照不出你 https://music.163.com/m/playlist?id=9104755614&creatorId=9046537786`
 * 3. 单曲
 *    `分享单曲: 歌名 歌手 https://y.music.163.com/m/song?id=1234567&userid=123 (来自 @网易云音乐)`
 * 4. 用户直接粘歌单号 `24381616`
 */
sealed interface ShareTarget {

    /**
     * 歌单。
     *
     * [userId] / [creatorId] / [creatorName] 都是从分享文本里顺带捞出来的 ——
     * 存下来是为了「分享歌单」时能把链接原样还原（网易云的链接必须带 creatorId 才完整）。
     */
    data class Playlist(
        val playlistId: String,
        val name: String? = null,
        val userId: String? = null,
        val creatorId: String? = null,
        val creatorName: String? = null
    ) : ShareTarget

    /** 单曲。 */
    data class Song(val songId: String) : ShareTarget
}

/**
 * 分享文本解析器。
 *
 * 设计上刻意「宽进」：用户从哪儿复制来的都能认，认不出来就返回 null 让调用方提示，
 * 不做「格式不对就报错」这种较真的事。
 */
object ShareLinkParser {

    /** 歌单：`/playlist?id=123`、`playlist?id=123` 都行。 */
    private val playlistId = Regex("playlist\\??[/#]?(?:\\?|&)?id=(\\d+)")

    /** 单曲：`/song?id=123`。 */
    private val songId = Regex("song\\??[/#]?(?:\\?|&)?id=(\\d+)")

    private val userId = Regex("[?&]userid=(\\d+)", RegexOption.IGNORE_CASE)
    private val creatorId = Regex("[?&]creatorid=(\\d+)", RegexOption.IGNORE_CASE)

    /** 名称在中文书名号里：`歌单「Blog」`。 */
    private val bracketName = Regex("「([^」]{1,80})」")

    /** `分享歌单: 名称 创建者 https://...` —— 名称取到 URL 之前。 */
    private val afterSharePlaylist = Regex("分享歌单\\s*[:：]\\s*(.+?)\\s*(?=https?://|$)")

    /** `分享XXX创建的歌单` —— XXX 就是创建者昵称。 */
    private val creatorBeforeCreated = Regex("分享\\s*(.+?)\\s*创建的歌单")

    fun parse(input: String): ShareTarget? {
        val text = input.trim()
        if (text.isEmpty()) return null

        // 单曲优先：链接里同时出现 song 和 playlist 的情况不存在，
        // 但先把 song 判掉能避免 `id=` 被歌单正则误吃
        songId.find(text)?.let { return ShareTarget.Song(it.groupValues[1]) }

        playlistId.find(text)?.let { m ->
            return ShareTarget.Playlist(
                playlistId = m.groupValues[1],
                name = extractPlaylistName(text),
                userId = userId.find(text)?.groupValues?.getOrNull(1),
                creatorId = creatorId.find(text)?.groupValues?.getOrNull(1),
                creatorName = creatorBeforeCreated.find(text)?.groupValues?.getOrNull(1)?.trim()
            )
        }

        // 兜底：用户直接粘歌单号
        PlaylistIdParser.parse(text)?.let { return ShareTarget.Playlist(playlistId = it) }
        return null
    }

    /**
     * 从分享文本里猜歌单名称。
     *
     * 优先取「」里的内容（最准），否则取 `分享歌单:` 之后、URL 之前的那一段。
     * 第二种情况没法可靠地把「歌单名」和「创建者昵称」分开，
     * 所以整段一起当名字用 —— 反正只是给输入框做预填，用户可以自己改。
     */
    private fun extractPlaylistName(text: String): String? {
        bracketName.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return afterSharePlaylist.find(text)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * 生成网易云风格的歌单分享文本。
     *
     * 链接参数只拼我们确实知道的：userid / creatorId 缺失就不写，
     * 而不是填个占位数字 —— 那样生成出来的链接点开是错的。
     */
    fun buildPlaylistShareText(
        playlistId: String,
        name: String,
        userId: String?,
        creatorId: String?,
        creatorName: String?
    ): String {
        val url = buildString {
            append("https://y.music.163.com/m/playlist?id=")
            append(playlistId)
            userId?.takeIf { it.isNotBlank() }?.let { append("&userid=").append(it) }
            creatorId?.takeIf { it.isNotBlank() }?.let { append("&creatorId=").append(it) }
        }
        val creatorPart = creatorName?.takeIf { it.isNotBlank() }?.let { "${it}创建的" }.orEmpty()
        return "分享${creatorPart}歌单「$name」: $url (来自 @网易云音乐)"
    }
}
