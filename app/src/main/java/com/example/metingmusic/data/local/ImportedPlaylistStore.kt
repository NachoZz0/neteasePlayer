package com.example.metingmusic.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.metingmusic.data.model.Song
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 导入过的网易云歌单记录。
 *
 * 只存歌单号、名称和歌曲列表 —— 播放直链每次播放时实时解析（直链带时效签名存不住）。
 *
 * [cover] / [userId] / [creatorId] / [creatorName] 是为了「歌单封面」和「分享」两个功能：
 * 封面默认取歌单第一首歌的图，分享链接则需要把创建者信息一起带上才完整。
 */
@Immutable
data class SavedPlaylist(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("name") val name: String,
    @SerializedName("songs") val songs: List<Song> = emptyList(),
    @SerializedName("savedAt") val savedAt: Long = System.currentTimeMillis(),
    /**
     * 封面图地址；为空时回退到第一首歌的封面。
     *
     * ⚠️ 这里必须是可空类型。Gson 反序列化不走 Kotlin 构造器，而是用 Unsafe 直接写字段，
     * 所以「JSON 里没有这个字段」时它会保持 **null**，即使 Kotlin 声明了非空 + 默认值。
     * 本次新增 cover 字段后，老记录（JSON 里没有 cover）读出来就是 null，
     * 声明成非空 String 会在访问时直接抛 NPE。
     */
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("creatorId") val creatorId: String? = null,
    @SerializedName("creatorName") val creatorName: String? = null
) {
    val size: Int get() = songs.size

    /**
     * 实际用于展示的封面。
     *
     * 用户明确要求「默认以歌单第一个歌曲封面为保存的歌单封面」，
     * 所以老数据（没有 cover）以及封面为空的情况都回退到第一首歌的图。
     */
    val displayCover: String
        get() = cover?.takeIf { it.isNotBlank() }
            ?: songs.firstOrNull()?.pic.orEmpty()
}

/**
 * 导入歌单的本地存储。
 *
 * 一个歌单可能有上千首（实测 1000 首约 300KB JSON），用 SharedPreferences 存是够的；
 * 若未来出现超大歌单，再换 Room 也不影响上层接口。
 */
class ImportedPlaylistStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _playlists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val playlists: StateFlow<List<SavedPlaylist>> = _playlists.asStateFlow()

    init {
        _playlists.value = readFromDisk()
    }

    /**
     * 保存/覆盖一个导入的歌单（同一歌单号重复导入则更新内容）。
     *
     * 封面默认取第一首歌的图；[userId] 等分享信息若本次没解析到，
     * 就沿用旧记录里的值，避免「重新导入一次把创建者信息弄丢」。
     */
    fun save(
        playlistId: String,
        name: String,
        songs: List<Song>,
        cover: String = "",
        userId: String? = null,
        creatorId: String? = null,
        creatorName: String? = null
    ) {
        val previous = get(playlistId)
        val record = SavedPlaylist(
            playlistId = playlistId,
            name = name,
            songs = songs,
            cover = cover.ifBlank { songs.firstOrNull()?.pic.orEmpty() },
            userId = userId ?: previous?.userId,
            creatorId = creatorId ?: previous?.creatorId,
            creatorName = creatorName ?: previous?.creatorName
        )
        mutate { list ->
            // 同一个歌单号只保留一条，放到最前面
            listOf(record) + list.filterNot { it.playlistId == playlistId }
        }
    }

    fun rename(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        mutate { list ->
            list.map { if (it.playlistId == playlistId) it.copy(name = clean) else it }
        }
    }

    fun delete(playlistId: String) {
        mutate { list -> list.filterNot { it.playlistId == playlistId } }
    }

    /** 批量删除（长按多选）。一次写盘，避免 N 次 IO。 */
    fun deleteMany(playlistIds: Collection<String>) {
        if (playlistIds.isEmpty()) return
        val ids = playlistIds.toSet()
        mutate { list -> list.filterNot { it.playlistId in ids } }
    }

    fun get(playlistId: String): SavedPlaylist? =
        _playlists.value.firstOrNull { it.playlistId == playlistId }

    private fun mutate(block: (List<SavedPlaylist>) -> List<SavedPlaylist>) {
        _playlists.update(block)
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(_playlists.value)).apply()
    }

    private fun readFromDisk(): List<SavedPlaylist> {
        val json = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedPlaylist>>() {}.type
        return runCatching { gson.fromJson<List<SavedPlaylist>>(json, type) }
            .getOrNull().orEmpty()
    }

    companion object {
        private const val PREFS_NAME = "meting_imported_playlists"
        private const val KEY_PLAYLISTS = "imported_playlists_json"

        @Volatile
        private var instance: ImportedPlaylistStore? = null

        fun get(context: Context): ImportedPlaylistStore =
            instance ?: synchronized(this) {
                instance ?: ImportedPlaylistStore(context.applicationContext).also { instance = it }
            }
    }
}
