package dev.frost819.newbv.app.viewmodel.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.user.SpaceCollectionPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoPage
import dev.frost819.newbv.biliapi.entity.user.UpCollection
import dev.frost819.newbv.biliapi.entity.user.UserFavFolder
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * UP 主空间内容栏目。
 *
 * 投稿（Videos）支持三种排序（见 [UserSpaceUiState.order]）；合集/系列为聚合内容页；收藏为公开收藏夹。
 */
enum class UserSpaceSection(
    val label: String,
) {
    Videos("投稿"),
    Seasons("合集"),
    Series("系列"),
    Fav("收藏"),
}

/**
 * 用户空间页 UiState。
 *
 * 与原版 BV 一致：用户名和头像由来源页通过路由传入，不单独调用 API 获取。
 *
 * [videos]（投稿）与 [collections]（合集/系列）按 [section] 二选一展示；
 * loading/error/hasMore 三个字段描述当前栏目内容的首载/翻页/出错状态。
 */
data class UserSpaceUiState(
    val mid: Long = 0L,
    val name: String = "",
    val face: String = "",
    val videos: List<SpaceVideo> = emptyList(),
    val collections: List<UpCollection> = emptyList(),
    val favFolders: List<UserFavFolder> = emptyList(),
    val section: UserSpaceSection = UserSpaceSection.Videos,
    /** 投稿排序（仅 [UserSpaceSection.Videos] 生效）。 */
    val order: SpaceVideoOrder = SpaceVideoOrder.PubDate,
    val loading: Boolean = false,
    val error: Boolean = false,
    val hasMore: Boolean = true,
    val isFollowing: Boolean = false,
    val followLoading: Boolean = false,
)

/**
 * 用户空间页一次性 UI 事件。
 */
sealed interface UserSpaceUiEffect {
    /** 显示 Toast 消息。 */
    data class ShowToast(
        val message: String,
    ) : UserSpaceUiEffect
}

/**
 * 用户空间页 ViewModel。
 *
 * 管理用户投稿视频列表分页和当前用户对 TA 的关注状态。
 * 用户名和头像由 [init] 从路由参数传入，不调用 getUserInfo API（与原版 BV 行为一致）。
 *
 * @param userRepository 用户数据仓库。
 */
@HiltViewModel
class UserSpaceViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("UserSpaceViewModel")

        private val _uiState = MutableStateFlow(UserSpaceUiState())
        val uiState: StateFlow<UserSpaceUiState> = _uiState.asStateFlow()

        private val _uiEffect = MutableSharedFlow<UserSpaceUiEffect>()
        val uiEffect: SharedFlow<UserSpaceUiEffect> = _uiEffect.asSharedFlow()

        private var videoPage = SpaceVideoPage()
        private var collectionPage = SpaceCollectionPage()

        /**
         * 初始化：设置用户信息（由路由传入），按当前栏目加载内容。
         *
         * @param mid 用户 MID
         * @param name 用户名（从详情页/播放器/卡片传入）
         * @param face 头像 URL（可选）
         */
        fun init(
            mid: Long,
            name: String = "",
            face: String? = null,
        ) {
            val sameMid = _uiState.value.mid == mid
            val hasCachedContent = sameMid && hasDisplayedContent()

            if (!hasCachedContent) {
                _uiState.update {
                    it.copy(
                        mid = mid,
                        name = name,
                        face = face ?: "",
                        // 切换到其他用户时先清空关注状态、内容栏目与旧列表，等待本次查询返回
                        isFollowing = if (sameMid) it.isFollowing else false,
                        section = if (sameMid) it.section else UserSpaceSection.Videos,
                        order = if (sameMid) it.order else SpaceVideoOrder.PubDate,
                        videos = if (sameMid) it.videos else emptyList(),
                        collections = if (sameMid) it.collections else emptyList(),
                        favFolders = if (sameMid) it.favFolders else emptyList(),
                        loading = false,
                        hasMore = true,
                        error = false,
                    )
                }
                loadContent(mid)
            }

            // 列表可复用，但关注状态每次进入都要刷新：用户可能刚从详情页关注/取关后返回本页
            viewModelScope.launch { refreshFollowStatus(mid) }
        }

        /**
         * 切换内容栏目（投稿/合集/系列）：清空当前列表并按新栏目重新加载。
         *
         * 同一栏目重复点击不触发刷新（重载走 [refresh]）。
         */
        fun selectSection(
            mid: Long,
            section: UserSpaceSection,
        ) {
            if (_uiState.value.section == section) return

            videoPage = SpaceVideoPage()
            collectionPage = SpaceCollectionPage()
            _uiState.update {
                it.copy(
                    section = section,
                    videos = emptyList(),
                    collections = emptyList(),
                    favFolders = emptyList(),
                    loading = false,
                    hasMore = true,
                    error = false,
                )
            }
            loadContent(mid)
        }

        /**
         * 切换投稿排序（最新发布/最多播放/最多收藏），清空投稿列表并重新加载。
         *
         * 仅投稿栏目生效；同一排序重复点击不触发刷新。
         */
        fun selectOrder(
            mid: Long,
            order: SpaceVideoOrder,
        ) {
            if (_uiState.value.section != UserSpaceSection.Videos) return
            if (_uiState.value.order == order) return

            videoPage = SpaceVideoPage()
            _uiState.update {
                it.copy(
                    order = order,
                    videos = emptyList(),
                    loading = false,
                    hasMore = true,
                    error = false,
                )
            }
            loadVideos(mid)
        }

        /** 加载当前栏目下一页内容（网格滚动到底部时触发）。 */
        fun loadMore(mid: Long) {
            loadContent(mid)
        }

        /** 重新加载当前栏目第一页（重试）。 */
        fun refresh(mid: Long) {
            videoPage = SpaceVideoPage()
            collectionPage = SpaceCollectionPage()
            _uiState.update {
                it.copy(
                    videos = emptyList(),
                    collections = emptyList(),
                    favFolders = emptyList(),
                    hasMore = true,
                    error = false,
                )
            }
            loadContent(mid)
        }

        private fun hasDisplayedContent(): Boolean {
            val state = _uiState.value
            return when (state.section) {
                UserSpaceSection.Videos -> state.videos.isNotEmpty()
                UserSpaceSection.Seasons, UserSpaceSection.Series -> state.collections.isNotEmpty()
                UserSpaceSection.Fav -> state.favFolders.isNotEmpty()
            }
        }

        private fun loadContent(mid: Long) {
            when (_uiState.value.section) {
                UserSpaceSection.Videos -> loadVideos(mid)
                UserSpaceSection.Seasons, UserSpaceSection.Series -> loadCollections(mid)
                UserSpaceSection.Fav -> loadFavFolders(mid)
            }
        }

        /**
         * 切换关注状态。
         *
         * 未关注时关注，已关注时取消关注。请求失败或接口返回非 0 码时
         * 通过 [uiEffect] 发出 Toast，并保持原状态不变。
         */
        fun toggleFollow() {
            val state = _uiState.value
            if (state.mid == 0L || state.followLoading) return

            val wasFollowing = state.isFollowing
            val preferApiType = Prefs.apiType
            _uiState.update { it.copy(followLoading = true) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        if (wasFollowing) {
                            userRepository.unfollowUser(mid = state.mid, preferApiType = preferApiType)
                        } else {
                            userRepository.followUser(mid = state.mid, preferApiType = preferApiType)
                        }
                    }
                }.onSuccess { success ->
                    if (success) {
                        _uiState.update { it.copy(isFollowing = !wasFollowing, followLoading = false) }
                    } else {
                        _uiState.update { it.copy(followLoading = false) }
                        _uiEffect.emit(
                            UserSpaceUiEffect.ShowToast(
                                "${if (wasFollowing) "取消关注" else "关注"}失败",
                            ),
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        throw error
                    }
                    logger.error(error) { "Failed to toggle follow" }
                    _uiState.update { it.copy(followLoading = false) }
                    _uiEffect.emit(
                        UserSpaceUiEffect.ShowToast(
                            "${if (wasFollowing) "取消关注" else "关注"}失败: ${error.message ?: "未知错误"}",
                        ),
                    )
                }
            }
        }

        /**
         * 拉取当前用户对 [mid] 的关注状态。
         *
         * 用户在本页手动切换关注期间（[UserSpaceUiState.followLoading] 为 true）忽略结果，
         * 避免慢查询返回后用旧值覆盖刚切换的状态。
         */
        private suspend fun refreshFollowStatus(mid: Long) {
            if (mid == 0L) return

            runCatching {
                userRepository.checkIsFollowing(mid)
            }.onSuccess { following ->
                if (!_uiState.value.followLoading) {
                    _uiState.update { it.copy(isFollowing = following ?: false) }
                }
            }.onFailure { error ->
                if (error is CancellationException && error !is TimeoutCancellationException) {
                    throw error
                }
                logger.error(error) { "Failed to fetch following status" }
            }
        }

        /** 按当前排序拉取投稿视频下一页。 */
        fun loadVideos(mid: Long) {
            if (_uiState.value.loading || !_uiState.value.hasMore) return

            val order = _uiState.value.order
            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceVideos(
                            mid = mid,
                            order = order,
                            page = videoPage,
                            preferApiType = Prefs.apiType,
                        )
                    }
                }.onSuccess { data ->
                    _uiState.update {
                        // App 通道排序回退 Web 时页码游标不同型，可能拿到重叠页，按 aid 去重防止 key 冲突
                        it.copy(
                            videos =
                                it.videos + data.videos.filterNot { v ->
                                    it.videos.any { v2 -> v2.aid == v.aid }
                                },
                            loading = false,
                            hasMore = data.page.hasNext,
                        )
                    }
                    videoPage = data.page
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load space videos" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }

        /** 拉取当前栏目的合集/系列列表下一页（合集与系列分别走专用端点）。 */
        fun loadCollections(mid: Long) {
            if (_uiState.value.loading || !_uiState.value.hasMore) return

            val isSeason = _uiState.value.section == UserSpaceSection.Seasons
            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceCollections(
                            mid = mid,
                            isSeason = isSeason,
                            page = collectionPage,
                            preferApiType = Prefs.apiType,
                        )
                    }
                }.onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            collections =
                                it.collections + data.collections.filterNot { c ->
                                    it.collections.any { c2 -> c2.id == c.id }
                                },
                            loading = false,
                            hasMore = data.page.hasNext,
                        )
                    }
                    collectionPage = data.page
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load space collections" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }

        /** 拉取公开收藏夹列表（list-all 一次全量，无分页）。 */
        fun loadFavFolders(mid: Long) {
            if (_uiState.value.loading || !_uiState.value.hasMore) return

            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceFavFolders(
                            mid = mid,
                            preferApiType = Prefs.apiType,
                        )
                    }
                }.onSuccess { folders ->
                    _uiState.update {
                        it.copy(
                            favFolders =
                                it.favFolders + folders.filterNot { f ->
                                    it.favFolders.any { f2 -> f2.id == f.id }
                                },
                            loading = false,
                            // 收藏夹一次全量返回，没有下一页
                            hasMore = false,
                        )
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load space fav folders" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }
    }
