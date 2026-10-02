package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.TabbedContent
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
 * 「番剧」Tab 使用 [AnimeHomeContent] 富布局（模块化板块/热播榜/时间表/索引），
 * 其余分区使用 [PgcHomeContent] 同构富布局（轮播图/猜你喜欢/热播榜/
 * 国创时间表/索引 chips，板块按接口数据动态渲染），分区间状态隔离。
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
            viewModel.loadIfNeeded(selectedTab.pgcType)
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
                viewModel.loadIfNeeded(tab.pgcType)
            }
        },
        onRefresh = { tab ->
            if (tab == PgcTabItem.Anime) {
                animeViewModel.refreshAll()
            } else {
                viewModel.refresh(tab.pgcType)
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
            PgcHomeContent(
                pgcType = tab.pgcType,
                viewModel = viewModel,
                focusSaver = focusSaver,
                navController = navController,
            )
        }
    }
}
