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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.example.metingmusic.ui.theme.LocalAppearance
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent

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
 * 另外整页最外层挂了一个空的 clickable，用来吸收点击、避免穿透到下层；
 * 注意只能用 clickable —— 用 pointerInput 消费全部事件会把歌词的拖动也吃掉。
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
            // 只吸收「点击」，不能用 pointerInput 把整串事件都吃掉 ——
            // 那样歌词列表的拖动会被根节点吞掉，导致滑不动。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
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
                    // 播放器拿不到时长（duration=0）时用歌词最后一句 + 4s 兜底。
                    // 但兜底值可能比当前播放位置还小（纯音乐、歌词只有一句），
                    // 那样进度条会顶到最右、时间显示还会「总时长比已播短」，
                    // 所以再和「当前位置 + 1s」取大。
                    durationMs = progress.durationMs.takeIf { it > 0L }
                        ?: maxOf(
                            state.lyrics.lastOrNull()?.timeMs?.plus(4_000L) ?: 0L,
                            progress.positionMs + 1_000L,
                            // 完全不知道时长时（部分流媒体源不上报）按 4 分钟估，
                            // 否则进度条会一直顶在最右边，看着像播放完了
                            240_000L
                        ),
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
        // 封面 + 歌名：顶栏也能看到是哪首歌，不用靠歌词区猜
        AsyncImage(
            model = state.currentSong?.pic,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = state.currentSong?.name ?: "正在播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.currentSong?.artist.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
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

    // 只有「手指滑动」才临时关掉景深，自动跟随当前行的滚动不算 ——
    // 否则每换一句歌词都会闪一下（animateScrollToItem 同样会让 isScrollInProgress 为 true）。
    var autoScrolling by remember { mutableStateOf(false) }
    var depthTarget by remember { mutableStateOf(1f) }
    LaunchedEffect(listState.isScrollInProgress, autoScrolling) {
        if (listState.isScrollInProgress && !autoScrolling) {
            depthTarget = 0f
        } else {
            delay(500)
            depthTarget = 1f
        }
    }
    // 景深强度 0~1：切换时渐变，避免「啪」一下从糊变清晰
    val depth by animateFloatAsState(
        targetValue = depthTarget,
        animationSpec = tween(320),
        label = "lyric-depth"
    )
    // 设置里关掉景深时强度恒为 0（不糊也不按距离变淡）
    val appearance = LocalAppearance.current
    val depthAmount = if (appearance.lyricDepth) depth else 0f

    LaunchedEffect(currentIndex) {
        // 把当前行放到视口中间偏上，给后面的歌词留出阅读空间
        val target = (currentIndex - 2).coerceAtLeast(0)
        autoScrolling = true
        try {
            listState.animateScrollToItem(target)
        } finally {
            autoScrolling = false
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            // 底部渐隐：歌词接近控制区时淡出，而不是被硬边切一刀。
            // 需要离屏合成（Offscreen），否则 DstIn 会作用到整棵树而不是这一层。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 110.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = (size.height - fade).coerceAtLeast(0f),
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
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
                    // 离当前行越远越淡：0.22 → 0.84，形成纵向的景深梯度；
                    // 滑动时统一提到 0.9，保证看得清
                    .copy(
                        // 景深强度 0 时统一 0.9（看得清），1 时按距离衰减
                        alpha = (
                            0.9f +
                                ((0.10f + 0.74f * focus).coerceAtMost(0.84f) - 0.9f) * depthAmount
                            )
                    ),
                textAlign = TextAlign.Start,
                lineHeight = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    // 景深：远处的歌词轻微失焦，当前行保持锐利；滑动时一律不糊
                    .blur(
                        if (isCurrent) 0.dp
                        else ((1f - focus) * 3.2f * depthAmount).dp
                    )
                    .clickableNoRipple { onSeek(line.timeMs) }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
    state: PlayerUiState,
    durationMs: Long,
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
            // 控制区直接压在背景上，用一层竖向渐变过渡，不做玻璃面板
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.18f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                    1f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                )
            )
            .navigationBarsPadding()
            .padding(bottom = 0.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // 歌名/歌手已移到顶栏，这里只保留出错提示
        state.error?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp)
            )
        }

        // ---- 进度条：左时间 / 中间细轨+圆点 / 右时间，三者一行居中
        val total = durationMs.coerceAtLeast(1L).toFloat()
        val fraction = (sliderPosition / total).coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LrcParser.formatDuration(sliderPosition.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp)
            )

            Slider(
                value = sliderPosition,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange = 0f..total,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                // 轨道和圆点都在 track 里自己画：这样两者共用一个坐标系，
                // 圆点永远落在已播部分的末端，不会因为 Slider 的内边距而错位。
                thumb = {},
                track = {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        val dotSize = 11.dp
                        val trackW = maxWidth
                        // 轨道
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        // 已播部分
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        // 圆点：圆心对准已播末端
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = trackW * fraction - dotSize / 2)
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(28.dp)
            )

            Text(
                text = LrcParser.formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- 控制按钮：播放模式 / 上一首 / 播放暂停 / 下一首（M3，尺寸收紧）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 播放模式：顺序 / 列表循环 / 单曲循环 / 随机，点一下换一种
            IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = state.playMode.icon,
                    contentDescription = "播放模式：${state.playMode.label}",
                    modifier = Modifier.size(20.dp),
                    tint = if (state.playMode == PlayMode.SEQUENTIAL)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(28.dp)
                )
            }

            // M3 的 FilledIconButton：自带正确的 ripple / 状态层
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(54.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
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
