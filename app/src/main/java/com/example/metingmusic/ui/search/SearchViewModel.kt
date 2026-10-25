package com.example.metingmusic.ui.search

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metingmusic.data.local.ImportedPlaylistStore
import com.example.metingmusic.data.local.SavedPlaylist
import com.example.metingmusic.data.model.ShareLinkParser
import com.example.metingmusic.data.model.ShareTarget
import com.example.metingmusic.data.model.Song
import com.example.metingmusic.data.remote.MetingApi
import com.example.metingmusic.data.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索结果页状态。
 *
 * 标 @Immutable 是给 Compose 编译器的承诺：这个类的实例一旦创建就不会被改写。
 * 只有这样，携带上千首歌单的界面才能在播放进度刷新时被「跳过重组」，
 * 而不是每次进度跳一下就把整个列表重新算一遍（这是歌单滑动掉帧的根因）。
 */
@Immutable
data class SearchUiState(
    val keyword: String = "",
    /**
     * 音源。QQ 音乐（tencent）实测大面积解析失败，已下线，
     * 目前固定为网易云，保留字段是为了后续接入其它音源时不用改 UI。
     */
    val server: String = MetingApi.SERVER_NETEASE,
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false
)

/**
 * 导入进来的网易云歌单的界面状态。
 *
 * [saved] 为 true 表示这份歌单已经落盘，重启后还在；
 * 打开的方式决定它是「刚导入的」还是「从历史记录里点进来的」。
 *
 * [nameInput] 是名称输入框的内容 —— 放在这里而不是 composable 的 remember 里，
 * 是因为「粘贴分享链接后自动补全歌单名」需要由 ViewModel 在解析输入时回填。
 */
@Immutable
data class ImportedPlaylistUiState(
    val input: String = "",
    val nameInput: String = "",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val playlistId: String? = null,
    val name: String = "",
    val songs: List<Song> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false
) {
    val hasResult: Boolean get() = playlistId != null && songs.isNotEmpty()
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository()
    private val importedStore = ImportedPlaylistStore.get(app)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _playlistState = MutableStateFlow(ImportedPlaylistUiState())
    val playlistState: StateFlow<ImportedPlaylistUiState> = _playlistState.asStateFlow()

    /** 已经导入并保存到本地的歌单，重启后依然在。 */
    val savedPlaylists: StateFlow<List<SavedPlaylist>> = importedStore.playlists

    private var searchJob: Job? = null

    /**
     * 用户是否手动改过歌单名。
     *
     * 一旦改过就不再自动补全 —— 否则用户辛苦打的字会被粘贴链接的解析结果覆盖掉。
     */
    private var nameTouched = false

    // ------------------------------------------------------------ 歌曲搜索

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
        debounceSearch(value)
    }

    /** 输入停顿 400ms 后自动搜索，避免打太多请求。 */
    private fun debounceSearch(value: String) {
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(results = emptyList(), hasSearched = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search(value)
        }
    }

    fun search(keyword: String = _state.value.keyword) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.search(keyword, _state.value.server)
                .onSuccess { list ->
                    _state.update {
                        it.copy(loading = false, results = list, hasSearched = true)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "搜索失败，请检查网络",
                            hasSearched = true
                        )
                    }
                }
        }
    }

    // ------------------------------------------------------------ 歌单导入

    /**
     * 歌单号 / 链接输入变化。
     *
     * 除了清掉上一次的报错，还会顺手解析一下分享文本 ——
     * 如果里面带着歌单名（网易云的分享文案里有），就自动补全到名称输入框，
     * 省得用户再手打一遍。用户手动改过名称后就不再覆盖。
     */
    fun onPlaylistInputChange(value: String) {
        val parsedName = (ShareLinkParser.parse(value) as? ShareTarget.Playlist)?.name
        _playlistState.update {
            it.copy(
                input = value,
                error = null,
                nameInput = if (!nameTouched && parsedName != null) parsedName else it.nameInput
            )
        }
    }

    /** 用户手动改名称 —— 之后不再自动补全。 */
    fun onPlaylistNameChange(value: String) {
        nameTouched = true
        _playlistState.update { it.copy(nameInput = value) }
    }

    /**
     * 导入网易云歌单。
     *
     * 名称取自名称输入框（可能是用户手填的，也可能是粘贴链接时自动补全的），
     * 留空则回退到「上次保存的名字」或默认名。
     * 导入成功后立刻落盘 —— 这就是「保存歌单」的实现，重启后依然在。
     */
    fun importPlaylist() {
        val raw = _playlistState.value.input
        val target = ShareLinkParser.parse(raw)
        if (target !is ShareTarget.Playlist) {
            _playlistState.update {
                it.copy(error = "没识别出歌单号，可以填纯数字或粘贴网易云歌单链接")
            }
            return
        }
        val id = target.playlistId

        viewModelScope.launch {
            _playlistState.update { it.copy(loading = true, error = null) }
            repository.playlist(id, _state.value.server)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        _playlistState.update {
                            it.copy(
                                loading = false,
                                error = "这个歌单是空的，或者里面的歌都不可用"
                            )
                        }
                        return@onSuccess
                    }
                    val displayName = _playlistState.value.nameInput.trim().ifEmpty {
                        importedStore.get(id)?.name ?: "网易云歌单 $id"
                    }
                    importedStore.save(
                        playlistId = id,
                        name = displayName,
                        songs = list,
                        userId = target.userId,
                        creatorId = target.creatorId,
                        creatorName = target.creatorName
                    )
                    _playlistState.update {
                        it.copy(
                            loading = false,
                            playlistId = id,
                            name = displayName,
                            songs = list,
                            error = null,
                            saved = true
                        )
                    }
                }
                .onFailure { e ->
                    _playlistState.update {
                        it.copy(loading = false, error = e.message ?: "歌单加载失败，请检查网络")
                    }
                }
        }
    }

    /** 直接打开一份已保存的歌单（不重新请求网络）。 */
    fun openSavedPlaylist(playlistId: String) {
        val saved = importedStore.get(playlistId) ?: return
        _playlistState.update {
            it.copy(
                playlistId = saved.playlistId,
                name = saved.name,
                songs = saved.songs,
                error = null,
                saved = true
            )
        }
    }

    /**
     * 刷新已保存的歌单 —— 重新从网络拉一次曲目并更新本地记录。
     *
     * 网易云的歌单会增删歌，而本地缓存是导入那一刻的快照，
     * 所以需要一个显式的刷新入口，而不是每次打开都偷偷请求。
     */
    fun refreshPlaylist(playlistId: String) {
        viewModelScope.launch {
            _playlistState.update { it.copy(refreshing = true, error = null) }
            val existing = importedStore.get(playlistId)
            repository.playlist(playlistId, _state.value.server)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        _playlistState.update {
                            it.copy(refreshing = false, error = "歌单是空的，或里面的歌都不可用了")
                        }
                        return@onSuccess
                    }
                    importedStore.save(
                        playlistId = playlistId,
                        name = existing?.name ?: "网易云歌单 $playlistId",
                        songs = list,
                        userId = existing?.userId,
                        creatorId = existing?.creatorId,
                        creatorName = existing?.creatorName
                    )
                    // 详情页要显示合并后的结果（含手动加的歌），不能直接用远端列表
                    val merged = importedStore.get(playlistId)?.songs ?: list
                    _playlistState.update {
                        // 只在「当前正打开这个歌单」时同步详情页内容
                        if (it.playlistId == playlistId) {
                            it.copy(refreshing = false, songs = merged, error = null)
                        } else {
                            it.copy(refreshing = false)
                        }
                    }
                }
                .onFailure { e ->
                    _playlistState.update {
                        it.copy(refreshing = false, error = e.message ?: "刷新失败，请检查网络")
                    }
                }
        }
    }

    fun renameSavedPlaylist(playlistId: String, name: String) {
        importedStore.rename(playlistId, name)
        _playlistState.update {
            if (it.playlistId == playlistId) it.copy(name = importedStore.get(playlistId)?.name ?: it.name)
            else it
        }
    }

    fun deleteSavedPlaylist(playlistId: String) {
        importedStore.delete(playlistId)
        _playlistState.update {
            if (it.playlistId == playlistId) ImportedPlaylistUiState(input = it.input)
            else it
        }
    }

    /**
     * 新建一个本地空歌单。
     *
     * @return 给用户看的提示文案（成功 / 失败都有）。
     */
    fun createPlaylist(name: String): String {
        val id = importedStore.create(name)
        return if (id == null) "歌单名不能为空" else "已创建「${name.trim()}」"
    }

    /**
     * 把一首歌加进歌单。
     *
     * @return 给用户看的提示文案。
     */
    fun addSongToPlaylist(playlistId: String, song: Song): String {
        val added = importedStore.addSongs(playlistId, listOf(song))
        val name = importedStore.get(playlistId)?.name ?: "歌单"
        return if (added > 0) "已把「${song.name}」加入「$name」" else "「$name」里已经有这首歌了"
    }

    /** 批量删除已保存的歌单（长按多选）。 */
    fun deleteSavedPlaylists(playlistIds: List<String>) {
        importedStore.deleteMany(playlistIds)
        _playlistState.update {
            if (it.playlistId in playlistIds) ImportedPlaylistUiState(input = it.input)
            else it
        }
    }

    /** 退出歌单详情，回到导入界面。 */
    /**
     * 从歌单里移除一首歌。
     *
     * 直接改本地记录，不去动网易云的源歌单 —— 用户在这里的增删是「本地歌单」的概念，
     * 想同步回源歌单得去网易云操作。移除后立刻刷新当前详情页，不用退出重进。
     */
    fun removeSongFromPlaylist(playlistId: String, songId: String): String =
        removeSongsFromPlaylist(playlistId, listOf(songId))

    /** 批量移除（多选删除）。只刷新一次列表，避免删十首刷十次。 */
    fun removeSongsFromPlaylist(playlistId: String, songIds: Collection<String>): String {
        songIds.forEach { importedStore.removeSong(playlistId, it) }
        val updated = importedStore.get(playlistId)
        if (updated == null) {
            // 歌单被删空了就回到列表页，避免停在一个不存在的详情页上
            clearImportedPlaylist()
            return "已移除 ${songIds.size} 首，歌单已空"
        }
        _playlistState.update {
            it.copy(
                playlistId = updated.playlistId,
                name = updated.name,
                songs = updated.songs,
                saved = true,
                error = null
            )
        }
        return "已从「${updated.name}」移除 ${songIds.size} 首"
    }

    /**
     * 从 txt 文件导入歌单。
     *
     * 文件格式就是「导出歌单」生成的：首行歌单名，之后每行 `序号. 歌名 - 作者`。
     * 解析出来的每一首都要**走一次搜索**换成真正可播放的歌曲 —— 纯文本没有音源，
     * 直接塞进歌单只会得到一堆点不开的条目。
     *
     * 逐首串行搜索是刻意的：并发几十个请求容易被音源限流。
     */
    fun importPlaylistFromText(raw: String, fallbackName: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) {
                onDone("文件是空的")
                return@launch
            }

            val entries = lines.mapNotNull { line ->
                // 去掉行首的「01. 」「1、」这类序号
                val body = line.replace(Regex("^[0-9]+[.、)]\\s*"), "").trim()
                if (body.isEmpty() || body.startsWith("共 ")) return@mapNotNull null
                val parts = body.split(" - ", limit = 2)
                val songName = parts[0].trim()
                val artist = parts.getOrNull(1)?.trim().orEmpty()
                if (songName.isEmpty()) null else songName to artist
            }
            if (entries.isEmpty()) {
                onDone("没识别到歌曲，格式应为「歌名 - 作者」")
                return@launch
            }

            val name = lines.first().takeIf { it != entries.first().first } ?: fallbackName
            val playlistId = importedStore.create(name)
            if (playlistId == null) {
                onDone("创建歌单失败")
                return@launch
            }

            val found = mutableListOf<Song>()
            entries.forEach { (songName, artist) ->
                val keyword = if (artist.isBlank()) songName else "$songName $artist"
                repository.search(keyword, _state.value.server, limit = 5)
                    .onSuccess { list -> list.firstOrNull()?.let { found += it } }
            }
            importedStore.addSongs(playlistId, found)
            openSavedPlaylist(playlistId)
            onDone("已导入 ${found.size}/${entries.size} 首到「$name」")
        }
    }

    fun clearImportedPlaylist() {
        _playlistState.update {
            ImportedPlaylistUiState(input = it.input, nameInput = it.nameInput)
        }
    }

    // ------------------------------------------------------------ 从外部导入到下载

    /**
     * 「从外部导入歌曲到下载」的状态。
     *
     * 用户粘一条网易云的分享文本（单曲或歌单都行），解析后交给播放器批量下载。
     */
    @Immutable
    data class ExternalImportState(
        val input: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false
    )

    private val _externalState = MutableStateFlow(ExternalImportState())
    val externalState: StateFlow<ExternalImportState> = _externalState.asStateFlow()

    fun onExternalInputChange(value: String) {
        _externalState.update { it.copy(input = value, message = null) }
    }

    fun clearExternalInput() {
        _externalState.update { ExternalImportState() }
    }

    /**
     * 解析输入并回调出要下载的歌曲。
     *
     * 用回调而不是把结果塞进 StateFlow：这是一次性的动作（触发下载），
     * 不是持续状态，塞进流里会变成「重组一次就重新下载一遍」。
     *
     * 回调里的 [playlistId] 用于给下载记录标记来源歌单（单曲为 null），
     * 下载页靠它把歌归到对应的歌单分组里。
     */
    fun importExternal(
        onResolved: (songs: List<Song>, label: String, playlistId: String?) -> Unit
    ) {
        val text = _externalState.value.input
        val target = ShareLinkParser.parse(text)
        if (target == null) {
            _externalState.update {
                it.copy(message = "没识别出内容，请粘贴网易云的单曲或歌单分享文本", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _externalState.update { it.copy(busy = true, message = null) }
            when (target) {
                is ShareTarget.Song -> {
                    val song = repository.songById(target.songId, _state.value.server)
                    if (song == null) {
                        _externalState.update {
                            it.copy(busy = false, message = "没找到这首歌，可能已下架", isError = true)
                        }
                    } else {
                        onResolved(listOf(song), song.name, null)
                        _externalState.update {
                            it.copy(busy = false, message = "已开始下载「${song.name}」", isError = false)
                        }
                    }
                }

                is ShareTarget.Playlist -> {
                    repository.playlist(target.playlistId, _state.value.server)
                        .onSuccess { list ->
                            if (list.isEmpty()) {
                                _externalState.update {
                                    it.copy(busy = false, message = "这个歌单是空的，或歌都不可用", isError = true)
                                }
                            } else {
                                val label = target.name ?: "网易云歌单 ${target.playlistId}"
                                onResolved(list, label, target.playlistId)
                                _externalState.update {
                                    it.copy(
                                        busy = false,
                                        message = "已开始下载「$label」共 ${list.size} 首",
                                        isError = false
                                    )
                                }
                            }
                        }
                        .onFailure { e ->
                            _externalState.update {
                                it.copy(busy = false, message = e.message ?: "加载失败", isError = true)
                            }
                        }
                }
            }
        }
    }
}
