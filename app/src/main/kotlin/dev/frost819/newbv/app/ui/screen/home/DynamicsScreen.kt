package dev.frost819.newbv.app.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.viewmodel.home.HomeViewModel
import dev.frost819.newbv.biliapi.entity.user.DynamicVideo

/**
 * 动态视频列表页。
 *
 * 需要登录，未登录时显示"请先登录"提示。
 */
@Composable
fun DynamicsScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()

    if (!state.isLogin) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "请先登录",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    VideoFeedGrid(
        items = state.dynamicItems,
        isLoading = state.dynamicLoading,
        isError = state.dynamicError,
        hasMore = state.dynamicHasMore,
        keyPrefix = "dynamics",
        toCardData = { item: DynamicVideo -> VideoCardData.fromDynamicVideo(item) },
        onLoadMore = viewModel::loadDynamic,
        onRetry = viewModel::loadDynamic,
        navController = navController,
        focusSaver = focusSaver,
        modifier = modifier,
    )
}
