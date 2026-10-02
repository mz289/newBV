package dev.frost819.newbv.app.ui.state.player

import dev.frost819.newbv.app.entity.player.ChapterMark
import dev.frost819.newbv.app.entity.player.ProgressSegmentMark
import dev.frost819.newbv.app.entity.player.VideoAspectRatio
import dev.frost819.newbv.app.sponsorblock.PendingSponsorSkip
import dev.frost819.newbv.biliapi.entity.video.VideoShot
import dev.frost819.newbv.data.datastore.Audio
import dev.frost819.newbv.data.datastore.VideoCodec

/**
 * 播放器主 UI 状态（低频更新）。
 *
 * 仅在播放状态发生实质性变化时更新，不包含高频的进度信息。
 * 高频进度信息使用独立的 [SeekerState]。
 *
 * @see SeekerState
 * @see PlayerState
 */
data class PlayerUiState(
    // 视频信息
    val aid: Long = 0,
    val cid: Long = 0,
    val epid: Int? = null,
    val authorMid: Long = 0,
    val authorName: String = "",
    val title: String = "",
    val onlineWatching: String = "",
    val videoHeight: Int = 0,
    val videoWidth: Int = 0,
    val lastPlayed: Int = 0,
    // 播放状态
    val playerState: PlayerState = PlayerState.Ready,
    val isBuffering: Boolean = false,
    val videoShot: VideoShot? = null,
    // 提示
    val showSkipToNextEp: Boolean = false,
    val showBackToStart: Boolean = false,
    val showPreviewTip: Boolean = false,
    val shortcutTipText: String? = null,
    val shortcutTipKey: String? = null,
    // SponsorBlock
    /** 进度条上的片段色块（仅策略非"不跳过"的片段）。 */
    val sponsorBlockMarks: List<ProgressSegmentMark> = emptyList(),
    /** SponsorBlock 提示（自动跳过结果或待确认片段），null 时不显示。 */
    val sponsorBlockTip: String? = null,
    /** 等待用户确认跳过的片段，非 null 时确认键跳过、返回键忽略。 */
    val pendingSponsorSkip: PendingSponsorSkip? = null,
    // 章节看点
    /** 章节（view_points 看点）标记，按开始时间升序，无章节时为空。 */
    val chapterMarks: List<ChapterMark> = emptyList(),
    // 可用资源
    val availableQuality: Map<Int, String> = emptyMap(),
    val availableVideoCodec: List<VideoCodec> = emptyList(),
    val availableAudio: List<Audio> = emptyList(),
    // 当前选中状态
    val mediaProfileState: MediaProfileState = MediaProfileState(),
    val playSpeed: Float = 1f,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.Default,
    val isLooping: Boolean = false,
    /** 投屏/外部直链播放（无站内视频身份，弹幕/字幕/历史/心跳均不适用）。 */
    val isExternalMedia: Boolean = false,
) {
    /**
     * 是否为番剧（PGC）播放内容。
     *
     * 由 [epid] 推导：有有效 EP ID 即视为番剧。UI 层据此隐藏
     * “视频信息 / up主页 / 相关视频”等仅 UGC 适用的入口。
     */
    val isPgc: Boolean get() = (epid ?: 0) != 0
}

/**
 * 播放器进度条状态（高频更新，100ms 间隔）。
 *
 * 与 [PlayerUiState] 分离，避免高频进度更新触发不必要的 UI 重组。
 *
 * @property totalDuration 视频总时长（毫秒）
 * @property currentTime 当前播放位置（毫秒）
 * @property bufferedPercentage 缓冲百分比（0-100）
 * @property debugInfo 调试信息字符串
 */
data class SeekerState(
    val totalDuration: Long = 0L,
    val currentTime: Long = 0L,
    val bufferedPercentage: Int = 0,
    val debugInfo: String = "",
)

/**
 * 媒体格式状态。
 *
 * @property qualityId 画质 ID（B 站 qn 参数）
 * @property videoCodec 视频编码
 * @property audio 音频配置
 */
data class MediaProfileState(
    val qualityId: Int = 80,
    val videoCodec: VideoCodec = VideoCodec.AVC,
    val audio: Audio = Audio.A192K,
)

/**
 * 字幕显示状态。
 *
 * @property fontSize 字体大小（sp）
 * @property opacity 背景透明度（0-1）
 * @property bottomPadding 底部间距（dp）
 */
data class SubtitleState(
    val fontSize: Int = 24,
    val opacity: Float = 0.4f,
    val bottomPadding: Int = 12,
)

/**
 * 播放器状态枚举。
 */
sealed class PlayerState {
    /** 准备就绪。 */
    data object Ready : PlayerState()

    /** 正在播放。 */
    data object Playing : PlayerState()

    /** 已暂停。 */
    data object Paused : PlayerState()

    /** 播放结束。 */
    data object Ended : PlayerState()

    /** 发生错误。
     * @param message 错误信息
     */
    data class Error(
        val message: String,
    ) : PlayerState()
}
