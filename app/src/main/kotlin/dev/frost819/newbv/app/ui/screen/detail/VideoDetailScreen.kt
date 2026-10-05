package dev.frost819.newbv.app.ui.screen.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SuggestionChip
import androidx.tv.material3.SuggestionChipDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.LoadingTip
import dev.frost819.newbv.app.ui.component.comment.CommentDialogMode
import dev.frost819.newbv.app.ui.component.comment.CommentsDialog
import dev.frost819.newbv.app.ui.component.dialog.EpisodeListButton
import dev.frost819.newbv.app.ui.component.dialog.EpisodeListDialog
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videocard.BadgeChip
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.SearchResultRoute
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.VideoDetailRoute
import dev.frost819.newbv.app.ui.navigation.VideoPlayerRoute
import dev.frost819.newbv.app.ui.navigation.navigateToVideoPlayer
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.comment.CommentViewModel
import dev.frost819.newbv.app.data.VideoSharedState
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.detail.VideoDetailUiEffect
import dev.frost819.newbv.app.viewmodel.detail.VideoDetailUiState
import dev.frost819.newbv.app.viewmodel.detail.VideoDetailViewModel
import dev.frost819.newbv.biliapi.entity.FavoriteFolderMetadata
import dev.frost819.newbv.biliapi.entity.video.RelatedVideo
import dev.frost819.newbv.biliapi.entity.video.Tag
import dev.frost819.newbv.biliapi.entity.video.VideoDetail
import dev.frost819.newbv.biliapi.entity.video.VideoPage
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.theme.LocalFocusOutlineColor
import java.text.SimpleDateFormat
import java.util.Locale

private const val PART_LIST_DIALOG_THRESHOLD = 5
private const val PART_LIST_DIALOG_PAGE_SIZE = 20

/**
 * 视频详情页路由注册。
 *
 * 从 [VideoDetailRoute] 读取 aid，通过 Hilt 注入 [VideoDetailViewModel]。
 */
fun NavGraphBuilder.videoDetailScreen(navController: NavController) {
    composable<VideoDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<VideoDetailRoute>()
        VideoDetailScreen(
            navController = navController,
            aid = route.aid,
        )
    }
}

/**
 * 视频详情页。
 *
 * 布局自上而下：
 * 1. 封面 + 标题/统计/UP/操作按钮
 * 2. 视频简介
 * 3. 分 P 列表（始终显示，含历史进度条）
 * 4. 合集列表（文字按钮 + 进度条）
 * 5. 相关视频（封面 + 标题 + UP + 播放量/弹幕/时长）
 */
@Composable
private fun VideoDetailScreen(
    navController: NavController,
    aid: Long,
) {
    val viewModel: VideoDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sharedState by viewModel.videoSharedState.collectAsState()
    // 仅采纳当前视频的共享状态，避免上一个视频的交互状态串入
    val shared = sharedState?.takeIf { it.aid == aid }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is VideoDetailUiEffect.ShowToast -> {
                    ToastUtils.show(context, effect.message)
                }
            }
        }
    }

    when {
        state.loading && state.detail == null -> LoadingScreen()
        state.error && state.detail == null -> {
            ErrorScreen(
                message = state.errorTip,
                onRetry = { viewModel.loadVideoDetail() },
            )
        }
        state.detail != null -> {
            val commentViewModel: CommentViewModel = hiltViewModel()
            VideoDetailContent(
                detail = state.detail!!,
                state = state,
                shared = shared,
                viewModel = viewModel,
                navController = navController,
                lastPlayedCid = shared?.lastPlayedCid ?: 0L,
                lastPlayedTime = shared?.lastPlayedTime ?: 0,
                commentViewModel = commentViewModel,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingTip()
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message.ifBlank { "加载失败" },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        .touchClickable(onClick = onRetry),
                onClick = onRetry,
                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
                colors =
                    ControlFocusDefaults.surfaceColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    text = "重试",
                )
            }
        }
    }
}

@Composable
private fun VideoDetailContent(
    detail: VideoDetail,
    state: VideoDetailUiState,
    shared: VideoSharedState?,
    viewModel: VideoDetailViewModel,
    navController: NavController,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    commentViewModel: CommentViewModel,
) {
    val scrollState = rememberScrollState()
    val focusSaver = rememberFocusSaver()
    focusSaver.RestoreFocus()

    var showPartListDialog by remember { mutableStateOf(false) }
    var showSeasonListDialog by remember { mutableStateOf(false) }
    var showFavoriteDialog by remember { mutableStateOf(false) }
    var seasonDialogSectionIndex by remember { mutableStateOf(0) }
    var seasonDialogEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var seasonDialogTitle by remember { mutableStateOf("") }
    var showCommentsDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoInfoHeader(
            detail = detail,
            isLiked = shared?.liked ?: false,
            isCoined = shared?.coined ?: false,
            isFavorite = shared?.favorited ?: false,
            isFollowing = state.isFollowing,
            onToggleLike = { viewModel.toggleLike((shared?.liked ?: false).not()) },
            onSendCoin = { viewModel.sendCoin() },
            onOneClickTriple = { viewModel.oneClickTripleAction() },
            onToggleFavorite = {
                if (shared?.favorited == true) {
                    showFavoriteDialog = true
                } else {
                    viewModel.toggleFavorite()
                }
            },
            onToggleFollow = { viewModel.toggleFollow() },
            onShowComments = { showCommentsDialog = true },
            onClickTag = { tag ->
                navController.navigate(SearchResultRoute(keyword = tag.name))
            },
            onPlayVideo = {
                val playCid = lastPlayedCid.takeIf { it != 0L } ?: detail.cid
                viewModel.updateVideoList(detail.aid, playCid, detail.title)
                navController.navigateToVideoPlayer(
                    VideoPlayerRoute(
                        aid = detail.aid,
                        cid = playCid,
                        bvid = detail.bvid,
                        title = detail.title,
                        cover = detail.cover,
                    ),
                )
            },
            onClickUp = {
                navController.navigate(
                    UserSpaceRoute(mid = detail.author.mid, name = detail.author.name, face = detail.author.face),
                )
            },
            focusSaver = focusSaver,
        )

        if (detail.description.isNotBlank()) {
            VideoDescription(
                description = detail.description,
                focusSaver = focusSaver,
            )
        }

        EpisodeRow(
            title = "分P",
            cells =
                detail.pages.map { page ->
                    EpisodeCell(
                        cid = page.cid,
                        title = page.title,
                        duration = page.duration,
                        isCurrent = page.cid == detail.cid,
                    )
                },
            rowKey = "parts",
            itemKeyPrefix = "part_",
            lastPlayedCid = lastPlayedCid,
            lastPlayedTime = lastPlayedTime,
            onClick = { cell ->
                viewModel.updateVideoList(detail.aid, cell.cid, detail.title)
                navController.navigateToVideoPlayer(
                    VideoPlayerRoute(
                        aid = detail.aid,
                        cid = cell.cid,
                        title = detail.title,
                        cover = detail.cover,
                    ),
                )
            },
            onShowListDialog = { showPartListDialog = true },
            focusSaver = focusSaver,
        )

        detail.ugcSeason?.let { season ->
            season.sections.forEachIndexed { sectionIndex, section ->
                EpisodeRow(
                    title = if (season.sections.size == 1) season.title else section.title,
                    cells =
                        section.episodes.map { episode ->
                            EpisodeCell(
                                cid = episode.cid,
                                title = episode.title,
                                duration = episode.duration,
                            )
                        },
                    rowKey = "seasons",
                    itemKeyPrefix = "episode_",
                    lastPlayedCid = lastPlayedCid,
                    lastPlayedTime = lastPlayedTime,
                    onClick = { cell ->
                        val episode = section.episodes.first { it.cid == cell.cid }
                        viewModel.updateVideoList(sectionIndex)
                        navController.navigateToVideoPlayer(
                            VideoPlayerRoute(
                                aid = episode.aid,
                                cid = episode.cid,
                                title = episode.title,
                                cover = episode.cover,
                            ),
                        )
                    },
                    onShowListDialog = {
                        seasonDialogSectionIndex = sectionIndex
                        seasonDialogEpisodes = section.episodes
                        seasonDialogTitle = if (season.sections.size == 1) season.title else section.title
                        showSeasonListDialog = true
                    },
                    focusSaver = focusSaver,
                )
            }
        }

        if (detail.relatedVideos.isNotEmpty()) {
            RelatedVideoRow(
                videos = detail.relatedVideos,
                onClick = { cardData -> navController.navigateFromVideoCard(cardData) },
                navController = navController,
                focusSaver = focusSaver,
            )
        }
    }

    if (showPartListDialog) {
        VideoPartListDialog(
            pages = detail.pages,
            currentCid = detail.cid,
            lastPlayedCid = lastPlayedCid,
            lastPlayedTime = lastPlayedTime,
            onDismiss = { showPartListDialog = false },
            onSelect = { page ->
                showPartListDialog = false
                viewModel.updateVideoList(detail.aid, page.cid, detail.title)
                navController.navigateToVideoPlayer(
                    VideoPlayerRoute(
                        aid = detail.aid,
                        cid = page.cid,
                        title = detail.title,
                        cover = detail.cover,
                    ),
                )
            },
        )
    }

    if (showSeasonListDialog) {
        VideoEpisodeListDialog(
            title = seasonDialogTitle,
            episodes = seasonDialogEpisodes,
            lastPlayedCid = lastPlayedCid,
            lastPlayedTime = lastPlayedTime,
            onDismiss = { showSeasonListDialog = false },
            onSelect = { episode ->
                showSeasonListDialog = false
                viewModel.updateVideoList(seasonDialogSectionIndex)
                navController.navigate(
                    VideoPlayerRoute(
                        aid = episode.aid,
                        cid = episode.cid,
                        title = episode.title,
                        cover = episode.cover,
                    ),
                ) {
                    popUpTo<VideoPlayerRoute> { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    if (showFavoriteDialog) {
        FavoriteFolderDialog(
            folders = state.favoriteFolders,
            selectedFolderIds = state.videoFavoriteFolderIds,
            onDismiss = { showFavoriteDialog = false },
            onUpdate = { folderIds ->
                viewModel.updateFavorite(folderIds)
            },
        )
    }

    if (showCommentsDialog) {
        CommentsDialog(
            aid = detail.aid,
            mode = CommentDialogMode.Detail,
            viewModel = commentViewModel,
            onDismiss = { showCommentsDialog = false },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoriteFolderDialog(
    folders: List<FavoriteFolderMetadata>,
    selectedFolderIds: Set<Long>,
    onDismiss: () -> Unit,
    onUpdate: (List<Long>) -> Unit,
) {
    val selectedIds = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val defaultFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedFolderIds) {
        selectedIds.clear()
        selectedIds.addAll(selectedFolderIds)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .focusRequester(defaultFocusRequester)
                    .onGloballyPositioned {
                        runCatching { defaultFocusRequester.requestFocus() }
                    }.width(500.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
        ) {
            Column {
                Text(
                    text = "选择收藏夹",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                androidx.compose.foundation.layout.FlowRow(
                    modifier =
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    folders.forEachIndexed { index, folder ->
                        val selected = selectedIds.contains(folder.id)
                        androidx.tv.material3.FilterChip(
                            selected = selected,
                            onClick = {
                                if (selectedIds.contains(folder.id)) {
                                    selectedIds.remove(folder.id)
                                } else {
                                    selectedIds.add(folder.id)
                                }
                                onUpdate(selectedIds.toList())
                            },
                            modifier =
                                Modifier.touchClickable(
                                    onClick = {
                                        if (selectedIds.contains(folder.id)) {
                                            selectedIds.remove(folder.id)
                                        } else {
                                            selectedIds.add(folder.id)
                                        }
                                        onUpdate(selectedIds.toList())
                                    },
                                ),
                            shape = FilterChipDefaults.shape(shape = ControlFocusDefaults.shape),
                            scale = FilterChipDefaults.scale(focusedScale = 1f),
                            colors = ControlFocusDefaults.filterColors(),
                            border = ControlFocusDefaults.filterBorder(),
                        ) {
                            Text(
                                text = folder.title,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoInfoHeader(
    detail: VideoDetail,
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorite: Boolean,
    isFollowing: Boolean,
    onToggleLike: () -> Unit,
    onSendCoin: () -> Unit,
    onOneClickTriple: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFollow: () -> Unit,
    onShowComments: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onPlayVideo: () -> Unit,
    onClickUp: () -> Unit,
    focusSaver: FocusSaver,
) {
    val coverFocusRequester = focusSaver.focusRequesterFor("cover")
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        if (focusSaver.let { it.savedKeyValue() }.isEmpty()) {
            runCatching { coverFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp, vertical = 16.dp),
        // 封面与右侧信息列垂直居中对齐
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier =
                Modifier
                    .focusSaverItem(focusSaver, "cover")
                    .weight(4f)
                    .fillMaxHeight()
                    .aspectRatio(1.6f)
                    .touchClickable(onClick = onPlayVideo),
            onClick = onPlayVideo,
            shape = CardDefaults.shape(MaterialTheme.shapes.large),
            border =
                CardDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    3.dp,
                                    LocalFocusOutlineColor.current,
                                ),
                            shape = MaterialTheme.shapes.large,
                        ),
                ),
            // 禁用焦点缩放：默认放大 1.1 倍会溢出布局压住右侧标题（与本页其它可聚焦元素一致）
            scale = CardDefaults.scale(focusedScale = 1f),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = detail.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(6f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 付费类标识：充电专属 / 付费视频（与官方 App 一致，置于标题与数据行之间）
            if (detail.isUpowerExclusive || detail.isUgcPay) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detail.isUpowerExclusive) BadgeChip("充电专属")
                    if (detail.isUgcPay) BadgeChip("付费")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val stat = detail.stat
                StatText("播放 ${stat.view.toWanString()}")
                StatSeparator()
                StatText("弹幕 ${stat.danmaku.toWanString()}")
                StatSeparator()
                StatText("点赞 ${stat.like.toWanString()}")
                StatSeparator()
                StatText("投币 ${stat.coin.toWanString()}")
                StatSeparator()
                StatText("收藏 ${stat.favorite.toWanString()}")
                StatSeparator()
                StatText("发布于 ${dateFormat.format(detail.publishDate)}")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onClickUp,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "up")
                            .touchClickable(onClick = onClickUp),
                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                    colors =
                        ControlFocusDefaults.surfaceColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AsyncImage(
                            model = detail.author.face,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = detail.author.name,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                ActionButton(
                    text = if (isFollowing) "已关注" else "关注",
                    icon = if (isFollowing) Icons.Rounded.PersonAdd else Icons.Outlined.PersonAdd,
                    highlighted = isFollowing,
                    onClick = onToggleFollow,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "follow"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "点赞",
                    icon = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    highlighted = isLiked,
                    onClick = onToggleLike,
                    onLongClick = onOneClickTriple,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "like"),
                )
                ActionButton(
                    text = "投币",
                    icon = if (isCoined) Icons.Rounded.Paid else Icons.Outlined.Paid,
                    highlighted = isCoined,
                    onClick = onSendCoin,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "coin"),
                )
                ActionButton(
                    text = "收藏",
                    icon = if (isFavorite) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                    highlighted = isFavorite,
                    onClick = onToggleFavorite,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "favorite"),
                )
                ActionButton(
                    text = "评论",
                    icon = Icons.AutoMirrored.Outlined.Comment,
                    highlighted = false,
                    onClick = onShowComments,
                    modifier =
                        Modifier
                            .focusSaverItem(focusSaver, "comments"),
                )
            }

            if (detail.tags.isNotEmpty()) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 视口向左外扩 4dp（offset）+ 首内容边距补偿：首个标签与点赞按钮
                            // 左缘对齐；外描边外沿最多伸出 2dp（outerFocusBorder），落在
                            // 视口余量内不被裁切。
                            .offset(x = (-4).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // 首尾各留 4dp 补偿视口外扩并给末标签描边留绘制空间；
                    // 上下 8dp 兼作与上一行的额外间距。
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    items(detail.tags) { tag ->
                        val tagKey = "tag_${tag.id}"
                        SuggestionChip(
                            onClick = { onClickTag(tag) },
                            modifier =
                                Modifier
                                    .focusSaverItem(focusSaver, tagKey)
                                    .touchClickable(onClick = { onClickTag(tag) }),
                            // 标签不放大，避免挤占相邻标签的焦点留白。
                            scale = SuggestionChipDefaults.scale(focusedScale = 1f),
                            shape = SuggestionChipDefaults.shape(shape = ControlFocusDefaults.shape),
                            colors = ControlFocusDefaults.suggestionColors(),
                            border = ControlFocusDefaults.suggestionBorder(),
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 外层不裁切，保留焦点外描边的绘制空间。
        modifier = modifier.touchClickable(onClick = onClick, onLongClick = onLongClick),
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor =
                    if (highlighted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun VideoDescription(
    description: String,
    focusSaver: FocusSaver,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp)
                .focusSaverItem(focusSaver, "description")
                .touchClickable(onClick = { expanded = !expanded }),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        onClick = { expanded = !expanded },
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            modifier =
                Modifier
                    .padding(16.dp)
                    .animateContentSize(),
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 选集单元（分 P 与 UGC 合集分集的公共展示模型）。
 *
 * @property cid 分集 CID（焦点 key 与续播匹配用）。
 * @property title 分集标题。
 * @property duration 时长（秒）。
 * @property isCurrent 是否为正在播放的分集（仅 UGC 单视频分 P 使用）。
 */
private data class EpisodeCell(
    val cid: Long,
    val title: String,
    val duration: Int,
    val isCurrent: Boolean = false,
)

/**
 * 选集列表行（分 P 与 UGC 合集共用）。
 *
 * 标题行（含网格按钮与续播按钮）+ 横向滚动选集按钮列表。
 * 超过 [PART_LIST_DIALOG_THRESHOLD] 个分集时显示网格按钮，点击弹出分页弹窗。
 * 有历史记录且分集数 > 1 时显示"上次看到：{分集标题}"续播按钮。
 *
 * @param title 行标题。
 * @param cells 分集列表。
 * @param rowKey 行级焦点保存 key（如 "parts"）。
 * @param itemKeyPrefix 分集焦点 key 前缀（如 "part_"，拼 cid）。
 * @param lastPlayedCid 上次播放的 CID。
 * @param lastPlayedTime 上次播放进度（秒）。
 * @param onClick 点击分集回调。
 * @param onShowListDialog 点击网格按钮回调。
 */
@Composable
private fun EpisodeRow(
    title: String,
    cells: List<EpisodeCell>,
    rowKey: String,
    itemKeyPrefix: String,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    onClick: (EpisodeCell) -> Unit,
    onShowListDialog: () -> Unit,
    focusSaver: FocusSaver,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (cells.size > PART_LIST_DIALOG_THRESHOLD) {
                Surface(
                    onClick = onShowListDialog,
                    modifier = Modifier.touchClickable(onClick = onShowListDialog),
                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                    colors =
                        ControlFocusDefaults.surfaceColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = "网格列表",
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(20.dp),
                    )
                }
            }
            if (cells.size > 1 && lastPlayedCid != 0L) {
                val lastCell = cells.find { it.cid == lastPlayedCid }
                if (lastCell != null) {
                    Surface(
                        onClick = { onClick(lastCell) },
                        modifier = Modifier.touchClickable(onClick = { onClick(lastCell) }),
                        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                        // 不放大，避免盖住相邻的选集按钮。
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors =
                            ControlFocusDefaults.surfaceColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(4.dp)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "历史",
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "上次看到：${lastCell.title}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        val focusRequester = focusSaver.focusRequesterFor(rowKey)
        LazyRow(
            modifier =
                Modifier
                    .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(rowKey) }
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 50.dp),
        ) {
            itemsIndexed(cells) { index, cell ->
                val played = if (cell.cid == lastPlayedCid) lastPlayedTime else 0
                EpisodeListButton(
                    title = cell.title,
                    duration = cell.duration,
                    played = played,
                    isCurrent = cell.isCurrent,
                    onClick = { onClick(cell) },
                    modifier =
                        if (index == 0) {
                            Modifier.focusRequester(focusRequester).focusSaverItem(focusSaver, itemKeyPrefix + cell.cid)
                        } else {
                            Modifier.focusSaverItem(focusSaver, itemKeyPrefix + cell.cid)
                        },
                )
            }
        }
    }
}


@Composable
private fun RelatedVideoRow(
    videos: List<RelatedVideo>,
    onClick: (VideoCardData) -> Unit,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()
    CollectWatchLaterEffects(watchLaterViewModel)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "相关视频",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 50.dp),
        )
        val focusRequester = focusSaver.focusRequesterFor("related")
        LazyRow(
            modifier =
                Modifier
                    .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey("related") }
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 50.dp),
        ) {
            items(videos) { video ->
                val cardData = VideoCardData.fromRelatedVideo(video)
                SmallVideoCard(
                    modifier =
                        if (video == videos.first()) {
                            Modifier
                                .width(200.dp)
                                .focusRequester(focusRequester)
                                .focusSaverItem(focusSaver, "related_${video.aid}")
                        } else {
                            Modifier
                                .width(200.dp)
                                .focusSaverItem(focusSaver, "related_${video.aid}")
                        },
                    data = cardData,
                    onClick = { onClick(cardData) },
                    onGoToDetailPage = { onClick(cardData) },
                    onGoToUpPage =
                        video.author?.mid?.let { mid ->
                            {
                                navController.navigate(
                                    UserSpaceRoute(
                                        mid = mid,
                                        name = video.author?.name ?: "",
                                        face = video.author?.face,
                                    ),
                                )
                            }
                        },
                    onAddWatchLater = { watchLaterViewModel.addToView(aid = video.aid) },
                )
            }
        }
    }
}

/**
 * 分 P 列表弹窗。
 *
 * 当分 P 数量超过 [PART_LIST_DIALOG_THRESHOLD] 时显示。
 * 委托给通用 [EpisodeListDialog]，每页 [PART_LIST_DIALOG_PAGE_SIZE] 个。
 *
 * @param pages 全部分 P 列表。
 * @param currentCid 当前 CID。
 * @param lastPlayedCid 上次播放 CID。
 * @param lastPlayedTime 上次播放进度（秒）。
 * @param onDismiss 关闭弹窗回调。
 * @param onSelect 选择分 P 回调。
 */
@Composable
private fun VideoPartListDialog(
    pages: List<VideoPage>,
    currentCid: Long,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    onDismiss: () -> Unit,
    onSelect: (VideoPage) -> Unit,
) {
    EpisodeListDialog(
        title = null,
        entries = pages,
        pageSize = PART_LIST_DIALOG_PAGE_SIZE,
        keyOf = { it.cid },
        titleOf = { it.title },
        durationOf = { it.duration },
        playedOf = { if (it.cid == lastPlayedCid) lastPlayedTime else 0 },
        isCurrentOf = { it.cid == currentCid },
        tabLabelOf = { start, end -> "P$start-$end" },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

/**
 * UGC 合集分集列表弹窗。
 *
 * 当分集数量超过 [PART_LIST_DIALOG_THRESHOLD] 时显示。
 * 委托给通用 [EpisodeListDialog]，每页 [PART_LIST_DIALOG_PAGE_SIZE] 个。
 *
 * @param title 弹窗标题。
 * @param episodes 全部分集列表。
 * @param lastPlayedCid 上次播放 CID。
 * @param lastPlayedTime 上次播放进度（秒）。
 * @param onDismiss 关闭弹窗回调。
 * @param onSelect 选择分集回调。
 */
@Composable
private fun VideoEpisodeListDialog(
    title: String,
    episodes: List<Episode>,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    onDismiss: () -> Unit,
    onSelect: (Episode) -> Unit,
) {
    EpisodeListDialog(
        title = title,
        entries = episodes,
        pageSize = PART_LIST_DIALOG_PAGE_SIZE,
        keyOf = { it.cid },
        titleOf = { it.title },
        durationOf = { it.duration },
        playedOf = { if (it.cid == lastPlayedCid) lastPlayedTime else 0 },
        isCurrentOf = { false },
        tabLabelOf = { start, end -> "$start-$end" },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}
