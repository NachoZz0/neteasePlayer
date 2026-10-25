package com.example.metingmusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.ui.search.ImportedPlaylistUiState
import com.example.metingmusic.ui.theme.clickableNoRipple
import com.example.metingmusic.ui.theme.longPressableNoRipple
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.ui.unit.sp
import com.example.metingmusic.data.model.DownloadLibrary
import com.example.metingmusic.data.model.DownloadState
import com.example.metingmusic.data.model.Song
import androidx.compose.material.icons.filled.Add
import com.example.metingmusic.ui.theme.glassCard
import com.example.metingmusic.ui.theme.glassButtonColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.activity.compose.BackHandler

/**
 * 网易云歌单导入页。
 *
 * 用户输入歌单号（或直接粘贴 App 里复制的分享链接），点导入即可把整个歌单拉下来。
 * 粘贴分享文案时会自动补全歌单名；导入成功后**自动保存到本地**，
 * 下方「我的导入」列出所有已保存的歌单，重启后依然在。
 */
@Composable
fun PlaylistImportScreen(
    state: ImportedPlaylistUiState,
    savedPlaylists: List<SavedPlaylist>,
    onInputChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onImport: () -> Unit,
    /** 创建本地空歌单（只填名字）。 */
    onCreatePlaylist: (String) -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onOpenSaved: (String) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onDeleteSavedMany: (List<String>) -> Unit,
    onRefresh: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onShare: (String) -> Unit,
    /** 从歌单里移除一首歌。 */
    onDeleteSong: (Song) -> Unit,
    /** 下载库，用来在每首歌右侧显示下载状态。 */
    downloads: DownloadLibrary,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    contentBottomPadding: PaddingValues = PaddingValues(0.dp)
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // 导入成功 / 打开历史 -> 显示歌单详情
    if (state.hasResult) {
        ImportedPlaylistDetail(
            state = state,
            savedPlaylists = savedPlaylists,
            onBack = onBack,
            onPlaySong = onPlaySong,
            onRefresh = onRefresh,
            onDownloadAll = onDownloadAll,
            onShare = onShare,
            onDeleteSong = onDeleteSong,
            downloads = downloads,
            onDownloadSong = onDownloadSong,
            onCancelDownload = onCancelDownload,
            contentBottomPadding = contentBottomPadding
        )
        return
    }

    // ---- 添加歌单的两种方式：本地创建 / 从网易云导入
    // null = 停在「选入口」这一层；选了具体方式才展开表单（二级菜单）
    var formMode by remember { mutableStateOf<AddMode?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    // 手势返回：表单展开时先退回「选入口」那一层
    BackHandler(enabled = !state.hasResult && formMode != null) {
        formMode = null
    }

    // ---- 「我的导入」多选模式
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmingBulkDelete by remember { mutableStateOf(false) }

    val validSelected = remember(selectedIds, savedPlaylists) {
        selectedIds intersect savedPlaylists.map { it.playlistId }.toSet()
    }
    val allSelected =
        savedPlaylists.isNotEmpty() && validSelected.size == savedPlaylists.size

    // 删空后自动退出多选，免得停在「已选 0 首」的空界面上
    LaunchedEffect(selectionMode, savedPlaylists.size) {
        if (selectionMode && savedPlaylists.isEmpty()) {
            selectionMode = false
            selectedIds = emptySet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "退出多选")
                }
                Text(
                    text = "已选 ${validSelected.size} 个歌单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selectedIds = if (allSelected) {
                        emptySet()
                    } else {
                        savedPlaylists.map { it.playlistId }.toSet()
                    }
                }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
                TextButton(
                    onClick = { confirmingBulkDelete = true },
                    enabled = validSelected.isNotEmpty()
                ) {
                    val tint = if (validSelected.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tint
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "删除", color = tint)
                }
            }
        } else {
            Text(
                text = "歌单",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
            )
            Text(
                text = when (formMode) {
                    null -> "建一个空歌单，或从网易云导入"
                    AddMode.CREATE -> "建一个空歌单，再从搜索页把喜欢的歌加进来"
                    AddMode.IMPORT -> "填网易云歌单号，或直接粘贴歌单分享链接"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )
            if (formMode == null) {
                // 二级菜单：先选「怎么加歌单」，再展开对应的表单
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AddEntry(
                        icon = Icons.Default.Add,
                        title = "创建本地歌单",
                        subtitle = "起个名字，之后从搜索页把歌加进来",
                        onClick = { formMode = AddMode.CREATE }
                    )
                    AddEntry(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        title = "从网易云导入",
                        subtitle = "填歌单号，或粘贴歌单分享链接",
                        onClick = { formMode = AddMode.IMPORT }
                    )
                }
            } else {
                TextButton(
                    onClick = { formMode = null },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("换一种方式")
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            contentPadding = contentBottomPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            // 多选模式下把输入区收起来，避免误触；
            // formMode 为 null 表示还停在「选入口」那一层，也不显示表单
            if (!selectionMode && formMode != null) {
                item {
                    Column {
                        if (formMode == AddMode.CREATE) {
                            // ---- 创建：只要一个名字
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                label = { Text("歌单名称") },
                                placeholder = { Text("例如 通勤路上") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newPlaylistName.isNotBlank()) {
                                        keyboard?.hide()
                                        onCreatePlaylist(newPlaylistName.trim())
                                        newPlaylistName = ""
                                    }
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                colors = glassButtonColors(),
                                onClick = {
                                    keyboard?.hide()
                                    onCreatePlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                },
                                enabled = newPlaylistName.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("创建歌单")
                            }
                        }

                        if (formMode == AddMode.IMPORT) {
                        // ---- 歌单号 / 链接
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInputChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            label = { Text("歌单号 / 分享链接") },
                            placeholder = { Text("例如 24381616") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (state.input.isNotEmpty()) {
                                    IconButton(onClick = { onInputChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清空")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Spacer(Modifier.height(10.dp))

                        // ---- 名称：粘贴分享链接后会自动补全，也可以自己改
                        OutlinedTextField(
                            value = state.nameInput,
                            onValueChange = onNameChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            label = { Text("歌单名称") },
                            placeholder = { Text("粘贴分享链接会自动填上") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboard?.hide()
                                onImport()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            colors = glassButtonColors(),
                            onClick = {
                                keyboard?.hide()
                                onImport()
                            },
                            enabled = state.input.isNotBlank() && !state.loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(48.dp)
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在导入…")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("导入并保存")
                            }
                        }

                        state.error?.let { err ->
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            )
                        }
                        } // formMode == IMPORT

                        Spacer(Modifier.height(22.dp))

                        // ---- 已保存歌单
                        if (savedPlaylists.isNotEmpty()) {
                            Text(
                                text = "我的歌单（${savedPlaylists.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "我的歌单（${savedPlaylists.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                    )
                }
            }

            itemsIndexed(
                items = savedPlaylists,
                key = { _, p -> p.playlistId },
                contentType = { _, _ -> "saved" }
            ) { _, playlist ->
                SavedPlaylistRow(
                    playlist = playlist,
                    refreshing = state.refreshing && state.playlistId == playlist.playlistId,
                    selectionMode = selectionMode,
                    selected = playlist.playlistId in validSelected,
                    onOpen = {
                        if (selectionMode) {
                            selectedIds = if (playlist.playlistId in validSelected) {
                                selectedIds - playlist.playlistId
                            } else {
                                selectedIds + playlist.playlistId
                            }
                        } else {
                            onOpenSaved(playlist.playlistId)
                        }
                    },
                    onLongPress = {
                        if (!selectionMode) {
                            selectionMode = true
                            selectedIds = setOf(playlist.playlistId)
                        }
                    },
                    onRefresh = { onRefresh(playlist.playlistId) },
                    onDelete = { onDeleteSaved(playlist.playlistId) }
                )
            }

            if (!selectionMode) {
                item {
                    Spacer(Modifier.height(20.dp))
                    // ---- 使用说明
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "怎么找歌单号？",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "在网易云音乐里打开歌单",
                            "点右上角「分享」→ 复制链接",
                            "粘贴到上面的输入框即可，歌单名会自动填上"
                        ).forEach { line ->
                            Text(
                                text = "· $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "歌单会自动保存；长按列表可批量删除。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (confirmingBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBulkDelete = false },
            title = { Text("删除歌单") },
            text = {
                Text(
                    "确定删除选中的 ${validSelected.size} 个歌单吗？" +
                        "只删除本地记录，不会影响网易云上的歌单。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingBulkDelete = false
                    onDeleteSavedMany(validSelected.toList())
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBulkDelete = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 歌曲行右侧的下载按钮。
 *
 * 三态：
 * - 未下载 → 下载图标，点一下开始下
 * - 下载中 → 环形进度 + 百分比，点一下取消（几百首的列表里，能单首取消比只能整单取消实用）
 * - 已下载 → 实心对勾，不可点
 */
@Composable
private fun SongDownloadButton(
    task: DownloadState,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    when {
        task is DownloadState.Downloading -> {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${task.progress}",
                    // 28dp 的环里要塞三位数，字号必须小；不显示 % 号省空间
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        downloaded -> {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "已下载",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        else -> {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 「我的导入」里的一行歌单。 */
@Composable
private fun SavedPlaylistRow(
    playlist: SavedPlaylist,
    refreshing: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .glassCard()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .longPressableNoRipple(onClick = onOpen, onLongClick = onLongPress)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) "已选中" else "未选中",
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
        // 封面：默认取歌单第一首歌的图；没有就退回一个占位图标
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val cover = playlist.displayCover
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (playlist.isLocalPlaylist) {
                    "${playlist.size} 首 · 本地歌单"
                } else {
                    "${playlist.size} 首 · 歌单号 ${playlist.playlistId}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 多选模式下收起单行操作按钮，避免误触
        if (!selectionMode) {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = { confirming = true }) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(start = if (selectionMode) 92.dp else 76.dp)
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("删除歌单") },
            text = { Text("确定删除「${playlist.name}」吗？只删除本地记录，不会影响网易云上的歌单。") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消") }
            }
        )
    }
}

/** 导入成功后的歌单详情。 */
@Composable
private fun ImportedPlaylistDetail(
    state: ImportedPlaylistUiState,
    savedPlaylists: List<SavedPlaylist>,
    onBack: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRefresh: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onShare: (String) -> Unit,
    onDeleteSong: (Song) -> Unit,
    downloads: DownloadLibrary,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    contentBottomPadding: PaddingValues
) {
    val id = state.playlistId
    val saved = remember(savedPlaylists, id) {
        savedPlaylists.firstOrNull { it.playlistId == id }
    }

    // 进入动画：从右侧滑一小段并淡入，让「列表 → 详情」有方向感
    val enterProgress = remember { Animatable(1f) }
    LaunchedEffect(id) {
        enterProgress.snapTo(1f)
        enterProgress.animateTo(0f, tween(durationMillis = 240))
    }

    // 二级菜单（分享 / 刷新 / 下载全部）
    var showMenu by remember { mutableStateOf(false) }
    // 待确认移除的歌：先问一句，避免误触把歌从歌单里点没了
    var pendingDelete by remember { mutableStateOf<Song?>(null) }
    // 手势返回：先关确认弹窗
    BackHandler(enabled = pendingDelete != null) {
        pendingDelete = null
    }

    // 退出时也放一遍动画再真正返回 —— 之前只有进场动画，点返回是硬切
    val scope = rememberCoroutineScope()
    val animatedBack: () -> Unit = {
        scope.launch {
            enterProgress.animateTo(1f, tween(durationMillis = 200))
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .offset { IntOffset((enterProgress.value * 48).roundToInt(), 0) }
            .alpha(1f - enterProgress.value)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = animatedBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("${state.songs.size} 首")
                        if (saved?.isLocalPlaylist == true) {
                            append(" · 本地歌单")
                        } else {
                            append(" · 歌单号 ${state.playlistId}")
                        }
                        if (state.saved) append(" · 已保存")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (saved != null) {
                // 分享 / 刷新 / 下载全部 都收进这个二级菜单
                IconButton(onClick = { showMenu = true }) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ---- 播放全部（独占一行）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Button(
                onClick = { onPlaySong(0) },
                colors = glassButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }

        LazyColumn(
            contentPadding = contentBottomPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            // itemsIndexed + contentType：列表可能上千首，让 Compose 能按类型复用、
            // 并且不去做「按内容找下标」这种 O(n²) 的事
            itemsIndexed(
                items = state.songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song" }
            ) { index, song ->
                val task = downloads.taskOf(song.id)
                val downloaded = task is DownloadState.Done || downloads.isDownloaded(song.id)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .glassCard()
                        .clickableNoRipple { onPlaySong(index) }
                        .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )
                    AsyncImage(
                        model = song.pic,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.bodyMedium,
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

                    // ---- 下载按钮：未下载 / 下载中百分比 / 已下载 三态
                    SongDownloadButton(
                        task = task,
                        downloaded = downloaded,
                        onDownload = { onDownloadSong(song) },
                        onCancel = { onCancelDownload(song.id) }
                    )

                    // ---- 单曲更多操作（目前只有「从歌单移除」）
                    var songMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { songMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = songMenu,
                            onDismissRequest = { songMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("从歌单移除") },
                                onClick = {
                                    songMenu = false
                                    pendingDelete = song
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 移除确认
    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("从歌单移除") },
            text = { Text("把「${song.name}」从「${state.name}」里移除？只影响本地歌单，不会动网易云的源歌单。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSong(song)
                    pendingDelete = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    // ---- 二级菜单
    if (showMenu && saved != null) {
        PlaylistMenuSheet(
            playlistName = saved.name,
            isLocal = saved.isLocalPlaylist,
            songCount = state.songs.size,
            refreshing = state.refreshing,
            onShare = { onShare(saved.playlistId) },
            onRefresh = { onRefresh(saved.playlistId) },
            onDownloadAll = onDownloadAll,
            onDismiss = { showMenu = false }
        )
    }
}

/** 添加歌单的两种方式。 */
private enum class AddMode(val label: String) {
    CREATE("创建歌单"),
    IMPORT("从网易云导入")
}

/** 添加歌单的入口卡片（二级菜单的一行）。 */
@Composable
private fun AddEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(corner = 16.dp)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
