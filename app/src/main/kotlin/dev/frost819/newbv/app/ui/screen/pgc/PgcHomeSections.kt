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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import dev.frost819.newbv.app.ui.component.animeCardScale
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
import dev.frost819.newbv.biliapi.entity.season.Timeline
import dev.frost819.newbv.biliapi.entity.season.TimelineEp
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Date
import kotlin.math.floor

/** 索引分类 chips 的最大展示数量（含「全部」与「更多」）。 */
internal const val INDEX_CHIP_LIMIT = 15

/** 时间表放送条目封面宽度（乘屏宽分档系数），16:9。 */
private const val TIMELINE_COVER_WIDTH = 96

/** 时间表表头（日期 + 星期 + 下划线）高度，排版字号不随分档系数缩放，用固定值。 */
private val TIMELINE_HEADER_HEIGHT = 52.dp

/** 时间表可见放送条目行数（末行部分露出，提示列内可继续滚动）。 */
private const val TIMELINE_VISIBLE_ROWS = 4.4f

/** 时间表单列最小宽度（乘屏宽分档系数），实际列数按看板可用宽度取整均分。 */
private val TIMELINE_MIN_COLUMN_WIDTH = 250.dp

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// ── 通用板块组件 ─────────────────────────────────────────────────────────

/**
 * 分区标题。不设为可聚焦，避免出现无动作的焦点目标。
 */
@Composable
internal fun SectionHeader(
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
 * 错误占位板块：提示文案 + 重试按钮，居中占 300dp 高。
 */
@Composable
internal fun ErrorRetryBlock(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(300.dp),
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

/**
 * PGC 卡片统一容器：surface 底色、聚焦 2dp 外描边、不缩放。
 *
 * @param cornerRadius 卡片圆角，外描边圆角 = 圆角 + 1dp。
 * @param shape 卡片裁切形状，默认全局 large；自定义圆角时需同步传入，保证描边贴合卡片圆角。
 */
@Composable
internal fun PgcCardSurface(
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
 * PGC 热播榜卡片：竖版封面 + 评分角标 + 排名数字 + 标题 + 最新一话。
 */
@Composable
internal fun PgcRankCard(
    item: PgcRankData.Item,
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

// ── 时间表看板（按天分列） ────────────────────────────────────────────────

/**
 * 时间表看板：类似网页版番剧时间表的按天分列布局。
 *
 * 一列一天（日期 + 星期表头，今天高亮下划线），列内自上而下排列当天的放送条目
 * （时间轴圆点 + 放送时间 + 封面 + 标题/话数）。D-Pad 左右跨列、上下在列内滚动，
 * 列数随看板宽度自适应（与 [animeCardScale] 同档分档），整块高度固定，
 * 首次进入横向滚动到「今天」附近（今天列落在第 2 列位置）；之后再组合
 * （滚出视口又滚回、从详情页返回）不再重置横向位置，由焦点恢复逻辑接手。
 *
 * @param focusKeyPrefix 看板内条目 focusSaver key 前缀（各分区需不同前缀避免串扰），
 *   形如 `"anime_timeline_"`，完整 key 为 `前缀 + 日期 + "_" + seasonId + "_" + 序号`。
 */
@Composable
internal fun PgcTimelineBoard(
    timeline: List<Timeline>,
    focusSaver: FocusSaver,
    onEpClick: (TimelineEp) -> Unit,
    focusKeyPrefix: String,
    modifier: Modifier = Modifier,
) {
    val scale = animeCardScale()
    val columnSpacing = 20.dp * scale
    val rowHeight = timelineRowHeight(scale)
    val rowPitch = rowHeight + 6.dp * scale
    val boardHeight = TIMELINE_HEADER_HEIGHT + rowPitch * TIMELINE_VISIBLE_ROWS
    val listState = rememberLazyListState()
    // 「今天」初始定位只在本次页面生命周期内做一次：看板是懒网格的一项，
    // 滚出视口被回收、再滚回来会重新组合，若每次组合都回「今天」，
    // 用户浏览到远端日期后一移出看板就会被自动打回开头
    var initialized by rememberSaveable { mutableStateOf(false) }
    // 看板内已有焦点说明是板内正常导航，恢复逻辑不要插手
    var boardHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(timeline) {
        if (!initialized) {
            // 进入时把「今天」带到第 2 列位置（与网页版时间表的初始视角一致）
            val todayIndex = timeline.indexOfFirst { it.isToday }
            if (todayIndex > 0) listState.scrollToItem(todayIndex - 1)
            // 从详情页返回时恢复保存的焦点条目：先在系统自动聚焦污染 savedKey 前快照 key
            restoreTimelineFocus(focusSaver.savedKeyValue(), timeline, listState, focusSaver, focusKeyPrefix)
            initialized = true
        }
        snapshotFlow { focusSaver.savedKeyValue() }
            .distinctUntilChanged()
            .collect { key ->
                if (!boardHasFocus) restoreTimelineFocus(key, timeline, listState, focusSaver, focusKeyPrefix)
            }
    }
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(boardHeight)
                .onFocusChanged { boardHasFocus = it.hasFocus },
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
                PgcTimelineDayColumn(
                    day = timeline[index],
                    width = columnWidth,
                    scale = scale,
                    listHeight = boardHeight - TIMELINE_HEADER_HEIGHT,
                    rowHeight = rowHeight,
                    focusSaver = focusSaver,
                    focusKeyPrefix = focusKeyPrefix,
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
    focusKeyPrefix: String,
) {
    if (!key.startsWith(focusKeyPrefix)) return
    val savedDate = key.removePrefix(focusKeyPrefix).substringBefore("_")
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
private fun PgcTimelineDayColumn(
    day: Timeline,
    width: Dp,
    scale: Float,
    listHeight: Dp,
    rowHeight: Dp,
    focusSaver: FocusSaver,
    focusKeyPrefix: String,
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
                    PgcTimelineEntry(
                        ep = ep,
                        published = published,
                        rowHeight = rowHeight,
                        scale = scale,
                        onClick = { onEpClick(ep) },
                        modifier =
                            Modifier.focusSaverItem(
                                focusSaver,
                                "${focusKeyPrefix}${day.dateString}_${ep.seasonId}_$index",
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
private fun PgcTimelineEntry(
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
        PgcCardSurface(
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

// ── 索引分类 chips ───────────────────────────────────────────────────────

/** 索引分类 chip 数据。 */
internal data class PgcIndexChipData(
    val styleId: Int,
    val label: String,
)

/**
 * 「分区索引」分类行：「全部」+ 各风格 + 「更多」，统一 secondaryContainer 单色，
 * 点击跳转该分区的索引筛选页。
 *
 * @param chips 风格 chips（不含「全部」，行内自动补「全部」「更多」）。
 * @param focusKeyPrefix focusSaver key 前缀（分区间隔离，形如 `"anime_index_"`）。
 */
@Composable
internal fun PgcIndexChips(
    chips: List<PgcIndexChipData>,
    focusSaver: FocusSaver,
    onNavigateIndex: (styleId: Int) -> Unit,
    focusKeyPrefix: String,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        item(key = "all") {
            PgcIndexChip(
                label = "全部",
                onClick = { onNavigateIndex(-1) },
                modifier = Modifier.focusSaverItem(focusSaver, "${focusKeyPrefix}all"),
            )
        }
        items(chips, key = { it.styleId }) { chip ->
            PgcIndexChip(
                label = chip.label,
                onClick = { onNavigateIndex(chip.styleId) },
                modifier = Modifier.focusSaverItem(focusSaver, "$focusKeyPrefix${chip.styleId}"),
            )
        }
        item(key = "more") {
            PgcIndexChip(
                label = "更多",
                onClick = { onNavigateIndex(-1) },
                modifier = Modifier.focusSaverItem(focusSaver, "${focusKeyPrefix}more"),
            )
        }
    }
}

/**
 * 索引分类 chip：secondaryContainer 单色胶囊，点击跳转索引筛选页。
 */
@Composable
private fun PgcIndexChip(
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
