package dev.frost819.newbv.app.ui.screen.personal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.InfiniteScrollEffect
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videoCardGridCells
import dev.frost819.newbv.app.ui.component.videoGridHSpacing
import dev.frost819.newbv.app.ui.component.videoGridVSpacing
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.personal.PersonalViewModel
import dev.frost819.newbv.biliapi.entity.CollectedFavoriteFolder
import dev.frost819.newbv.biliapi.entity.CollectedFavoriteType
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.focusShakeTarget
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable

/** 封面圆角：与视频卡统一的 8dp。 */
private val folderCoverShape = RoundedCornerShape(8.dp)

/**
 * 订阅页面（我的订阅：订阅的收藏夹与合集）。
 *
 * 默认展示订阅夹网格；点进订阅夹后展示其中视频，
 * 顶部出现返回胶囊，系统返回键同样回到订阅夹列表。
 *
 * @param viewModel 个人页 ViewModel。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 */
@Composable
fun SubscriptionScreen(
    modifier: Modifier = Modifier,
    viewModel: PersonalViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    val currentFolder = state.currentSubscriptionFolder

    BackHandler(enabled = currentFolder != null) {
        viewModel.exitSubscriptionFolder()
    }

    LaunchedEffect(currentFolder?.id) {
        gridState.scrollToItem(0)
    }

    if (currentFolder == null &&
        state.subscriptionFolders.isEmpty() &&
        !state.subscriptionLoading &&
        !state.subscriptionError
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "没有订阅",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    InfiniteScrollEffect(
        state = gridState,
        // 均通过委托属性读取，保证模式切换后闭包拿到的是最新状态
        itemCount = {
            if (state.currentSubscriptionFolder == null) {
                state.subscriptionFolders.size
            } else {
                state.subscriptionItems.size
            }
        },
        onLoadMore = {
            val folder = state.currentSubscriptionFolder
            if (folder == null) {
                viewModel.loadSubscriptionFolders()
            } else {
                viewModel.loadSubscriptionItems(folder)
            }
        },
    )

    TvLazyVerticalGrid(
        state = gridState,
        columns = videoCardGridCells(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(videoGridHSpacing()),
        verticalArrangement = Arrangement.spacedBy(videoGridVSpacing()),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentFolder != null) {
                    SubscriptionBackChip(onClick = viewModel::exitSubscriptionFolder)
                }
                Text(
                    text = currentFolder?.title ?: "我的订阅",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (currentFolder == null) {
            itemsIndexed(
                items = state.subscriptionFolders,
                key = { _, folder -> "subscription_${folder.id}" },
            ) { index, folder ->
                SubscriptionFolderCard(
                    folder = folder,
                    onClick = { viewModel.loadSubscriptionItems(folder, forceRefresh = true) },
                    modifier = Modifier.focusSaverItem(focusSaver, "subscription_folder_$index"),
                )
            }
        } else {
            itemsIndexed(
                items = state.subscriptionItems,
                key = { _, item -> item.id },
            ) { index, item ->
                val cardData =
                    remember(item) {
                        VideoCardData(
                            avid = item.id,
                            title = item.title,
                            cover = item.cover,
                            playString = "",
                            danmakuString = "",
                            timeString = (item.duration * 1000L).formatHourMinSec(),
                            upName = item.upper.name,
                            upMid = item.upper.mid,
                        )
                    }
                SmallVideoCard(
                    modifier = Modifier.focusSaverItem(focusSaver, "subscription_item_$index"),
                    data = cardData,
                    onClick = {
                        navController.navigateFromVideoCard(cardData)
                    },
                    onGoToDetailPage = {
                        navController.navigateFromVideoCard(cardData, forceDetail = true)
                    },
                    onGoToUpPage = {
                        navController.navigate(UserSpaceRoute(mid = item.upper.mid, name = item.upper.name))
                    },
                    onAddWatchLater = { watchLaterViewModel.addToView(aid = item.id) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            if (currentFolder == null) {
                ListFooterTip(
                    isLoading = state.subscriptionLoading,
                    isError = state.subscriptionError,
                    hasMore = state.subscriptionHasMore,
                    itemsIsEmpty = state.subscriptionFolders.isEmpty(),
                    onRetry = viewModel::refreshSubscription,
                )
            } else {
                ListFooterTip(
                    isLoading = state.subscriptionItemsLoading,
                    isError = state.subscriptionItemsError,
                    hasMore = state.subscriptionItemsHasMore,
                    itemsIsEmpty = state.subscriptionItems.isEmpty(),
                    onRetry = {
                        state.currentSubscriptionFolder?.let {
                            viewModel.loadSubscriptionItems(it, forceRefresh = true)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 订阅夹/合集卡片。
 *
 * 横版封面（16:9）+ 标题 + 「UP主 · 内容数 · 类型」副标题。
 */
@Composable
private fun SubscriptionFolderCard(
    folder: CollectedFavoriteFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .focusShakeTarget()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column {
            AsyncImage(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(folderCoverShape),
                model = folder.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )

            Column(
                // 水平微内缩：文字不直接顶到焦点描边
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = folderSubtitle(folder),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 卡片副标题：UP主名 · 内容数 · 类型。 */
private fun folderSubtitle(folder: CollectedFavoriteFolder): String =
    when (folder.type) {
        CollectedFavoriteType.Season -> "${folder.upper.name} · ${folder.mediaCount} 个内容 · 合集"
        CollectedFavoriteType.Folder -> "${folder.upper.name} · ${folder.mediaCount} 个内容 · 收藏夹"
    }

/** 内容列表左上角的返回胶囊。 */
@Composable
private fun SubscriptionBackChip(
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.touchClickable(onClick = onClick),
        shape =
            ClickableSurfaceDefaults.shape(
                shape = RoundedCornerShape(50),
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = outerFocusBorder(50.dp),
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                focusedScale = 1f,
            ),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
            )
            Text(
                text = "返回",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
