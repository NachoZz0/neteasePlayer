package com.example.metingmusic

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.metingmusic.data.local.DisclaimerStore
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.DownloadState
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.data.remote.NoticeApi
import com.example.metingmusic.player.PlaybackProgress
import com.example.metingmusic.player.PlayerHolder
import com.example.metingmusic.player.PlayerUiState
import com.example.metingmusic.ui.common.DisclaimerDialog
import com.example.metingmusic.ui.common.NoticeDialog
import com.example.metingmusic.ui.common.ToastMessage
import com.example.metingmusic.ui.common.ToastOverlay
import com.example.metingmusic.ui.common.icon
import com.example.metingmusic.ui.download.DownloadScreen
import com.example.metingmusic.ui.player.PlayerMenuSheet
import com.example.metingmusic.ui.player.PlayerScreen
import com.example.metingmusic.ui.player.QueueBottomSheet
import com.example.metingmusic.ui.player.SleepTimerDialog
import com.example.metingmusic.ui.playlist.PlaylistImportScreen
import com.example.metingmusic.ui.playlist.SharePlaylistDialog
import com.example.metingmusic.ui.search.SearchScreen
import com.example.metingmusic.ui.search.SearchViewModel
import com.example.metingmusic.ui.theme.AppBackground
import com.example.metingmusic.ui.theme.MetingMusicTheme
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* 用户拒绝也没关系，播放功能不受影响，只是没有通知栏控制卡片 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            MetingMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicApp()
                }
            }
        }
    }

    /** Android 13+ 展示媒体通知需要用户授权。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** 底部导航的三个主页面。 */
private enum class MainTab(val label: String) {
    Search("搜索"),
    Playlist("歌单"),
    Download("下载")
}

@Composable
private fun MusicApp() {
    val context = LocalContext.current
    val controller = remember { PlayerHolder.get(context) }
    val searchViewModel: SearchViewModel = viewModel()
    val disclaimerStore = remember { DisclaimerStore.get(context) }

    // 免责声明：同意过就不再弹
    var agreed by remember { mutableStateOf(disclaimerStore.hasAgreed) }

    // 启动公告：每次启动拉一次；拉不到（断网 / 内容为空）就静默不弹
    val noticeApi = remember { NoticeApi() }
    var noticeText by remember { mutableStateOf<String?>(null) }
    var noticeDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        noticeText = noticeApi.fetch()
    }

    val state by controller.state.collectAsStateWithLifecycle()
    val importState by searchViewModel.playlistState.collectAsStateWithLifecycle()
    val savedPlaylists by searchViewModel.savedPlaylists.collectAsStateWithLifecycle()
    val downloads by controller.downloads.collectAsStateWithLifecycle()
    val batchDownload by controller.batchDownload.collectAsStateWithLifecycle()
    val externalImportState by searchViewModel.externalState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    /** 正在分享的歌单；非 null 时弹出分享对话框。 */
    var sharingPlaylist by remember { mutableStateOf<SavedPlaylist?>(null) }

    val clipboard = LocalClipboardManager.current

    // 播放模式提示。
    // 原先用 SnackbarHostState.showSnackbar()，但它是挂起函数、要等上一条播完，
    // 连点几次按钮时提示会排队缓慢冒出。改成自增 id 的自替换浮层，
    // 每次切换都立刻覆盖上一条，点多少下都是即时反馈。
    var modeToast by remember { mutableStateOf<ToastMessage?>(null) }
    var toastSeq by remember { mutableLongStateOf(0L) }

    fun toast(text: String, icon: ImageVector? = null) {
        modeToast = ToastMessage(id = ++toastSeq, text = text, icon = icon)
    }

    val cyclePlayMode: () -> Unit = {
        val mode = controller.cyclePlayMode()
        toast(mode.label, mode.icon)
    }

    // 底部要额外让出的高度：迷你播放条 88 + 导航栏 80
    val hasMiniBar = state.currentSong != null
    val bottomInset = if (hasMiniBar) 172.dp else 84.dp

    BackHandler(
        enabled = showPlayer || showQueue || showMenu || importState.hasResult
    ) {
        when {
            showPlayer -> showPlayer = false
            showQueue -> showQueue = false
            showMenu -> showMenu = false
            importState.hasResult -> searchViewModel.clearImportedPlaylist()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- 全局背景：主题底色 + 30% 透明度的插画
        AppBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> SearchScreen(
                        onPlaySong = { list: List<Song>, index: Int ->
                            controller.playAll(list, index)
                        },
                        onOpenPlayer = { showPlayer = true },
                        onDownload = { controller.download(it) },
                        isDownloaded = { controller.isDownloaded(it) },
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )

                    1 -> PlaylistImportScreen(
                        state = importState,
                        savedPlaylists = savedPlaylists,
                        onInputChange = searchViewModel::onPlaylistInputChange,
                        onNameChange = searchViewModel::onPlaylistNameChange,
                        onImport = searchViewModel::importPlaylist,
                        onBack = { searchViewModel.clearImportedPlaylist() },
                        onPlaySong = { index ->
                            controller.playAll(importState.songs, index)
                            showPlayer = true
                        },
                        onOpenSaved = searchViewModel::openSavedPlaylist,
                        onDeleteSaved = searchViewModel::deleteSavedPlaylist,
                        onDeleteSavedMany = searchViewModel::deleteSavedPlaylists,
                        onRefresh = searchViewModel::refreshPlaylist,
                        onDownloadAll = {
                            controller.downloadAll(
                                songs = importState.songs,
                                label = importState.name,
                                playlistId = importState.playlistId
                            ) { done, total ->
                                if (done == total) toast("「${importState.name}」已下载 $total 首")
                            }
                        },
                        onShare = { playlistId ->
                            sharingPlaylist = savedPlaylists.firstOrNull { it.playlistId == playlistId }
                        },
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )

                    else -> DownloadScreen(
                        library = downloads,
                        batch = batchDownload,
                        importState = externalImportState,
                        savedPlaylists = savedPlaylists,
                        onPlaySong = { songs, index ->
                            controller.playAll(songs, index)
                            showPlayer = true
                        },
                        onPlayAll = { songs ->
                            controller.playAll(songs, 0)
                            showPlayer = true
                        },
                        onDelete = { songId -> controller.deleteDownload(songId) },
                        onDeleteMany = controller::deleteDownloads,
                        onImportInputChange = searchViewModel::onExternalInputChange,
                        onImportSubmit = {
                            searchViewModel.importExternal { songs, label, playlistId ->
                                controller.downloadAll(
                                    songs = songs,
                                    label = label,
                                    playlistId = playlistId
                                ) { done, total ->
                                    if (done == total) toast("「$label」已下载 $total 首")
                                }
                            }
                        },
                        onImportDismiss = searchViewModel::clearExternalInput,
                        onCancelDownload = controller::cancelDownload,
                        onCancelAll = controller::cancelDownloadAll,
                        onClearFinished = controller::clearFinishedDownloads,
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )
                }
            }

            // ---- 迷你播放条（在导航栏之上）
            AnimatedVisibility(
                visible = hasMiniBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                MiniPlayerBar(
                    state = state,
                    progressFlow = controller.progress,
                    onExpand = { showPlayer = true },
                    onTogglePlay = controller::togglePlayPause,
                    onNext = controller::next,
                    onOpenQueue = { showQueue = true }
                )
            }

            // ---- 底部导航栏
            BottomNavBar(
                selectedIndex = selectedTab,
                onSelect = { index -> selectedTab = index }
            )
        }

        // ---- 全屏歌词页
        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(
                state = state,
                progressFlow = controller.progress,
                onBack = { showPlayer = false },
                onTogglePlay = controller::togglePlayPause,
                onNext = controller::next,
                onPrevious = controller::previous,
                onSeek = controller::seekTo,
                onCyclePlayMode = cyclePlayMode,
                onOpenQueue = { showQueue = true },
                onOpenMenu = { showMenu = true }
            )
        }

        // ---- 播放模式提示：自替换浮层，连点也不会排队，播放页里同样可见。
        //      底部留白要盖过整块控制区（歌名+进度条+两行按钮），否则会压在进度条上。
        ToastOverlay(
            message = modeToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 232.dp)
        )

        // ---- 启动免责声明：必须同意才能使用
        if (!agreed) {
            DisclaimerDialog(
                onAgree = {
                    disclaimerStore.agree()
                    agreed = true
                },
                onDecline = { (context as? Activity)?.finish() }
            )
        }

        // ---- 启动公告：每次启动都拉一次远端文案
        // 只在同意免责声明之后弹，避免两个对话框叠在一起
        if (agreed && !noticeDismissed) {
            noticeText?.let { text ->
                NoticeDialog(
                    text = text,
                    onDismiss = { noticeDismissed = true }
                )
            }
        }
    }

    // ---- 底部队列面板（歌曲列表）
    if (showQueue) {
        QueueBottomSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onDismiss = { showQueue = false },
            onPlayIndex = { index -> controller.playQueueItem(index) },
            actions = { song ->
                val task = downloads.taskOf(song.id)
                if (task is DownloadState.Downloading) {
                    // 下载中：显示百分比，点一下可以取消
                    IconButton(onClick = { controller.cancelDownload(song.id) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "取消下载",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${task.progress}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    val downloaded = downloads.isDownloaded(song.id)
                    IconButton(
                        onClick = { if (!downloaded) controller.download(song) },
                        enabled = !downloaded
                    ) {
                        Icon(
                            imageVector = if (downloaded) Icons.Default.DownloadDone
                            else Icons.Default.Download,
                            contentDescription = if (downloaded) "已下载" else "下载",
                            tint = if (downloaded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )
    }

    // ---- 播放页二级菜单
    if (showMenu) {
        PlayerMenuSheet(
            state = state,
            onCyclePlayMode = cyclePlayMode,
            onSelectBitrate = controller::setBitrate,
            onDownload = controller::downloadCurrent,
            onOpenSleepTimer = {
                showMenu = false
                showSleepTimer = true
            },
            onOpenQueue = {
                showMenu = false
                showQueue = true
            },
            onDismiss = { showMenu = false }
        )
    }

    // ---- 定时播放设置
    if (showSleepTimer) {
        SleepTimerDialog(
            state = state.sleepTimer,
            onStart = { minutes, finishCurrent ->
                controller.startSleepTimer(minutes, finishCurrent)
            },
            onCancelTimer = controller::cancelSleepTimer,
            onDismiss = { showSleepTimer = false }
        )
    }

    // ---- 歌单分享
    sharingPlaylist?.let { playlist ->
        SharePlaylistDialog(
            playlist = playlist,
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                sharingPlaylist = null
                toast("分享文本已复制")
            },
            onDismiss = { sharingPlaylist = null }
        )
    }
}

/** 底部导航栏。 */
@Composable
private fun BottomNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            MainTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                MainTab.Search -> Icons.Default.Search
                                MainTab.Playlist -> Icons.AutoMirrored.Filled.PlaylistAdd
                                MainTab.Download -> Icons.Default.Download
                            },
                            contentDescription = tab.label
                        )
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val song = state.currentSong ?: return
    // 进度在这里单独订阅：只有这条迷你条会随进度重组，外层页面不受影响
    val progress by progressFlow.collectAsStateWithLifecycle()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // 细进度条，给出播放进度的直观反馈
            val fraction = if (progress.durationMs > 0) {
                (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                drawStopIndicator = {}
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧整块（封面+文字）点击展开全屏歌词
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.pic,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放"
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "播放队列"
                        )
                    }
                }
            }
        }
    }
}
