package com.example.metingmusic.ui.download

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.metingmusic.data.model.DownloadLibrary
import com.example.metingmusic.data.model.DownloadState
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.DownloadedSong
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.player.BatchDownloadState
import com.example.metingmusic.ui.search.SearchViewModel
import com.example.metingmusic.ui.theme.clickableNoRipple
import com.example.metingmusic.ui.theme.longPressableNoRipple

/**
 * 「下载」页：列出已下载到本地的歌曲。
 *
 * 已下载的歌播放时直接读本地文件，不走网络 —— 所以这个列表也是「离线可听」清单。
 *
 * 顶部提供「从外部导入」：粘贴网易云的单曲 / 歌单分享文本，
 * 解析后自动加入下载队列 —— 这样不在本应用里搜到的歌也能离线听。
 */
@Composable
fun DownloadScreen(
    library: DownloadLibrary,
    batch: BatchDownloadState,
    importState: SearchViewModel.ExternalImportState,
    /** 已保存的歌单，用来把老下载记录归回它原本所属的歌单。 */
    savedPlaylists: List<SavedPlaylist>,
    /** 传入「有序的歌曲列表 + 下标」，避免分组后下标和扁平列表错位。 */
    onPlaySong: (List<Song>, Int) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteMany: (List<String>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onImportInputChange: (String) -> Unit,
    onImportSubmit: () -> Unit,
    onImportDismiss: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    contentBottomPadding: PaddingValues = PaddingValues(0.dp)
) {
    var pendingDelete by remember { mutableStateOf<DownloadedSong?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var confirmingCancelAll by remember { mutableStateOf(false) }
    var confirmingBulkDelete by remember { mutableStateOf(false) }

    // ---- 多选模式：长按任意一行进入，进入后点击行 = 勾选/取消勾选
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    /**
     * 被收起的歌单分组（存 group.key）。
     *
     * 默认全部展开 —— 收起是为了「我只要看某一两个歌单」时快速收掉其它几十组，
     * 而不是默认把内容藏起来让用户多点一次。
     */
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    val active = library.activeDownloads
    val downloading = active.size
    val failed = library.tasks.values.count { it is DownloadState.Failed }
    val cancelled = library.tasks.values.count { it is DownloadState.Cancelled }
    val problems = failed + cancelled

    // 批量任务在跑，或还有单首在下载 —— 两种情况下都要能取消
    val cancellable = batch.running || downloading > 0

    // ---- 按歌单分组；播放用的扁平顺序 = 分组顺序铺平
    val groups = remember(library.songs, savedPlaylists) {
        groupDownloads(library.songs, savedPlaylists)
    }
    val orderedSongs = remember(groups) { groups.flatMap { it.songs } }
    val orderedIndexById = remember(orderedSongs) {
        orderedSongs.mapIndexed { i, item -> item.song.id to i }.toMap()
    }

    // 列表可能因为删除而变短，把已经不存在的选中项剔除，避免「已选 3 首」实际只剩 1 首
    val validSelected = remember(selectedIds, library.songs) {
        selectedIds intersect library.songs.map { it.song.id }.toSet()
    }
    val allSelected = library.songs.isNotEmpty() && validSelected.size == library.songs.size

    // 全部删完自动退出多选，否则会停在一个空的「已选 0 首」界面上
    LaunchedEffect(selectionMode, library.songs.size) {
        if (selectionMode && library.songs.isEmpty()) {
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
            // ---- 多选模式的顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "退出多选")
                }
                Text(
                    text = "已选 ${validSelected.size} 首",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selectedIds = if (allSelected) {
                        emptySet()
                    } else {
                        library.songs.map { it.song.id }.toSet()
                    }
                }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
                TextButton(
                    onClick = { confirmingBulkDelete = true },
                    enabled = validSelected.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (validSelected.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "删除",
                        color = if (validSelected.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "我的下载",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            batch.running -> buildString {
                                append("正在下载")
                                if (batch.label.isNotBlank()) append("「${batch.label}」")
                                if (batch.total > 0) append(" ${batch.done}/${batch.total}")
                                if (problems > 0) append(" · 未完成 $problems 首")
                            }
                            downloading > 0 ->
                                "正在下载 $downloading 首" +
                                    if (problems > 0) " · 未完成 $problems 首" else ""
                            library.songs.isEmpty() -> "下载后可以离线播放"
                            else -> "${library.songs.size} 首 · 共 ${formatTotal(library.totalSize)}" +
                                if (problems > 0) " · 未完成 $problems 首" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showImport = true }) {
                    Icon(
                        Icons.Default.AddLink,
                        contentDescription = "从外部导入",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (library.songs.isNotEmpty()) {
                    TextButton(onClick = { onPlayAll(orderedSongs.map { it.song }) }) {
                        Text("播放全部")
                    }
                }
            }
        }

        // ---- 下载中的操作条：取消全部 / 清理未完成
        if (cancellable || problems > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cancellable) {
                    TextButton(onClick = { confirmingCancelAll = true }) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("取消下载", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (problems > 0) {
                    TextButton(onClick = onClearFinished) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("清理未完成（$problems）")
                    }
                }
            }
        }

        // 进度条：批量任务用「已完成/总数」的确定进度，单首下载用不确定进度
        if (batch.running && batch.total > 0) {
            LinearProgressIndicator(
                progress = { batch.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                drawStopIndicator = {}
            )
        } else if (cancellable) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        if (library.songs.isEmpty() && active.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (cancellable) "正在下载…" else "还没有下载的歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (cancellable) {
                            "下载完成的歌会陆续出现在这里"
                        } else {
                            "在搜索结果的「⋮」菜单里选「下载」，\n或用右上角的链接按钮从外部导入"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                // ---- 正在下载（还没落盘，所以单独一段）
                if (active.isNotEmpty()) {
                    item(key = "active-header", contentType = "header") {
                        Text(
                            text = "正在下载（${active.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(
                        items = active,
                        key = { _, t -> "active-${t.song.id}" },
                        contentType = { _, _ -> "active" }
                    ) { _, task ->
                        ActiveDownloadRow(
                            task = task,
                            onCancel = { onCancelDownload(task.song.id) }
                        )
                    }
                    item(key = "active-divider", contentType = "header") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                    }
                }

                // ---- 按歌单分组展示（单曲单独一组，排最后）
                groups.forEach { group ->
                    val collapsed = group.key in collapsedGroups
                    item(key = "group-${group.key}", contentType = "group") {
                        DownloadGroupHeader(
                            group = group,
                            collapsed = collapsed,
                            selectedCount = group.songs.count { it.song.id in validSelected },
                            selectionMode = selectionMode,
                            onToggleCollapse = {
                                collapsedGroups = if (collapsed) {
                                    collapsedGroups - group.key
                                } else {
                                    collapsedGroups + group.key
                                }
                            },
                            onToggleAll = {
                                val ids = group.songs.map { it.song.id }.toSet()
                                val allChecked = ids.isNotEmpty() && ids.all { it in validSelected }
                                selectedIds = if (allChecked) {
                                    selectedIds - ids
                                } else {
                                    selectedIds + ids
                                }
                            },
                            onPlayGroup = {
                                onPlaySong(
                                    orderedSongs.map { it.song },
                                    orderedIndexById[group.songs.first().song.id] ?: 0
                                )
                            }
                        )
                    }

                    // 收起时整组歌曲不进入列表 —— 这是收起真正的价值：
                    // 几百首的下载页可以只留下标题，滚动距离立刻变短
                    if (collapsed) return@forEach

                    itemsIndexed(
                        items = group.songs,
                        key = { _, item -> item.song.id },
                        contentType = { _, _ -> "download" }
                    ) { _, item ->
                        val checked = item.song.id in validSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .longPressableNoRipple(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (checked) {
                                                selectedIds - item.song.id
                                            } else {
                                                selectedIds + item.song.id
                                            }
                                        } else {
                                            onPlaySong(
                                                orderedSongs.map { it.song },
                                                orderedIndexById[item.song.id] ?: 0
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(item.song.id)
                                        }
                                    }
                                )
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selectionMode) {
                                Icon(
                                    imageVector = if (checked) Icons.Default.CheckCircle
                                    else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (checked) "已选中" else "未选中",
                                    tint = if (checked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AsyncImage(
                                model = item.song.pic,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.song.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.song.artist} · ${item.fileSizeLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // 多选模式下右侧留给勾选状态，不再显示单条删除按钮
                            if (!selectionMode) {
                                IconButton(onClick = { pendingDelete = item }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除下载",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImport) {
        ExternalImportDialog(
            state = importState,
            onInputChange = onImportInputChange,
            onSubmit = onImportSubmit,
            onDismiss = {
                showImport = false
                onImportDismiss()
            }
        )
    }

    if (confirmingBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBulkDelete = false },
            title = { Text("删除下载") },
            text = {
                Text(
                    "确定删除选中的 ${validSelected.size} 首歌曲吗？" +
                        "本地文件会被一起删掉，删除后无法离线播放。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingBulkDelete = false
                    onDeleteMany(validSelected.toList())
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBulkDelete = false }) { Text("取消") }
            }
        )
    }

    if (confirmingCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmingCancelAll = false },
            title = { Text("取消下载") },
            text = {
                Text(
                    if (batch.running) {
                        "确定取消这个批量下载任务吗？" +
                            "已经下载好的歌曲不会受影响，未下完的会被删除。"
                    } else {
                        "确定取消正在进行的 $downloading 个下载吗？" +
                            "已经下载好的歌曲不会受影响，未下完的会被删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCancelAll = false
                    onCancelAll()
                }) { Text("取消下载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancelAll = false }) { Text("继续下载") }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除下载") },
            text = { Text("确定删除「${target.song.name}」的本地文件吗？删除后将无法离线播放。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.song.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 分组标题：歌单名 + 首数/体积，右侧是「播放这组」或多选时的「全选」，最右是展开箭头。
 *
 * 整行可点（除了右侧按钮）用来收起/展开这一组 —— 下载页经常有几十个歌单，
 * 能一键收掉不关心的那些，滚动距离会短很多。
 */
@Composable
private fun DownloadGroupHeader(
    group: DownloadGroup,
    collapsed: Boolean,
    selectedCount: Int,
    selectionMode: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleAll: () -> Unit,
    onPlayGroup: () -> Unit
) {
    // 收起时箭头转 90°（指向右），展开时朝下
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onToggleCollapse)
            .padding(start = 20.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (group.isSingle) Icons.Default.MusicNote
            else Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = if (group.isSingle) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // 收起时把「已选」也带上，避免用户以为选中的歌不见了
                text = buildString {
                    append("${group.size} 首 · ${formatTotal(group.totalSize)}")
                    if (collapsed && selectionMode && selectedCount > 0) {
                        append(" · 已选 $selectedCount")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selectionMode) {
            TextButton(onClick = onToggleAll) {
                Text(if (selectedCount == group.size) "取消全选" else "全选")
            }
        } else {
            IconButton(onClick = onPlayGroup) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放「${group.name}」",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (collapsed) "展开「${group.name}」" else "收起「${group.name}」",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation)
        )
        Spacer(Modifier.width(4.dp))
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * 「正在下载」里的一行。
 *
 * 进度用百分比 + 细进度条双保险：百分比给精确值，进度条给「还在动」的直观感受。
 */
@Composable
private fun ActiveDownloadRow(
    task: DownloadState.Downloading,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = task.song.pic,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.song.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${task.song.artist} · ${task.progress}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = "取消下载",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 从外部导入的对话框。
 *
 * 单曲链接和歌单链接都收：单曲就下这一首，歌单就整张下下来。
 */
@Composable
private fun ExternalImportDialog(
    state: SearchViewModel.ExternalImportState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AddLink,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("从外部导入歌曲", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "粘贴网易云的分享文本：单曲会下载这一首，歌单会把整张下下来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分享链接 / 文本") },
                    placeholder = { Text("分享单曲: … https://y.music.163.com/m/song?id=…") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        onSubmit()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                state.message?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    onSubmit()
                },
                enabled = state.input.isNotBlank() && !state.busy
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("解析中…")
                } else {
                    Text("导入并下载", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private fun formatTotal(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
