package dev.frost819.newbv.app.ui.screen.ugc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.TabbedContent
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.screen.home.VideoFeedGrid
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.ugc.UgcViewModel
import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2

/**
 * UGC 分区顶部导航项。
 *
 * 仅包含一级分区（有 channelId 的 [UgcTypeV2]）。
 *
 * @property ugcTypeV2 对应的 UGC 分区类型。
 */
enum class UgcTabItem(
    val ugcTypeV2: UgcTypeV2,
    val displayName: String,
) {
    Douga(UgcTypeV2.Douga, "动画"),
    Game(UgcTypeV2.Game, "游戏"),
    Kichiku(UgcTypeV2.Kichiku, "鬼畜"),
    Music(UgcTypeV2.Music, "音乐"),
    Dance(UgcTypeV2.Dance, "舞蹈"),
    Cinephile(UgcTypeV2.Cinephile, "影视"),
    Ent(UgcTypeV2.Ent, "娱乐"),
    Knowledge(UgcTypeV2.Knowledge, "知识"),
    Tech(UgcTypeV2.Tech, "科技数码"),
    Information(UgcTypeV2.Information, "资讯"),
    Food(UgcTypeV2.Food, "美食"),
    Car(UgcTypeV2.Car, "汽车"),
    Fashion(UgcTypeV2.Fashion, "时尚美妆"),
    Sports(UgcTypeV2.Sports, "体育运动"),
    Animal(UgcTypeV2.Animal, "动物"),
}

/**
 * UGC 分区内容（TopNav + 4 列网格）。
 *
 * 顶部 Tab 切换分区，内容区为视频网格 + 无限滚动。
 * 菜单键刷新当前分区数据。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel UGC ViewModel。
 */
@Composable
fun UgcContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: UgcViewModel = hiltViewModel(),
) {
    TabbedContent(
        navFocusRequester = navFocusRequester,
        tabs = UgcTabItem.entries.toList(),
        initialTab = UgcTabItem.Douga,
        displayName = { it.displayName },
        onTabSelected = { viewModel.switchType(it.ugcTypeV2) },
        onRefresh = { viewModel.refresh() },
    ) { _ ->
        UgcGrid(
            viewModel = viewModel,
            navController = navController,
            focusSaver = focusSaver,
        )
    }
}

/**
 * UGC 视频网格。
 *
 * 4 列网格 + 无限滚动，距离底部 20 条时触发加载更多。
 */
@Composable
private fun UgcGrid(
    viewModel: UgcViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsState()

    VideoFeedGrid(
        items = state.items,
        isLoading = state.loading,
        isError = state.error,
        hasMore = state.hasMore,
        keyPrefix = "ugc",
        toCardData = { item ->
            VideoCardData(
                avid = item.aid,
                title = item.title,
                cover = item.cover,
                playString = item.play.takeIf { it != -1 }.toWanString(),
                danmakuString = item.danmaku.takeIf { it != -1 }.toWanString(),
                timeString = (item.duration * 1000L).formatHourMinSec(),
                upName = item.author,
                upMid = item.authorMid,
                pubTime = item.pubTime,
            )
        },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::refresh,
        navController = navController,
        focusSaver = focusSaver,
    )
}
