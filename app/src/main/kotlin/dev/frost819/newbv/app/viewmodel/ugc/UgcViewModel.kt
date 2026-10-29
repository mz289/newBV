package dev.frost819.newbv.app.viewmodel.ugc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.ugc.UgcItem
import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedData
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedPage
import dev.frost819.newbv.biliapi.repositories.SearchFilterDuration
import dev.frost819.newbv.biliapi.repositories.SearchFilterOrderType
import dev.frost819.newbv.biliapi.repositories.UgcRepository
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * UGC 分区顶部导航项。
 *
 * 仅包含一级分区（有 channelId 的 [UgcTypeV2]）。
 *
 * @property ugcTypeV2 对应的 UGC 分区类型。
 * @property displayName 分区中文名，筛选搜索时兼作检索词。
 * @property searchTid 搜索接口（tids 参数）要求的旧版分区号，与 [UgcTypeV2.tid]（新版）不同。
 */
enum class UgcTabItem(
    val ugcTypeV2: UgcTypeV2,
    val displayName: String,
    val searchTid: Int,
) {
    Douga(UgcTypeV2.Douga, "动画", 1),
    Game(UgcTypeV2.Game, "游戏", 4),
    Kichiku(UgcTypeV2.Kichiku, "鬼畜", 22),
    Music(UgcTypeV2.Music, "音乐", 3),
    Dance(UgcTypeV2.Dance, "舞蹈", 129),
    Cinephile(UgcTypeV2.Cinephile, "影视", 181),
    Ent(UgcTypeV2.Ent, "娱乐", 5),
    Knowledge(UgcTypeV2.Knowledge, "知识", 36),
    Tech(UgcTypeV2.Tech, "科技数码", 188),
    Information(UgcTypeV2.Information, "资讯", 202),
    Food(UgcTypeV2.Food, "美食", 76),
    Car(UgcTypeV2.Car, "汽车", 223),
    Fashion(UgcTypeV2.Fashion, "时尚美妆", 155),
    Sports(UgcTypeV2.Sports, "体育运动", 234),
    Animal(UgcTypeV2.Animal, "动物", 217),
}

/**
 * UGC 分区 UI 状态。
 *
 * @property items 当前分区的视频列表。
 * @property loading 是否正在加载。
 * @property hasMore 是否还有更多数据。
 * @property error 是否加载失败。
 * @property selectedOrder 筛选排序方式。
 * @property selectedDuration 筛选时长。
 * @property showFilter 是否显示筛选弹窗。
 */
data class UgcUiState(
    val items: List<UgcItem> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: Boolean = false,
    val selectedOrder: SearchFilterOrderType = SearchFilterOrderType.ComprehensiveSort,
    val selectedDuration: SearchFilterDuration = SearchFilterDuration.All,
    val showFilter: Boolean = false,
) {
    /** 是否处于筛选态（排序或时长任一非默认），此时数据源切换为分区搜索接口。 */
    val isFiltered: Boolean
        get() =
            selectedOrder != SearchFilterOrderType.ComprehensiveSort ||
                selectedDuration != SearchFilterDuration.All
}

/**
 * UGC 分区 ViewModel。
 *
 * 管理分区顶部导航切换和数据加载。默认走分区推荐信息流；筛选排序/时长后
 * 切换为分区搜索接口（同哔哩合辑站源方案），并按 aid 去重追加。
 *
 * @property ugcRepository UGC 分区数据仓库。
 */
@HiltViewModel
class UgcViewModel
    @Inject
    constructor(
        private val ugcRepository: UgcRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("UgcViewModel")

        private val _uiState = MutableStateFlow(UgcUiState())
        val uiState: StateFlow<UgcUiState> = _uiState.asStateFlow()

        /** 当前选中的分区 Tab。 */
        private var currentTab: UgcTabItem = UgcTabItem.Douga

        /** 当前分区的下一页游标（推荐流为 display-id，搜索流为页码，均从 1 递增）。 */
        private var nextPage: UgcFeedPage = UgcFeedPage()

        init {
            loadMore()
        }

        /**
         * 切换到指定分区。
         *
         * 如果该分区已有数据则不重新加载，否则触发首次加载。筛选条件跨分区保留。
         *
         * @param tab 目标分区。
         */
        fun switchType(tab: UgcTabItem) {
            if (tab == currentTab && _uiState.value.items.isNotEmpty()) return
            currentTab = tab
            nextPage = UgcFeedPage()
            _uiState.value = _uiState.value.copy(items = emptyList(), hasMore = true, error = false)
            loadMore()
        }

        /**
         * 加载更多当前分区视频。
         *
         * 筛选态走搜索接口，默认态走分区推荐流。搜索分页可能出现跨页重复，
         * 追加前按 aid 去重；去重后无新数据即视为没有更多。
         */
        fun loadMore() {
            val current = _uiState.value
            if (current.loading || !current.hasMore) return

            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = false) }

                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        val data: UgcFeedData =
                            if (current.isFiltered) {
                                ugcRepository.getRegionFeedBySearch(
                                    searchTid = currentTab.searchTid,
                                    keyword = currentTab.displayName,
                                    order = current.selectedOrder,
                                    duration = current.selectedDuration,
                                    page = nextPage,
                                )
                            } else {
                                ugcRepository.getRegionFeedRcmd(
                                    ugcType = currentTab.ugcTypeV2,
                                    page = nextPage,
                                )
                            }
                        val existingAids = current.items.mapTo(mutableSetOf()) { it.aid }
                        val newItems = data.items.filter { it.aid !in existingAids }.distinctBy { it.aid }
                        nextPage = data.nextPage
                        // 搜索流翻页可能整页重复，去重后无新增即停止；推荐流保持原判断
                        val hasMore =
                            if (current.isFiltered) data.hasNext && newItems.isNotEmpty() else data.hasNext
                        _uiState.update {
                            it.copy(
                                items = it.items + newItems,
                                hasMore = hasMore,
                            )
                        }
                    }
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    logger.error(error) { "Failed to load UGC region: $currentTab" }
                    _uiState.update { it.copy(error = true) }
                }

                _uiState.update { it.copy(loading = false) }
            }
        }

        /**
         * 应用筛选条件并重新加载当前分区。
         *
         * 筛选恢复为默认（综合排序 + 全部时长）时回到分区推荐流。
         */
        fun updateFilter(
            order: SearchFilterOrderType,
            duration: SearchFilterDuration,
        ) {
            val unchanged =
                order == _uiState.value.selectedOrder && duration == _uiState.value.selectedDuration
            _uiState.update {
                it.copy(selectedOrder = order, selectedDuration = duration, showFilter = false)
            }
            if (unchanged) return
            refresh()
        }

        /** 显示/隐藏筛选弹窗。 */
        fun toggleFilter(show: Boolean) {
            _uiState.update { it.copy(showFilter = show) }
        }

        /**
         * 刷新当前分区数据。
         */
        fun refresh() {
            nextPage = UgcFeedPage()
            _uiState.value = _uiState.value.copy(items = emptyList(), hasMore = true, error = false)
            loadMore()
        }
    }
