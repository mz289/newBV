package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.frost819.newbv.app.entity.player.ChapterMark
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.core.theme.BVTheme
import kotlinx.coroutines.delay

/**
 * 章节列表覆盖层。
 *
 * 从左侧滑入的半透明面板，显示当前视频的章节（看点）列表，
 * 行尾展示章节起始时间。显示时自动滚动到当前章节并请求焦点。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param chapterMarks 章节列表
 * @param currentTimeMs 当前播放位置（毫秒），用于高亮当前章节
 * @param onSeekToChapter 点击章节回调，参数为章节起始位置（毫秒）
 */
@Composable
fun ChapterListController(
    modifier: Modifier = Modifier,
    show: Boolean,
    chapterMarks: List<ChapterMark>,
    currentTimeMs: Long,
    onSeekToChapter: (ChapterMark) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // 无正在播放的章节时聚焦第一章
    val focusTargetIndex =
        chapterMarks
            .indexOfFirst { currentTimeMs in it }
            .let { if (it >= 0) it else 0 }

    // 显示时自动滚动到当前章节并请求焦点
    LaunchedEffect(show) {
        if (show && chapterMarks.isNotEmpty()) {
            listState.scrollToItem(focusTargetIndex)
            delay(50)
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = expandHorizontally(),
        exit = shrinkHorizontally(),
    ) {
        Surface(
            modifier = modifier,
            colors =
                SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(300.dp)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 60.dp),
                ) {
                    itemsIndexed(
                        items = chapterMarks,
                        key = { _, chapter -> chapter.startMs },
                    ) { index, chapter ->
                        PlayerListItem(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .then(
                                        if (index == focusTargetIndex) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            text = "${index + 1}. ${chapter.title}",
                            selected = currentTimeMs in chapter,
                            textAlign = TextAlign.Start,
                            trailingContent = {
                                Text(
                                    text = chapter.startMs.formatHourMinSec(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            },
                            onClick = { onSeekToChapter(chapter) },
                        )
                    }
                }
            }
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun ChapterListControllerPreview() {
    BVTheme {
        ChapterListController(
            show = true,
            chapterMarks =
                listOf(
                    ChapterMark(startMs = 0L, endMs = 32_000L, title = "引言"),
                    ChapterMark(startMs = 32_000L, endMs = 313_000L, title = "哈勃和韦布的局限"),
                    ChapterMark(startMs = 313_000L, endMs = 563_000L, title = "罗曼望远镜"),
                ),
            currentTimeMs = 40_000L,
            onSeekToChapter = {},
        )
    }
}

// endregion
