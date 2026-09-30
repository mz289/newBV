package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.InfiniteScrollEffect
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.POSTER_CARD_MIN_WIDTH
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonSeasonCard
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videocard.SeasonCard
import dev.frost819.newbv.app.ui.component.videocard.SeasonCardData
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.ui.navigation.PgcIndexRoute
import dev.frost819.newbv.app.viewmodel.pgc.PgcIndexUiState
import dev.frost819.newbv.app.viewmodel.pgc.PgcIndexViewModel
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.index.Area
import dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
import dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
import dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexParam
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
import dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.pgc.index.Year
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.touchClickable
import androidx.compose.material3.Scaffold as Material3Scaffold

/** 结果网格前的表头 item 数量（10 个筛选行 + 1 个命中数行），用于无限滚动阈值换算。 */
private const val INDEX_HEADER_ITEM_COUNT = 11

/**
 * 番剧索引筛选页路由注册。
 *
 * [PgcIndexViewModel] 通过 SavedStateHandle 读取 [PgcIndexRoute.pgcTypeName]/[PgcIndexRoute.styleId]。
 */
fun NavGraphBuilder.pgcIndexScreen(navController: NavController) {
    composable<PgcIndexRoute> {
        PgcIndexScreen(navController = navController)
    }
}

/**
 * 番剧索引筛选页。
 *
 * 顶栏标题 + 多维度筛选行（排序/风格/地区/年份/状态/付费/版本/
 * 配音/月度/版权）+ 6 列结果网格 + 无限滚动。菜单键等同于返回。
 *
 * @param navController 导航控制器。
 */
@Composable
private fun PgcIndexScreen(navController: NavController) {
    val viewModel: PgcIndexViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusSaver = rememberFocusSaver()
    focusSaver.RestoreFocus()

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    Material3Scaffold(
        topBar = {
            Text(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                text = indexTitle(viewModel.pgcType),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) { innerPadding ->
        IndexBody(
            modifier = Modifier.padding(innerPadding),
            state = state,
            viewModel = viewModel,
            focusSaver = focusSaver,
            navController = navController,
        )
    }
}

/**
 * 索引页主体：筛选区 + 结果网格共用一个网格容器，随内容纵向滚动。
 */
@Composable
private fun IndexBody(
    modifier: Modifier = Modifier,
    state: PgcIndexUiState,
    viewModel: PgcIndexViewModel,
    focusSaver: FocusSaver,
    navController: NavController,
) {
    val gridState = rememberLazyGridState()

    // 距离底部 10 条时触发加载更多
    InfiniteScrollEffect(
        state = gridState,
        itemCount = { INDEX_HEADER_ITEM_COUNT + state.items.size },
        threshold = 10,
        onLoadMore = { viewModel.loadMore() },
    )

    TvLazyVerticalGrid(
        modifier =
            modifier.onPreviewKeyEvent { event ->
                if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                    navController.popBackStack()
                    return@onPreviewKeyEvent true
                }
                false
            },
        state = gridState,
        columns = GridCells.Adaptive(POSTER_CARD_MIN_WIDTH),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_order") {
            IndexFilterRow(
                spec = IndexFilterSpec("排序", IndexOrder.getList(viewModel.pgcType), state.order, viewModel::setOrder),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_style") {
            IndexFilterRow(
                spec = IndexFilterSpec("风格", Style.getList(viewModel.pgcType), state.style, viewModel::setStyle),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_area") {
            IndexFilterRow(
                spec = IndexFilterSpec("地区", Area.getList(viewModel.pgcType), state.area, viewModel::setArea),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_year") {
            IndexFilterRow(
                spec = IndexFilterSpec("年份", Year.getList(viewModel.pgcType), state.year, viewModel::setYear),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_is_finish") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "状态",
                        IsFinish.getList(viewModel.pgcType),
                        state.isFinish,
                        viewModel::setIsFinish,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_status") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "付费",
                        SeasonStatus.getList(viewModel.pgcType),
                        state.seasonStatus,
                        viewModel::setSeasonStatus,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_version") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "版本",
                        SeasonVersion.getList(viewModel.pgcType),
                        state.seasonVersion,
                        viewModel::setSeasonVersion,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_language") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "配音",
                        SpokenLanguage.getList(viewModel.pgcType),
                        state.spokenLanguage,
                        viewModel::setSpokenLanguage,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_month") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "月度",
                        SeasonMonth.getList(viewModel.pgcType),
                        state.seasonMonth,
                        viewModel::setSeasonMonth,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_copyright") {
            IndexFilterRow(
                spec =
                    IndexFilterSpec(
                        "版权",
                        Copyright.getList(viewModel.pgcType),
                        state.copyright,
                        viewModel::setCopyright,
                    ),
                focusSaver = focusSaver,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "index_summary") {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = if (state.totalSize > 0) "共 ${state.totalSize} 部" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        itemsIndexed(
            items = state.items,
            key = { _, item -> "index_result_${item.seasonId}" },
        ) { _, item ->
            SeasonCard(
                data =
                    SeasonCardData(
                        seasonId = item.seasonId,
                        title = item.title,
                        subTitle = item.subTitle,
                        cover = item.cover,
                        rating = item.rating,
                    ),
                onClick = {
                    navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                },
                onGoToDetailPage = {
                    navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                },
                modifier = Modifier.focusSaverItem(focusSaver, "index_result_${item.seasonId}"),
            )
        }

        if (!state.loaded && state.loading) {
            // 首屏加载中：同构骨架屏占位（P0-4）
            items(SKELETON_FIRST_SCREEN_COUNT) { SkeletonSeasonCard() }
        }

        if (state.loaded && !state.loading && !state.error && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "index_empty") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有找到符合条件的番剧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (state.loaded) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "index_footer") {
                ListFooterTip(
                    isLoading = state.loading,
                    isError = state.error,
                    hasMore = state.hasMore,
                    itemsIsEmpty = state.items.isEmpty(),
                    onRetry = viewModel::loadMore,
                )
            }
        }
    }
}

/**
 * 筛选维度描述。
 *
 * @param T 筛选枚举类型。
 * @property label 维度名（排序/风格/地区…）。
 * @property options 可选项。
 * @property selected 当前选中项。
 * @property onSelect 选中回调。
 */
private data class IndexFilterSpec<T : PgcIndexParam>(
    val label: String,
    val options: List<T>,
    val selected: T,
    val onSelect: (T) -> Unit,
)

/**
 * 单个筛选维度行：维度名 + 横向滚动 chip 列表。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T : PgcIndexParam> IndexFilterRow(
    spec: IndexFilterSpec<T>,
    focusSaver: FocusSaver,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(56.dp),
            text = spec.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier =
                Modifier
                    .weight(1f)
                    .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(spec.options.size, key = { index -> index }) { index ->
                val option = spec.options[index]
                FilterChip(
                    selected = option == spec.selected,
                    onClick = { spec.onSelect(option) },
                    modifier =
                        Modifier
                            .touchClickable(onClick = { spec.onSelect(option) })
                            .focusSaverItem(focusSaver, "index_filter_${spec.label}_$index"),
                    shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                    scale = FilterChipDefaults.scale(focusedScale = 1f),
                    colors = ControlFocusDefaults.filterColors(),
                    border = ControlFocusDefaults.filterBorder(),
                ) {
                    Text(
                        text = option.label,
                        color =
                            if (option == spec.selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

/** 索引页标题。 */
private fun indexTitle(pgcType: PgcType): String =
    when (pgcType) {
        PgcType.Anime -> "番剧索引"
        PgcType.GuoChuang -> "国创索引"
        PgcType.Movie -> "电影索引"
        PgcType.Documentary -> "纪录片索引"
        PgcType.Tv -> "电视剧索引"
        PgcType.Variety -> "综艺索引"
    }
