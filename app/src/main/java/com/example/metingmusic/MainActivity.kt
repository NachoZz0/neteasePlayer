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
import com.example.metingmusic.ui.playlist.AddToPlaylistDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import com.example.metingmusic.data.local.Appearance
import com.example.metingmusic.data.local.AppearanceStore
import com.example.metingmusic.ui.settings.SettingsScreen
import com.example.metingmusic.ui.theme.BackgroundImage
import com.example.metingmusic.ui.theme.extractAccentColor
import com.example.metingmusic.ui.theme.FrostedBackdrop
import com.example.metingmusic.ui.theme.LocalFrosting
import com.example.metingmusic.ui.theme.supportsRealBlur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import com.example.metingmusic.ui.theme.clickableNoRipple
import androidx.compose.runtime.rememberCoroutineScope
import com.example.metingmusic.data.remote.ReleaseInfo
import com.example.metingmusic.data.remote.UpdateApi
import com.example.metingmusic.data.remote.findUpdate
import com.example.metingmusic.ui.settings.UpdateDialog
import com.example.metingmusic.ui.settings.UpdateUiState
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import com.example.metingmusic.data.remote.ApkInstaller
import com.example.metingmusic.data.remote.MetingApi
import com.example.metingmusic.ui.settings.ApkDownloadState
import java.io.File

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
            val appContext = applicationContext
            val appearanceStore = remember { AppearanceStore.get(appContext) }
            var appearance by remember { mutableStateOf(appearanceStore.load()) }

            // 背景图变了就重算一次取色（没有背景图则为 null）
            val extractedAccent = remember(appearance.backgroundPath) {
                BackgroundImage.load(appearance.backgroundPath)?.let { extractAccentColor(it) }
            }

            // 开了「从背景图取色」就自动跟随；关掉后保留当前颜色，用户可以自己改
            LaunchedEffect(appearance.accentFromBackground, extractedAccent) {
                if (appearance.accentFromBackground &&
                    extractedAccent != null &&
                    appearance.accentColor != extractedAccent
                ) {
                    appearance = appearance.copy(accentColor = extractedAccent)
                    appearanceStore.save(appearance)
                }
            }

            val pickBackground = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    val path = BackgroundImage.importFrom(appContext, uri)
                    if (path != null) {
                        appearance = appearance.copy(backgroundPath = path)
                        appearanceStore.save(appearance)
                    }
                }
            }

            val updateAppearance: (Appearance) -> Unit = { new ->
                appearance = new
                appearanceStore.save(new)
            }

            MetingMusicTheme(appearance = appearance) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicApp(
                        appearance = appearance,
                        onAppearanceChange = updateAppearance,
                        extractedAccent = extractedAccent,
                        onPickBackground = {
                            pickBackground.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
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
    Download("下载"),
    Settings("设置")
}

@Composable
private fun MusicApp(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    extractedAccent: Int?,
    onPickBackground: () -> Unit
) {
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

    /** 正在为哪首歌挑选歌单（null = 没在选择）。 */
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // ---- 更新检查
    val updateApi = remember { UpdateApi() }
    val updateScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var newRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var apkState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    val httpClient = remember { MetingApi.defaultClient }

    /** 下载 Release 里的 APK。 */
    fun downloadApk(release: ReleaseInfo) {
        val asset = release.apkAsset ?: return
        apkState = ApkDownloadState.Downloading(-1f)
        updateScope.launch {
            ApkInstaller.download(
                context = context,
                asset = asset,
                client = httpClient,
                onProgress = { p -> apkState = ApkDownloadState.Downloading(p) }
            ).onSuccess { file ->
                downloadedApk = file
                apkState = ApkDownloadState.Ready
            }.onFailure { e ->
                apkState = ApkDownloadState.Failed(e.message ?: "下载失败")
            }
        }
    }

    /** 唤起系统安装器。 */
    fun installApk() {
        val file = downloadedApk ?: return
        when (val result = ApkInstaller.install(context, file)) {
            ApkInstaller.InstallResult.Started -> {
                // 安装器已拉起，收起弹窗；装完用户自己会回来
                newRelease = null
                apkState = ApkDownloadState.Idle
            }
            // 已跳到授权页，保持 Ready，用户授权回来点「立即安装」即可，不用重下
            ApkInstaller.InstallResult.NeedPermission -> {
                apkState = ApkDownloadState.NeedPermission
            }
            is ApkInstaller.InstallResult.Failed -> {
                apkState = ApkDownloadState.Failed(result.message)
            }
        }
    }
    val currentVersion = remember { UpdateApi.currentVersion(context) }

    fun checkUpdate(silent: Boolean) {
        updateState = UpdateUiState.Checking
        updateScope.launch {
            updateApi.fetchReleases()
                .onSuccess { releases ->
                    val newer = findUpdate(releases, currentVersion)
                    if (newer != null) {
                        newRelease = newer
                        updateState = UpdateUiState.Idle
                    } else {
                        updateState = UpdateUiState.UpToDate
                    }
                }
                .onFailure { e ->
                    // 自动检查失败时保持安静，只有用户手动点才提示
                    updateState = if (silent) UpdateUiState.Idle
                    else UpdateUiState.Failed(e.message ?: "检查更新失败")
                }
        }
    }

    // 启动后自动查一次（只在同意免责声明后），失败不打扰
    LaunchedEffect(agreed) {
        if (agreed && updateState == UpdateUiState.Idle) checkUpdate(silent = true)
    }

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
    // 悬浮条不占布局高度，所以列表底部要自己留出空间，否则最后几首会被挡住
    val bottomInset = if (hasMiniBar) 212.dp else 132.dp

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

        // 内容铺满整屏 —— 底部悬浮条必须叠在它上面，才真的「浮」起来。
        // 之前把底部条放在 Column 里当兄弟节点，内容到它上方就截止了，
        // 后面根本没有东西可透，看起来自然像贴了一块实心色块。
        Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (
                            slideInHorizontally(
                                animationSpec = tween(260)
                            ) { width -> direction * width / 4 } + fadeIn(tween(220))
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(260)
                            ) { width -> -direction * width / 4 } + fadeOut(tween(180))
                            )
                    },
                    label = "tab"
                ) { tab ->
                when (tab) {
                    0 -> SearchScreen(
                        onPlaySong = { list: List<Song>, index: Int ->
                            controller.playAll(list, index)
                        },
                        onOpenPlayer = { showPlayer = true },
                        onDownload = { controller.download(it) },
                        onAddToPlaylist = { songToAdd = it },
                        isDownloaded = { controller.isDownloaded(it) },
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )

                    1 -> PlaylistImportScreen(
                        state = importState,
                        savedPlaylists = savedPlaylists,
                        onInputChange = searchViewModel::onPlaylistInputChange,
                        onNameChange = searchViewModel::onPlaylistNameChange,
                        onImport = searchViewModel::importPlaylist,
                        onCreatePlaylist = { name ->
                            toast(searchViewModel.createPlaylist(name))
                        },
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
                        downloads = downloads,
                        onDownloadSong = { song ->
                            // 从歌单页点的下载要标记来源，下载页才会归到这张歌单
                            controller.download(
                                song = song,
                                originPlaylistId = importState.playlistId,
                                originPlaylistName = importState.name
                            )
                        },
                        onCancelDownload = controller::cancelDownload,
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )

                    2 -> DownloadScreen(
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

                    3 -> SettingsScreen(
                        appearance = appearance,
                        extractedAccent = extractedAccent,
                        onThemeModeChange = { mode ->
                            onAppearanceChange(appearance.copy(themeMode = mode))
                        },
                        onPickBackground = onPickBackground,
                        onClearBackground = {
                            BackgroundImage.clear(context)
                            onAppearanceChange(
                                appearance.copy(
                                    backgroundPath = null,
                                    accentFromBackground = false
                                )
                            )
                        },
                        onAccentChange = { argb ->
                            onAppearanceChange(
                                appearance.copy(
                                    accentColor = argb,
                                    accentFromBackground = false
                                )
                            )
                        },
                        onAccentFromBackgroundChange = { enabled ->
                            onAppearanceChange(
                                appearance.copy(accentFromBackground = enabled)
                            )
                        },
                        onGlobalBlurChange = { enabled ->
                            onAppearanceChange(appearance.copy(globalBlur = enabled))
                        },
                        onBottomFrostedChange = { enabled ->
                            onAppearanceChange(appearance.copy(bottomFrosted = enabled))
                        },
                        onFloatingNavBarChange = { floating ->
                            onAppearanceChange(appearance.copy(floatingNavBar = floating))
                        },
                        updateState = updateState,
                        onCheckUpdate = { checkUpdate(silent = false) },
                        contentBottomPadding = PaddingValues(bottom = bottomInset)
                    )
                }
                }
            }

        // ---- 底部悬浮区：迷你播放器 + 胶囊导航栏
        // align(BottomCenter) 让它们叠在内容之上，列表才能从后面穿过去
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
                // 只有「贴底」样式才垫渐变：那种样式下栏是不透明的，需要一层过渡避免硬切。
                // 悬浮样式**不能**垫 —— 垫了就变成一整块底色，胶囊看起来像贴在色块上，
                // 后面的列表也看不见了。悬浮的意义就是内容从胶囊底下穿过去。
                if (!appearance.floatingNavBar) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            horizontal = if (appearance.floatingNavBar) 16.dp else 0.dp,
                            vertical = if (appearance.floatingNavBar) 8.dp else 0.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (appearance.floatingNavBar) 8.dp else 0.dp
                    )
                ) {
                    AnimatedVisibility(
                        visible = hasMiniBar,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        MiniPlayerBar(
                            floating = appearance.floatingNavBar,
                            state = state,
                            progressFlow = controller.progress,
                            onExpand = { showPlayer = true },
                            onTogglePlay = controller::togglePlayPause,
                            onNext = controller::next,
                            onOpenQueue = { showQueue = true }
                        )
                    }

                    TabBar(
                        floating = appearance.floatingNavBar,
                        selectedIndex = selectedTab,
                        onSelect = { index -> selectedTab = index }
                    )
                }
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

    // ---- 发现新版本
    newRelease?.let { release ->
        UpdateDialog(
            release = release,
            currentVersion = currentVersion,
            downloadState = apkState,
            onDownload = { downloadApk(release) },
            onInstall = { installApk() },
            onOpenRelease = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                    )
                }
                newRelease = null
                apkState = ApkDownloadState.Idle
            },
            onDismiss = {
                newRelease = null
                apkState = ApkDownloadState.Idle
            }
        )
    }

    // ---- 加入歌单选择器
    songToAdd?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = savedPlaylists,
            onPick = { playlist ->
                toast(searchViewModel.addSongToPlaylist(playlist.playlistId, song))
                songToAdd = null
            },
            onDismiss = { songToAdd = null }
        )
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
/**
 * 悬浮胶囊导航栏。
 *
 * 不再是一条贴边的横栏，而是一颗浮在内容之上的圆角胶囊 ——
 * 好处是列表可以一直滚到屏幕最底，视觉上更轻。
 *
 * 磨砂开启且系统支持（Android 12+）时用 [FrostedBackdrop] 做真模糊；
 * 不透明度取 0.8，模糊只作为底色的「质感」，不牺牲文字清晰度。
 */
@Composable
private fun TabBar(
    floating: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val frosting = LocalFrosting.current
    val frosted = frosting.navBar && supportsRealBlur
    val tint = bottomBarTint(frosted = frosted, floating = floating)

    val shape = if (floating) RoundedCornerShape(26.dp) else RectangleShape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (floating) Modifier.shadow(elevation = 12.dp, shape = shape) else Modifier)
            .clip(shape)
    ) {
        // 只有「贴底」才画模糊背景层。悬浮态必须留空 —— FrostedBackdrop 会自己
        // 铺一层「模糊后的背景」，把后面的列表整个盖住，那就不是悬浮而是贴在色块上了。
        if (frosted && !floating) {
            FrostedBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = 30.dp,
                tint = tint
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(tint)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (floating) 6.dp else 4.dp,
                    vertical = if (floating) 6.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            else Color.Transparent
                        )
                        .clickableNoRipple { onSelect(index) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (tab) {
                            MainTab.Search -> Icons.Default.Search
                            MainTab.Playlist -> Icons.AutoMirrored.Filled.PlaylistAdd
                            MainTab.Download -> Icons.Default.Download
                            MainTab.Settings -> Icons.Default.Settings
                        },
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 底部栏的底色。
 *
 * 三种情况：
 * - 没开磨砂 → 完全不透明（和磨砂态拉开差别，否则开关看着没反应）
 * - 悬浮 → 45% 不透明。悬浮的意义就是「内容从底下穿过去」，
 *   80% 的观感是一条实心色带，透不出东西。
 * - 贴底 → 80% 不透明，整条栏更实，和内容的边界更清楚。
 *
 * 注：Compose 没法真正模糊「背后的列表内容」（Modifier.blur 只糊自己，
 * 而 FrostedBackdrop 糊的是背景图），所以悬浮态靠低不透明度让列表透出来。
 */
@Composable
private fun bottomBarTint(frosted: Boolean, floating: Boolean) =
    when {
        !frosted -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        floating -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)
    }

@Composable
private fun MiniPlayerBar(
    floating: Boolean,
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

    val frosting = LocalFrosting.current
    // 迷你播放器和导航栏是一组底部控件，共用同一个磨砂开关，保证观感一致
    val frosted = frosting.navBar && supportsRealBlur
    val tint = bottomBarTint(frosted = frosted, floating = floating)
    val shape = if (floating) RoundedCornerShape(20.dp) else RectangleShape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (floating) Modifier.shadow(elevation = 12.dp, shape = shape) else Modifier)
            .clip(shape)
    ) {
        // 同 TabBar：悬浮态不铺背景层，让列表从卡片底下透出来
        if (frosted && !floating) {
            FrostedBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = 30.dp,
                tint = tint
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(tint)
            )
        }

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
