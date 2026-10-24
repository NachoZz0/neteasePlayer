package com.example.metingmusic.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.metingmusic.data.remote.ReleaseAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 下载 Release 里的 APK 并交给系统安装器。
 *
 * 为什么不自己做静默安装：那需要系统签名或 root。普通应用只能
 * 「下载 → 唤起系统安装器 → 用户点安装」，所以这里做的就是这两步。
 *
 * 装到 `cacheDir/updates/`：缓存目录被系统清理也无所谓，重下即可。
 */
object ApkInstaller {

    private const val DIR = "updates"
    private const val FILE_NAME = "update.apk"

    /**
     * 下载 APK。
     *
     * @param onProgress 0f~1f；服务端没给 Content-Length 时回调 -1f（不确定进度）。
     */
    suspend fun download(
        context: Context,
        asset: ReleaseAsset,
        client: OkHttpClient,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            // 每次覆盖同一个文件名，避免缓存里堆一堆旧包
            val target = File(dir, FILE_NAME)
            if (target.exists()) target.delete()

            val request = Request.Builder()
                .url(asset.browserDownloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载失败（HTTP ${response.code}）")
                val body = response.body ?: error("下载失败：没有响应内容")
                val total = body.contentLength()

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(if (total > 0) (written.toFloat() / total) else -1f)
                        }
                    }
                }
            }

            // 简单校验：至少得是个像样的 APK，否则说明下到的是错误页
            if (target.length() < 100 * 1024) error("下载的文件不完整")
            target
        }
    }

    /** 安装结果。 */
    sealed interface InstallResult {
        data object Started : InstallResult
        /** 缺「安装未知应用」授权，已经帮用户跳到授权页。 */
        data object NeedPermission : InstallResult
        data class Failed(val message: String) : InstallResult
    }

    /**
     * 唤起系统安装器。
     *
     * Android 8+ 装未知来源应用需要单独授权，没给的话系统安装器会被静默拦掉 ——
     * 所以这里检测到没授权就**直接把用户送到那个开关页面**，
     * 而不是丢一句「请去设置里打开」让他自己找。
     */
    fun install(context: Context, apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return InstallResult.NeedPermission
        }

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        }.getOrElse { return InstallResult.Failed("无法读取安装包：${it.message}") }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }
            .fold(
                onSuccess = { InstallResult.Started },
                onFailure = { InstallResult.Failed("无法打开安装器：${it.message}") }
            )
    }

    /** 清掉缓存里的安装包（用户取消或装完后）。 */
    fun clearCache(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }
}
