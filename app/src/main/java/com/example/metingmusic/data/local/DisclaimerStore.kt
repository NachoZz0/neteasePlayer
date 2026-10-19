package com.example.metingmusic.data.local

import android.content.Context

/** 免责声明的同意状态。同意过一次就不再弹。 */
class DisclaimerStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasAgreed: Boolean get() = prefs.getBoolean(KEY_AGREED, false)

    fun agree() = prefs.edit().putBoolean(KEY_AGREED, true).apply()

    companion object {
        private const val PREFS_NAME = "meting_disclaimer"
        private const val KEY_AGREED = "agreed"

        @Volatile
        private var instance: DisclaimerStore? = null

        fun get(context: Context): DisclaimerStore =
            instance ?: synchronized(this) {
                instance ?: DisclaimerStore(context.applicationContext).also { instance = it }
            }
    }
}
