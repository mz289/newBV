package dev.frost819.newbv.app.ui.screen.ugc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.TabbedContent
import dev.frost819.newbv.app.ui.component.search.SearchResultFilter
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.screen.home.VideoFeedGrid
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.removeHtmlTags
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.ugc.UgcTabItem
import dev.frost819.newbv.app.viewmodel.ugc.UgcViewModel

/**
 * UGC 分区内容（TopNav + 4 列网格 + 筛选）。
 *
 * 顶部 Tab 切换分区，内容区为视频网格 + 无限滚动。默认数据源为分区推荐信息流；
 * 菜单键唤出筛选弹窗，选择排序/时长后切换为分区搜索接口（参考哔哩合辑站源方案），
 * 筛选恢复默认即回到推荐流。筛选条件跨分区保留。点击当前 Tab 仍为刷新。
 *
 * 菜单键在本页外层 Box 拦截（Down/Up 均消费），不再下传 TabbedContent 的菜单键刷新。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel UGC ViewModel。
 */
@Composable
fun UgcContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: UgcViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // 菜单键唤出筛选；Down/Up 都消费，KeyUp 不落回 TabbedContent 触发刷新
                    if (event.key == Key.Menu) {
                        if (event.type == KeyEventType.KeyDown) {
                            viewModel.toggleFilter(true)
                        }
                        true
                    } else {
                        false
                    }
                },
    ) {
        TabbedContent(
            navFocusRequester = navFocusRequester,
            tabs = UgcTabItem.entries.toList(),
            initialTab = UgcTabItem.Douga,
            displayName = { it.displayName },
            onTabSelected = { viewModel.switchType(it) },
            onRefresh = { viewModel.refresh() },
        ) { _ ->
            UgcGrid(
                viewModel = viewModel,
                navController = navController,
                focusSaver = focusSaver,
                emptyText = if (uiState.isFiltered) "没有找到相关视频" else null,
            )
        }
    }

    // 筛选弹窗（与搜索结果页共用）
    if (uiState.showFilter) {
        SearchResultFilter(
            selectedOrder = uiState.selectedOrder,
            selectedDuration = uiState.selectedDuration,
            onConfirm = { order, duration ->
                viewModel.updateFilter(order, duration)
            },
            onDismiss = { viewModel.toggleFilter(false) },
        )
    }
}

/**
 * UGC 视频网格。
 *
 * 4 列网格 + 无限滚动，距离底部 20 条时触发加载更多。
 * 搜索流标题含高亮标签，统一去 HTML 后展示。
 */
@Composable
private fun UgcGrid(
    viewModel: UgcViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
    emptyText: String?,
) {
    val state by viewModel.uiState.collectAsState()

    VideoFeedGrid(
        items = state.items,
        isLoading = state.loading,
        isError = state.error,
        hasMore = state.hasMore,
        keyPrefix = "ugc",
        toCardData = { item ->
            VideoCardData(
                avid = item.aid,
                title = item.title.removeHtmlTags(),
                cover = item.cover,
                playString = item.play.takeIf { it != -1 }.toWanString(),
                danmakuString = item.danmaku.takeIf { it != -1 }.toWanString(),
                timeString = (item.duration * 1000L).formatHourMinSec(),
                upName = item.author,
                upMid = item.authorMid,
                pubTime = item.pubTime,
            )
        },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        navController = navController,
        focusSaver = focusSaver,
        emptyText = emptyText,
    )
}
