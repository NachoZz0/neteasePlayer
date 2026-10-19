package com.example.metingmusic.data.local

import android.content.Context

/**
 * 定时播放（睡眠定时）的设置。
 *
 * 只存「上次选的时长」和「是否播完当前歌曲再暂停」这两个偏好，
 * 不存剩余时间 —— 定时是本次会话的事，重启后不应该还挂着倒计时。
 */
class SleepTimerStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次设置的时长（分钟）。默认 30。 */
    var minutes: Int
        get() = prefs.getInt(KEY_MINUTES, DEFAULT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_MINUTES, value).apply()

    /** 是否「播完当前歌曲再暂停」。 */
    var finishCurrentSong: Boolean
        get() = prefs.getBoolean(KEY_FINISH_CURRENT, false)
        set(value) = prefs.edit().putBoolean(KEY_FINISH_CURRENT, value).apply()

    companion object {
        private const val PREFS_NAME = "meting_sleep_timer"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_FINISH_CURRENT = "finish_current_song"
        const val DEFAULT_MINUTES = 30

        @Volatile
        private var instance: SleepTimerStore? = null

        fun get(context: Context): SleepTimerStore =
            instance ?: synchronized(this) {
                instance ?: SleepTimerStore(context.applicationContext).also { instance = it }
            }
    }
}
