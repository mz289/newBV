package dev.frost819.newbv.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.EmptyTip
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.InfiniteScrollEffect
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonVideoCard
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.videoCardGridCells
import dev.frost819.newbv.app.ui.component.videoGridHSpacing
import dev.frost819.newbv.app.ui.component.videoGridVSpacing
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel

/**
 * 首页视频流网格（推荐 / 热门 / 动态共用）。
 *
 * 自适应列宽网格 + 骨架屏首屏 + 无限滚动（距底部 20 条预加载）+ 底部加载提示，
 * 卡片支持稍后再看与 UP 主页跳转。
 *
 * @param keyPrefix 焦点保存 key 前缀（各 Tab 不同，避免恢复串位）。
 * @param toCardData 条目到卡片数据的纯映射。
 * @param emptyText 列表为空且不在加载/出错时的全屏空态文案；null 走底部提示的默认空态。
 */
@Composable
fun <T> VideoFeedGrid(
    items: List<T>,
    isLoading: Boolean,
    isError: Boolean,
    hasMore: Boolean,
    keyPrefix: String,
    toCardData: (T) -> VideoCardData,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    focusSaver: FocusSaver,
    emptyText: String? = null,
    modifier: Modifier = Modifier,
) {
    if (emptyText != null && items.isEmpty() && !isLoading && !isError) {
        EmptyTip(text = emptyText, modifier = modifier)
        return
    }

    val gridState = rememberLazyGridState()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    val currentItems by rememberUpdatedState(items)
    InfiniteScrollEffect(
        state = gridState,
        itemCount = { currentItems.size },
        onLoadMore = onLoadMore,
    )

    TvLazyVerticalGrid(
        modifier = modifier,
        state = gridState,
        columns = videoCardGridCells(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(videoGridHSpacing()),
        verticalArrangement = Arrangement.spacedBy(videoGridVSpacing()),
    ) {
        if (items.isEmpty() && isLoading) {
            // 首屏加载中：同构骨架屏占位（P0-4）
            items(SKELETON_FIRST_SCREEN_COUNT) { SkeletonVideoCard() }
        }

        itemsIndexed(
            items = items,
            key = { index, _ -> index },
        ) { index, item ->
            val cardData =
                remember(item) { toCardData(item) }
            SmallVideoCard(
                modifier = Modifier.focusSaverItem(focusSaver, "${keyPrefix}_$index", gridIndex = index),
                data = cardData,
                onClick = {
                    navController.navigateFromVideoCard(cardData)
                },
                onGoToDetailPage = {
                    navController.navigateFromVideoCard(cardData, forceDetail = true)
                },
                onGoToUpPage =
                    cardData.upMid?.let { mid ->
                        { navController.navigate(UserSpaceRoute(mid = mid, name = cardData.upName)) }
                    },
                onAddWatchLater = { watchLaterViewModel.addToView(aid = cardData.avid) },
            )
        }

        if (items.isNotEmpty() || !isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ListFooterTip(
                    isLoading = isLoading,
                    isError = isError,
                    hasMore = hasMore,
                    itemsIsEmpty = items.isEmpty(),
                    onRetry = onRetry,
                )
            }
        }
    }
}
