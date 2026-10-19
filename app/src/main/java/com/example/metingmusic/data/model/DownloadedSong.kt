package com.example.metingmusic.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 一首已下载到本地的歌曲。
 *
 * 记录里存了原始 [song]，这样「下载」列表页不依赖网络就能展示歌名 / 歌手 / 封面。
 * 音频文件存在应用私有目录，卸载 App 会一起清掉，不需要处理存储权限。
 */
@Immutable
data class DownloadedSong(
    @SerializedName("song") val song: Song,
    /** 音频文件名（相对下载目录），不存绝对路径 —— 应用私有目录在升级后可能变化。 */
    @SerializedName("fileName") val fileName: String,
    @SerializedName("bitrate") val bitrate: Int = 320,
    @SerializedName("fileSize") val fileSize: Long = 0L,
    @SerializedName("downloadedAt") val downloadedAt: Long = System.currentTimeMillis(),
    /**
     * 这首是从哪个歌单下下来的。
     *
     * 为 null 表示「单曲下载」（从搜索结果、播放队列或外部单曲链接下的）。
     *
     * ⚠️ 必须是可空类型：Gson 不走 Kotlin 构造器，老数据没有这两个字段时会保持 null，
     * 声明成非空会在读取时直接 NPE。
     */
    @SerializedName("originPlaylistId") val originPlaylistId: String? = null,
    @SerializedName("originPlaylistName") val originPlaylistName: String? = null
) {
    /** 展示用的文件大小。 */
    val fileSizeLabel: String
        get() = when {
            fileSize <= 0L -> "未知大小"
            fileSize < 1024 * 1024 -> "%.0f KB".format(fileSize / 1024.0)
            else -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
        }
}

/**
 * 下载任务状态。
 *
 * 用密封类而不是「一个 Int 进度 + 一个错误字符串」，
 * 是因为「下载中 / 已完成 / 失败 / 已取消」是互斥的，用类型区分后 UI 不需要自己判断字段组合是否合法。
 *
 * [Downloading] 和 [Cancelled] 都带上 [Song]：下载中的歌还没进下载库，
 * 但「正在下载」列表要显示歌名和封面，所以元信息得随状态一起带着走。
 */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Downloading(val progress: Int, val song: Song) : DownloadState

    data class Done(val downloaded: DownloadedSong) : DownloadState

    data class Failed(val message: String, val song: Song) : DownloadState

    /** 用户手动取消。单独一个状态是为了让 UI 能区分「失败」和「我主动停的」。 */
    data class Cancelled(val song: Song) : DownloadState
}

/** 下载库整体状态，供「下载」列表页消费。 */
@Immutable
data class DownloadLibrary(
    val songs: List<DownloadedSong> = emptyList(),
    /** 歌曲 id -> 该曲的下载状态，用于在各处按钮上显示进度。 */
    val tasks: Map<String, DownloadState> = emptyMap()
) {
    fun isDownloaded(songId: String): Boolean = songs.any { it.song.id == songId }

    fun downloadedOf(songId: String): DownloadedSong? = songs.firstOrNull { it.song.id == songId }

    fun taskOf(songId: String): DownloadState = tasks[songId] ?: DownloadState.Idle

    val totalSize: Long get() = songs.sumOf { it.fileSize }

    /** 正在下载的任务，按歌名排序保证列表不跳动。 */
    val activeDownloads: List<DownloadState.Downloading>
        get() = tasks.values
            .filterIsInstance<DownloadState.Downloading>()
            .sortedBy { it.song.name }

    val downloadingCount: Int get() = activeDownloads.size

    /** 失败 + 被取消的任务，供「一键清理」用。 */
    val finishedWithProblem: List<DownloadState>
        get() = tasks.values.filter {
            it is DownloadState.Failed || it is DownloadState.Cancelled
        }
}
