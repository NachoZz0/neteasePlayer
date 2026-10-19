package com.example.metingmusic.data.local

import android.content.Context
import com.example.metingmusic.data.model.DownloadLibrary
import com.example.metingmusic.data.model.DownloadState
import com.example.metingmusic.data.model.DownloadedSong
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.data.remote.MetingApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * 音乐下载器。
 *
 * 设计要点：
 *
 * 1. **文件存到应用私有目录** `filesDir/downloads/`。这样不需要任何存储权限，
 *    也不用管 Android 10+ 的分区存储（Scoped Storage）适配，卸载 App 自动清理。
 *
 * 2. **不复用 ExoPlayer 的缓存**。ExoPlayer 的 `CacheDataSource` 是为「边下边播」
 *    设计的，分片存储、无法直接拿到完整文件；而用户要的是能长期离线播放的整首歌。
 *    所以这里用 OkHttp 独立流式下载到独立文件，播放时直接读本地文件。
 *
 * 3. **先下到 .part 再改名**。下载中断时磁盘上只有 `.part` 文件，
 *    不会被误认为是一个可播放的完整音频；下载成功才改成正式名并写入下载库。
 */
class DownloadManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 独立 OkHttpClient：下载走长连接，超时要比接口请求宽松得多。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _library = MutableStateFlow(DownloadLibrary(emptyList()))
    val library: StateFlow<DownloadLibrary> = _library.asStateFlow()

    /**
     * 每个下载任务对应的 Job。
     *
     * 取消下载必须靠取消协程来做：OkHttp 的同步 `execute()` 在协程被取消时，
     * 会在 `input.read()` 处抛出 CancellationException 把连接一起关掉，
     * 光把状态改成「已取消」是停不掉实际网络传输的。
     */
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** 下载目录，播放器读本地文件时也用它拼路径。 */
    val downloadDir: File
        get() = File(appContext.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    init {
        // 清理上次异常退出残留的半成品文件
        runCatching {
            downloadDir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
        }
        _library.value = DownloadLibrary(readLibrary())
    }

    /**
     * 下载一首歌。
     *
     * [streamUrl] 必须已经解析好的真实直链（不是 API 的 type=url 接口地址）。
     * [originPlaylistId] / [originPlaylistName] 用来在下载页按歌单归类；
     * 传 null 表示「单曲下载」。
     */
    fun download(
        song: Song,
        streamUrl: String,
        bitrate: Int,
        originPlaylistId: String? = null,
        originPlaylistName: String? = null
    ) {
        val id = song.id
        // 已在下载或已下载完成，直接忽略，避免重复任务
        val current = _library.value.taskOf(id)
        if (current is DownloadState.Downloading || current is DownloadState.Done) return
        if (_library.value.isDownloaded(id)) return

        val job = scope.launch {
            setTask(id, DownloadState.Downloading(0, song))
            val partFile = File(downloadDir, "$id$PART_SUFFIX")
            try {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("下载失败：HTTP ${response.code}")
                    }
                    val body = response.body
                        ?: throw java.io.IOException("下载失败：响应为空")
                    val total = body.contentLength()

                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var lastPercent = -1
                            while (true) {
                                // 协程被取消时这里会抛 CancellationException，连接随之关闭
                                if (!isActive) throw CancellationException("已取消")
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val percent = ((downloaded * 100) / total).toInt()
                                    // 只在百分比变化时更新状态，避免每 64KB 就刷一次 UI
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        setTask(id, DownloadState.Downloading(percent, song))
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (!partFile.exists() || partFile.length() == 0L) {
                        throw java.io.IOException("下载失败：文件为空")
                    }

                    // 校验拿到的确实是音频，避免把错误页面存成「歌曲」
                    if (!looksLikeAudio(partFile)) {
                        partFile.delete()
                        throw java.io.IOException("下载失败：拿到的不是音频文件")
                    }

                    val finalFile = File(downloadDir, "$id$AUDIO_SUFFIX")
                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        throw java.io.IOException("下载失败：无法保存文件")
                    }

                    val record = DownloadedSong(
                        song = song,
                        fileName = finalFile.name,
                        bitrate = bitrate,
                        fileSize = finalFile.length(),
                        originPlaylistId = originPlaylistId,
                        originPlaylistName = originPlaylistName
                    )
                    val updated = _library.value.songs.filterNot { it.song.id == id } + record
                    writeLibrary(updated)
                    _library.update {
                        it.copy(
                            songs = updated,
                            tasks = it.tasks + (id to DownloadState.Done(record))
                        )
                    }
                }
            } catch (e: CancellationException) {
                // 用户主动取消：清掉半成品，标记为「已取消」而不是「失败」
                runCatching { if (partFile.exists()) partFile.delete() }
                setTask(id, DownloadState.Cancelled(song))
                throw e
            } catch (e: Exception) {
                runCatching { if (partFile.exists()) partFile.delete() }
                setTask(id, DownloadState.Failed(e.message ?: "下载失败", song))
            }
        }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id) }
    }

    /**
     * 取消某一首的下载。
     *
     * 取消后状态置为 [DownloadState.Cancelled]，而不是直接删掉任务 ——
     * 批量下载里的 `awaitFinished` 正等着这个任务结束，删掉会让它永远等下去。
     */
    fun cancel(songId: String) {
        jobs[songId]?.cancel()
    }

    /** 取消全部正在进行的下载。 */
    fun cancelAll() {
        jobs.keys.toList().forEach { jobs[it]?.cancel() }
    }

    /** 当前是否还有下载任务在跑。 */
    val hasActiveDownloads: Boolean get() = jobs.isNotEmpty()


    /** 删除一首已下载的歌（连同音频文件）。 */
    fun delete(songId: String) {
        // 顺带把任务状态清掉：可能是「下载中」被删（先取消），也可能是失败/取消的残留
        jobs[songId]?.cancel()
        val record = _library.value.downloadedOf(songId)
        if (record != null) {
            runCatching { File(downloadDir, record.fileName).delete() }
        }
        val updated = _library.value.songs.filterNot { it.song.id == songId }
        writeLibrary(updated)
        _library.update {
            it.copy(
                songs = updated,
                tasks = it.tasks - songId
            )
        }
    }

    /** 已下载歌曲在本地的绝对路径；未下载返回 null。 */
    fun localPathOf(songId: String): String? {
        val record = _library.value.downloadedOf(songId) ?: return null
        val file = File(downloadDir, record.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun isDownloaded(songId: String): Boolean = localPathOf(songId) != null

    /**
     * 清掉失败/取消任务的状态，让按钮恢复可点。
     *
     * 下载中的任务不在这里清 —— 那需要走 [cancel] 才能真正停下来。
     */
    fun clearTask(songId: String) {
        _library.update {
            val task = it.taskOf(songId)
            if (task is DownloadState.Failed || task is DownloadState.Cancelled) {
                it.copy(tasks = it.tasks - songId)
            } else it
        }
    }

    /** 清掉所有失败/取消的任务状态。 */
    fun clearFinishedTasks() {
        _library.update {
            it.copy(
                tasks = it.tasks.filterValues { t ->
                    t !is DownloadState.Failed && t !is DownloadState.Cancelled
                }
            )
        }
    }

    /**
     * 挂起等待某一首下载结束（成功、失败或取消）。
     *
     * 批量下载歌单时用它把并发收敛成串行 —— 一次性并发几百个连接
     * 既容易被 CDN 限速，也会让进度条乱跳。
     *
     * ⚠️ 「已取消」也算结束：否则用户取消后，批量循环会卡在这一首上永远等下去。
     */
    suspend fun awaitFinished(songId: String) {
        library.first { lib ->
            val task = lib.taskOf(songId)
            task is DownloadState.Done ||
                task is DownloadState.Failed ||
                task is DownloadState.Cancelled
        }
    }

    // ------------------------------------------------------------ 内部实现

    private fun setTask(songId: String, state: DownloadState) {
        _library.update { it.copy(tasks = it.tasks + (songId to state)) }
    }

    /**
     * 简单判断文件是不是音频。
     *
     * 只看文件头几个字节的魔数：mp3 可能以 `ID3` 开头（带标签）或直接是帧同步 `0xFF 0xFB`；
     * flac 以 `fLaC` 开头。这样能挡住「接口返回了一段 JSON 错误信息但我们存成了 .mp3」的情况。
     */
    private fun looksLikeAudio(file: File): Boolean = runCatching {
        if (file.length() < 4) return false
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        val isId3 = header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()
        val isMp3Frame = header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0
        val isFlac = header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() && header[3] == 0x43.toByte()
        val isM4a = header[0] == 0x00.toByte() && header[1] == 0x00.toByte() &&
            header[2] == 0x00.toByte() && header[3] == 0x20.toByte()
        isId3 || isMp3Frame || isFlac || isM4a
    }.getOrDefault(false)

    private fun readLibrary(): List<DownloadedSong> {
        val json = prefs.getString(KEY_LIBRARY, null) ?: return emptyList()
        val type = object : TypeToken<List<DownloadedSong>>() {}.type
        val all = runCatching { gson.fromJson<List<DownloadedSong>>(json, type) }
            .getOrNull().orEmpty()
        // 只保留文件仍然存在的记录（用户可能手动清理过目录）
        return all.filter { File(downloadDir, it.fileName).exists() }
    }

    private fun writeLibrary(list: List<DownloadedSong>) {
        prefs.edit().putString(KEY_LIBRARY, gson.toJson(list)).apply()
    }

    companion object {
        private const val PREFS_NAME = "meting_downloads"
        private const val KEY_LIBRARY = "download_library_json"
        private const val DIR_NAME = "downloads"
        private const val PART_SUFFIX = ".part"
        private const val AUDIO_SUFFIX = ".mp3"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        @Volatile
        private var instance: DownloadManager? = null

        fun get(context: Context): DownloadManager =
            instance ?: synchronized(this) {
                instance ?: DownloadManager(context.applicationContext).also { instance = it }
            }
    }
}
