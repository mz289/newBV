package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val EPISODES_PER_GROUP = 50

/**
 * 番剧全部选集：分段侧栏与自适应网格，进入时定位最近观看的分集。
 *
 * 分段侧栏获得焦点（D-Pad 移动）即自动切换右侧对应分段内容，无需按确认键；
 * 点击仍可切换（触屏路径）。
 *
 * @param seasonTitle 番剧名称。
 * @param sectionTitle 当前分区名称，如正片或 SP。
 * @param episodes 当前分区的全部分集，按接口顺序展示。
 * @param lastPlayedCid 最近观看的 CID，0 表示无记录。
 * @param lastPlayedTime 最近观看的秒数，负数表示看完。
 * @param onDismiss 关闭弹窗。
 * @param onSelect 播放选中的分集。
 */
@Composable
internal fun SeasonEpisodeDialog(
    seasonTitle: String,
    sectionTitle: String,
    episodes: List<Episode>,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    onDismiss: () -> Unit,
    onSelect: (Episode) -> Unit,
) {
    val historyIndex = episodes.indexOfFirst { lastPlayedCid != 0L && it.cid == lastPlayedCid }
    val initialIndex = historyIndex.coerceAtLeast(0)
    val groupCount = (episodes.size + EPISODES_PER_GROUP - 1) / EPISODES_PER_GROUP
    var group by rememberSaveable { mutableIntStateOf(initialIndex / EPISODES_PER_GROUP) }
    val start = (group * EPISODES_PER_GROUP).coerceAtMost(episodes.size)
    val end = (start + EPISODES_PER_GROUP).coerceAtMost(episodes.size)
    val slice = episodes.subList(start, end)
    val gridState = rememberLazyGridState(initialIndex % EPISODES_PER_GROUP)
    val rangeState = rememberLazyListState(group)
    val focusSaver = rememberFocusSaver()
    var pendingIndex by remember { mutableStateOf<Int?>(initialIndex.takeIf { episodes.isNotEmpty() }) }
    var focusRequest by remember { mutableIntStateOf(0) }
    focusSaver.RestoreFocus()

    // 先滚动再等待目标项进入布局，避免长篇番剧的离屏 FocusRequester 尚未绑定。
    LaunchedEffect(group, focusRequest) {
        // 单段时不组合侧栏；对尚未布局的 LazyList 调用 scrollToItem 会一直等待。
        if (groupCount > 1) rangeState.scrollToItem(group.coerceAtMost(groupCount - 1))
        val target = pendingIndex
        if (episodes.isNotEmpty()) gridState.scrollToItem(if (target != null) target - start else 0)
        if (target != null && target in start until end) {
            val episode = episodes[target]
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == episode.id } }.first { it }
            // Dialog 首帧的自动焦点可能晚于网格布局，等待窗口获得焦点后再设置默认分集。
            if (focusRequest == 0) delay(100)
            focusSaver.focusRequesterFor("episode_${episode.id}").requestFocus()
            pendingIndex = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            val scrollColor = MaterialTheme.colorScheme.onSurfaceVariant
            val rangeWidth = if (maxWidth >= 720.dp) 112.dp else 88.dp
            val columns =
                if (maxWidth >= 720.dp) {
                    5
                } else if (maxWidth >= 520.dp) {
                    4
                } else {
                    2
                }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("全部选集", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "$seasonTitle · 共 ${episodes.size} 话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (historyIndex >= 0) {
                        EpisodeDialogAction(
                            onClick = {
                                group = historyIndex / EPISODES_PER_GROUP
                                pendingIndex = historyIndex
                                focusRequest++
                            },
                            modifier = Modifier.testTag("episode_locate").focusSaverItem(focusSaver, "locate"),
                        ) { Text("定位上次观看", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            1.dp,
                        ).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (groupCount > 1) {
                        LazyColumn(
                            state = rangeState,
                            modifier = Modifier.width(rangeWidth).fillMaxHeight(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(groupCount, key = { it }) { index ->
                                EpisodeDialogAction(
                                    selected = index == group,
                                    onClick = {
                                        group = index
                                        pendingIndex = null
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .testTag("episode_group_$index")
                                            .focusSaverItem(focusSaver, "group_$index")
                                            .onFocusChanged {
                                                // 焦点落到某一分段即切换右侧内容，免去再按确认键；
                                                // 同时丢弃待定位请求，避免切段后焦点被抢回网格。
                                                if (it.isFocused && group != index) {
                                                    group = index
                                                    pendingIndex = null
                                                }
                                            }.focusProperties {
                                                if (index == group && slice.isNotEmpty()) {
                                                    right =
                                                        focusSaver.focusRequesterFor(
                                                            "episode_${slice[
                                                                gridState.firstVisibleItemIndex.coerceAtMost(
                                                                    slice.lastIndex,
                                                                ),
                                                            ].id}",
                                                        )
                                                }
                                            },
                                ) {
                                    Text(
                                        "${index * EPISODES_PER_GROUP + 1}–${minOf(
                                            (index + 1) * EPISODES_PER_GROUP,
                                            episodes.size,
                                        )}",
                                    )
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sectionTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (episodes.isNotEmpty()) {
                                Text(
                                    "${start + 1}–$end 话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (episodes.isEmpty()) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) { Text("暂无选集") }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = gridState,
                                modifier =
                                    Modifier.weight(1f).testTag("episode_grid").drawWithContent {
                                        drawContent()
                                        if (gridState.canScrollForward || gridState.canScrollBackward) {
                                            val rowHeight = 76.dp.toPx()
                                            val totalHeight = ((slice.size + columns - 1) / columns) * rowHeight
                                            val thumbHeight =
                                                (size.height * size.height / totalHeight).coerceIn(
                                                    16.dp.toPx(),
                                                    size.height,
                                                )
                                            val scroll =
                                                gridState.firstVisibleItemIndex / columns * rowHeight +
                                                    gridState.firstVisibleItemScrollOffset
                                            val top =
                                                (scroll / (totalHeight - size.height)).coerceIn(0f, 1f) *
                                                    (size.height - thumbHeight)
                                            drawRect(
                                                scrollColor.copy(alpha = 0.15f),
                                                Offset(size.width - 2.dp.toPx(), 0f),
                                                Size(2.dp.toPx(), size.height),
                                            )
                                            drawRect(
                                                scrollColor.copy(alpha = 0.65f),
                                                Offset(size.width - 2.dp.toPx(), top),
                                                Size(2.dp.toPx(), thumbHeight),
                                            )
                                        }
                                    },
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(slice, key = { _, episode -> episode.id }) { index, episode ->
                                    EpisodeSelectionCard(
                                        episode = episode,
                                        isHistory = start + index == historyIndex,
                                        played = lastPlayedTime,
                                        onClick = { onSelect(episode) },
                                        modifier =
                                            Modifier
                                                .testTag("episode_choice_${episode.id}")
                                                .focusSaverItem(focusSaver, "episode_${episode.id}")
                                                .focusProperties {
                                                    if (groupCount > 1 &&
                                                        index % columns == 0
                                                    ) {
                                                        left =
                                                            focusSaver.focusRequesterFor("group_$group")
                                                    }
                                                },
                                    )
                                }
                            }
                            val layout = gridState.layoutInfo
                            val visible =
                                layout.visibleItemsInfo.filter {
                                    it.offset.y + it.size.height > layout.viewportStartOffset &&
                                        it.offset.y < layout.viewportEndOffset
                                }
                            val first = (visible.firstOrNull()?.index ?: 0) + start + 1
                            val last = (visible.lastOrNull()?.index ?: 0) + start + 1
                            Text(
                                "$first–$last / ${episodes.size}",
                                modifier = Modifier.align(Alignment.End),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelectionCard(
    episode: Episode,
    isHistory: Boolean,
    played: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    EpisodeDialogAction(onClick, modifier.height(68.dp), selected = isHistory) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    episode.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isHistory) Text("上次观看", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                episode.longTitle,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isHistory && played != 0 && episode.duration > 0) {
                val progress = if (played < 0) 1f else (played.toFloat() / episode.duration).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            3.dp,
                        ).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun EpisodeDialogAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.25f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}
