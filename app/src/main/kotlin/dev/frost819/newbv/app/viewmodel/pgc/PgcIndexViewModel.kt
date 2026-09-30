package dev.frost819.newbv.app.viewmodel.pgc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.pgc.PgcItem
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.index.Area
import dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
import dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
import dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
import dev.frost819.newbv.biliapi.entity.pgc.index.Producer
import dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
import dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.pgc.index.Year
import dev.frost819.newbv.biliapi.repositories.PgcRepository
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * 番剧索引筛选页 UI 状态。
 *
 * @property order 排序方式。
 * @property orderType 排序方向（当前固定降序，未提供切换行）。
 * @property seasonVersion 类型（正片/电影等）。
 * @property spokenLanguage 配音。
 * @property area 地区。
 * @property isFinish 完结状态。
 * @property copyright 版权。
 * @property seasonStatus 付费状态。
 * @property seasonMonth 季度（番剧为 1/4/7/10 月）。
 * @property producer 出品方（纪录片维度，番剧恒为全部）。
 * @property year 年份。
 * @property style 风格（番剧页入口 chip 预选）。
 * @property items 筛选结果。
 * @property totalSize 命中总数（首页返回后更新）。
 * @property loading 是否正在加载。
 * @property hasMore 是否还有更多。
 * @property error 最近一次加载是否失败。
 * @property loaded 是否完成过首次加载。
 */
data class PgcIndexUiState(
    val order: IndexOrder,
    val orderType: IndexOrderType = IndexOrderType.Desc,
    val seasonVersion: SeasonVersion = SeasonVersion.All,
    val spokenLanguage: SpokenLanguage = SpokenLanguage.All,
    val area: Area = Area.All,
    val isFinish: IsFinish = IsFinish.All,
    val copyright: Copyright = Copyright.All,
    val seasonStatus: SeasonStatus = SeasonStatus.All,
    val seasonMonth: SeasonMonth = SeasonMonth.All,
    val producer: Producer = Producer.All,
    val year: Year = Year.All,
    val style: Style = Style.All,
    val items: List<PgcItem> = emptyList(),
    val totalSize: Int = 0,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * 番剧索引筛选页 ViewModel。
 *
 * 从 [dev.frost819.newbv.app.ui.navigation.PgcIndexRoute] 读取分区与预选风格，
 * 支持排序/风格/地区/年份等多维度筛选 + 页码分页。
 * 切换筛选条件会重置分页并重新加载；旧请求的结果按请求序号丢弃，避免污染新筛选。
 *
 * @param pgcRepository PGC 数据仓库。
 * @param savedStateHandle 导航路由参数。
 */
@HiltViewModel
class PgcIndexViewModel
    @Inject
    constructor(
        private val pgcRepository: PgcRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val logger = Loggers.get("PgcIndexViewModel")

        /** 索引分区（当前入口仅番剧页，保留扩展能力）。 */
        val pgcType: PgcType =
            savedStateHandle.get<String>("pgcTypeName")?.let { name ->
                PgcType.entries.firstOrNull { it.name == name }
            } ?: PgcType.Anime

        private var page = PgcIndexData.PgcIndexPage()

        /** 请求序号：筛选条件变化后旧请求结果作废。 */
        private var requestSeq = 0

        private val _uiState =
            MutableStateFlow(
                PgcIndexUiState(
                    order = IndexOrder.getList(pgcType).first(),
                    style =
                        savedStateHandle.get<Int>("styleId")?.let { id ->
                            Style.entries.firstOrNull { it.id == id }
                        } ?: Style.All,
                ),
            )
        val uiState: StateFlow<PgcIndexUiState> = _uiState.asStateFlow()

        /** 首次进入时懒加载。 */
        fun loadIfNeeded() {
            val current = _uiState.value
            if (current.loaded || current.loading) return
            loadMore()
        }

        fun setOrder(value: IndexOrder) = setFilter { it.copy(order = value) }

        fun setStyle(value: Style) = setFilter { it.copy(style = value) }

        fun setArea(value: Area) = setFilter { it.copy(area = value) }

        fun setYear(value: Year) = setFilter { it.copy(year = value) }

        fun setIsFinish(value: IsFinish) = setFilter { it.copy(isFinish = value) }

        fun setSeasonStatus(value: SeasonStatus) = setFilter { it.copy(seasonStatus = value) }

        fun setSeasonVersion(value: SeasonVersion) = setFilter { it.copy(seasonVersion = value) }

        fun setSpokenLanguage(value: SpokenLanguage) = setFilter { it.copy(spokenLanguage = value) }

        fun setSeasonMonth(value: SeasonMonth) = setFilter { it.copy(seasonMonth = value) }

        fun setCopyright(value: Copyright) = setFilter { it.copy(copyright = value) }

        private fun setFilter(update: (PgcIndexUiState) -> PgcIndexUiState) {
            _uiState.update(update)
            requestSeq += 1
            page = PgcIndexData.PgcIndexPage()
            _uiState.update { it.copy(items = emptyList(), loading = false, hasMore = true, error = false) }
            loadMore()
        }

        /**
         * 加载下一页。筛选条件变化后，旧序号请求的结果会被丢弃。
         */
        fun loadMore() {
            val current = _uiState.value
            if (current.loading || !current.hasMore) return

            val requestId = requestSeq
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = false) }

                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        pgcRepository.getPgcIndex(
                            pgcType = pgcType,
                            indexOrder = current.order,
                            indexOrderType = current.orderType,
                            seasonVersion = current.seasonVersion,
                            spokenLanguage = current.spokenLanguage,
                            area = current.area,
                            isFinish = current.isFinish,
                            copyright = current.copyright,
                            seasonStatus = current.seasonStatus,
                            seasonMonth = current.seasonMonth,
                            producer = current.producer,
                            year = current.year,
                            releaseDate = ReleaseDate.All,
                            style = current.style,
                            page = page,
                        )
                    }
                }.onSuccess { data ->
                    if (requestId != requestSeq) return@onSuccess
                    page = data.nextPage
                    _uiState.update {
                        it.copy(
                            items = it.items + data.list,
                            totalSize = data.nextPage.totalSize,
                            hasMore = data.nextPage.hasNext,
                            loaded = true,
                        )
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load PGC index: $pgcType" }
                    if (requestId == requestSeq) {
                        _uiState.update { it.copy(error = true) }
                    }
                }

                if (requestId == requestSeq) {
                    _uiState.update { it.copy(loading = false) }
                }
            }
        }
    }
