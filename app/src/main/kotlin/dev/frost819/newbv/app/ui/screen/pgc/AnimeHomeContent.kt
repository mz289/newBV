package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.frost819.newbv.app.ui.component.POSTER_CARD_MIN_WIDTH
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonSeasonCard
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
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
import kotlinx.coroutines.flow.distinctUntilChanged

/** 番剧页索引分类 chips 的最大展示数量（含「全部」与「更多」）。 */
private const val INDEX_CHIP_LIMIT = 15

/** 模块样式：我的追番。 */
private const val MODULE_STYLE_FOLLOW = "follow"

/** 模块样式：猜你喜欢（支持游标翻页）。 */
private const val MODULE_STYLE_DOUBLE_FEED = "double_feed"

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 番剧页内容（PGC「番剧」Tab 专属布局）。
 *
 * 按版式纵向排布四个板块：正在追（需登录，未登录隐藏）、番剧热播榜（排名卡）、
 * 新番时间表（星期切换 + 当日放送）、番剧索引（分类 chips，点击跳转索引筛选页）。
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
 * 所有板块堆叠在同一个 4 列网格中（分区标题与横向滚动行占满整行），
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
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(-1) }

    TvLazyVerticalGrid(
        columns = GridCells.Adaptive(POSTER_CARD_MIN_WIDTH),
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
            val effectiveDayIndex =
                if (selectedDayIndex in state.timeline.indices) {
                    selectedDayIndex
                } else {
                    state.timeline.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
                }
            item(span = { GridItemSpan(maxLineSpan) }, key = "anime_timeline_days") {
                AnimeTimelineDays(
                    timeline = state.timeline,
                    selectedIndex = effectiveDayIndex,
                    onSelectDay = { selectedDayIndex = it },
                    focusSaver = focusSaver,
                )
            }
            state.timeline.getOrNull(effectiveDayIndex)?.let { selectedDay ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "anime_timeline_row") {
                    LazyRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        items(selectedDay.episodes.size, key = { index -> index }) { index ->
                            val ep = selectedDay.episodes[index]
                            AnimeTimelineCard(
                                ep = ep,
                                onClick = {
                                    navController.navigate(PgcFeatureRoute(seasonId = ep.seasonId.toLong()))
                                },
                                modifier =
                                    Modifier.focusSaverItem(
                                        focusSaver,
                                        "anime_timeline_${ep.seasonId}_$index",
                                    ),
                            )
                        }
                    }
                }
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
 */
@Composable
private fun AnimeCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
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
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(300.dp)) {
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
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(190.dp)) {
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
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(190.dp)) {
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

/**
 * 时间表星期切换行。聚焦即切换选中日（与 TopNav 交互一致）。
 *
 * 15 天不裁剪后「今天」在列表中段，首次进入时初始滚动到选中日附近。
 */
@Composable
private fun AnimeTimelineDays(
    timeline: List<Timeline>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit,
    focusSaver: FocusSaver,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(timeline) {
        // 让选中日（默认今天）前保留 2 天上下文，避免落在首屏之外
        listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(timeline.size, key = { index -> timeline[index].dateString }) { index ->
            val day = timeline[index]
            AnimeDayPill(
                dayOfWeek = day.dayOfWeek,
                dateText = shortDate(day.dateString),
                isSelected = index == selectedIndex,
                onClick = { onSelectDay(index) },
                modifier =
                    Modifier
                        .focusSaverItem(focusSaver, "anime_day_${day.dateString}")
                        .onFocusChanged { if (it.isFocused) onSelectDay(index) },
            )
        }
    }
}

/**
 * 时间表星期胶囊：选中使用 primaryContainer 底色。
 */
@Composable
private fun AnimeDayPill(
    dayOfWeek: Int,
    dateText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        colors = ControlFocusDefaults.surfaceColors(containerColor = containerColor, contentColor = contentColor),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = weekdayName(dayOfWeek),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 时间表放送卡片：缩略图 + 发布时间/话数 + 番剧名。
 */
@Composable
private fun AnimeTimelineCard(
    ep: TimelineEp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimeCardSurface(onClick = onClick, modifier = modifier.width(300.dp), cornerRadius = 8.dp) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(132.dp)
                        .aspectRatio(1.6f)
                        .clip(MaterialTheme.shapes.medium),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = ep.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ep.publishTime,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ep.publishIndex,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ep.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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

/** 「2026-04-23」转「4月23日」。 */
private fun shortDate(dateString: String): String {
    val parts = dateString.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return dateString
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return dateString
    return "${month}月${day}日"
}
