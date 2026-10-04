package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
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
import dev.frost819.newbv.app.ui.component.PgcCarousel
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonSeasonCard
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.animeCardScale
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.ui.navigation.PgcIndexRoute
import dev.frost819.newbv.app.ui.navigation.VideoDetailRoute
import dev.frost819.newbv.app.viewmodel.pgc.PgcHomeUiState
import dev.frost819.newbv.app.viewmodel.pgc.PgcViewModel
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.PgcWebPage
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.season.Timeline

/** 榜单板块行内展示的名次上限。 */
private const val RANK_ITEMS_LIMIT = 10

/** 国创页时间表条目 focusSaver key 前缀。 */
private const val GUOCHUANG_TIMELINE_KEY_PREFIX = "guochuang_timeline_"

/**
 * 影视分区页内容（国创/电影/纪录片/电视剧/综艺共用，PGC「番剧」外的 Tab）。
 *
 * 与番剧页同构的板块化富布局：板块名称、内容与顺序均以接口下发为准
 * （分区页 `__INITIAL_STATE__` 的 modules），页面只按 [模块样式][PgcWebPage.WebModule.style]
 * 选择版式（榜单/时间表/编辑精选横版/普通竖版）动态渲染，板块无可跳转条目
 * 时自动隐藏（如专题活动模块）；索引 chips 行取接口的风格分组（缺失时回退本地风格表）。
 * 菜单键刷新由 [PgcContent] 统一分发。
 *
 * @param pgcType 目标分区。
 * @param viewModel PGC 分区 ViewModel（由 PgcContent 提升持有，切 Tab 不丢状态）。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param navController 导航控制器。
 */
@Composable
fun PgcHomeContent(
    pgcType: PgcType,
    viewModel: PgcViewModel,
    focusSaver: FocusSaver,
    navController: NavController,
) {
    val states by viewModel.uiStates.collectAsState()
    val state = states[pgcType] ?: PgcHomeUiState()

    // 首次进入分区时懒加载（切 Tab 后状态保留，不重复请求）
    LaunchedEffect(pgcType) {
        viewModel.loadIfNeeded(pgcType)
    }

    PgcHomeGrid(
        pgcType = pgcType,
        state = state,
        focusSaver = focusSaver,
        keyPrefix = pgcType.name.lowercase(),
        navController = navController,
        onRetry = { viewModel.refresh(pgcType) },
    )
}

/**
 * 分区页主网格。布局骨架与番剧页主网格一致：
 * 板块标题与横滑行占满整行，网格列宽仅决定首屏骨架卡宽度；
 * 时间表看板持焦期间锁滚动，避免定轴把固定高度的看板推出视口。
 */
@Composable
private fun PgcHomeGrid(
    pgcType: PgcType,
    state: PgcHomeUiState,
    focusSaver: FocusSaver,
    keyPrefix: String,
    navController: NavController,
    onRetry: () -> Unit,
) {
    val cardScale = animeCardScale()
    var timelineFocused by remember { mutableStateOf(false) }

    TvLazyVerticalGrid(
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

        if (state.carouselItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "${keyPrefix}_carousel") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PgcCarousel(
                        modifier = Modifier.focusSaverItem(focusSaver, "${keyPrefix}_carousel"),
                        data = state.carouselItems,
                        onClick = { item ->
                            item.seasonId?.toLong()?.let { seasonId ->
                                navController.navigate(PgcFeatureRoute(seasonId = seasonId))
                            }
                        },
                    )
                }
            }
        }

        // 服务端下发的板块：标题与顺序保持接口原样，按样式选版式渲染
        state.modules.forEach { module ->
            val isTimeline = module.style.startsWith("web_timeline")
            // 时间表板块条目数据来自时间表接口（SSR 条目为按天分组），看板数据缺失时不渲染；
            // 其余板块无可跳转条目（如专题活动页）时自动隐藏
            if (isTimeline) {
                if (state.timeline.isEmpty()) return@forEach
            } else if (moduleItems(module).isEmpty()) {
                return@forEach
            }
            val rowKey = "${keyPrefix}_module_${module.moduleId}"
            item(span = { GridItemSpan(maxLineSpan) }, key = "${rowKey}_header") {
                SectionHeader(title = module.title)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "${rowKey}_row") {
                PgcModuleRow(
                    module = module,
                    timeline = state.timeline,
                    focusSaver = focusSaver,
                    keyPrefix = keyPrefix,
                    onTimelineFocusChange = { timelineFocused = it },
                    navController = navController,
                )
            }
        }

        // 索引 chips：接口的风格分组（风格名/风格 id 均为接口下发），缺失时回退本地风格表
        val styleGroup = state.indexGroups.firstOrNull { it.field == "style_id" }
        val chips =
            if (styleGroup != null) {
                styleGroup.values
                    .filter { it.keyword != "-1" }
                    .take(INDEX_CHIP_LIMIT)
                    .map { group ->
                        PgcIndexChipData(styleId = group.keyword.toIntOrNull() ?: -1, label = group.name)
                    }
            } else {
                Style
                    .getList(pgcType)
                    .drop(1)
                    .take(INDEX_CHIP_LIMIT)
                    .map { PgcIndexChipData(styleId = it.id, label = it.label) }
            }
        item(span = { GridItemSpan(maxLineSpan) }, key = "${keyPrefix}_index_header") {
            SectionHeader(title = styleGroup?.name ?: "${pgcDisplayName(pgcType)}索引")
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "${keyPrefix}_index_row") {
            PgcIndexChips(
                chips = chips,
                focusSaver = focusSaver,
                onNavigateIndex = { styleId ->
                    navController.navigate(PgcIndexRoute(pgcTypeName = pgcType.name, styleId = styleId))
                },
                focusKeyPrefix = "${keyPrefix}_index_",
            )
        }

        // 分区页数据失败且没有任何板块可显示时提示错误（板块级失败由空板块静默表达）
        if (state.error && state.modules.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "${keyPrefix}_error") {
                ErrorRetryBlock(onRetry = onRetry)
            }
        }
    }
}

/**
 * 单个板块行：按服务端 [样式][PgcWebPage.WebModule.style] 分流版式。
 *
 * - `web_timeline_*`（新番时间表）：按天分列看板，条目数据来自时间表接口，
 *   板块标题取接口下发；看板数据缺失时不渲染。
 * - `web_rank_*`（热播榜）：带名次的榜单卡。
 * - `web_archive*`（编辑精选）：UGC 稿件，横版卡。
 * - 其余（推荐/运营位/猜你喜欢等）：竖版海报卡。
 */
@Composable
private fun PgcModuleRow(
    module: PgcWebPage.WebModule,
    timeline: List<Timeline>,
    focusSaver: FocusSaver,
    keyPrefix: String,
    onTimelineFocusChange: (Boolean) -> Unit,
    navController: NavController,
) {
    when {
        module.style.startsWith("web_timeline") -> {
            if (timeline.isNotEmpty()) {
                PgcTimelineBoard(
                    timeline = timeline,
                    focusSaver = focusSaver,
                    onEpClick = { ep ->
                        navController.navigate(PgcFeatureRoute(seasonId = ep.seasonId.toLong()))
                    },
                    focusKeyPrefix = GUOCHUANG_TIMELINE_KEY_PREFIX,
                    onFocusInsideChange = onTimelineFocusChange,
                )
            }
        }

        module.style.startsWith("web_rank") -> {
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                val rankItems =
                    moduleItems(module)
                        .filter { it.seasonId != null }
                        .take(RANK_ITEMS_LIMIT)
                items(rankItems, key = { "${it.seasonId}" }) { item ->
                    PgcRankCard(
                        item =
                            PgcRankData.Item(
                                rank = item.rank ?: 0,
                                seasonId = item.seasonId!!,
                                title = item.title,
                                cover = item.cover,
                                rating = item.rating,
                                newEpIndexShow = null,
                                badge = "",
                            ),
                        onClick = { navigateToItem(navController, item) },
                        modifier =
                            Modifier.focusSaverItem(
                                focusSaver,
                                "${keyPrefix}_module_${module.moduleId}_${item.seasonId}",
                            ),
                    )
                }
            }
        }

        else -> {
            val landscape = module.style.startsWith("web_archive")
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                items(moduleItems(module), key = { "${it.seasonId}_${it.avid}" }) { item ->
                    PgcModuleCard(
                        item = item,
                        landscape = landscape,
                        onClick = { navigateToItem(navController, item) },
                        modifier =
                            Modifier.focusSaverItem(
                                focusSaver,
                                "${keyPrefix}_module_${module.moduleId}_${item.seasonId ?: item.avid}",
                            ),
                    )
                }
            }
        }
    }
}

/** 板块可渲染条目：过滤掉没有站内跳转目标的条目（如专题活动页）。 */
private fun moduleItems(module: PgcWebPage.WebModule): List<PgcWebPage.ModuleItem> =
    module.items.filter { it.isNavigable }

/** 板块条目跳转：剧集 id 走剧集详情，UGC 稿件走视频详情。 */
private fun navigateToItem(
    navController: NavController,
    item: PgcWebPage.ModuleItem,
) {
    val seasonId = item.seasonId?.toLong()
    if (seasonId != null) {
        navController.navigate(PgcFeatureRoute(seasonId = seasonId))
    } else {
        item.avid?.let { aid -> navController.navigate(VideoDetailRoute(aid = aid)) }
    }
}

/**
 * 板块竖版海报卡（推荐/运营位/猜你喜欢等）：封面 + 评分角标（无评分隐藏）+ 标题 + 副标题；
 * [landscape] 为横版（编辑精选等 UGC 稿件封面）。
 */
@Composable
private fun PgcModuleCard(
    item: PgcWebPage.ModuleItem,
    landscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PgcCardSurface(
        onClick = onClick,
        modifier = modifier.width((if (landscape) 200.dp else 150.dp) * animeCardScale()),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (landscape) 1.6f else 0.75f)
                        .clip(MaterialTheme.shapes.large),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = item.cover,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                )
                val rating = item.rating
                if (rating != null) {
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        text = rating,
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
                val subTitle = item.subTitle
                if (subTitle != null) {
                    Text(
                        text = subTitle,
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

/** 分区中文名（索引 chips 行回退标题用）。 */
internal fun pgcDisplayName(pgcType: PgcType): String =
    when (pgcType) {
        PgcType.Anime -> "番剧"
        PgcType.GuoChuang -> "国创"
        PgcType.Movie -> "电影"
        PgcType.Documentary -> "纪录片"
        PgcType.Tv -> "电视剧"
        PgcType.Variety -> "综艺"
    }
