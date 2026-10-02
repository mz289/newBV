package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.R
import dev.frost819.newbv.app.ui.component.ErrorTip
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
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.season.Timeline
import dev.frost819.newbv.biliapi.entity.season.TimelineEp
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Date
import kotlin.math.floor

/** 番剧页索引分类 chips 的最大展示数量（含「全部」与「更多」）。 */
private const val INDEX_CHIP_LIMIT = 15

/** 模块样式：我的追番。 */
private const val MODULE_STYLE_FOLLOW = "follow"

/** 模块样式：猜你喜欢（支持游标翻页）。 */
private const val MODULE_STYLE_DOUBLE_FEED = "double_feed"

/** 时间表放送条目封面宽度（乘屏宽分档系数），16:9。 */
private const val TIMELINE_COVER_WIDTH = 96

/** 时间表表头（日期 + 星期 + 下划线）高度，排版字号不随分档系数缩放，用固定值。 */
private val TIMELINE_HEADER_HEIGHT = 52.dp

/** 时间表可见放送条目行数（末行部分露出，提示列内可继续滚动）。 */
private const val TIMELINE_VISIBLE_ROWS = 4.4f

/** 时间表单列最小宽度（乘屏宽分档系数），实际列数按看板可用宽度取整均分。 */
private val TIMELINE_MIN_COLUMN_WIDTH = 250.dp

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

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
 * D-Pad 上下移动时由 BringIntoViewSpec 自动滚动到焦点项。
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

    TvLazyVerticalGrid(
        // 实际内容行均占满整行，网格列宽只决定首屏骨架卡宽度，与竖版卡片同宽
        columns = GridCells.Adaptive(150.dp * cardScale),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        AnimeRankCard(
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
                AnimeTimelineBoard(
                    timeline = state.timeline,
                    focusSaver = focusSaver,
                    onEpClick = { ep ->
                        navController.navigate(PgcFeatureRoute(seasonId = ep.seasonId.toLong()))
                    },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "anime_index_header") {
            SectionHeader(title = "番剧索引")
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "anime_index_row") {
            AnimeIndexChips(
                focusSaver = focusSaver,
                onNavigateIndex = { styleId ->
                    navController.navigate(PgcIndexRoute(pgcTypeName = PgcType.Anime.name, styleId = styleId))
                },
            )
        }

        if (state.error && state.rankItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_error") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorTip()
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ControlFocusDefaults.buttonColors(),
                        border = ControlFocusDefaults.buttonBorder(),
                        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Text(text = stringResource(id = R.string.login_retry))
                    }
                }
            }
        }
    }
}

// ── 分区组件 ─────────────────────────────────────────────────────────────

/**
 * 分区标题。不设为可聚焦，避免出现无动作的焦点目标。
 */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.padding(top = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 番剧卡片统一容器：surface 底色、聚焦 2dp 外描边、不缩放。
 *
 * @param cornerRadius 卡片圆角，外描边圆角 = 圆角 + 1dp。
 * @param shape 卡片裁切形状，默认全局 large；自定义圆角时需同步传入，保证描边贴合卡片圆角。
 */
@Composable
private fun AnimeCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(cornerRadius)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        content()
    }
}

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
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(200.dp * animeCardScale())) {
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
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(150.dp * animeCardScale())) {
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

/**
 * 「番剧热播榜」卡片：竖版封面 + 评分角标 + 排名数字 + 标题 + 最新一话。
 */
@Composable
private fun AnimeRankCard(
    item: PgcRankData.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(150.dp * animeCardScale())) {
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
                item.rating?.let { rating ->
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                        text = rating,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.rank.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.newEpIndexShow?.let { indexShow ->
                        Text(
                            text = indexShow,
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
}

// ── 新番时间表（按天分列看板） ───────────────────────────────────────────

/**
 * 时间表看板：类似网页版番剧时间表的按天分列布局。
 *
 * 一列一天（日期 + 星期表头，今天高亮下划线），列内自上而下排列当天的放送条目
 * （时间轴圆点 + 放送时间 + 封面 + 标题/话数）。D-Pad 左右跨列、上下在列内滚动，
 * 列数随看板宽度自适应（与 [animeCardScale] 同档分档），整块高度固定，
 * 首次进入横向滚动到「今天」附近（今天列落在第 2 列位置）。
 */
@Composable
private fun AnimeTimelineBoard(
    timeline: List<Timeline>,
    focusSaver: FocusSaver,
    onEpClick: (TimelineEp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = animeCardScale()
    val columnSpacing = 20.dp * scale
    val rowHeight = timelineRowHeight(scale)
    val rowPitch = rowHeight + 6.dp * scale
    val boardHeight = TIMELINE_HEADER_HEIGHT + rowPitch * TIMELINE_VISIBLE_ROWS
    val listState = rememberLazyListState()
    LaunchedEffect(timeline) {
        // 进入时把「今天」带到第 2 列位置（与网页版时间表的初始视角一致）
        val todayIndex = timeline.indexOfFirst { it.isToday }
        if (todayIndex > 0) listState.scrollToItem(todayIndex - 1)
        // 从详情页返回时恢复保存的焦点条目：先在系统自动聚焦污染 savedKey 前快照 key，
        // 之后 savedKey 一有变化（聚焦移动、返回页面）也重新走一次恢复流程
        restoreTimelineFocus(focusSaver.savedKeyValue(), timeline, listState, focusSaver)
        snapshotFlow { focusSaver.savedKeyValue() }
            .distinctUntilChanged()
            .collect { restoreTimelineFocus(it, timeline, listState, focusSaver) }
    }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(boardHeight),
    ) {
        // 与其他横滑行一致预留 4dp 边距，避免最右列卡片的聚焦外描边被裁切
        val horizontalInset = 4.dp
        val usableWidth = maxWidth - horizontalInset * 2
        val columnCount =
            maxOf(
                1,
                floor((usableWidth + columnSpacing) / (TIMELINE_MIN_COLUMN_WIDTH * scale + columnSpacing))
                    .toInt(),
            )
        val columnWidth = (usableWidth - columnSpacing * (columnCount - 1)) / columnCount
        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            contentPadding = PaddingValues(horizontal = horizontalInset),
        ) {
            items(timeline.size, key = { index -> timeline[index].dateString }) { index ->
                AnimeTimelineDayColumn(
                    day = timeline[index],
                    width = columnWidth,
                    scale = scale,
                    listHeight = boardHeight - TIMELINE_HEADER_HEIGHT,
                    rowHeight = rowHeight,
                    focusSaver = focusSaver,
                    onEpClick = onEpClick,
                )
            }
        }
    }
}

/**
 * 把焦点恢复到 [key] 指向的时间表条目（非时间表 key 直接忽略）。
 *
 * 返回本页时目标天所在列可能已被横向懒列表回收：MainScreen 的 RestoreFocus 定时更早、
 * 请求落在目标列组合完成之前，会静默落空。这里先把横轴吸附回那一天（正常在板内移动
 * 焦点时目标列必然可见，跳过吸附、只做一次幂等的 requestFocus），等列完成组合后
 * 带重试地请求焦点。
 */
private suspend fun restoreTimelineFocus(
    key: String,
    timeline: List<Timeline>,
    listState: LazyListState,
    focusSaver: FocusSaver,
) {
    if (!key.startsWith("anime_timeline_")) return
    val savedDate = key.removePrefix("anime_timeline_").substringBefore("_")
    val index = timeline.indexOfFirst { it.dateString == savedDate }
    if (index < 0) return
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    repeat(6) {
        if (runCatching { focusSaver.focusRequesterFor(key).requestFocus() }.isSuccess) return
        delay(50)
    }
}

/**
 * 时间表单日列：表头（日期 + 星期 + 高亮下划线）+ 左侧时间轴竖线 + 当天放送条目列表。
 *
 * 今天常亮下划线；焦点进入本列时下划线同步点亮，指示当前浏览的是哪一天。
 */
@Composable
private fun AnimeTimelineDayColumn(
    day: Timeline,
    width: Dp,
    scale: Float,
    listHeight: Dp,
    rowHeight: Dp,
    focusSaver: FocusSaver,
    onEpClick: (TimelineEp) -> Unit,
) {
    var columnHasFocus by remember { mutableStateOf(false) }
    val highlighted = day.isToday || columnHasFocus
    Column(
        modifier =
            Modifier
                .width(width)
                .height(TIMELINE_HEADER_HEIGHT + listHeight)
                .onFocusChanged { columnHasFocus = it.hasFocus },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TIMELINE_HEADER_HEIGHT)
                    .padding(bottom = 6.dp),
        ) {
            Text(
                text = dayDateText(day.dateString),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Text(
                text = weekdayName(day.dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (highlighted) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (highlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            },
                        ),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // 时间轴竖线：与每条条目的圆点同一横坐标，纵贯整列
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 11.dp * scale)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            )
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(6.dp * scale),
                // 右侧预留 4dp，避免条目聚焦外描边被列边界裁切（左侧由时间轴圆点通道让位）
                contentPadding = PaddingValues(top = 3.dp, bottom = 3.dp, end = 4.dp),
            ) {
                items(day.episodes.size, key = { index -> "${day.dateString}_$index" }) { index ->
                    val ep = day.episodes[index]
                    val published = remember(ep.publishDate) { !ep.publishDate.after(Date()) }
                    AnimeTimelineEntry(
                        ep = ep,
                        published = published,
                        rowHeight = rowHeight,
                        scale = scale,
                        onClick = { onEpClick(ep) },
                        modifier =
                            Modifier.focusSaverItem(
                                focusSaver,
                                "anime_timeline_${day.dateString}_${ep.seasonId}_$index",
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 时间表放送条目：时间轴圆点 + 时间 + 封面 + 标题/话数。
 *
 * 已播出的话数用主色、未播用弱化色区分（与网页版时间表的粉/灰一致）；
 * 标题最多两行，话数固定在条目底部与封面底边对齐。
 */
@Composable
private fun AnimeTimelineEntry(
    ep: TimelineEp,
    published: Boolean,
    rowHeight: Dp,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 10.dp * scale + 4.dp)
                    .width(22.dp * scale),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }
        AnimeCardSurface(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            cornerRadius = 10.dp * scale,
            shape = RoundedCornerShape(10.dp * scale),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = 10.dp * scale, vertical = 10.dp * scale),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    modifier = Modifier.padding(top = 2.dp * scale),
                    text = ep.publishTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp * scale))
                Box(
                    modifier =
                        Modifier
                            .width(TIMELINE_COVER_WIDTH.dp * scale)
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(6.dp * scale)),
                ) {
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = ep.cover,
                        contentDescription = ep.title,
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp * scale))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = ep.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ep.publishIndex,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (published) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 时间表放送条目行高：封面高（宽 / 1.6）加卡片上下内边距，条目布局与看板高度共用。 */
private fun timelineRowHeight(scale: Float): Dp = TIMELINE_COVER_WIDTH.dp * scale / 1.6f + 20.dp * scale

/**
 * 「番剧索引」分类行：「全部」+ 前 N 个风格 + 「更多」，统一 secondaryContainer 单色。
 */
@Composable
private fun AnimeIndexChips(
    focusSaver: FocusSaver,
    onNavigateIndex: (styleId: Int) -> Unit,
) {
    val styles =
        remember { Style.getList(PgcType.Anime).drop(1).take(INDEX_CHIP_LIMIT) }

    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        item(key = "all") {
            AnimeIndexChip(
                label = "全部",
                onClick = { onNavigateIndex(-1) },
                modifier = Modifier.focusSaverItem(focusSaver, "anime_index_all"),
            )
        }
        items(styles, key = { it.id }) { style ->
            AnimeIndexChip(
                label = style.label,
                onClick = { onNavigateIndex(style.id) },
                modifier = Modifier.focusSaverItem(focusSaver, "anime_index_${style.id}"),
            )
        }
        item(key = "more") {
            AnimeIndexChip(
                label = "更多",
                onClick = { onNavigateIndex(-1) },
                modifier = Modifier.focusSaverItem(focusSaver, "anime_index_more"),
            )
        }
    }
}

/**
 * 索引分类 chip：secondaryContainer 单色胶囊，点击跳转索引筛选页。
 */
@Composable
private fun AnimeIndexChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ── 工具 ─────────────────────────────────────────────────────────────────

/** 星期数字（1-7）转中文名。 */
private fun weekdayName(dayOfWeek: Int): String = WEEKDAY_NAMES.getOrElse(dayOfWeek - 1) { "" }

/** 「2026-10-02」转「10-2」（去前导零，与网页版时间表的日期格式一致）。 */
private fun dayDateText(dateString: String): String {
    val parts = dateString.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return dateString
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return dateString
    return "$month-$day"
}
