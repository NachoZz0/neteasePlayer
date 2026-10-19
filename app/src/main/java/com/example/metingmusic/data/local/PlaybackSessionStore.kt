package com.example.metingmusic.data.local

import android.content.Context
import com.example.metingmusic.data.model.Song
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 上次的播放会话：队列、播放位置、播放模式、音质。
 *
 * 这样冷启动后能恢复到用户离开时的状态，而不是每次都从空白开始。
 */
data class PlaybackSession(
    @SerializedName("queue") val queue: List<Song> = emptyList(),
    @SerializedName("currentIndex") val currentIndex: Int = 0,
    @SerializedName("positionMs") val positionMs: Long = 0L,
    /**
     * 播放模式。存枚举名而不是序号 —— 序号在枚举增删时会串位，
     * 名字则是自解释的，将来加模式也不会把老数据读错。
     */
    @SerializedName("playMode") val playMode: String = "SEQUENTIAL",
    @SerializedName("bitrate") val bitrate: Int = 320
) {
    val isValid: Boolean get() = queue.isNotEmpty() && currentIndex in queue.indices
}

/**
 * 播放会话的本地存储。
 *
 * 只存歌曲元信息与播放进度，不存直链 —— 直链带时效签名，
 * 恢复时重新解析一次反而更可靠。
 */
class PlaybackSessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): PlaybackSession? {
        val json = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { gson.fromJson(json, PlaybackSession::class.java) }
            .getOrNull()
            ?.takeIf { it.isValid }
    }

    fun save(session: PlaybackSession) {
        runCatching {
            prefs.edit().putString(KEY_SESSION, gson.toJson(session)).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val PREFS_NAME = "meting_session"
        private const val KEY_SESSION = "playback_session_json"

        @Volatile
        private var instance: PlaybackSessionStore? = null

        fun get(context: Context): PlaybackSessionStore =
            instance ?: synchronized(this) {
                instance ?: PlaybackSessionStore(context.applicationContext).also { instance = it }
            }
    }
}
