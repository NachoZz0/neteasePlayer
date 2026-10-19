package com.example.metingmusic

import com.example.metingmusic.data.local.SavedPlaylist
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 老数据兼容测试。
 *
 * 背景：Gson 反序列化**不走 Kotlin 构造器**，而是用 Unsafe 分配对象后直接反射写字段。
 * 因此「JSON 里没有这个字段」时，字段会保持 JVM 默认值（对象是 null），
 * **即使 Kotlin 声明了非空类型并给了默认值** —— 默认值只在走构造器时才生效。
 *
 * 结果是：给持久化模型新增一个非空字段，老数据读出来就是 null，访问即 NPE。
 * 这个坑在新增 cover 字段时真的踩到了（歌单一打开就崩），所以固定成测试。
 */
class SavedPlaylistMigrationTest {

    private val gson = Gson()

    /** 这是「新增 cover 之前」真实写进 SharedPreferences 的 JSON 形状。 */
    private val legacyJson = """
        [
          {
            "playlistId": "24381616",
            "name": "网易云歌单 24381616",
            "songs": [
              {
                "name": "一縷",
                "artist": "大原ゆい子",
                "url": "https://api.qijieya.cn/meting/?type=url&id=123",
                "pic": "https://api.qijieya.cn/meting/?type=pic&id=123",
                "lrc": "https://api.qijieya.cn/meting/?type=lrc&id=123",
                "server": "netease"
              }
            ],
            "savedAt": 1760000000000
          }
        ]
    """.trimIndent()

    private fun parseLegacy(): List<SavedPlaylist> {
        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        return gson.fromJson(legacyJson, type)
    }

    @Test
    fun `老数据缺少 cover 字段时读出来是 null 而不是崩溃`() {
        val playlist = parseLegacy().first()
        assertNull(playlist.cover)
        // 关键：访问 displayCover 不能抛 NPE（这正是线上崩的那一行）
        assertEquals("https://api.qijieya.cn/meting/?type=pic&id=123", playlist.displayCover)
    }

    @Test
    fun `老数据没有分享信息时为 null`() {
        val playlist = parseLegacy().first()
        assertNull(playlist.userId)
        assertNull(playlist.creatorId)
        assertNull(playlist.creatorName)
    }

    @Test
    fun `没有歌曲时封面回退为空串而不是崩溃`() {
        val json = """[{"playlistId":"1","name":"空歌单","songs":[],"savedAt":1}]"""
        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        val playlist = gson.fromJson<List<SavedPlaylist>>(json, type).first()
        assertEquals("", playlist.displayCover)
    }

    @Test
    fun `新数据带 cover 时优先用 cover`() {
        val json = """
            [{"playlistId":"1","name":"新","songs":[],"savedAt":1,
              "cover":"https://example.com/c.jpg"}]
        """.trimIndent()
        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        val playlist = gson.fromJson<List<SavedPlaylist>>(json, type).first()
        assertEquals("https://example.com/c.jpg", playlist.displayCover)
    }
}
