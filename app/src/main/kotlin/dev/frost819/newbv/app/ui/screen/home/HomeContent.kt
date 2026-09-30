package dev.frost819.newbv.app.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.TabbedContent
import dev.frost819.newbv.app.ui.component.settings.displayName
import dev.frost819.newbv.app.viewmodel.home.HomeViewModel
import dev.frost819.newbv.data.datastore.HomeTopNavItem
import dev.frost819.newbv.data.datastore.Prefs

/**
 * 首页内容（TopNav + 3 个子 Tab）。
 *
 * 顶部 Tab 按首选项排序，Tab 切换使用横向滑动动画；动态页首次切入时懒加载。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器（由 MainScreen 传入）。
 * @param navController 导航控制器（跳转详情页等）。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 */
@Composable
fun HomeContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TabbedContent(
        navFocusRequester = navFocusRequester,
        tabs = HomeTopNavItem.entries.toList(),
        initialTab = Prefs.firstHomeTopNavItem,
        displayName = { it.displayName },
        animated = true,
        onTabSelected = { tab ->
            if (tab == HomeTopNavItem.Dynamics && uiState.dynamicItems.isEmpty()) {
                viewModel.loadDynamic()
            }
        },
        onRefresh = viewModel::refresh,
    ) { tab ->
        when (tab) {
            HomeTopNavItem.Recommend ->
                RecommendScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            HomeTopNavItem.Popular ->
                PopularScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            HomeTopNavItem.Dynamics ->
                DynamicsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
        }
    }
}
