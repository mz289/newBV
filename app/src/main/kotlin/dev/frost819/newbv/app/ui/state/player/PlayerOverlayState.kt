package dev.frost819.newbv.app.ui.state.player

import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.biliapi.entity.video.Subtitle
import dev.frost819.newbv.bilisubtitle.entity.SubtitleItem
import dev.frost819.newbv.danmaku.config.DanmakuState

/**
 * 播放器覆盖层聚合状态。
 *
 * 由弹幕/字幕/视频列表三个子 ViewModel 提供、UI 层组装后传给控制器，
 * 与播放核心的 [PlayerUiState] 分离——本类字段不在 PlayerUiState 中。
 */
data class PlayerOverlayState(
    val danmakuState: DanmakuState = DanmakuState(),
    val subtitleState: SubtitleState = SubtitleState(),
    val subtitleId: Long = -1L,
    val subtitleData: List<SubtitleItem> = emptyList(),
    val subtitleList: List<Subtitle> = emptyList(),
    val videoList: List<VideoListItem> = emptyList(),
    val relatedVideos: List<VideoCardData> = emptyList(),
)
