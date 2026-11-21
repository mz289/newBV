package dev.frost819.newbv.app.ui.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.UpActionButton
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videoCardGridCells
import dev.frost819.newbv.app.ui.component.videoGridHSpacing
import dev.frost819.newbv.app.ui.component.videoGridVSpacing
import dev.frost819.newbv.app.ui.component.videocard.SeasonCard
import dev.frost819.newbv.app.ui.component.videocard.SeasonCardData
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserCollectionRoute
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.user.UserSpaceTab
import dev.frost819.newbv.app.viewmodel.user.UserSpaceUiEffect
import dev.frost819.newbv.app.viewmodel.user.UserSpaceUiState
import dev.frost819.newbv.app.viewmodel.user.UserSpaceViewModel
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 用户空间页。
 *
 * 展示用户信息（头像、昵称、关注按钮）、内容标签行（最新发布/最多播放/最多收藏/合集/系列）
 * 和对应内容网格。用户名和头像由路由参数传入，不单独调用 API 获取（与原版 BV 行为一致）。
 */
fun NavGraphBuilder.userSpaceScreen(navController: NavController) {
    composable<UserSpaceRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserSpaceRoute>()
        val viewModel: UserSpaceViewModel = hiltViewModel()
        UserSpaceScreen(
            mid = route.mid,
            name = route.name ?: "",
            face = route.face,
            viewModel = viewModel,
            navController = navController,
        )
    }
}

@Composable
private fun UserSpaceScreen(
    mid: Long,
    name: String,
    face: String?,
    viewModel: UserSpaceViewModel,
    navController: NavController,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val focusSaver = rememberFocusSaver()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()
    val context = LocalContext.current

    CollectWatchLaterEffects(watchLaterViewModel)

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is UserSpaceUiEffect.ShowToast -> ToastUtils.show(context, effect.message)
            }
        }
    }

    LaunchedEffect(mid) {
        viewModel.init(mid, name, face)
    }

    focusSaver.RestoreFocus()

    // 切换标签时回到网格顶部，避免停留在旧内容的滚动位置
    LaunchedEffect(gridState, state.tab) {
        gridState.scrollToItem(0)
    }

    val displayedItemCount =
        if (state.tab.isCollectionTab) state.collections.size else state.videos.size

    LaunchedEffect(gridState, displayedItemCount) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= displayedItemCount - 20
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
            UserSpaceHeader(
                state = state,
                focusSaver = focusSaver,
                onToggleFollow = { viewModel.toggleFollow() },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            UserSpaceTabRow(
                selectedTab = state.tab,
                focusSaver = focusSaver,
                onSelectTab = { viewModel.selectTab(mid, it) },
            )
        }

        if (state.tab.isCollectionTab) {
            itemsIndexed(
                items = state.collections,
                // season_id 与 series_id 是两套 ID 空间，加前缀防 key 冲突
                key = { _, collection ->
                    if (collection.isSeason) "season_${collection.id}" else "series_${collection.id}"
                },
            ) { index, collection ->
                SeasonCard(
                    data =
                        SeasonCardData(
                            seasonId = collection.id.toInt(),
                            title = collection.title,
                            subTitle = "共${collection.total}个视频",
                            cover = collection.cover,
                            badge = if (collection.isSeason) "合集" else "系列",
                        ),
                    onClick = {
                        navController.navigate(
                            UserCollectionRoute(
                                mid = mid,
                                collectionId = collection.id,
                                isSeason = collection.isSeason,
                                title = collection.title,
                                cover = collection.cover,
                            ),
                        )
                    },
                    modifier = Modifier.focusSaverItem(focusSaver, "user_space_collection_$index"),
                )
            }
            if (!state.loading && !state.error && state.collections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.tab == UserSpaceTab.Seasons) "TA还没有合集" else "TA还没有系列",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
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
                            upName = video.author,
                            upMid = mid,
                            pubTime = video.pubTime,
                            progress = progressRatio,
                        )
                    }
                SmallVideoCard(
                    modifier = Modifier.focusSaverItem(focusSaver, "user_space_$index"),
                    data = cardData,
                    onClick = { navController.navigateFromVideoCard(cardData) },
                    onGoToDetailPage = {
                        navController.navigateFromVideoCard(cardData, forceDetail = true)
                    },
                    onGoToUpPage = {},
                    onAddWatchLater = { watchLaterViewModel.addToView(aid = video.aid) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListFooterTip(
                isLoading = state.loading,
                isError = state.error,
                hasMore = state.hasMore,
                itemsIsEmpty = displayedItemCount == 0,
                onRetry = { viewModel.refresh(mid) },
            )
        }
    }
}

/**
 * 内容标签行：最新发布 / 最多播放 / 最多收藏 / 合集 / 系列。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UserSpaceTabRow(
    selectedTab: UserSpaceTab,
    focusSaver: FocusSaver,
    onSelectTab: (UserSpaceTab) -> Unit,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(bottom = 8.dp)
                .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(UserSpaceTab.entries.size, key = { index -> index }) { index ->
            val tab = UserSpaceTab.entries[index]
            FilterChip(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                modifier =
                    Modifier
                        .touchClickable(onClick = { onSelectTab(tab) })
                        .focusSaverItem(focusSaver, "user_space_tab_$index"),
                shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = FilterChipDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.filterColors(),
                border = ControlFocusDefaults.filterBorder(),
            ) {
                Text(
                    text = tab.label,
                    color =
                        if (tab == selectedTab) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

@Composable
private fun UserSpaceHeader(
    state: UserSpaceUiState,
    focusSaver: FocusSaver,
    onToggleFollow: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.face.isNotEmpty()) {
            AsyncImage(
                model = state.face,
                contentDescription = state.name,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // fill = false 让名称列只占内容宽度，关注按钮紧跟名称右侧，而不是被顶到屏幕最右
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        UpActionButton(
            text = if (state.isFollowing) "已关注" else "关注",
            icon = if (state.isFollowing) Icons.Rounded.PersonAdd else Icons.Outlined.PersonAdd,
            highlighted = state.isFollowing,
            onClick = onToggleFollow,
            modifier = Modifier.focusSaverItem(focusSaver, "follow"),
        )
    }
}
