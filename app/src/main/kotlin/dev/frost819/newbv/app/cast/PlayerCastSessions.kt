package dev.frost819.newbv.app.cast

import dev.frost819.newbv.app.ui.action.player.DanmakuSettingAction
import dev.frost819.newbv.app.ui.action.player.MediaProfileSettingAction
import dev.frost819.newbv.app.viewmodel.live.LivePlayerState
import dev.frost819.newbv.app.viewmodel.live.LivePlayerViewModel
import dev.frost819.newbv.app.viewmodel.player.DanmakuViewModel
import dev.frost819.newbv.app.viewmodel.player.PlayerViewModel

/**
 * 播放界面侧的 [CastPlaybackSession] 工厂。
 *
 * 各播放界面（站内视频 / 直播 / 外部直链）在进入时注册、离开时注销，
 * 投屏 HTTP 服务即可把手机端的播放控制转发到当前界面。
 */
object PlayerCastSessions {
    /**
     * 站内视频 / 外部直链播放会话。
     *
     * 命令采用「乐观状态」上报：手机端下发 play/pause/seek 后，播放器
     * 状态短暂滞后（缓冲、起播），快照在 3s 窗口内优先返回命令目标值，
     * 避免官方客户端进度条回跳。
     *
     * @param onFinish `stop` 命令的回调（通常为退出播放界面）
     */
    fun video(
        playerViewModel: PlayerViewModel,
        danmakuViewModel: DanmakuViewModel,
        onFinish: () -> Unit,
    ): CastPlaybackSession =
        object : CastPlaybackSession {
            private var commandedState: CastTransportState? = null
            private var commandedStateAtMs: Long = 0L
            private var commandedPositionMs: Long? = null
            private var commandedPositionAtMs: Long = 0L

            override fun play() {
                commandedState = CastTransportState.PLAYING
                commandedStateAtMs = System.currentTimeMillis()
                playerViewModel.resumePlayback()
            }

            override fun pause() {
                commandedState = CastTransportState.PAUSED_PLAYBACK
                commandedStateAtMs = System.currentTimeMillis()
                playerViewModel.pausePlayback()
            }

            override fun stop() {
                commandedState = CastTransportState.STOPPED
                commandedStateAtMs = System.currentTimeMillis()
                playerViewModel.pausePlayback()
                onFinish()
            }

            override fun seekTo(positionMs: Long) {
                val durationMs =
                    (playerViewModel.videoPlayer?.duration
                        ?: playerViewModel.seekerState.value.totalDuration)
                        .coerceAtLeast(0L)
                val targetMs =
                    if (durationMs > 0L) {
                        positionMs.coerceIn(0L, durationMs)
                    } else {
                        positionMs.coerceAtLeast(0L)
                    }
                commandedPositionMs = targetMs
                commandedPositionAtMs = System.currentTimeMillis()
                playerViewModel.seekToTime(targetMs)
            }

            override fun setSpeed(speed: Float) {
                playerViewModel.updatePlaySpeed(speed = speed)
            }

            override fun setQuality(qualityId: Int) {
                playerViewModel.updateMediaProfile(MediaProfileSettingAction.SetQuality(qualityId))
            }

            override fun setDanmakuEnabled(enabled: Boolean) {
                // 投屏临时开关：只切总开关，persist=false 避免覆盖用户默认弹幕设置
                danmakuViewModel.updateDanmakuState(
                    DanmakuSettingAction.SetEnabled(enabled, persist = false),
                )
            }

            override fun snapshot(): CastPlaybackSnapshot {
                val uiState = playerViewModel.uiState.value
                val seekerState = playerViewModel.seekerState.value
                val player = playerViewModel.videoPlayer
                val actualTransportState =
                    when {
                        uiState.isBuffering -> CastTransportState.TRANSITIONING
                        player?.isPlaying == true -> CastTransportState.PLAYING
                        uiState.playerState == dev.frost819.newbv.app.ui.state.player.PlayerState.Playing ->
                            CastTransportState.PLAYING
                        uiState.playerState == dev.frost819.newbv.app.ui.state.player.PlayerState.Paused ||
                            uiState.playerState == dev.frost819.newbv.app.ui.state.player.PlayerState.Ready ->
                            CastTransportState.PAUSED_PLAYBACK
                        else -> CastTransportState.STOPPED
                    }
                val nowMs = System.currentTimeMillis()
                if (commandedState != null && actualTransportState == commandedState) {
                    commandedState = null
                }
                val transportState = commandedState
                    ?.takeIf { nowMs - commandedStateAtMs <= CAST_COMMAND_OPTIMISTIC_WINDOW_MS }
                    ?: actualTransportState
                val actualPositionMs = (player?.currentPosition ?: seekerState.currentTime).coerceAtLeast(0L)
                val pendingStartPositionMs = uiState.lastPlayed.toLong().takeIf { it > 0L }
                if (commandedPositionMs != null &&
                    kotlin.math.abs(actualPositionMs - commandedPositionMs!!) <= CAST_SEEK_POSITION_TOLERANCE_MS
                ) {
                    commandedPositionMs = null
                }
                val positionMs = commandedPositionMs
                    ?.takeIf { nowMs - commandedPositionAtMs <= CAST_COMMAND_OPTIMISTIC_WINDOW_MS }
                    ?: pendingStartPositionMs
                    ?: actualPositionMs
                return CastPlaybackSnapshot(
                    state = transportState,
                    positionMs = positionMs,
                    durationMs = (player?.duration ?: seekerState.totalDuration).coerceAtLeast(0L),
                    speed = uiState.playSpeed,
                    aid = uiState.aid,
                    cid = uiState.cid,
                    epid = uiState.epid,
                    title = uiState.title,
                    qualityId = uiState.mediaProfileState.qualityId,
                    availableQuality = uiState.availableQuality,
                    danmakuEnabled = danmakuViewModel.danmakuState.value.enabled,
                )
            }
        }

    /**
     * 直播播放会话：仅支持播放/暂停/停止，进度与画质不适用于直播流。
     */
    fun live(
        liveViewModel: LivePlayerViewModel,
        onFinish: () -> Unit,
    ): CastPlaybackSession =
        object : CastPlaybackSession {
            override fun play() {
                liveViewModel.videoPlayer?.start()
            }

            override fun pause() {
                liveViewModel.videoPlayer?.pause()
            }

            override fun stop() {
                liveViewModel.videoPlayer?.pause()
                onFinish()
            }

            override fun seekTo(positionMs: Long) = Unit

            override fun setSpeed(speed: Float) = Unit

            override fun setQuality(qualityId: Int) = Unit

            override fun setDanmakuEnabled(enabled: Boolean) = Unit

            override fun snapshot(): CastPlaybackSnapshot {
                val uiState = liveViewModel.uiState.value
                val player = liveViewModel.videoPlayer
                val state =
                    when {
                        uiState.playerState == LivePlayerState.Buffering -> CastTransportState.TRANSITIONING
                        player?.isPlaying == true ||
                            uiState.playerState == LivePlayerState.Playing -> CastTransportState.PLAYING
                        uiState.playerState == LivePlayerState.Paused -> CastTransportState.PAUSED_PLAYBACK
                        else -> CastTransportState.STOPPED
                    }
                return CastPlaybackSnapshot(
                    state = state,
                    roomId = uiState.roomId,
                    title = uiState.title,
                )
            }
        }

    private const val CAST_COMMAND_OPTIMISTIC_WINDOW_MS = 3_000L
    private const val CAST_SEEK_POSITION_TOLERANCE_MS = 1_500L
}
