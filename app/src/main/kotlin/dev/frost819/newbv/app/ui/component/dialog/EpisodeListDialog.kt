package dev.frost819.newbv.app.ui.component.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.LocalUserFocusIntent
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.focusWhenReady
import dev.frost819.newbv.app.ui.component.isDirectionalKeyDown
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import java.util.Locale

/** 选集卡片高度：行内与弹窗共用，保证两处条目观感一致。 */
private val EPISODE_CARD_HEIGHT = 84.dp

/** 选集卡片圆角。 */
private val EPISODE_CARD_SHAPE = RoundedCornerShape(12.dp)

/**
 * 选集排序方向。
 *
 * [Ascending] 按接口自然顺序（P1 → P180 / 第1集 → 第180集）；
 * [Descending] 最新在前（P180 → P1），适合追更中的长合集。
 */
enum class EpisodeOrder {
    Ascending,
    Descending,
}

/**
 * 一页选集：[ordinals] 为该页条目在**原始列表**中的 1-based 序号，按当前排序的显示顺序排列。
 *
 * 序号始终指向原始位置，因此切换排序不会改变条目身份，页签文案
 * （[label]，如 `161–180`）也始终与集数对得上。
 */
internal data class EpisodePage(val ordinals: List<Int>) {
    /** 页签文案：原始序号区间，倒序页同样从大到小编排。 */
    val label: String
        get() {
            val min = ordinals.minOrNull() ?: return ""
            val max = ordinals.maxOrNull() ?: return ""
            return "$min–$max"
        }
}

/**
 * 按 [order] 把 [count] 个条目切页，每页 [pageSize] 个。
 *
 * 排序只改变显示顺序，页码区间仍以原始序号表达：正序第一页 `1–20`，
 * 倒序第一页 `161–180`（同一批集数，只是最新在前）。
 */
internal fun episodePages(
    count: Int,
    pageSize: Int,
    order: EpisodeOrder,
): List<EpisodePage> {
    if (count <= 0 || pageSize <= 0) return emptyList()
    val display = if (order == EpisodeOrder.Ascending) (1..count).toList() else (count downTo 1).toList()
    return display.chunked(pageSize).map { EpisodePage(it) }
}

/** 序号 [ordinal] 所在页下标；不在任何页时返回 -1。 */
internal fun List<EpisodePage>.pageIndexOfOrdinal(ordinal: Int?): Int {
    if (ordinal == null) return -1
    return indexOfFirst { ordinal in it.ordinals }
}

/**
 * 选集排序切换（正序 / 倒序）。
 *
 * 与列表页的排序行同款筛选标签：选中态用主色底表达，焦点只加外框。
 * 聚焦不改变排序（避免方向键路过时误切），确认键才生效。
 *
 * @param selected 当前排序。
 * @param onSelect 选择回调。
 * @param modifier 外部修饰符。
 * @param focusSaver 焦点保存器，用于返回页面时定位到上次聚焦的选项。
 * @param focusKeyPrefix 焦点 key 前缀，同一屏幕多行排序时需区分。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeOrderToggle(
    selected: EpisodeOrder,
    onSelect: (EpisodeOrder) -> Unit,
    modifier: Modifier = Modifier,
    focusSaver: FocusSaver? = null,
    focusKeyPrefix: String = "episode_order",
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeOrder.entries.forEach { order ->
            val label = if (order == EpisodeOrder.Ascending) "正序" else "倒序"
            FilterChip(
                selected = order == selected,
                onClick = { onSelect(order) },
                modifier =
                    Modifier
                        .touchClickable(onClick = { onSelect(order) })
                        .then(
                            if (focusSaver != null) {
                                Modifier.focusSaverItem(
                                    focusSaver,
                                    "${focusKeyPrefix}_${order.name.lowercase(Locale.ROOT)}",
                                )
                            } else {
                                Modifier
                            },
                        ),
                shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = FilterChipDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.filterColors(),
                border = ControlFocusDefaults.filterBorder(),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (order == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 分 P / 分集通用卡片。
 *
 * 两行标题 + 时长/播放中标记，底部叠加播放进度条。
 * 当前播放项用主色底 + 主色描边标记，与普通项一眼可分。
 *
 * @param title 条目标题。
 * @param duration 总时长（秒），<= 0 时不显示。
 * @param played 已播放时长（秒），0 表示无进度；传负数表示已看完（进度条拉满）。
 * @param isCurrent 是否为当前播放项。
 * @param onClick 点击回调。
 * @param modifier 外部修饰符（行内传 `width(200.dp)`，弹窗内交由网格拉伸）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeListButton(
    title: String,
    duration: Int,
    played: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
// 不要在外层用 .clip()：它会裁剪掉 Surface 内部的聚焦反馈
        modifier =
            modifier
                .height(EPISODE_CARD_HEIGHT)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = EPISODE_CARD_SHAPE),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor =
                    if (isCurrent) {
                        primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor = contentColor,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (isCurrent) {
                        Border(border = BorderStroke(2.dp, primary), inset = 0.dp, shape = EPISODE_CARD_SHAPE)
                    } else {
                        Border.None
                    },
                focusedBorder = outerFocusBorder(12.dp),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // 底部留出进度条空间，标题不压进度条
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (duration > 0) {
                        Text(
                            text = formatEpisodeDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isCurrent) {
                        Text(
                            text = "播放中",
                            style = MaterialTheme.typography.labelSmall,
                            color = primary,
                        )
                    }
                }
            }
            if (played != 0 && duration > 0) {
                val ratio = if (played < 0) 1f else (played.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(primary.copy(alpha = 0.14f)),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(ratio)
                            .height(3.dp)
                            .background(primary.copy(alpha = 0.75f)),
                )
            }
        }
    }
}

/**
 * 分 P / 分集分页弹窗。
 *
 * 自上而下：标题区（标题/类型角标/集数/当前集 + 定位当前 + 正序倒序切换）、
 * 页码页签、2~5 列自适应卡片网格。打开时自动定位到当前播放集所在页并聚焦该集；
 * 切换排序时保持当前集数区间不变（倒序只是把同一批集数换个方向展示）。
 *
 * @param title 弹窗标题（合集名 / 分 P 区块名）。
 * @param badge 标题旁的类别角标，如「合集」。
 * @param countUnit 条目计量单位，如「集」「P」。
 * @param entries 全部条目，按接口自然顺序。
 * @param pageSize 每页条目数。
 * @param order 当前排序（由调用方持有，行内列表与弹窗共用同一状态）。
 * @param onOrderChange 切换排序回调。
 * @param currentOrdinal 当前播放条目的原始序号（1-based），用于强标记。
 * @param locateOrdinal 自动定位的原始序号，缺省退回 [currentOrdinal]。
 * @param keyOf 提取条目的稳定 key，用于网格项 key。
 * @param titleOf 提取条目显示标题。
 * @param ordinalLabelOf 由原始序号生成标题区「当前」文案的标签（如 `P12`）。
 * @param durationOf 提取条目总时长（秒）。
 * @param playedOf 提取条目已播放时长（秒），0 表示无进度。
 * @param isCurrentOf 判断条目是否为当前播放项。
 * @param onDismiss 关闭回调。
 * @param onSelect 选中条目回调。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> EpisodeListDialog(
    title: String,
    badge: String? = null,
    countUnit: String,
    entries: List<T>,
    pageSize: Int,
    order: EpisodeOrder,
    onOrderChange: (EpisodeOrder) -> Unit,
    currentOrdinal: Int? = null,
    locateOrdinal: Int? = null,
    keyOf: (T) -> Any,
    titleOf: (T) -> String,
    ordinalLabelOf: (Int) -> String,
    durationOf: (T) -> Int,
    playedOf: (T) -> Int,
    isCurrentOf: (T) -> Boolean,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val pages = remember(entries.size, pageSize, order) { episodePages(entries.size, pageSize, order) }
    val locateTarget = locateOrdinal.coerceToOrdinal(entries.size) ?: currentOrdinal.coerceToOrdinal(entries.size)
    val initialPage = remember { pages.pageIndexOfOrdinal(locateTarget).takeIf { it >= 0 } ?: 0 }
    var selectedPage by remember { mutableIntStateOf(initialPage) }
    val focusSaver = rememberFocusSaver()
    val gridState = rememberLazyGridState()
    val chipState = rememberLazyListState()
    val userFocusIntent = LocalUserFocusIntent.current

    // 打开时先定位当前集；没有当前集时聚焦本页第一项，保证弹窗内有焦点。
    var pendingFocus by remember {
        mutableStateOf(locateTarget ?: pages.getOrNull(initialPage)?.ordinals?.firstOrNull())
    }
    var locateRequest by remember { mutableIntStateOf(0) }

    // 页签始终跟随选中页滚进可视区
    LaunchedEffect(selectedPage) {
        if (pages.size > 1) chipState.animateScrollToItem(selectedPage.coerceIn(0, pages.lastIndex))
    }
    // 单个 effect 负责「待定位则定位+聚焦，否则回到本页第一行」，避免两个 effect 抢滚动
    LaunchedEffect(selectedPage, order, locateRequest) {
        val page = pages.getOrNull(selectedPage) ?: return@LaunchedEffect
        val target = pendingFocus
        val indexInPage = page.ordinals.indexOf(target)
        if (target != null && indexInPage >= 0) {
            gridState.scrollToItem(indexInPage)
            entries.getOrNull(target - 1)?.let { entry ->
                focusSaver.focusRequesterFor(entryFocusKey(keyOf(entry))).focusWhenReady()
            }
            pendingFocus = null
        } else {
            gridState.scrollToItem(0)
        }
    }

    val selectOrder: (EpisodeOrder) -> Unit = { next ->
        if (next != order) {
            // 锚定当前页首条集数，切换后停在同一批集数上，不把用户送回第一页
            val anchor = pages.getOrNull(selectedPage)?.ordinals?.firstOrNull()
            val nextPages = episodePages(entries.size, pageSize, next)
            val nextIndex = nextPages.pageIndexOfOrdinal(anchor)
            onOrderChange(next)
            selectedPage =
                if (nextIndex >= 0) {
                    nextIndex
                } else {
                    selectedPage.coerceIn(0, nextPages.lastIndex.coerceAtLeast(0))
                }
            pendingFocus = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .onPreviewKeyEvent { keyEvent ->
                        // 纯观测不消费：向恢复链路声明「用户按过方向键」
                        if (keyEvent.isDirectionalKeyDown()) userFocusIntent.noteDirectionalKey()
                        false
                    }
                    .widthIn(max = 1280.dp)
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
        ) {
            val columns =
                when {
                    maxWidth >= 960.dp -> 5
                    maxWidth >= 700.dp -> 4
                    maxWidth >= 480.dp -> 3
                    else -> 2
                }
            val page = pages.getOrNull(selectedPage)
            val slice =
                page
                    ?.ordinals
                    .orEmpty()
                    .mapNotNull { ordinal -> entries.getOrNull(ordinal - 1)?.let { ordinal to it } }
            val rows = (slice.size + columns - 1) / columns
            val fullGridHeight = EPISODE_CARD_HEIGHT * rows + 8.dp * (rows - 1).coerceAtLeast(0) + 8.dp
            // 弹窗高度 = 标题区 + 网格内容，封顶到窗口的 92%；网格用 weight 吃掉剩余高度，
            // 条目多时在网格内部滚动，任何窗口尺寸下都不会把内容裁在弹窗边缘
            val chromeHeight = 200.dp
            val dialogHeight = minOf(chromeHeight + fullGridHeight, maxHeight * 0.92f)
            val scrollColor = MaterialTheme.colorScheme.onSurfaceVariant
            val density = LocalDensity.current.density

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dialogHeight),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (badge != null) {
                                EpisodeKindBadge(text = badge)
                            }
                        }
                        Text(
                            text =
                                buildString {
                                    append("共 ")
                                    append(entries.size)
                                    append(' ')
                                    append(countUnit)
                                    if (currentOrdinal != null) {
                                        append(" · 当前 ")
                                        append(ordinalLabelOf(currentOrdinal))
                                    }
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (locateTarget != null) {
                        EpisodeDialogPill(
                            label = "定位当前",
                            onClick = {
                                val index = pages.pageIndexOfOrdinal(locateTarget)
                                if (index >= 0) {
                                    selectedPage = index
                                    pendingFocus = locateTarget
                                    locateRequest++
                                }
                            },
                            modifier = Modifier.focusSaverItem(focusSaver, "episode_locate"),
                        )
                    }
                    EpisodeOrderToggle(
                        selected = order,
                        onSelect = selectOrder,
                        focusSaver = focusSaver,
                        focusKeyPrefix = "episode_dialog_order",
                    )
                }

                if (pages.size > 1) {
                    LazyRow(
                        state = chipState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    ) {
                        items(count = pages.size, key = { index -> "page_${pages[index].label}" }) { index ->
                            val pageInfo = pages[index]
                            FilterChip(
                                selected = index == selectedPage,
                                onClick = { selectedPage = index },
                                modifier = Modifier.touchClickable(onClick = { selectedPage = index }),
                                shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                                scale = FilterChipDefaults.scale(focusedScale = 1f),
                                colors = ControlFocusDefaults.filterColors(),
                                border = ControlFocusDefaults.filterBorder(),
                            ) {
                                Text(text = pageInfo.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (slice.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    TvLazyVerticalGrid(
                        leftExitRequester = null,
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .drawWithContent {
                                    drawContent()
                                    drawGridScrollIndicator(gridState, columns, density, scrollColor)
                                },
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(count = slice.size, key = { index -> keyOf(slice[index].second) }) { index ->
                            val entry = slice[index].second
                            EpisodeListButton(
                                title = titleOf(entry),
                                duration = durationOf(entry),
                                played = playedOf(entry),
                                isCurrent = isCurrentOf(entry),
                                onClick = { onSelect(entry) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusSaverItem(focusSaver, entryFocusKey(keyOf(entry))),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 网格右侧细滚动条：内容装不下时提示还有内容。 */
private fun DrawScope.drawGridScrollIndicator(
    state: LazyGridState,
    columns: Int,
    density: Float,
    color: Color,
) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 0) return
    val rows = (total + columns - 1) / columns
    val rowHeight = (EPISODE_CARD_HEIGHT + 8.dp).value * density
    val totalHeight = rows * rowHeight
    if (totalHeight <= size.height) return
    val thumbHeight = (size.height * size.height / totalHeight).coerceIn(16f * density, size.height)
    val scroll = state.firstVisibleItemIndex / columns * rowHeight + state.firstVisibleItemScrollOffset
    val maxScroll = (totalHeight - size.height).coerceAtLeast(1f)
    val top = (scroll / maxScroll).coerceIn(0f, 1f) * (size.height - thumbHeight)
    val width = 2.dp.toPx()
    drawRect(color.copy(alpha = 0.18f), Offset(size.width - width, 0f), Size(width, size.height))
    drawRect(color.copy(alpha = 0.65f), Offset(size.width - width, top), Size(width, thumbHeight))
}

/** 标题旁类别角标（合集 / 分 P 等）。 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeKindBadge(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 弹窗内的文字按钮（定位当前）：描边幽灵按钮，与实心排序标签区分开，避免被当成第三个排序项。 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeDialogPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = ControlFocusDefaults.shape),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    Border(
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            ),
                        inset = 0.dp,
                        shape = ControlFocusDefaults.shape,
                    ),
                focusedBorder = outerFocusBorder(8.dp),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 网格项焦点 key：条目稳定 key 可能重复于不同列表，前缀区分本组件的所有条目。 */
private fun entryFocusKey(key: Any): String = "episode_item_$key"

/** 把可能越界的序号收敛到 1..size；size 为 0 时返回 null。 */
private fun Int?.coerceToOrdinal(size: Int): Int? = this?.takeIf { size > 0 && it in 1..size }

/** 秒 → `mm:ss`（超过 1 小时带小时位）。 */
private fun formatEpisodeDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
    }
}
