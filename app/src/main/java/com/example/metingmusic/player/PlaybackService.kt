package com.example.metingmusic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.metingmusic.MainActivity
import com.example.metingmusic.data.remote.MetingApi
import com.example.metingmusic.data.local.AppearanceStore

/**
 * 前台播放服务，承载 ExoPlayer 实例与 MediaSession。
 *
 * 有了它，App 退到后台、锁屏之后音乐仍可继续播放，
 * 系统通知栏也会出现播放控制卡片（Media3 自动生成）。
 *
 * ⚠️ 关于通知栏「上一首 / 下一首」：
 * Media3 的通知栏按钮是直接映射到 `Player.seekToNext/PreviousMediaItem()` 的，
 * 如果播放器的播放列表里只有一首歌，这两个命令就不可用（按钮会变灰或点了没反应）。
 * 因此队列必须整体交给播放器 —— 见 [PlayerController.playAll]。
 *
 * ⚠️ 关于「放不出来的歌」的自动跳过为什么放在这里而不是 UI 层：
 * 应用退到后台后，Activity 可能被回收，[PlayerController] 里的监听器随之消失，
 * 但服务和播放器还活着。如果跳过逻辑只写在 Controller 里，这时遇到一首
 * VIP / 下架的歌就会直接卡住（听感是「后台听着听着自己停了」）。
 * 把这段兜底放到服务侧，只要还在播，就一定能续上。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /** 连续失败计数：整个队列都放不出来时不要无限跳歌。 */
    private var consecutiveErrors = 0

    /**
     * 播放连续性兜底。
     *
     * 不依赖任何 UI 状态，只看播放器本身 —— 所以应用进程被回收也照样生效。
     */
    private val continuityListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 真的响起来了才说明失败链断了
            if (isPlaying) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession?.player ?: return

            // 设置里关掉「失败自动下一首」就停下，把错误留给界面显示
            if (!AppearanceStore.get(this@PlaybackService).load().skipOnError) {
                player.pause()
                return
            }

            consecutiveErrors++

            // 只有一首，或连续失败太多：认了，停下
            if (player.mediaItemCount <= 1 || consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                player.pause()
                return
            }

            // 能顺延就顺延（随机模式下按 shuffle 顺序走），到队尾绕回第一首。
            // 注意不能用 hasNextMediaItem() 判断：顺序播放到队尾时它是 false，
            // 那样就会「什么都不做」，正是后台播放自己停掉的根因。
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.seekToDefaultPosition(0)
            }
            // 出错后播放器处于 IDLE，必须重新 prepare 才会真的开始拉流
            player.prepare()
            player.play()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 网易云 CDN 会校验 UA，必须和接口请求用同一个，否则拉流 403。
        // 另外直链 302 可能跨协议（http/https 互换），需要显式放开。
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(MetingApi.MEDIA_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true) // 拔耳机自动暂停
            .build()

        player.addListener(continuityListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(continuityListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        /** 连续失败上限，见 [continuityListener]。 */
        const val MAX_CONSECUTIVE_ERRORS = 8
    }
}
