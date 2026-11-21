package dev.frost819.newbv.app.ui.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videoCardGridCells
import dev.frost819.newbv.app.ui.component.videoGridHSpacing
import dev.frost819.newbv.app.ui.component.videoGridVSpacing
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserCollectionRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.user.UserCollectionViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * UP 主合集/系列视频列表页。
 *
 * 从用户空间页的合集/系列网格进入，展示该合集或系列内的全部视频。
 */
fun NavGraphBuilder.userCollectionScreen(navController: NavController) {
    composable<UserCollectionRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserCollectionRoute>()
        val viewModel: UserCollectionViewModel = hiltViewModel()
        UserCollectionScreen(
            mid = route.mid,
            collectionId = route.collectionId,
            isSeason = route.isSeason,
            title = route.title,
            cover = route.cover,
            viewModel = viewModel,
            navController = navController,
        )
    }
}

@Composable
private fun UserCollectionScreen(
    mid: Long,
    collectionId: Long,
    isSeason: Boolean,
    title: String,
    cover: String,
    viewModel: UserCollectionViewModel,
    navController: NavController,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val focusSaver = rememberFocusSaver()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    LaunchedEffect(mid, collectionId) {
        viewModel.init(mid, collectionId, isSeason, title, cover)
    }

    focusSaver.RestoreFocus()

    LaunchedEffect(gridState, state.videos.size) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.videos.size - 20
            }.collect {
                viewModel.loadMore(mid)
            }
    }

    TvLazyVerticalGrid(
        state = gridState,
        columns = videoCardGridCells(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(videoGridHSpacing()),
        verticalArrangement = Arrangement.spacedBy(videoGridVSpacing()),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isSeason) "合集" else "系列",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(
            items = state.videos,
            key = { _, item -> item.aid },
        ) { index, video ->
            val cardData =
                remember(video) {
                    val durationMs = video.duration * 1000L
                    val progressRatio =
                        if (video.playbackPosition > 0) {
                            video.playbackPosition / 100f
                        } else {
                            null
                        }
                    VideoCardData(
                        avid = video.aid,
                        bvid = video.bvid,
                        title = video.title,
                        cover = video.cover,
                        playString = video.play.takeIf { it != -1 }.toWanString(),
                        danmakuString = video.danmaku.takeIf { it != -1 }.toWanString(),
                        timeString = durationMs.formatHourMinSec(),
                        upName = video.author.ifEmpty { state.collection?.title ?: "" },
                        upMid = mid,
                        pubTime = video.pubTime,
                        progress = progressRatio,
                    )
                }
            SmallVideoCard(
                modifier = Modifier.focusSaverItem(focusSaver, "user_collection_$index"),
                data = cardData,
                onClick = { navController.navigateFromVideoCard(cardData) },
                onGoToDetailPage = {
                    navController.navigateFromVideoCard(cardData, forceDetail = true)
                },
                onGoToUpPage = {},
                onAddWatchLater = { watchLaterViewModel.addToView(aid = video.aid) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListFooterTip(
                isLoading = state.loading,
                isError = state.error,
                hasMore = state.hasMore,
                itemsIsEmpty = state.videos.isEmpty(),
                onRetry = { viewModel.refresh(mid) },
            )
        }
    }
}
