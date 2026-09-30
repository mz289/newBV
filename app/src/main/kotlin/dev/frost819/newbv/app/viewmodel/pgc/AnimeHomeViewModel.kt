package dev.frost819.newbv.app.viewmodel.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.pgc.PgcPageTab
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.season.Timeline
import dev.frost819.newbv.biliapi.entity.season.TimelineFilter
import dev.frost819.newbv.biliapi.repositories.PgcRepository
import dev.frost819.newbv.biliapi.repositories.SeasonRepository
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** 番剧页「番剧热播榜」展示的条数。 */
private const val RANK_ITEMS_LIMIT = 10

/** double_feed（猜你喜欢）模块的 style 标识。 */
private const val STYLE_DOUBLE_FEED = "double_feed"

/**
 * 番剧页（PGC 番剧 Tab）UI 状态。
 *
 * @property loading 是否正在加载。
 * @property loaded 是否已完成过一次加载（用于懒加载判断）。
 * @property error 核心数据（模块/热播榜）加载是否失败。
 * @property pageModules 番剧页模块（接口返回顺序，空模块已剔除），
 * 页面按模块 style 数据驱动渲染。
 * @property rankItems 番剧热播榜。
 * @property timeline 新番时间表（API 返回今天前后各 7 天，共 15 天）。
 * @property timelineError 时间表加载是否失败。
 */
data class AnimeHomeUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: Boolean = false,
    val pageModules: List<PgcPageTab.Module> = emptyList(),
    val rankItems: List<PgcRankData.Item> = emptyList(),
    val timeline: List<Timeline> = emptyList(),
    val timelineError: Boolean = false,
)

/**
 * 番剧页 ViewModel。
 *
 * 主体数据来自番剧页模块化接口（`/pgc/page/pc/bangumi/tab`）：我的追番、
 * 番剧/国创推荐、猜你喜欢等模块按接口语义整体下发，页面随模块数据驱动渲染；
 * 另聚合番剧热播榜与新番时间表。切换 Tab 状态保留。
 *
 * @param pgcRepository PGC 数据仓库。
 * @param seasonRepository 时间表仓库。
 */
@HiltViewModel
class AnimeHomeViewModel
    @Inject
    constructor(
        private val pgcRepository: PgcRepository,
        private val seasonRepository: SeasonRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("AnimeHomeViewModel")

        private val _uiState = MutableStateFlow(AnimeHomeUiState())
        val uiState: StateFlow<AnimeHomeUiState> = _uiState.asStateFlow()

        /** 猜你喜欢下一页游标；null 表示无更多或未加载。 */
        private var guessCursor: String? = null

        private var guessLoading = false

        /**
         * 首次进入时懒加载；已加载或正在加载则跳过。
         */
        fun loadIfNeeded() {
            val current = _uiState.value
            if (current.loaded || current.loading) return
            loadAll(isRefresh = 0)
        }

        /**
         * 刷新全部板块（菜单键），is_refresh=1 更换推荐内容。
         */
        fun refreshAll() {
            if (_uiState.value.loading) return
            loadAll(isRefresh = 1)
        }

        /**
         * 猜你喜欢翻页：用已存游标请求下一页，条目去重后追加进 double_feed 模块
         * （对齐 wiliwili 的 onNextPage 语义）。
         */
        fun loadMoreGuess() {
            val cursor = guessCursor ?: return
            if (guessLoading) return
            guessLoading = true
            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        val data = pgcRepository.getPgcPageTab(cursor = cursor)
                        guessCursor = data.nextCursor.takeIf { data.hasNext }
                        _uiState.update { state ->
                            state.copy(
                                pageModules =
                                    state.pageModules.map { module ->
                                        if (module.style != STYLE_DOUBLE_FEED) {
                                            module
                                        } else {
                                            val seen = module.items.mapTo(mutableSetOf()) { it.seasonId }
                                            module.copy(
                                                items =
                                                    module.items +
                                                        data.modules
                                                            .filter { it.style == STYLE_DOUBLE_FEED }
                                                            .flatMap { it.items }
                                                            .filter { seen.add(it.seasonId) },
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load more guess feed" }
                }
                guessLoading = false
            }
        }

        private fun loadAll(isRefresh: Int) {
            _uiState.update { it.copy(loading = true, error = false) }
            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        coroutineScope {
                            listOf(
                                async { loadPageTab(isRefresh) },
                                async { loadRank() },
                                async { loadTimeline() },
                            ).awaitAll()
                        }
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load anime home" }
                    _uiState.update { it.copy(error = true) }
                }
                _uiState.update { it.copy(loading = false, loaded = true) }
            }
        }

        /**
         * 番剧页模块：核心数据，失败置 [AnimeHomeUiState.error]；自捕获异常以保证
         * 并行的热播榜/时间表加载不被协程结构化并发连带取消。空模块剔除（未登录的
         * 我的追番等），与 wiliwili 的空模块跳过语义一致。
         */
        private suspend fun loadPageTab(isRefresh: Int) {
            runCatching {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val data = pgcRepository.getPgcPageTab(isRefresh = isRefresh)
                    guessCursor = data.nextCursor.takeIf { data.hasNext }
                    _uiState.update {
                        it.copy(pageModules = data.modules.filter { module -> module.items.isNotEmpty() })
                    }
                }
            }.onFailure { error ->
                error.rethrowUnlessTimeout()
                logger.error(error) { "Failed to load pgc page modules" }
                _uiState.update { it.copy(error = true) }
            }
        }

        /**
         * 番剧热播榜：失败置 error；自捕获异常保证并行加载不被连带取消。
         */
        private suspend fun loadRank() {
            runCatching {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val data = pgcRepository.getPgcRankList(PgcType.Anime)
                    _uiState.update { it.copy(rankItems = data.items.take(RANK_ITEMS_LIMIT)) }
                }
            }.onFailure { error ->
                error.rethrowUnlessTimeout()
                logger.error(error) { "Failed to load pgc rank list" }
                _uiState.update { it.copy(error = true) }
            }
        }

        /** 时间表加载失败独立标记，不置全局 error。 */
        private suspend fun loadTimeline() {
            runCatching {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val timeline =
                        seasonRepository.getTimeline(
                            filter = TimelineFilter.Anime,
                            preferApiType = Prefs.apiType,
                        )
                    _uiState.update { it.copy(timeline = timeline, timelineError = false) }
                }
            }.onFailure { error ->
                error.rethrowUnlessTimeout()
                logger.error(error) { "Failed to load anime timeline" }
                _uiState.update { it.copy(timelineError = true) }
            }
        }
    }
