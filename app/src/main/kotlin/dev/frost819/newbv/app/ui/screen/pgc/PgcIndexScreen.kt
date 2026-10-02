package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * 顶栏标题 + 筛选区（排序/风格/地区/年份常驻，状态/付费/版本/配音/月度/版权
 * 折叠进「更多筛选」开关） + 6 列结果网格 + 无限滚动。菜单键等同于返回。
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

    // 次要筛选维度默认折叠，让结果网格进首屏；展开状态跨重建保留
    var filterExpanded by rememberSaveable { mutableStateOf(false) }
    val filterLayout = indexFilterLayout(state, viewModel, filterExpanded)

    // 距离底部 10 条时触发加载更多
    InfiniteScrollEffect(
        state = gridState,
        itemCount = { filterLayout.headerItemCount + state.items.size },
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
        filterLayout.fixedRows.forEach { spec ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_${spec.key}") {
                IndexFilterRow(spec = spec, focusSaver = focusSaver)
            }
        }
        if (filterLayout.toggleVisible) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_toggle") {
                IndexFilterToggleRow(
                    expanded = filterExpanded,
                    hiddenCount = filterLayout.hiddenSecondaryCount,
                    onToggle = { filterExpanded = !filterExpanded },
                    focusSaver = focusSaver,
                )
            }
        }
        filterLayout.secondaryRows.forEach { spec ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "index_filter_${spec.key}") {
                IndexFilterRow(spec = spec, focusSaver = focusSaver)
            }
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
 * 索引筛选区布局结果。
 *
 * @property fixedRows 常驻维度行（排序/风格/地区/年份，分区无该维度时剔除）。
 * @property secondaryRows 当前需渲染的次要维度行（展开态全部显示；收起态仅保留
 *   有非默认选中的行，提示结果被过滤的原因；可折叠维度不足 2 个时并入常驻行，此处为空）。
 * @property toggleVisible 是否显示「更多筛选」折叠开关行。
 * @property hiddenSecondaryCount 收起状态下被隐藏的次要维度数，用于开关文案提示。
 */
private data class IndexFilterLayout(
    val fixedRows: List<IndexFilterSpec>,
    val secondaryRows: List<IndexFilterSpec>,
    val toggleVisible: Boolean,
    val hiddenSecondaryCount: Int,
) {
    /** 结果网格前的表头 item 数（维度行 + 开关行 + 命中数行），供无限滚动阈值换算。 */
    val headerItemCount: Int
        get() = fixedRows.size + secondaryRows.size + (if (toggleVisible) 1 else 0) + 1
}

/**
 * 组装索引筛选区布局：前 4 个常用维度（排序/风格/地区/年份）常驻，
 * 其余低频维度（状态/付费/版本/配音/月度/版权）折叠进「更多筛选」；
 * 选项为空的维度不渲染（电影/纪录片等分区本身没有这些维度）。
 */
private fun indexFilterLayout(
    state: PgcIndexUiState,
    viewModel: PgcIndexViewModel,
    expanded: Boolean,
): IndexFilterLayout {
    val pgcType = viewModel.pgcType

    val fixedRows =
        listOf(
            filterSpec("order", "排序", IndexOrder.getList(pgcType), state.order, viewModel::setOrder),
            filterSpec("style", "风格", Style.getList(pgcType), state.style, viewModel::setStyle),
            filterSpec("area", "地区", Area.getList(pgcType), state.area, viewModel::setArea),
            filterSpec("year", "年份", Year.getList(pgcType), state.year, viewModel::setYear),
        ).filter { it.options.isNotEmpty() }

    // 次要维度及是否处于非默认选中：收起时仍保留非默认选中的行，避免用户忘记已生效的筛选条件
    val secondaryCandidates =
        listOf(
            filterSpec(
                "is_finish",
                "状态",
                IsFinish.getList(pgcType),
                state.isFinish,
                viewModel::setIsFinish,
            ) to (state.isFinish != IsFinish.All),
            filterSpec(
                "status",
                "付费",
                SeasonStatus.getList(pgcType),
                state.seasonStatus,
                viewModel::setSeasonStatus,
            ) to (state.seasonStatus != SeasonStatus.All),
            filterSpec(
                "version",
                "版本",
                SeasonVersion.getList(pgcType),
                state.seasonVersion,
                viewModel::setSeasonVersion,
            ) to (state.seasonVersion != SeasonVersion.All),
            filterSpec(
                "language",
                "配音",
                SpokenLanguage.getList(pgcType),
                state.spokenLanguage,
                viewModel::setSpokenLanguage,
            ) to (state.spokenLanguage != SpokenLanguage.All),
            filterSpec(
                "month",
                "月度",
                SeasonMonth.getList(pgcType),
                state.seasonMonth,
                viewModel::setSeasonMonth,
            ) to (state.seasonMonth != SeasonMonth.All),
            filterSpec(
                "copyright",
                "版权",
                Copyright.getList(pgcType),
                state.copyright,
                viewModel::setCopyright,
            ) to (state.copyright != Copyright.All),
        )
    val secondary = secondaryCandidates.filter { it.first.options.isNotEmpty() }

    // 可折叠的次要维度不足 2 个时，折叠省不下空间，直接全部常驻
    if (secondary.size < 2) {
        return IndexFilterLayout(
            fixedRows = fixedRows + secondary.map { it.first },
            secondaryRows = emptyList(),
            toggleVisible = false,
            hiddenSecondaryCount = 0,
        )
    }
    return IndexFilterLayout(
        fixedRows = fixedRows,
        secondaryRows = secondary.filter { expanded || it.second }.map { it.first },
        toggleVisible = true,
        hiddenSecondaryCount = if (expanded) 0 else secondary.count { !it.second },
    )
}

/**
 * 筛选维度描述（渲染用，类型经 [filterSpec] 擦除）。
 *
 * @property key 稳定标识（网格 item key）。
 * @property label 维度名（排序/风格/地区…）。
 * @property options 可选项。
 * @property selected 当前选中项。
 * @property onSelect 选中回调，只会收到 [options] 中的实例。
 */
private class IndexFilterSpec(
    val key: String,
    val label: String,
    val options: List<PgcIndexParam>,
    val selected: PgcIndexParam,
    val onSelect: (PgcIndexParam) -> Unit,
)

/**
 * 构造筛选维度描述并擦除类型：[options] 元素与 [onSelect] 参数同型，
 * 回调入参必然来自 [options]，擦除后的强转不会失败。
 */
@Suppress("UNCHECKED_CAST")
private fun <T : PgcIndexParam> filterSpec(
    key: String,
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
): IndexFilterSpec = IndexFilterSpec(key, label, options, selected) { onSelect(it as T) }

/**
 * 单个筛选维度行：维度名 + 横向滚动 chip 列表。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IndexFilterRow(
    spec: IndexFilterSpec,
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

/**
 * 「更多筛选」折叠开关行：空出维度名列宽与维度行左对齐。
 *
 * 收起时以普通 chip 样式标注被隐藏的维度数；展开后用选中底色提示当前处于展开态。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IndexFilterToggleRow(
    expanded: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
    focusSaver: FocusSaver,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(56.dp))
        val label =
            when {
                expanded -> "收起筛选"
                hiddenCount > 0 -> "更多筛选 · $hiddenCount"
                else -> "更多筛选"
            }
        FilterChip(
            selected = expanded,
            onClick = onToggle,
            modifier =
                Modifier
                    .touchClickable(onClick = onToggle)
                    .focusSaverItem(focusSaver, "index_filter_toggle"),
            shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
            scale = FilterChipDefaults.scale(focusedScale = 1f),
            colors = ControlFocusDefaults.filterColors(),
            border = ControlFocusDefaults.filterBorder(),
        ) {
            Text(
                text = label,
                color =
                    if (expanded) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
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
