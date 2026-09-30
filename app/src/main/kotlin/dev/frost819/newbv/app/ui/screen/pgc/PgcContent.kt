package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.InfiniteScrollEffect
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.POSTER_CARD_MIN_WIDTH
import dev.frost819.newbv.app.ui.component.PgcCarousel
import dev.frost819.newbv.app.ui.component.SKELETON_FIRST_SCREEN_COUNT
import dev.frost819.newbv.app.ui.component.SkeletonSeasonCard
import dev.frost819.newbv.app.ui.component.TabbedContent
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SeasonCard
import dev.frost819.newbv.app.ui.component.videocard.SeasonCardData
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.viewmodel.pgc.AnimeHomeViewModel
import dev.frost819.newbv.app.viewmodel.pgc.PgcViewModel
import dev.frost819.newbv.biliapi.entity.pgc.PgcType

/**
 * PGC 影视顶部导航项。
 *
 * 对应 6 个 PGC 分区。
 *
 * @property pgcType 对应的 PGC 分区类型。
 */
enum class PgcTabItem(
    val pgcType: PgcType,
    val displayName: String,
) {
    Anime(PgcType.Anime, "番剧"),
    GuoChuang(PgcType.GuoChuang, "国创"),
    Movie(PgcType.Movie, "电影"),
    Documentary(PgcType.Documentary, "纪录片"),
    Tv(PgcType.Tv, "电视剧"),
    Variety(PgcType.Variety, "综艺"),
}

/**
 * PGC 影视内容（TopNav + 分区内容）。
 *
 * 「番剧」Tab 使用 [AnimeHomeContent] 富布局（最近更新/热门推荐/编辑精选/
 * 新番时间表/番剧索引），其余分区保持轮播图 + 4 列网格 + 无限滚动。
 * 菜单键刷新当前分区数据。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel PGC ViewModel（番剧外的 5 个分区）。
 * @param animeViewModel 番剧页 ViewModel。
 */
@Composable
fun PgcContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: PgcViewModel = hiltViewModel(),
    animeViewModel: AnimeHomeViewModel = hiltViewModel(),
) {
    // rememberSaveable 记住上次停留的 Tab；恢复到非番剧 Tab 时触发对应分区懒加载
    var selectedTab by rememberSaveable { mutableStateOf(PgcTabItem.Anime) }

    LaunchedEffect(Unit) {
        if (selectedTab != PgcTabItem.Anime) {
            viewModel.switchType(selectedTab.pgcType)
        }
    }

    TabbedContent(
        navFocusRequester = navFocusRequester,
        tabs = PgcTabItem.entries.toList(),
        initialTab = selectedTab,
        displayName = { it.displayName },
        onTabSelected = { tab ->
            if (tab == PgcTabItem.Anime) {
                animeViewModel.loadIfNeeded()
            } else {
                viewModel.switchType(tab.pgcType)
            }
        },
        onRefresh = { tab ->
            if (tab == PgcTabItem.Anime) {
                animeViewModel.refreshAll()
            } else {
                viewModel.refresh()
            }
        },
    ) { tab ->
        if (tab == PgcTabItem.Anime) {
            AnimeHomeContent(
                navController = navController,
                focusSaver = focusSaver,
                viewModel = animeViewModel,
            )
        } else {
            PgcGrid(
                viewModel = viewModel,
                navController = navController,
                focusSaver = focusSaver,
            )
        }
    }
}

/**
 * PGC 番剧网格（含轮播图）。
 *
 * 第一行为全宽轮播图，后续为 4 列番剧卡片网格 + 无限滚动。
 * 距离底部 10 条时触发加载更多；数据为空时懒加载。
 */
@Composable
private fun PgcGrid(
    viewModel: PgcViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    InfiniteScrollEffect(
        state = gridState,
        itemCount = { state.items.size },
        threshold = 10,
        onLoadMore = viewModel::loadMore,
    )

    TvLazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(POSTER_CARD_MIN_WIDTH),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 轮播图：全宽，始终占位避免异步加载后内容下移
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (state.carouselItems.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PgcCarousel(
                        modifier = Modifier.focusSaverItem(focusSaver, "pgc_carousel"),
                        data = state.carouselItems,
                        onClick = { item ->
                            val seasonId = item.seasonId?.toLong()
                            if (seasonId != null) {
                                navController.navigate(PgcFeatureRoute(seasonId = seasonId))
                            }
                        },
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                )
            }
        }

        if (state.items.isEmpty() && state.loading) {
            // 首屏加载中：同构骨架屏占位（P0-4）
            items(SKELETON_FIRST_SCREEN_COUNT) { SkeletonSeasonCard() }
        }

        itemsIndexed(
            items = state.items,
            key = { index, _ -> index },
        ) { index, item ->
            val cardData =
                remember(item) {
                    SeasonCardData(
                        seasonId = item.seasonId,
                        title = item.title,
                        subTitle = item.subTitle,
                        cover = item.cover,
                        rating = item.rating,
                    )
                }
            SeasonCard(
                data = cardData,
                onClick = {
                    navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                },
                onGoToDetailPage = {
                    navController.navigate(PgcFeatureRoute(seasonId = item.seasonId.toLong()))
                },
                modifier = Modifier.focusSaverItem(focusSaver, "pgc_item_$index"),
            )
        }

        if (state.items.isNotEmpty() || !state.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ListFooterTip(
                    isLoading = state.loading,
                    isError = state.error,
                    hasMore = state.hasMore,
                    itemsIsEmpty = state.items.isEmpty(),
                    onRetry = viewModel::refresh,
                )
            }
        }
    }
}
