package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.LoadingTip
import dev.frost819.newbv.app.ui.component.dialog.EpisodeListDialog
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.ui.navigation.VideoPlayerRoute
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailUiEffect
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailUiState
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailViewModel
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.biliapi.entity.video.season.SeasonDetail
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable

/** 快速选集弹窗每页集数。 */
private const val SEASON_EPISODE_DIALOG_PAGE_SIZE = 50

/**
 * 番剧详情页路由注册。
 *
 * [SeasonDetailViewModel] 通过 SavedStateHandle 读取 [PgcFeatureRoute.seasonId]/[PgcFeatureRoute.epid]。
 */
fun NavGraphBuilder.pgcFeatureScreen(navController: NavController) {
    composable<PgcFeatureRoute> {
        SeasonDetailScreen(navController = navController)
    }
}

/**
 * 番剧详情页。
 *
 * 布局自上而下：
 * 1. 封面 + 标题/简介/播放按钮/追番按钮
 * 2. 正片列表
 * 3. 附加分集（PV/SP 等）
 */
@Composable
private fun SeasonDetailScreen(navController: NavController) {
    val viewModel: SeasonDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SeasonDetailUiEffect.ShowToast -> {
                    ToastUtils.show(context, effect.message)
                }
                is SeasonDetailUiEffect.NavigateToPlayer -> {
                    navController.navigate(
                        VideoPlayerRoute(
                            aid = effect.aid,
                            cid = effect.cid,
                            epid = effect.epid?.toLong(),
                            title = effect.title,
                            cover = effect.cover,
                        ),
                    ) {
                        popUpTo<VideoPlayerRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    when {
        state.loading && state.seasonDetail == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingTip()
            }
        }
        state.error && state.seasonDetail == null -> {
            SeasonErrorScreen(
                message = state.errorTip,
                onRetry = { viewModel.loadSeasonDetail() },
            )
        }
        state.seasonDetail != null -> {
            SeasonDetailContent(
                detail = requireNotNull(state.seasonDetail),
                state = state,
                onPlay = viewModel::onPlay,
                onToggleFollow = viewModel::toggleFollow,
                onPlayEpisode = viewModel::onPlayEpisode,
                onSwitchSeason = viewModel::onSwitchSeason,
            )
        }
    }
}

/** 番剧详情展示层；回调由页面 ViewModel 处理，便于独立验证遥控器与触屏交互。 */
@Composable
internal fun SeasonDetailContent(
    detail: SeasonDetail,
    state: SeasonDetailUiState,
    onPlay: () -> Unit,
    onToggleFollow: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onSwitchSeason: (Int) -> Unit,
) {
    val focusSaver = rememberFocusSaver()
    val entryFocusKey = remember(detail.seasonId) { focusSaver.savedKeyValue() }
    focusSaver.RestoreFocus()

    // 快速选集弹窗状态：当前打开的是哪一行的剧集
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }

    // 切换季时用 key 强制重组，重置所有 LazyRow 滚动位置
    key(detail.seasonId) {
        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .padding(bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SeasonInfoHeader(
                detail = detail,
                state = state,
                entryFocusKey = entryFocusKey,
                onPlay = onPlay,
                onToggleFollow = onToggleFollow,
                onSwitchSeason = onSwitchSeason,
                focusSaver = focusSaver,
            )

            if (detail.episodes.isNotEmpty()) {
                SeasonEpisodeRow(
                    title = "正片",
                    episodes = detail.episodes,
                    lastPlayedCid = state.historyLastPlayedCid,
                    lastPlayedTime = state.historyLastPlayedTime,
                    onClick = onPlayEpisode,
                    onShowListDialog = {
                        dialogTitle = "正片"
                        dialogEpisodes = detail.episodes
                        showEpisodeDialog = true
                    },
                    focusSaver = focusSaver,
                    rowKey = "episodes",
                )
            }

            detail.sections.forEach { section ->
                SeasonEpisodeRow(
                    title = section.title,
                    episodes = section.episodes,
                    lastPlayedCid = state.historyLastPlayedCid,
                    lastPlayedTime = state.historyLastPlayedTime,
                    onClick = onPlayEpisode,
                    onShowListDialog = {
                        dialogTitle = section.title
                        dialogEpisodes = section.episodes
                        showEpisodeDialog = true
                    },
                    focusSaver = focusSaver,
                    rowKey = "section_${section.id}",
                )
            }
        }
    }

    if (showEpisodeDialog) {
        val lastPlayedCid = state.historyLastPlayedCid
        val lastPlayedTime = state.historyLastPlayedTime
        EpisodeListDialog(
            title = dialogTitle,
            entries = dialogEpisodes,
            pageSize = SEASON_EPISODE_DIALOG_PAGE_SIZE,
            keyOf = { it.id },
            titleOf = { it.title },
            durationOf = { it.duration },
            playedOf = { episode ->
                // 仅记录最近一次观看的分集进度，其余分集无单集进度
                if (lastPlayedCid != 0L && episode.cid == lastPlayedCid) lastPlayedTime else 0
            },
            isCurrentOf = { episode -> lastPlayedCid != 0L && episode.cid == lastPlayedCid },
            tabLabelOf = { start, end -> "$start-$end" },
            onDismiss = { showEpisodeDialog = false },
            onSelect = { episode ->
                showEpisodeDialog = false
                onPlayEpisode(episode)
            },
        )
    }
}

/** 双层渐变保证文字区域和底部选集始终可读；图片缺失时自然退回主题背景。 */
@Composable
private fun SeasonInfoHeader(
    detail: SeasonDetail,
    state: SeasonDetailUiState,
    entryFocusKey: String,
    onPlay: () -> Unit,
    onToggleFollow: () -> Unit,
    onSwitchSeason: (Int) -> Unit,
    focusSaver: FocusSaver,
) {
    val background = MaterialTheme.colorScheme.background
    val backdrop =
        detail.seasons
            .firstOrNull { it.seasonId == detail.seasonId }
            ?.horizontalCover
            ?.takeIf { it.isNotBlank() }
            ?: detail.episodes.firstOrNull { it.cover.isNotBlank() }?.cover
            ?: detail.cover
    val lastEpisode =
        (detail.episodes + detail.sections.flatMap { it.episodes })
            .firstOrNull { state.historyLastPlayedCid != 0L && it.cid == state.historyLastPlayedCid }
    val initialFocusKey =
        remember {
            val validKeys =
                buildSet {
                    addAll(listOf("play", "follow", "episodes_all"))
                    detail.episodes.forEach { add("episodes_${it.id}") }
                    detail.sections.forEach { section ->
                        add("section_${section.id}_all")
                        section.episodes.forEach { add("section_${section.id}_${it.id}") }
                    }
                    detail.seasons.forEach { add("season_${it.seasonId}") }
                }
            entryFocusKey.takeIf { it in validKeys } ?: if (detail.episodes.isEmpty()) "follow" else "play"
        }
    LaunchedEffect(detail.seasonId) {
        // 等待首帧自动聚焦和 LazyRow 布局完成，避免系统自动聚焦覆盖入口保存的 key。
        kotlinx.coroutines.delay(100)
        focusSaver.saveFocusedKey(initialFocusKey)
        runCatching { focusSaver.focusRequesterFor(initialFocusKey).requestFocus() }
    }
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to background,
                    0.45f to background.copy(alpha = 0.94f),
                    1f to background.copy(alpha = 0.18f),
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0.15f),
                    0.65f to Color.Transparent,
                    1f to background,
                ),
            ),
        )
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp)) {
                val compact = maxWidth < 700.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = detail.cover,
                        contentDescription = "番剧封面",
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(if (compact) 112.dp else 132.dp)
                                .aspectRatio(0.68f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            detail.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (detail.styles.isNotEmpty()) {
                                Text(
                                    detail.styles.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (detail.newEpDesc.isNotBlank()) {
                                Text(
                                    detail.newEpDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            detail.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 540.dp),
                        )
                        if (detail.seasons.size > 1) {
                            SeasonSwitcherRow(detail, onSwitchSeason, focusSaver)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SeasonActionButton(
                                text = lastEpisode?.let { "继续播放 ${it.title}" } ?: "立即播放",
                                icon = Icons.Rounded.PlayArrow,
                                highlighted = true,
                                onClick = onPlay,
                                enabled = detail.episodes.isNotEmpty() || lastEpisode != null,
                                modifier =
                                    Modifier
                                        .weight(
                                            1f,
                                            fill = false,
                                        ).focusSaverItem(focusSaver, "play")
                                        .testTag("season_play"),
                            )
                            SeasonActionButton(
                                text = if (state.isFollowing) "已追番" else "追番",
                                icon = if (state.isFollowing) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                                highlighted = false,
                                onClick = onToggleFollow,
                                modifier = Modifier.focusSaverItem(focusSaver, "follow"),
                            )
                        }
                        if (lastEpisode != null) {
                            val time = state.historyLastPlayedTime
                            Text(
                                if (time <
                                    0
                                ) {
                                    "上次已看完"
                                } else {
                                    "上次看到 ${time / 60}:${(time % 60).toString().padStart(2, '0')}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonActionButton(
    text: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    val container =
        if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
                .copy(
                    alpha = 0.85f,
                )
        }
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.touchClickable(onClick = { if (enabled) onClick() }),
        enabled = enabled,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                        inset = (-4).dp,
                        shape = shape,
                    ),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = container,
                focusedContentColor = content,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeasonEpisodeRow(
    title: String,
    episodes: List<Episode>,
    lastPlayedCid: Long,
    lastPlayedTime: Int,
    onClick: (Episode) -> Unit,
    onShowListDialog: () -> Unit,
    focusSaver: FocusSaver,
    rowKey: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${episodes.size} 话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            SeasonActionButton(
                "全部选集",
                Icons.Rounded.ChevronRight,
                false,
                onShowListDialog,
                modifier = Modifier.focusSaverItem(focusSaver, "${rowKey}_all"),
            )
        }
        val initialIndex = remember(episodes) { episodes.indexOfFirst { it.cid == lastPlayedCid }.coerceAtLeast(0) }
        val listState =
            androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = initialIndex,
            )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - 80.dp - 64.dp) / 5).coerceIn(140.dp, 220.dp)
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        isLastWatched = lastPlayedCid != 0L && episode.cid == lastPlayedCid,
                        playedTime = if (episode.cid == lastPlayedCid) lastPlayedTime else 0,
                        onClick = { onClick(episode) },
                        modifier = Modifier.width(cardWidth).focusSaverItem(focusSaver, "${rowKey}_${episode.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    isLastWatched: Boolean,
    playedTime: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Card(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("season_episode_${episode.id}")
                    .aspectRatio(16f / 9f)
                    .touchClickable(onClick = onClick),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            scale = CardDefaults.scale(focusedScale = 1.03f),
            border =
                CardDefaults.border(
                    focusedBorder =
                        Border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                            shape = RoundedCornerShape(8.dp),
                        ),
                ),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(
                    model = episode.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isLastWatched) {
                    Text(
                        "上次观看",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    if (episode.duration > 0 && playedTime != 0) {
                        val progress =
                            if (playedTime <
                                0
                            ) {
                                1f
                            } else {
                                (playedTime.toFloat() / episode.duration).coerceIn(0f, 1f)
                            }
                        Box(
                            Modifier
                                .align(
                                    Alignment.BottomStart,
                                ).fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = 0.3f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(
                                        progress,
                                    ).height(3.dp)
                                    .background(MaterialTheme.colorScheme.border),
                            )
                        }
                    }
                }
            }
        }
        Text(
            episode.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (episode.longTitle.isNotBlank() && episode.longTitle != episode.title) {
            Text(
                episode.longTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeasonSwitcherRow(
    detail: SeasonDetail,
    onClick: (Int) -> Unit,
    focusSaver: FocusSaver,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
    ) {
        items(detail.seasons, key = { it.seasonId }) { season ->
            val current = season.seasonId == detail.seasonId
            Surface(
                onClick = { onClick(season.seasonId) },
                modifier =
                    Modifier
                        .focusSaverItem(focusSaver, "season_${season.seasonId}")
                        .touchClickable(onClick = { onClick(season.seasonId) }),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                                inset = (-3).dp,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    ),
                colors =
                    focusInvertedColors(
                        containerColor =
                            if (current) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        contentColor =
                            if (current) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
            ) {
                Text(
                    season.shortTitle.ifBlank { season.title.orEmpty() },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonErrorScreen(
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
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.border,
                                    ),
                                shape = MaterialTheme.shapes.medium,
                            ),
                    ),
                colors =
                    focusInvertedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    text = "重试",
                )
            }
        }
    }
}
