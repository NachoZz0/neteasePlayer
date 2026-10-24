package com.example.metingmusic.data.remote

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一个 GitHub Release。 */
@Immutable
data class ReleaseInfo(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String = ""
) {
    /** 去掉前缀 v 的版本号，例如 `v1.3.1` -> `1.3.1`。 */
    val version: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

/**
 * 基于 GitHub Release 的更新检查。
 *
 * 约定：**release 的 tag 就是版本号**（例如 `v1.3.1`），
 * 所以只要比较 tag 和当前 versionName 就能判断有没有新版本，不需要额外的清单文件。
 *
 * 用 `/releases` 列表而不是 `/releases/latest`：后者会跳过 prerelease 和 draft，
 * 拿列表自己筛可以顺便把预发布版过滤掉，行为更可控。
 */
class UpdateApi(
    private val client: OkHttpClient = MetingApi.defaultClient
) {

    suspend fun fetchReleases(): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$API_BASE/repos/$OWNER/$REPO/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MetingMusicPlayer-UpdateChecker")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(
                        when (response.code) {
                            404 -> "仓库还没有发布过 Release"
                            403 -> "GitHub 接口限流了，稍后再试"
                            else -> "检查更新失败（HTTP ${response.code}）"
                        }
                    )
                }
                val json = response.body?.string().orEmpty()
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java,
                    ReleaseInfo::class.java
                ).type
                Gson().fromJson<List<ReleaseInfo>>(json, type).orEmpty()
            }
        }
    }

    companion object {
        const val OWNER = "NachoZz0"
        const val REPO = "MetingMusicPlayer"
        const val API_BASE = "https://api.github.com"

        /** 当前版本号（来自 PackageManager，和 release tag 同一个格式）。 */
        fun currentVersion(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

        /** 发布页地址，找不到具体 release 时用。 */
        val releasesPage: String get() = "https://github.com/$OWNER/$REPO/releases"
    }
}

/**
 * 比较两个版本号。
 *
 * 只做「数字段逐段比较」，够用且不会被 `1.10` vs `1.9` 这种坑到 ——
 * 按字符串比会把 1.10 判成比 1.9 小。
 *
 * @return 正数表示 [a] 比 [b] 新，0 表示相同，负数表示更旧。
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}

/** 在 release 列表里找出比 [currentVersion] 新的那一版（忽略预发布）。 */
fun findUpdate(releases: List<ReleaseInfo>, currentVersion: String): ReleaseInfo? =
    releases
        .filter { !it.prerelease && it.version.isNotBlank() }
        .filter { compareVersions(it.version, currentVersion) > 0 }
        .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
