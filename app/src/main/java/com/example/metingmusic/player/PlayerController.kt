package com.example.metingmusic.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.metingmusic.data.local.DownloadManager
import com.example.metingmusic.data.local.PlaybackSession
import com.example.metingmusic.data.local.PlaybackSessionStore
import com.example.metingmusic.data.local.SleepTimerStore
import com.example.metingmusic.data.model.DownloadState
import com.example.metingmusic.data.model.LyricLine
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.data.remote.MetingApi
import com.example.metingmusic.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

/** 单首下载的等待上限；超过就跳过，避免一首卡住拖死整批。 */
private const val DOWNLOAD_ITEM_TIMEOUT_MS = 120_000L

/**
 * 播放模式。
 *
 * 顺序 / 列表循环 / 单曲循环 / 随机 —— 四种模式共用一个按钮循环切换。
 * 虽然「随机」在专业播放器里和「循环」是两个正交维度，但这里按用户要求合并成一个按钮，
 * 用一个四态枚举表达，避免出现「随机 + 单曲循环」这种点不明白的组合。
 */
enum class PlayMode(val label: String) {
    SEQUENTIAL("顺序播放"),
    LIST_LOOP("列表循环"),
    SINGLE_LOOP("单曲循环"),
    SHUFFLE("随机播放");

    /** 下一个模式（循环）。 */
    val next: PlayMode get() = entries[(ordinal + 1) % entries.size]
}

/** 定时播放（睡眠定时）的运行时状态。 */
@Immutable
data class SleepTimerState(
    val enabled: Boolean = false,
    val remainingMs: Long = 0L,
    /** 上次设置的时长，用于回填对话框。 */
    val minutes: Int = SleepTimerStore.DEFAULT_MINUTES,
    /** 到点后是否等当前歌曲播完再暂停。 */
    val finishCurrentSong: Boolean = false
) {
    /** mm:ss 形式的剩余时间；未启用时为空串。 */
    val label: String
        get() {
            if (!enabled) return ""
            val total = (remainingMs / 1000).coerceAtLeast(0L)
            return "%d:%02d".format(total / 60, total % 60)
        }
}

/**
 * 播放进度。
 *
 * 单独抽成一条流：位置每 500ms 变一次，如果和 [PlayerUiState] 混在一起，
 * 整棵 UI 树（含上千条的歌单列表）都会跟着每 500ms 重组一次，滑动必然掉帧。
 * 现在只有真正需要进度的组件（迷你条、播放页）订阅它。
 */
@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * 批量下载的进度。
 *
 * 为什么不直接用「下载库里有几首在下载中」来判断？
 * 因为批量下载是「解析直链 → 下载 → 再解析下一首」的循环，
 * 解析那一小段时间里下载库是没有 Downloading 任务的 ——
 * 如果 UI 靠它来决定要不要显示「取消下载」，按钮就会一首一闪地跳，
 * 用户根本点不中。所以批量任务的存在与否必须单独记一份。
 */
@Immutable
data class BatchDownloadState(
    val running: Boolean = false,
    /** 已处理（成功或失败）的首数。 */
    val done: Int = 0,
    val total: Int = 0,
    /** 正在下载哪个歌单 / 单曲，用于提示文案。 */
    val label: String = ""
) {
    val fraction: Float
        get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** 播放器对外暴露的 UI 状态（不含高频变化的进度）。 */
@Immutable
data class PlayerUiState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val server: String = "netease",
    val bitrate: Int = 320,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val lyrics: List<LyricLine> = emptyList(),
    val error: String? = null,
    /** 当前播放歌曲是否已下载到本地。 */
    val isCurrentDownloaded: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState()
) {
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex in 0 until queue.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
    val isRepeatOne: Boolean get() = playMode == PlayMode.SINGLE_LOOP
    val isShuffleOn: Boolean get() = playMode == PlayMode.SHUFFLE
}

/**
 * 播放控制中枢。
 *
 * 架构（第 6 轮重构）：
 *
 * **整个队列都作为 MediaItem 交给 ExoPlayer**，播放器的播放列表就是我们的队列。
 * 这样通知栏 / 锁屏 / 蓝牙耳机的「上一首、下一首」按钮才有意义 ——
 * Media3 把这些按钮直接映射成 `seekToNext/PreviousMediaItem()`，
 * 如果播放器里只有一首歌，这两个命令根本不可用（这正是「通知栏切不了歌」的原因）。
 *
 * 之前那套「播放器只持有当前一首 + 自己算 STATE_ENDED 决定下一首」的做法虽然能把
 * 四种模式集中在一处，但代价是牺牲了系统级控制。现在改为把模式映射到
 * Media3 原生的 [Player.setRepeatMode] / [Player.setShuffleModeEnabled]：
 *
 * | 应用模式 | repeatMode | shuffle |
 * |---|---|---|
 * | 顺序播放 | OFF | false |
 * | 列表循环 | ALL | false |
 * | 单曲循环 | ONE | false |
 * | 随机播放 | ALL | true  |
 *
 * 自动续播交给 ExoPlayer 自己完成，不再需要监听 STATE_ENDED。
 *
 * 关于「不预先解析直链」：MediaItem 的 uri 直接给 Meting 的 `type=url` 接口地址，
 * ExoPlayer 拉流时会自己跟随 302 拿到真实音频 —— 所以放进上千首也不会产生上千次解析请求。
 * 歌词仍然是「切到哪首才拉哪首」。
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---- 淡入淡出：时长由设置页决定，0 表示关闭
    var fadeInMs: Int = 200
    var fadeOutMs: Int = 200
    private var fadeJob: Job? = null

    /** 把音量从当前值平滑推到 [target]，结束后执行 [onEnd]。 */
    private fun fadeTo(target: Float, durationMs: Int, onEnd: (() -> Unit)? = null) {
        val c = controller
        fadeJob?.cancel()
        if (c == null || durationMs <= 0) {
            c?.volume = target
            onEnd?.invoke()
            return
        }
        fadeJob = scope.launch {
            val from = c.volume
            val steps = (durationMs / 16).coerceAtLeast(1)
            for (i in 1..steps) {
                c.volume = from + (target - from) * (i.toFloat() / steps)
                delay(16L)
            }
            c.volume = target
            onEnd?.invoke()
        }
    }
    private val repository = MusicRepository()
    private val downloadManager = DownloadManager.get(context)
    private val sessionStore = PlaybackSessionStore.get(context)
    private val sleepStore = SleepTimerStore.get(context)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _batchDownload = MutableStateFlow(BatchDownloadState())
    val batchDownload: StateFlow<BatchDownloadState> = _batchDownload.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sessionJob: Job? = null
    private var sleepJob: Job? = null

    /** 批量下载的调度协程；取消下载时用它停掉后续解析。 */
    private var downloadAllJob: Job? = null

    /** 歌词解析中标记，避免快速切歌时对同一首重复发起请求。 */
    private var lyricsLoadingId: String? = null

    /** 冷启动恢复时记录的待恢复进度（毫秒），首次播放前 seek 过去。 */
    private var pendingRestorePosition: Long = 0L

    /**
     * 定时到点、且用户选了「播完当前歌曲再暂停」时置位。
     * 下一首本该自动开始时改为暂停，并清掉定时。
     */
    private var pauseAfterCurrentSong = false

    /** 控制器还没连上时先攒着播放请求，连上后立刻执行。 */
    private var pendingPlay: Pair<List<Song>, Int>? = null

    /** 上一次已知的「已下载集合」，用来发现「刚刚下载完成」的那首歌。 */
    private var knownDownloadedIds: Set<String> = emptySet()

    /**
     * 进入随机播放前的原始顺序。
     *
     * 随机模式是「真打乱队列」，队列一旦被打乱就回不去了，
     * 所以进随机时先存一份原始顺序，切回顺序播放时恢复。
     */
    private var originalQueue: List<Song>? = null

    init {
        // 回填上次的定时设置（时长 + 是否播完再停）
        _state.update {
            it.copy(
                sleepTimer = SleepTimerState(
                    minutes = sleepStore.minutes,
                    finishCurrentSong = sleepStore.finishCurrentSong
                )
            )
        }
        restoreSession()
        connectSession()
        scope.launch {
            downloadManager.library.collect { lib ->
                val ids = lib.songs.map { it.song.id }.toSet()
                val newlyDownloaded = ids - knownDownloadedIds
                knownDownloadedIds = ids
                syncDownloadedFlag()
                // 刚下载完的歌如果在当前队列里，把它的 MediaItem 换成本地文件地址
                if (newlyDownloaded.any { id -> _state.value.queue.any { it.id == id } }) {
                    rebuildQueueInPlayer()
                }
            }
        }
    }

    // ------------------------------------------------------------ 会话恢复 / 持久化

    /** 从本地恢复上次的队列与播放模式（不自动播放，等用户手动点）。 */
    private fun restoreSession() {
        val saved = sessionStore.load() ?: return
        pendingRestorePosition = saved.positionMs
        val mode = runCatching { PlayMode.valueOf(saved.playMode) }.getOrDefault(PlayMode.SEQUENTIAL)
        _state.update {
            it.copy(
                queue = saved.queue,
                currentIndex = saved.currentIndex,
                playMode = mode,
                bitrate = saved.bitrate
            )
        }
        _progress.update { it.copy(positionMs = saved.positionMs) }
    }

    /**
     * 把队列与播放模式写回磁盘。
     *
     * 用一个 debounce 循环而不是「每次状态变化就写」—— 播放进度 500ms 就变一次，
     * 每次都写盘会产生大量无意义的 IO。
     */
    private fun startSessionLoop() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            while (true) {
                delay(3_000)
                persistSession()
            }
        }
    }

    private fun persistSession() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        sessionStore.save(
            PlaybackSession(
                queue = s.queue,
                currentIndex = s.currentIndex.coerceIn(0, s.queue.lastIndex),
                positionMs = controller?.currentPosition ?: _progress.value.positionMs,
                playMode = s.playMode.name,
                bitrate = s.bitrate
            )
        )
    }

    // ------------------------------------------------------------ 连接 MediaSession

    private fun connectSession() {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(playerListener)
            applyPlayMode()
            // 恢复上次队列：只灌进播放器不播放，等用户按播放键
            val s = _state.value
            if (s.queue.isNotEmpty()) {
                controller?.setMediaItems(
                    buildMediaItems(s.queue),
                    s.currentIndex.coerceIn(0, s.queue.lastIndex),
                    pendingRestorePosition
                )
                // 起始位置已经交给 setMediaItems 了，别再让进度循环重复 seek
                pendingRestorePosition = 0L
            }
            // 连接完成前用户就点了歌 —— 现在补上
            pendingPlay?.let { (songs, index) ->
                pendingPlay = null
                playAll(songs, index)
            }
            syncState()
            startProgressLoop()
            startSessionLoop()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId ?: return
            val index = _state.value.queue.indexOfFirst { it.id == id }
            if (index >= 0) {
                _state.update { it.copy(currentIndex = index) }
                _progress.update { it.copy(positionMs = 0L) }
                loadLyrics(_state.value.queue[index])
            }
            syncDownloadedFlag()
            syncState()

            // 定时到点且要求「播完当前歌曲再暂停」：此刻正好是那一首的结尾
            if (pauseAfterCurrentSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                pauseAfterCurrentSong = false
                controller?.pause()
                cancelSleepTimer()
            }
        }

        /**
         * 某首歌放不出来时只负责「告诉用户」，**不在这里跳歌**。
         *
         * 真正的跳过逻辑在 [PlaybackService] 的 continuityListener 里 ——
         * 因为应用退到后台后 Controller 可能已经随 Activity 被回收，
         * 而服务还在放；跳过必须由服务侧兜底才能保证后台不断播。
         * 如果两边都跳，一首坏歌会被跳掉两首，所以这里只更新提示文案。
         */
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val failedName = _state.value.currentSong?.name ?: "当前歌曲"
            _state.update { it.copy(error = "「$failedName」无法播放，已自动跳过") }
        }
    }

    // ------------------------------------------------------------ 队列构建

    /**
     * 把 [songs] 转成播放器的播放列表。
     *
     * 已下载的歌直接指向本地文件（离线可听）；其余指向 Meting 的 `type=url` 接口地址，
     * 由 ExoPlayer 自己跟随 302 拿真实音频。
     */
    private fun buildMediaItems(songs: List<Song>): List<MediaItem> {
        val br = _state.value.bitrate
        return songs.map { song ->
            val localPath = downloadManager.localPathOf(song.id)
            val uri = if (localPath != null) {
                Uri.fromFile(File(localPath))
            } else {
                Uri.parse(MetingApi.streamUrl(song.url, br))
            }
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.name)
                        .setArtist(song.artist)
                        .setArtworkUri(song.pic.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                )
                .build()
        }
    }

    /** 播放模式 -> Media3 原生 repeatMode / shuffleMode。 */
    private fun applyPlayMode() {
        val c = controller ?: return
        when (_state.value.playMode) {
            PlayMode.SEQUENTIAL -> {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
                restoreOriginalOrderIfNeeded()
            }
            PlayMode.LIST_LOOP -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = false
                restoreOriginalOrderIfNeeded()
            }
            PlayMode.SINGLE_LOOP -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
                c.shuffleModeEnabled = false
                restoreOriginalOrderIfNeeded()
            }
            PlayMode.SHUFFLE -> {
                // 不用播放器的 shuffleModeEnabled：那只是「播完这首随机挑下一首」，
                // 队列本身没变，用户看到的列表顺序和实际播放顺序对不上。
                // 这里直接把队列打乱，之后就是普通的顺序播放。
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = false
                enterShuffleOrderIfNeeded()
            }
        }
    }

    /**
     * 把队列打乱一次，当前播放的歌留在最前面。
     *
     * 只有播放器里已经有内容时才动手 —— 会话刚连上时队列还没灌进去，
     * 这时打乱会和 [connectSession] 里的 setMediaItems 打架。
     */
    private fun enterShuffleOrderIfNeeded() {
        val c = controller ?: return
        if (originalQueue != null) return
        val s = _state.value
        if (c.mediaItemCount <= 1 || s.queue.size <= 1) return

        originalQueue = s.queue
        val current = s.queue.getOrNull(s.currentIndex) ?: return
        val rest = s.queue.filterIndexed { i, _ -> i != s.currentIndex }.shuffled()
        val ordered = listOf(current) + rest

        swapQueue(ordered, anchorIndex = 0)
    }

    /** 退出随机时恢复原始顺序，当前播放的歌位置跟着重算。 */
    private fun restoreOriginalOrderIfNeeded() {
        val original = originalQueue ?: return
        originalQueue = null
        val c = controller ?: return
        if (c.mediaItemCount <= 1) return

        val currentId = _state.value.currentSong?.id
        val newIndex = original.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        swapQueue(original, anchorIndex = newIndex)
    }

    /**
     * 换掉播放器的整条队列，保持「当前这首继续播、进度不跳」。
     *
     * setMediaItems 会重建播放列表，所以要显式记住播放位置和播放状态再补回去。
     */
    private fun swapQueue(ordered: List<Song>, anchorIndex: Int) {
        val c = controller ?: return
        val wasPlaying = c.isPlaying
        val pos = c.currentPosition.coerceAtLeast(0L)

        _state.update { it.copy(queue = ordered, currentIndex = anchorIndex) }
        c.setMediaItems(buildMediaItems(ordered), anchorIndex, pos)
        if (wasPlaying) c.play()
    }

    /** 把 [songs] 打乱，但让 [startIndex] 那首排在最前。 */
    private fun shuffledWithFirst(songs: List<Song>, startIndex: Int): List<Song> {
        if (songs.size <= 1) return songs
        val first = songs[startIndex]
        val rest = songs.filterIndexed { i, _ -> i != startIndex }.shuffled()
        return listOf(first) + rest
    }

    /** 保持当前曲目与进度，重建播放列表（音质变更、某首歌刚下载完时用）。 */
    private fun rebuildQueueInPlayer() {
        val c = controller ?: return
        val s = _state.value
        if (s.queue.isEmpty()) return
        val index = s.currentIndex.coerceIn(0, s.queue.lastIndex)
        val position = c.currentPosition.coerceAtLeast(0L)
        val wasPlaying = c.isPlaying
        c.setMediaItems(buildMediaItems(s.queue), index, position)
        c.prepare()
        if (wasPlaying) c.play()
        applyPlayMode()
    }

    // ------------------------------------------------------------ 对外操作方法

    /** 用一批歌曲替换整个播放队列，并从 [startIndex] 开始播放。 */
    fun playAll(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null) {
            // 会话还没连上，先记下来，连上后立刻执行
            pendingPlay = songs to startIndex
            _state.update { it.copy(queue = songs, currentIndex = startIndex, error = null) }
            return
        }
        val start = startIndex.coerceIn(0, songs.lastIndex)
        pendingRestorePosition = 0L

        // 随机模式下「播放全部」= 打乱歌单后从头顺序播；点某首歌则那首排第一
        val ordered: List<Song>
        val index: Int
        if (_state.value.playMode == PlayMode.SHUFFLE) {
            originalQueue = songs
            ordered = shuffledWithFirst(songs, start)
            index = 0
        } else {
            originalQueue = null
            ordered = songs
            index = start
        }

        _state.update { it.copy(queue = ordered, currentIndex = index, error = null) }
        _progress.update { it.copy(positionMs = 0L) }

        c.setMediaItems(buildMediaItems(ordered), index, 0L)
        c.prepare()
        c.play()
        applyPlayMode()
        loadLyrics(ordered[index])
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        val song = _state.value.queue.getOrNull(index) ?: return

        // 随机模式下点列表里的某首：把它挪到最前，后面的顺序保持不变
        if (_state.value.playMode == PlayMode.SHUFFLE && index != 0) {
            val s = _state.value
            val reordered = listOf(song) + s.queue.filterIndexed { i, _ -> i != index }
            originalQueue?.let { orig ->
                // 原始顺序里也把它提到最前，退出随机时不会跳回去
                val origIndex = orig.indexOfFirst { it.id == song.id }
                if (origIndex > 0) {
                    originalQueue = listOf(song) + orig.filterIndexed { i, _ -> i != origIndex }
                }
            }
            swapQueue(reordered, anchorIndex = 0)
            _progress.update { it.copy(positionMs = 0L) }
            loadLyrics(song)
            return
        }

        _state.update { it.copy(currentIndex = index, error = null) }
        _progress.update { it.copy(positionMs = 0L) }
        // 播放列表和队列是一一对应的，直接按索引 seek 即可
        c.seekTo(index, 0L)
        c.prepare()
        c.volume = 0f
        c.play()
        fadeTo(1f, fadeInMs)
        loadLyrics(song)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        when {
            // 淡出后再真正暂停，避免"啪"一下静音
            c.isPlaying -> {
                fadeTo(0f, fadeOutMs) {
                    c.pause()
                    // 恢复音量，否则下次播放会从静音开始
                    c.volume = 1f
                }
                syncState()
                return
            }
            // 还没准备好任何媒体 —— 重新灌一遍当前队列
            c.playbackState == Player.STATE_IDLE -> {
                val s = _state.value
                if (s.queue.isNotEmpty()) {
                    c.setMediaItems(
                        buildMediaItems(s.queue),
                        s.currentIndex.coerceIn(0, s.queue.lastIndex),
                        _progress.value.positionMs
                    )
                }
                c.prepare()
                c.play()
            }
            // 上一首已经放完了：从 0 重新开始，而不是「继续播结尾的静音」
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0)
                c.play()
            }
            else -> {
                c.volume = 0f
                c.play()
                fadeTo(1f, fadeInMs)
            }
        }
        syncState()
    }

    /**
     * 播放下一首。
     *
     * 委托给播放器自身的 [Player.seekToNextMediaItem] —— 随机模式下它会按 shuffle 顺序走，
     * 列表循环到队尾会自动绕回第一首。这同时也保证了通知栏按钮走的是同一条逻辑。
     */
    fun next() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        when {
            // 单曲循环下点「下一首」= 重播当前曲（保持与旧版一致）
            _state.value.playMode == PlayMode.SINGLE_LOOP -> replayCurrent()
            c.hasNextMediaItem() -> c.seekToNextMediaItem()
            else -> replayCurrent()
        }
    }

    /**
     * 播放上一首。
     *
     * 「已播过 3 秒先回到本曲开头」这条规则保留 —— 这是主流播放器的通用手感。
     */
    fun previous() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        when {
            c.currentPosition > 3_000 -> c.seekTo(0)
            c.hasPreviousMediaItem() -> c.seekToPreviousMediaItem()
            else -> c.seekTo(0)
        }
    }

    /** 回到当前曲开头重播。 */
    private fun replayCurrent() {
        val c = controller ?: return
        c.seekTo(0)
        c.play()
    }

    /** 点击播放队列里的某一项。 */
    fun playQueueItem(index: Int) = playAt(index)

    /** 切换播放模式，返回新模式 —— 调用方用它来弹提示。 */
    fun cyclePlayMode(): PlayMode {
        val next = _state.value.playMode.next
        _state.update { it.copy(playMode = next) }
        applyPlayMode()
        return next
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _progress.update { it.copy(positionMs = ms) }
    }

    fun setBitrate(br: Int) {
        if (_state.value.bitrate == br) return
        _state.update { it.copy(bitrate = br) }
        // 音质是拼在 URL 里的，必须重建播放列表才能生效（保持当前曲目与进度）
        rebuildQueueInPlayer()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------ 歌词

    /**
     * 拉取歌词。
     *
     * 歌词不在音频里，必须单独请求；切到哪首拉哪首，失败就留空不影响播放。
     * 已下载的歌同样要拉（音频是离线的，歌词不是）。
     */
    private fun loadLyrics(song: Song) {
        if (lyricsLoadingId == song.id) return
        lyricsLoadingId = song.id
        scope.launch {
            val lyrics = repository.lyricsOnly(song)
            if (_state.value.currentSong?.id == song.id) {
                _state.update { it.copy(lyrics = lyrics) }
            }
            if (lyricsLoadingId == song.id) lyricsLoadingId = null
        }
    }

    // ------------------------------------------------------------ 定时播放

    /**
     * 开启定时播放。
     *
     * @param minutes 倒计时时长（分钟）
     * @param finishCurrentSong 到点后是否等当前歌曲播完再暂停
     */
    fun startSleepTimer(minutes: Int, finishCurrentSong: Boolean) {
        val safeMinutes = minutes.coerceIn(1, 600)
        sleepStore.minutes = safeMinutes
        sleepStore.finishCurrentSong = finishCurrentSong
        pauseAfterCurrentSong = false

        sleepJob?.cancel()
        _state.update {
            it.copy(
                sleepTimer = SleepTimerState(
                    enabled = true,
                    remainingMs = safeMinutes * 60_000L,
                    minutes = safeMinutes,
                    finishCurrentSong = finishCurrentSong
                )
            )
        }

        sleepJob = scope.launch {
            var remaining = safeMinutes * 60_000L
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _state.update { it.copy(sleepTimer = it.sleepTimer.copy(remainingMs = remaining)) }
            }
            // 时间到
            val finish = _state.value.sleepTimer.finishCurrentSong
            if (finish && controller?.isPlaying == true) {
                // 标记一下，等这首放完再暂停（在 onMediaItemTransition 里处理）
                pauseAfterCurrentSong = true
            } else {
                controller?.pause()
                cancelSleepTimer()
            }
            syncState()
        }
    }

    /** 关闭定时播放。 */
    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        pauseAfterCurrentSong = false
        _state.update {
            it.copy(sleepTimer = it.sleepTimer.copy(enabled = false, remainingMs = 0L))
        }
    }

    /** 只更新偏好（用于对话框里改设置但还没开始计时）。 */
    fun updateSleepTimerPrefs(minutes: Int, finishCurrentSong: Boolean) {
        val safe = minutes.coerceIn(1, 600)
        sleepStore.minutes = safe
        sleepStore.finishCurrentSong = finishCurrentSong
        _state.update {
            it.copy(
                sleepTimer = it.sleepTimer.copy(
                    minutes = safe,
                    finishCurrentSong = finishCurrentSong
                )
            )
        }
    }

    // ------------------------------------------------------------ 下载

    /** 下载当前播放的歌曲。 */
    /** 已下载歌曲的本地文件绝对路径；没下载过返回 null。供「分享音频文件」用。 */
    fun localAudioPath(songId: String): String? = downloadManager.localPathOf(songId)

    fun downloadCurrent() {
        val song = _state.value.currentSong ?: return
        download(song)
    }

    /**
     * 下载指定歌曲。
     *
     * 下载需要「真实直链」，而播放列表里存的是 302 端点，所以这里单独解析一次。
     * 失败时写进 [PlayerUiState.error] 由 UI 提示。
     *
     * [originPlaylistId] / [originPlaylistName] 标记来源歌单 ——
     * 从歌单页里点的下载要归到那个歌单分组，而不是「单曲」。
     */
    fun download(
        song: Song,
        originPlaylistId: String? = null,
        originPlaylistName: String? = null
    ) {
        if (downloadManager.isDownloaded(song.id)) {
            _state.update { it.copy(error = "「${song.name}」已经下载过了") }
            return
        }
        scope.launch {
            repository.resolve(song, _state.value.bitrate)
                .onSuccess { track ->
                    downloadManager.download(
                        song = song,
                        streamUrl = track.streamUrl,
                        bitrate = _state.value.bitrate,
                        originPlaylistId = originPlaylistId,
                        originPlaylistName = originPlaylistName
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "无法下载：解析失败") }
                }
        }
    }

    /**
     * 批量下载整个歌单。
     *
     * 串行逐首解析 + 下载：并发下载会同时打开大量连接，容易被 CDN 限速或封禁，
     * 而且下载器本身也会对同一首歌去重，串行反而更稳。
     *
     * 进度写进 [batchDownload]，下载完成的歌会陆续出现在「我的下载」列表里。
     * 中途可以用 [cancelDownloadAll] 停掉。
     */
    fun downloadAll(
        songs: List<Song>,
        label: String = "",
        playlistId: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        if (songs.isEmpty()) return
        downloadAllJob?.cancel()
        _batchDownload.value = BatchDownloadState(running = true, label = label)

        downloadAllJob = scope.launch {
            val pending = songs.filterNot { downloadManager.isDownloaded(it.id) }
            val total = pending.size
            if (total == 0) {
                _batchDownload.value = BatchDownloadState()
                _state.update { it.copy(error = "这些歌都已经下载过了") }
                return@launch
            }
            _batchDownload.update { it.copy(done = 0, total = total) }

            pending.forEachIndexed { i, song ->
                // 用户中途点了取消 —— 立刻停手，不再解析后续的歌
                if (!isActive) return@launch
                repository.resolve(song, _state.value.bitrate).onSuccess { track ->
                    downloadManager.download(
                        song = song,
                        streamUrl = track.streamUrl,
                        bitrate = _state.value.bitrate,
                        // 记下来源歌单，下载页才能按歌单分组
                        originPlaylistId = playlistId,
                        originPlaylistName = label.ifBlank { null }
                    )
                    // 等这一首真正落盘，把并发收敛成串行。
                    // 加超时是因为：万一某个下载卡住不动，不能让整批永远停在这一首上。
                    withTimeoutOrNull(DOWNLOAD_ITEM_TIMEOUT_MS) {
                        downloadManager.awaitFinished(song.id)
                    }
                }
                _batchDownload.update { it.copy(done = i + 1) }
                onProgress(i + 1, total)
            }
            _batchDownload.value = BatchDownloadState()
            downloadAllJob = null
        }
    }

    /**
     * 取消批量下载。
     *
     * 两件事都要做：取消外层的调度协程（不再解析后续歌曲），
     * 以及取消下载器里正在传输的那一首（真正断开网络连接）。
     */
    fun cancelDownloadAll() {
        downloadAllJob?.cancel()
        downloadAllJob = null
        downloadManager.cancelAll()
        _batchDownload.value = BatchDownloadState()
    }

    /** 取消单首下载。 */
    fun cancelDownload(songId: String) = downloadManager.cancel(songId)

    /** 清掉失败/取消的任务标记。 */
    fun clearFinishedDownloads() = downloadManager.clearFinishedTasks()

    val downloads get() = downloadManager.library

    fun isDownloaded(songId: String): Boolean = downloadManager.isDownloaded(songId)

    fun deleteDownload(songId: String) = downloadManager.delete(songId)

    /**
     * 批量删除下载。
     *
     * 只删「已经下完」的那些 —— 正在下载的交给 [cancelDownload]，
     * 否则会出现「文件删了但下载协程还在往里写」的竞态。
     */
    fun deleteDownloads(songIds: Collection<String>) {
        songIds.forEach { id ->
            val task = downloadManager.library.value.taskOf(id)
            if (task is DownloadState.Downloading) {
                downloadManager.cancel(id)
            } else {
                downloadManager.delete(id)
            }
        }
    }

    private fun syncDownloadedFlag() {
        val song = _state.value.currentSong
        val downloaded = song != null && downloadManager.isDownloaded(song.id)
        if (_state.value.isCurrentDownloaded != downloaded) {
            _state.update { it.copy(isCurrentDownloaded = downloaded) }
        }
    }

    // ------------------------------------------------------------ 内部实现

    private fun syncState() {
        val c = controller ?: return
        _state.update {
            it.copy(
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING
            )
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                controller?.let { c ->
                    _progress.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun release() {
        // 退场前把当前进度落盘，下次冷启动能续上
        persistSession()
        progressJob?.cancel()
        sessionJob?.cancel()
        sleepJob?.cancel()
        downloadAllJob?.cancel()
        _batchDownload.value = BatchDownloadState()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }
}

/** 供 UI 层构建播放器实例用的工厂，保证 Context 只被持有在 Application 层。 */
object PlayerHolder {
    @Volatile
    private var instance: PlayerController? = null

    fun get(context: Context): PlayerController =
        instance ?: synchronized(this) {
            instance ?: PlayerController(context.applicationContext).also { instance = it }
        }
}
