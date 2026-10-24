package com.example.metingmusic.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** 深浅色模式。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

/**
 * 外观设置。
 *
 * 全部收在一个对象里，方便一次性读写 —— 这些项之间是有关联的
 * （比如「从背景图取色」依赖背景图存在），分开存容易出现半更新状态。
 */
@Immutable
data class Appearance(
    @SerializedName("themeMode") val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 自定义背景图的本地绝对路径；null 表示用内置插画。 */
    @SerializedName("backgroundPath") val backgroundPath: String? = null,
    /** 自定义主题色（ARGB）；null 表示用默认的淡蓝。 */
    @SerializedName("accentColor") val accentColor: Int? = null,
    /** 是否让主题色跟随背景图自动取色。 */
    @SerializedName("accentFromBackground") val accentFromBackground: Boolean = false,
    /**
     * 底部栏（导航栏 + 迷你播放器）的磨砂玻璃质感。
     *
     * 字段名沿用 navBarFrosted，是为了不把用户已经选过的设置冲掉。
     */
    @SerializedName("navBarFrosted") val bottomFrosted: Boolean = false,
    /**
     * 全局模糊。
     *
     * 开启后背景本身会被模糊掉，同时歌曲卡片、歌单行、主要按钮都变成半透明的「玻璃」。
     *
     * 实现上**只模糊一次背景**，而不是给每个卡片单独做模糊 ——
     * 一屏可能有二三十个卡片，每个都建一个全屏模糊层会直接卡死。
     * 背景糊掉之后，卡片只要半透明就已经是磨砂玻璃的观感了。
     */
    @SerializedName("globalBlur") val globalBlur: Boolean = false,
    /**
     * 底部栏背景模糊的强度（dp）。
     *
     * 范围 0~80：0 等于关掉模糊只留半透明，80 基本糊成一片色块。
     * 参考 NeriPlayer 的默认值是 18~28dp；这里默认给 60dp，因为列表是白字小图标，
     * 同样的半径下比纯色封面更容易看出轮廓，要更糊一点才像参考图。
     */
    @SerializedName("blurRadiusDp") val blurRadiusDp: Float = 60f,
    /**
     * 底部导航栏是否悬浮。
     *
     * true = 浮在内容上的胶囊；false = 贴底的整条导航栏。
     */
    @SerializedName("floatingNavBar") val floatingNavBar: Boolean = true
)

/**
 * 外观设置的持久化。
 *
 * 存在 SharedPreferences 里（一个 JSON），和项目里其他 Store 保持一致。
 */
class AppearanceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): Appearance {
        val json = prefs.getString(KEY_APPEARANCE, null) ?: return Appearance()
        return runCatching { gson.fromJson(json, Appearance::class.java) }
            .getOrNull() ?: Appearance()
    }

    fun save(appearance: Appearance) {
        prefs.edit()
            .putString(KEY_APPEARANCE, gson.toJson(appearance))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "meting_appearance"
        private const val KEY_APPEARANCE = "appearance_json"

        @Volatile
        private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) {
                instance ?: AppearanceStore(context.applicationContext).also { instance = it }
            }
    }
}
