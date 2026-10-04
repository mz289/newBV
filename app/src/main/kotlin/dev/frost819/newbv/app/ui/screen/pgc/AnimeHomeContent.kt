package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonSeasonCard
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.animeCardScale
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.ui.navigation.PgcIndexRoute
import dev.frost819.newbv.app.viewmodel.pgc.AnimeHomeUiState
import dev.frost819.newbv.app.viewmodel.pgc.AnimeHomeViewModel
import dev.frost819.newbv.biliapi.entity.pgc.PgcPageTab
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import kotlinx.coroutines.flow.distinctUntilChanged

/** 模块样式：我的追番。 */
private const val MODULE_STYLE_FOLLOW = "follow"

/** 模块样式：猜你喜欢（支持游标翻页）。 */
private const val MODULE_STYLE_DOUBLE_FEED = "double_feed"

/** 番剧页时间表条目 focusSaver key 前缀。 */
private const val ANIME_TIMELINE_KEY_PREFIX = "anime_timeline_"

/** 番剧页索引 chips focusSaver key 前缀。 */
private const val ANIME_INDEX_KEY_PREFIX = "anime_index_"

/**
 * 番剧页内容（PGC「番剧」Tab 专属布局）。
 *
 * 按版式纵向排布四个板块：正在追（需登录，未登录隐藏）、番剧热播榜（排名卡）、
 * 新番时间表（按天分列看板，一列一天）、番剧索引（分类 chips，点击跳转索引筛选页）。
 * 空板块自动隐藏，菜单键刷新由 [PgcContent] 统一分发。
 *
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel 番剧页 ViewModel（由 PgcContent 提升持有，切 Tab 不丢状态）。
 */
@Composable
fun AnimeHomeContent(
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: AnimeHomeViewModel,
) {
    val state by viewModel.uiState.collectAsState()

    // 首次进入番剧 Tab 时懒加载（切 Tab 后状态保留，不重复请求）
    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    AnimeHomeGrid(
        state = state,
        focusSaver = focusSaver,
        navController = navController,
        onLoadMoreGuess = viewModel::loadMoreGuess,
        onRetry = viewModel::refreshAll,
    )
}

/**
 * 番剧页主网格。
 *
 * 所有板块堆叠在同一个网格中（分区标题与横向滚动行占满整行，
 * 网格列宽仅决定首屏骨架卡宽度，随屏宽分档与竖版卡同宽），
 * D-Pad 上下移动时由 BringIntoViewSpec 自动滚动到焦点项；
 * 新番时间表看板持焦期间锁滚动，避免定轴把固定高度的看板推出视口。
 */
@Composable
private fun AnimeHomeGrid(
    state: AnimeHomeUiState,
    focusSaver: FocusSaver,
    navController: NavController,
    onLoadMoreGuess: () -> Unit,
    onRetry: () -> Unit,
) {
    val cardScale = animeCardScale()
    var timelineFocused by remember { mutableStateOf(false) }

    TvLazyVerticalGrid(
        // 实际内容行均占满整行，网格列宽只决定首屏骨架卡宽度，与竖版卡片同宽
        columns = GridCells.Adaptive(150.dp * cardScale),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        scrollLock = { timelineFocused },
    ) {
        if (!state.loaded && state.loading) {
            // 首屏加载中：同构骨架屏占位（P0-4）
            items(SKELETON_FIRST_SCREEN_COUNT) { SkeletonSeasonCard() }
            return@TvLazyVerticalGrid
        }

        // 模块化板块：我的追番/番剧推荐/国创推荐/猜你喜欢等，按接口返回顺序渲染
        state.pageModules.forEach { module ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "pgc_module_${module.moduleId}_header") {
                SectionHeader(title = module.title)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "pgc_module_${module.moduleId}_row") {
                AnimeModuleRow(
                    module = module,
                    focusSaver = focusSaver,
                    onItemClick = { item ->
                        navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                    },
                    onLoadMore = if (module.style == MODULE_STYLE_DOUBLE_FEED) onLoadMoreGuess else null,
                )
            }
        }

        if (state.rankItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_rank_header") {
                SectionHeader(title = "番剧热播榜")
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_rank_row") {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(state.rankItems, key = { it.seasonId }) { item ->
                        PgcRankCard(
                            item = item,
                            onClick = {
                                navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                            },
                            modifier = Modifier.focusSaverItem(focusSaver, "anime_rank_${item.seasonId}"),
                        )
                    }
                }
            }
        }

        if (state.timeline.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_timeline_header") {
                SectionHeader(title = "新番时间表")
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_timeline_board") {
                PgcTimelineBoard(
                    timeline = state.timeline,
                    focusSaver = focusSaver,
                    onEpClick = { ep ->
                        navController.navigate(PgcFeatureRoute(seasonId = ep.seasonId.toLong()))
                    },
                    focusKeyPrefix = ANIME_TIMELINE_KEY_PREFIX,
                    onFocusInsideChange = { timelineFocused = it },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "anime_index_header") {
            SectionHeader(title = "番剧索引")
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "anime_index_row") {
            val chips =
                remember {
                    Style
                        .getList(PgcType.Anime)
                        .drop(1)
                        .take(INDEX_CHIP_LIMIT)
                        .map { PgcIndexChipData(styleId = it.id, label = it.label) }
                }
            PgcIndexChips(
                chips = chips,
                focusSaver = focusSaver,
                onNavigateIndex = { styleId ->
                    navController.navigate(PgcIndexRoute(pgcTypeName = PgcType.Anime.name, styleId = styleId))
                },
                focusKeyPrefix = ANIME_INDEX_KEY_PREFIX,
            )
        }

        if (state.error && state.rankItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_error") {
                ErrorRetryBlock(onRetry = onRetry)
            }
        }
    }
}

// ── 分区组件 ─────────────────────────────────────────────────────────────

/**
 * 模块横滑行，按 wiliwili 的语义分流卡片样式：
 * `follow`/`double_feed` 用横版卡片，其余样式（v_card 等）用竖版卡片；
 * double_feed 滚动到列表末尾时触发 [onLoadMore] 翻页。
 */
@Composable
private fun AnimeModuleRow(
    module: PgcPageTab.Module,
    focusSaver: FocusSaver,
    onItemClick: (PgcPageTab.Card) -> Unit,
    onLoadMore: (() -> Unit)?,
) {
    val listState = rememberLazyListState()
    if (onLoadMore != null) {
        LaunchedEffect(listState, module.items.size) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            }.distinctUntilChanged()
                .collect { lastIndex ->
                    if (lastIndex >= module.items.size - 4) onLoadMore()
                }
        }
    }
    val landscape = module.style == MODULE_STYLE_FOLLOW || module.style == MODULE_STYLE_DOUBLE_FEED
    LazyRow(
        state = listState,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(module.items, key = { it.seasonId }) { item ->
            val cardModifier =
                Modifier.focusSaverItem(focusSaver, "pgc_module_${module.moduleId}_${item.seasonId}")
            if (landscape) {
                AnimeModuleLandscapeCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = cardModifier,
                )
            } else {
                AnimeModulePortraitCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = cardModifier,
                )
            }
        }
    }
}

/**
 * 模块横版卡片（我的追番/猜你喜欢）：封面 + 进度或热度文案角标 +
 * 左上角标 + 标题 + 副标题。
 */
@Composable
private fun AnimeModuleLandscapeCard(
    item: PgcPageTab.Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PgcCardSurface(onClick = onClick, modifier = modifier.width(200.dp * animeCardScale())) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(MaterialTheme.shapes.large),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = item.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ),
                )
                item.badge?.let { badge ->
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                item.desc?.let { desc ->
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        text = desc,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = item.subTitle ?: item.bottomBadge
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 模块竖版卡片（v_card 等运营推荐）：封面 + 左上角标 + 右下角标 + 标题 + 描述。
 */
@Composable
private fun AnimeModulePortraitCard(
    item: PgcPageTab.Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PgcCardSurface(onClick = onClick, modifier = modifier.width(150.dp * animeCardScale())) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(MaterialTheme.shapes.large),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = item.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                item.badge?.let { badge ->
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                item.bottomBadge?.let { bottomBadge ->
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                        text = bottomBadge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.desc?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
