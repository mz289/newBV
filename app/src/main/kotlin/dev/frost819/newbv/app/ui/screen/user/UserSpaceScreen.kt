package dev.frost819.newbv.app.ui.screen.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
import dev.frost819.newbv.app.ui.navigation.UserCollectionKind
import dev.frost819.newbv.app.ui.navigation.UserCollectionRoute
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.user.UserSpaceSection
import dev.frost819.newbv.app.viewmodel.user.UserSpaceUiEffect
import dev.frost819.newbv.app.viewmodel.user.UserSpaceUiState
import dev.frost819.newbv.app.viewmodel.user.UserSpaceViewModel
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder
import dev.frost819.newbv.biliapi.entity.user.UserFavFolder
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.focusShakeTarget
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 用户空间页。
 *
 * 展示用户信息（头像、昵称、关注按钮）、内容栏目行（投稿/合集/系列，投稿下含排序行）
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

    // 切换栏目/排序时回到网格顶部，避免停留在旧内容的滚动位置
    LaunchedEffect(gridState, state.section, state.order) {
        gridState.scrollToItem(0)
    }

    val displayedItemCount =
        when (state.section) {
            UserSpaceSection.Videos -> state.videos.size
            UserSpaceSection.Seasons, UserSpaceSection.Series -> state.collections.size
            UserSpaceSection.Fav -> state.favFolders.size
        }

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
            UserSpaceSectionRow(
                selectedSection = state.section,
                focusSaver = focusSaver,
                onSelectSection = { viewModel.selectSection(mid, it) },
            )
        }

        // 排序行仅投稿栏目显示（合集/系列没有排序维度）
        if (state.section == UserSpaceSection.Videos) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                UserSpaceSortRow(
                    selectedOrder = state.order,
                    focusSaver = focusSaver,
                    onSelectOrder = { viewModel.selectOrder(mid, it) },
                )
            }
        }

        when (state.section) {
            UserSpaceSection.Seasons, UserSpaceSection.Series -> {
                itemsIndexed(
                    items = state.collections,
                    // season_id 与 series_id 是两套 ID 空间，加前缀防 key 冲突
                    key = { _, collection ->
                        if (collection.isSeason) "season_${collection.id}" else "series_${collection.id}"
                    },
                ) { index, collection ->
                    val kind =
                        if (collection.isSeason) UserCollectionKind.Season else UserCollectionKind.Series
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
                                    kind = kind,
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
                                text = if (state.section == UserSpaceSection.Seasons) "TA还没有合集" else "TA还没有系列",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            UserSpaceSection.Fav -> {
                itemsIndexed(
                    items = state.favFolders,
                    key = { _, folder -> "fav_${folder.id}" },
                ) { index, folder ->
                    FavFolderCard(
                        folder = folder,
                        onClick = {
                            navController.navigate(
                                UserCollectionRoute(
                                    mid = mid,
                                    collectionId = folder.id,
                                    kind = UserCollectionKind.Fav,
                                    title = folder.title,
                                    cover = "",
                                ),
                            )
                        },
                        modifier = Modifier.focusSaverItem(focusSaver, "user_space_fav_$index"),
                    )
                }
                if (!state.loading && !state.error && state.favFolders.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 96.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "TA还没有公开收藏夹",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            UserSpaceSection.Videos -> {
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
 * 内容栏目行：投稿 / 合集 / 系列。
 *
 * 排序维度收纳在「投稿」栏目下（见 [UserSpaceSortRow]），与 B 站空间
 * 「投稿 → 排序」「合集和列表」的信息层级一致。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UserSpaceSectionRow(
    selectedSection: UserSpaceSection,
    focusSaver: FocusSaver,
    onSelectSection: (UserSpaceSection) -> Unit,
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
        items(UserSpaceSection.entries.size, key = { index -> index }) { index ->
            val section = UserSpaceSection.entries[index]
            FilterChip(
                selected = section == selectedSection,
                onClick = { onSelectSection(section) },
                modifier =
                    Modifier
                        .touchClickable(onClick = { onSelectSection(section) })
                        .focusSaverItem(focusSaver, "user_space_section_$index"),
                shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = FilterChipDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.filterColors(),
                border = ControlFocusDefaults.filterBorder(),
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color =
                        if (section == selectedSection) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

/**
 * 投稿排序行：最新发布 / 最多播放 / 最多收藏（仅投稿栏目显示）。
 *
 * 行首"排序"维度标签与番剧索引筛选行同款，标明这一行从属于上方选中的「投稿」。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UserSpaceSortRow(
    selectedOrder: SpaceVideoOrder,
    focusSaver: FocusSaver,
    onSelectOrder: (SpaceVideoOrder) -> Unit,
) {
    val orders =
        remember {
            listOf(
                SpaceVideoOrder.PubDate to "最新发布",
                SpaceVideoOrder.Click to "最多播放",
                SpaceVideoOrder.Stow to "最多收藏",
            )
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(56.dp),
            text = "排序",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier =
                Modifier
                    .weight(1f)
                    .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(orders.size, key = { index -> orders[index].first }) { index ->
                val (order, label) = orders[index]
                FilterChip(
                    selected = order == selectedOrder,
                    onClick = { onSelectOrder(order) },
                    modifier =
                        Modifier
                            .touchClickable(onClick = { onSelectOrder(order) })
                            .focusSaverItem(focusSaver, "user_space_sort_${order.value}"),
                    shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                    scale = FilterChipDefaults.scale(focusedScale = 1f),
                    colors = ControlFocusDefaults.filterColors(),
                    border = ControlFocusDefaults.filterBorder(),
                ) {
                    Text(
                        text = label,
                        color =
                            if (order == selectedOrder) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}

/**
 * 公开收藏夹卡片（无封面：收藏夹列表接口不返回封面，用图标块占位）。
 */
@Composable
private fun FavFolderCard(
    folder: UserFavFolder,
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
        colors = ControlFocusDefaults.surfaceColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "共${folder.mediaCount}个内容",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
