package dev.frost819.newbv.app.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.viewmodel.home.HomeViewModel
import dev.frost819.newbv.biliapi.entity.ugc.UgcItem

/**
 * 推荐视频列表页。
 *
 * 支持从详情页返回后恢复焦点到之前点击的卡片。
 */
@Composable
fun RecommendScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()
    VideoFeedGrid(
        items = state.recommendItems,
        isLoading = state.recommendLoading,
        isError = state.recommendError,
        hasMore = state.recommendHasMore,
        keyPrefix = "rcmd",
        toCardData = { item: UgcItem -> VideoCardData.fromUgcItem(item) },
        onLoadMore = viewModel::loadRecommend,
        onRetry = viewModel::loadRecommend,
        navController = navController,
        focusSaver = focusSaver,
        modifier = modifier,
    )
}
