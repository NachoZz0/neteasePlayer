package com.example.metingmusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metingmusic.data.model.LrcParser
import com.example.metingmusic.data.model.LyricLine
import com.example.metingmusic.player.PlayMode
import com.example.metingmusic.ui.common.icon
import com.example.metingmusic.player.PlaybackProgress
import com.example.metingmusic.player.PlayerUiState
import com.example.metingmusic.ui.theme.AppBackground
import com.example.metingmusic.ui.theme.clickableNoRipple
import kotlinx.coroutines.flow.StateFlow

/**
 * 全屏播放页：以歌词为主视觉，控制区固定在底部。
 *
 * 布局原则是「主界面只留最常用的」：
 * 上一首 / 播放暂停 / 下一首 / 播放模式 / 播放队列，其余功能（音质、下载、定时）
 * 都收进左下角的「⋯」二级菜单。
 *
 * 进度通过 [progressFlow] 单独订阅，而不是从 [state] 里读 ——
 * 这样进度每 500ms 的更新只会让本页重组，不会波及底下的歌单列表。
 *
 * 另外整页最外层挂了 [consumeAllTouches]，把所有触摸事件在根节点吃掉，
 * 避免出现「点到歌词后面的东西」的穿透问题。
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val song = state.currentSong
    val progress by progressFlow.collectAsStateWithLifecycle()

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(progress.positionMs, isSeeking) {
        if (isSeeking == 0f) sliderPosition = progress.positionMs.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeAllTouches()
    ) {
        // ---- 背景：全局插画 + 上下压暗，保证歌词文字始终有对比度
        AppBackground()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        0.35f to MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.86f)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ---- 顶栏（状态栏避让，否则会被时间和状态图标盖住）
            TopBar(
                state = state,
                onBack = onBack,
                modifier = Modifier.statusBarsPadding()
            )

            // ---- 中部：歌词（主视觉）
            Box(modifier = Modifier.weight(1f)) {
                if (song == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "还没有正在播放的歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LyricsView(
                        lyrics = state.lyrics,
                        positionMs = progress.positionMs,
                        onSeek = onSeek
                    )
                }
            }

            if (song != null) {
                BottomControls(
                    state = state,
                    durationMs = progress.durationMs,
                    songName = song.name,
                    artist = song.artist,
                    sliderPosition = sliderPosition,
                    onSliderChange = {
                        sliderPosition = it
                        isSeeking = 1f
                    },
                    onSliderFinished = {
                        onSeek(sliderPosition.toLong())
                        isSeeking = 0f
                    },
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCyclePlayMode = onCyclePlayMode,
                    onOpenQueue = onOpenQueue,
                    onOpenMenu = onOpenMenu
                )
            }
        }

        // ---- 解析中遮罩
        AnimatedVisibility(
            visible = state.isBuffering && song != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

/** 对整块区域做手势拦截，杜绝点击穿透到下层。 */
private fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        // 吃掉 down 事件，并在手势结束前保持消费状态
        awaitFirstDown(requireUnconsumed = false).consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
private fun TopBar(
    state: PlayerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.currentSong?.name ?: "正在播放",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.currentSong?.artist.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 定时播放生效时常驻一个小胶囊，避免用户忘了它还开着
        if (state.sleepTimer.enabled) {
            Row(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.sleepTimer.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/**
 * 歌词视图。
 *
 * 做了两件事：
 * 1. 当前行居中放大、其余行缩小淡出，营造「焦点跟随」的观感；
 * 2. 点击任意一行可跳转到该行对应的时间点。
 */
@Composable
private fun LyricsView(
    lyrics: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    if (lyrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无歌词",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "纯音乐或接口未收录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val currentIndex = remember(lyrics, positionMs) {
        lyrics.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        // 把当前行放到视口中间偏上，给后面的歌词留出阅读空间
        val target = (currentIndex - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(lyrics.size) { index ->
            val line = lyrics[index]
            val isCurrent = index == currentIndex
            // 距离当前行越远，越小越淡
            val distance = kotlin.math.abs(index - currentIndex)
            val focus by animateFloatAsState(
                targetValue = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.86f
                    distance == 2 -> 0.78f
                    else -> 0.72f
                },
                animationSpec = tween(320),
                label = "lyric-focus"
            )

            Text(
                text = line.text,
                style = if (isCurrent) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.titleMedium,
                fontSize = (if (isCurrent) 21 else 18).sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = (0.32f + 0.5f * focus).coerceAtMost(0.9f)),
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableNoRipple { onSeek(line.timeMs) }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun BottomControls(
    state: PlayerUiState,
    durationMs: Long,
    songName: String,
    artist: String,
    sliderPosition: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    // 顶部快速过渡到不透明：歌词滚到底部时不会和歌名糊在一起
                    0f to Color.Transparent,
                    0.18f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                    1f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                )
            )
            // 底部手势条避让，否则控制按钮会贴到系统导航条上。
            // 再额外留 12dp 把整块控制区抬离屏幕底边，缓解「挤在底部」的观感。
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ---- 歌名 / 歌手（歌词区是滚动列表，这里固定显示当前曲目信息）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // ---- 进度条
        Slider(
            value = sliderPosition,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = LrcParser.formatDuration(sliderPosition.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = LrcParser.formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        // ---- 控制按钮：播放模式 / 上一首 / 播放暂停 / 下一首
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 播放模式：顺序 / 列表循环 / 单曲循环 / 随机，点一下换一种
            IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = state.playMode.icon,
                    contentDescription = "播放模式：${state.playMode.label}",
                    modifier = Modifier.size(24.dp),
                    tint = if (state.playMode == PlayMode.SEQUENTIAL)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(36.dp)
                )
            }

            // 打开底部队列面板（歌曲列表）
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "播放队列",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // ---- 二级菜单入口：横着的三个点，紧挨在「播放队列」按钮正下方。
        //      两行的 horizontal padding 必须一致，并用与上一行等宽的占位对齐，
        //      这样这一行唯一的按钮才会和上方的「播放队列」落在同一列。
        //      注意这里不能用 IconButton（它自带 48dp 最小触摸区），
        //      否则这一行会白白占掉一整条高度，把控制区撑得更挤。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Spacer(Modifier.size(44.dp)) // 对应「播放模式」
            Spacer(Modifier.size(52.dp)) // 对应「上一首」
            Spacer(Modifier.size(64.dp)) // 对应「播放/暂停」
            Spacer(Modifier.size(52.dp)) // 对应「下一首」
            Box(
                modifier = Modifier
                    .size(44.dp, 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenMenu
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "更多功能",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}
