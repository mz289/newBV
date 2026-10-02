package dev.frost819.newbv.app.viewmodel.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.PgcWebPage
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

/**
 * PGC 影视分区（番剧外的 5 个 Tab）单个分区的 UI 状态。
 *
 * 板块列表完全由接口下发（标题/样式/条目），页面按板块数据驱动渲染、
 * 非空才显示；时间表看板的条目数据来自时间表接口（国创有数据）。
 *
 * @property loading 是否正在首次加载。
 * @property loaded 是否已完成过一次加载（用于懒加载判断）。
 * @property error 分区页数据加载是否失败。
 * @property carouselItems 轮播图。
 * @property modules 服务端下发的板块列表（保持接口顺序，空板块由页面剔除）。
 * @property indexGroups 索引快捷筛选分组（风格组驱动索引 chips 行）。
 * @property timeline 放送时间表（仅国创有数据，供时间表板块渲染看板）。
 * @property timelineError 时间表加载是否失败。
 */
data class PgcHomeUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: Boolean = false,
    val carouselItems: List<CarouselData.CarouselItem> = emptyList(),
    val modules: List<PgcWebPage.WebModule> = emptyList(),
    val indexGroups: List<PgcWebPage.IndexGroup> = emptyList(),
    val timeline: List<Timeline> = emptyList(),
    val timelineError: Boolean = false,
)

/**
 * PGC 影视分区 ViewModel（国创/电影/纪录片/电视剧/综艺 5 个 Tab 共用）。
 *
 * 与番剧页同构的富布局数据源：分区页数据（轮播图/服务端板块/索引分组）
 * 由 `__INITIAL_STATE__` 一次取得，国创额外聚合放送时间表。
 * 状态按 [PgcType] 隔离持有，切 Tab 不丢已加载数据；板块数据与时间表
 * 独立捕获失败，互不连带取消。
 *
 * @param pgcRepository PGC 数据仓库。
 * @param seasonRepository 时间表仓库。
 */
@HiltViewModel
class PgcViewModel
    @Inject
    constructor(
        private val pgcRepository: PgcRepository,
        private val seasonRepository: SeasonRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("PgcViewModel")

        private val _uiStates = MutableStateFlow<Map<PgcType, PgcHomeUiState>>(emptyMap())
        val uiStates: StateFlow<Map<PgcType, PgcHomeUiState>> = _uiStates.asStateFlow()

        /** 读取指定分区状态；未加载过时返回默认空状态。 */
        fun uiStateFor(type: PgcType): PgcHomeUiState = _uiStates.value[type] ?: PgcHomeUiState()

        /**
         * 首次展示对应分区时懒加载；已加载或正在加载则跳过。
         */
        fun loadIfNeeded(type: PgcType) {
            val state = uiStateFor(type)
            if (state.loaded || state.loading) return
            load(type)
        }

        /**
         * 刷新指定分区全部板块（菜单键）。
         */
        fun refresh(type: PgcType) {
            if (uiStateFor(type).loading) return
            load(type)
        }

        private fun load(type: PgcType) {
            updateState(type) { it.copy(loading = true, error = false) }
            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        coroutineScope {
                            listOf(
                                async { loadWebPage(type) },
                                async { loadTimelineIfSupported(type) },
                            ).awaitAll()
                        }
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load pgc home: $type" }
                }
                updateState(type) { it.copy(loading = false, loaded = true) }
            }
        }

        /** 分区页数据：核心数据，失败置 error。 */
        private suspend fun loadWebPage(type: PgcType) {
            runCatching {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val page = pgcRepository.getPgcWebPage(type)
                    updateState(type) {
                        it.copy(
                            carouselItems = page.banner,
                            modules = page.modules,
                            indexGroups = page.indexGroups,
                        )
                    }
                }
            }.onFailure { error ->
                error.rethrowUnlessTimeout()
                logger.error(error) { "Failed to load pgc web page: $type" }
                updateState(type) { it.copy(error = true) }
            }
        }

        /**
         * 放送时间表：仅国创拉取（时间表接口的分区过滤只覆盖番剧/国创，
         * 其余分区无有效数据，对应板块也不会下发）；失败独立标记，不置全局 error。
         */
        private suspend fun loadTimelineIfSupported(type: PgcType) {
            if (type != PgcType.GuoChuang) return
            runCatching {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val timeline =
                        seasonRepository.getTimeline(
                            filter = TimelineFilter.GuoChuang,
                            preferApiType = Prefs.apiType,
                        )
                    updateState(type) { it.copy(timeline = timeline, timelineError = false) }
                }
            }.onFailure { error ->
                error.rethrowUnlessTimeout()
                logger.error(error) { "Failed to load guochuang timeline" }
                updateState(type) { it.copy(timelineError = true) }
            }
        }

        private fun updateState(
            type: PgcType,
            transform: (PgcHomeUiState) -> PgcHomeUiState,
        ) {
            _uiStates.update { current ->
                current + (type to transform(current[type] ?: PgcHomeUiState()))
            }
        }
    }
