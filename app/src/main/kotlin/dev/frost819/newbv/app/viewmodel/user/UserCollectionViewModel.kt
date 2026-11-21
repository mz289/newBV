package dev.frost819.newbv.app.viewmodel.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoPage
import dev.frost819.newbv.biliapi.entity.user.UpCollection
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * UP 主合集/系列视频列表页 UiState。
 */
data class UserCollectionUiState(
    val mid: Long = 0L,
    val collection: UpCollection? = null,
    val videos: List<SpaceVideo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val hasMore: Boolean = true,
)

/**
 * UP 主合集/系列视频列表页 ViewModel。
 *
 * 合集走 polymer seasons_archives_list、系列走 x/series/archives，按页码分页。
 *
 * @param userRepository 用户数据仓库。
 */
@HiltViewModel
class UserCollectionViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("UserCollectionViewModel")

        private val _uiState = MutableStateFlow(UserCollectionUiState())
        val uiState: StateFlow<UserCollectionUiState> = _uiState.asStateFlow()

        private var page = SpaceVideoPage()

        /**
         * 初始化并加载第一页。
         *
         * 标题/封面由路由参数传入（来自合集列表页），不重新拉取元数据。
         *
         * @param mid UP 主 MID
         * @param collectionId 合集（seasonId）或系列（seriesId）
         * @param isSeason true=合集 false=系列
         * @param title 标题（路由传入，可空）
         * @param cover 封面（路由传入，可空）
         */
        fun init(
            mid: Long,
            collectionId: Long,
            isSeason: Boolean,
            title: String,
            cover: String,
        ) {
            val sameCollection =
                _uiState.value.collection?.id == collectionId && _uiState.value.mid == mid
            if (sameCollection && _uiState.value.videos.isNotEmpty()) return

            _uiState.update {
                it.copy(
                    mid = mid,
                    collection =
                        UpCollection(
                            id = collectionId,
                            title = title,
                            cover = cover,
                            total = 0,
                            isSeason = isSeason,
                        ),
                    videos = emptyList(),
                    loading = false,
                    error = false,
                    hasMore = true,
                )
            }
            page = SpaceVideoPage()
            loadVideos(mid)
        }

        /** 拉取下一页（网格滚动到底部时触发）。 */
        fun loadMore(mid: Long) {
            loadVideos(mid)
        }

        /** 重新加载第一页（重试）。 */
        fun refresh(mid: Long) {
            page = SpaceVideoPage()
            _uiState.update { it.copy(videos = emptyList(), hasMore = true, error = false) }
            loadVideos(mid)
        }

        private fun loadVideos(mid: Long) {
            val collection = _uiState.value.collection ?: return
            if (_uiState.value.loading || !_uiState.value.hasMore) return

            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getCollectionVideos(
                            mid = mid,
                            collection = collection,
                            page = page,
                            preferApiType = Prefs.apiType,
                        )
                    }
                }.onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            videos =
                                it.videos + data.videos.filterNot { v ->
                                    it.videos.any { v2 -> v2.aid == v.aid }
                                },
                            loading = false,
                            hasMore = data.page.hasNext,
                        )
                    }
                    page = data.page
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load collection videos" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }
    }
