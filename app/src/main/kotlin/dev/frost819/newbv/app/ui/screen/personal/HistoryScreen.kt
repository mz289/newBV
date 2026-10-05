package dev.frost819.newbv.app.ui.screen.personal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.EmptyTip
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.screen.home.VideoFeedGrid
import dev.frost819.newbv.app.viewmodel.personal.PersonalViewModel

/**
 * 历史记录页面。
 *
 * 4 列网格 + 无限滚动，距离底部 20 条时触发加载更多。
 * 卡片底部显示播放进度条（progress/duration）。
 *
 * @param viewModel 个人页 ViewModel。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: PersonalViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()

    if (state.historyItems.isEmpty() && !state.historyLoading && !state.historyError) {
        EmptyTip(text = "没有观看记录", modifier = modifier)
        return
    }

    VideoFeedGrid(
        items = state.historyItems,
        isLoading = state.historyLoading,
        isError = state.historyError,
        hasMore = state.historyHasMore,
        keyPrefix = "history",
        toCardData = VideoCardData::fromHistoryItem,
        onLoadMore = viewModel::loadHistory,
        onRetry = viewModel::loadHistory,
        navController = navController,
        focusSaver = focusSaver,
    )
}
