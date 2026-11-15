package dev.frost819.newbv.app.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.viewmodel.home.HomeViewModel
import dev.frost819.newbv.biliapi.entity.ugc.UgcItem

/** 热门视频列表页。 */
@Composable
fun PopularScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VideoFeedGrid(
        items = state.popularItems,
        isLoading = state.popularLoading,
        isError = state.popularError,
        hasMore = state.popularHasMore,
        keyPrefix = "popular",
        toCardData = { item: UgcItem -> VideoCardData.fromUgcItem(item) },
        onLoadMore = viewModel::loadPopular,
        onRetry = viewModel::loadPopular,
        navController = navController,
        focusSaver = focusSaver,
        modifier = modifier,
    )
}
