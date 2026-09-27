package dev.frost819.newbv.app.viewmodel.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.VideoInfoRepository
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.biliapi.entity.video.RelatedVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视频列表 ViewModel。
 *
 * 管理分集列表和相关视频，提供上下集查找功能。
 * 观察 [VideoInfoRepository] 的共享状态，自动同步更新。
 *
 * @see VideoListState
 */
@HiltViewModel
class VideoListViewModel
    @Inject
    constructor(
        private val videoInfoRepository: VideoInfoRepository,
    ) : ViewModel() {
        private val _videoListState = MutableStateFlow(VideoListState())
        val videoListState = _videoListState.asStateFlow()

        private var currentAid = 0L

        /**
         * 设置当前播放视频 AV 号，用于过滤 repository 的数据。
         *
         * 必须在 [init] 之前或同时调用，否则相关视频可能因 aid 不匹配而被过滤。
         *
         * @param aid 当前视频 AV 号
         */
        fun setCurrentAid(aid: Long) {
            currentAid = aid
        }

        init {
            viewModelScope.launch {
                videoInfoRepository.videoList
                    .collect { list ->
                        if (list.isEmpty()) return@collect
                        _videoListState.update { it.copy(videoList = list) }
                    }
            }
            viewModelScope.launch {
                videoInfoRepository.relatedVideos
                    .filter { videoInfoRepository.videoDetail.value?.aid == currentAid }
                    .collect { list ->
                        if (currentAid == 0L) return@collect
                        _videoListState.update { it.copy(relatedVideos = list) }
                    }
            }
        }
    }

/**
 * 视频列表状态。
 */
data class VideoListState(
    val videoList: List<VideoListItem> = emptyList(),
    val relatedVideos: List<RelatedVideo> = emptyList(),
)
