package dev.frost819.newbv.app.ui.screen.personal

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.TabbedContent
import dev.frost819.newbv.app.ui.component.settings.displayName
import dev.frost819.newbv.app.viewmodel.personal.PersonalViewModel
import dev.frost819.newbv.data.datastore.PersonalTopNavItem
import dev.frost819.newbv.data.datastore.Prefs

/**
 * 个人页内容（TopNav + 4 个子 Tab）。
 *
 * 顶部 Tab 按首选项排序，Tab 切换使用横向滑动动画。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器（由 MainScreen 传入）。
 * @param navController 导航控制器（跳转详情页等）。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel 个人页 ViewModel。
 */
@Composable
fun PersonalContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: PersonalViewModel = hiltViewModel(),
) {
    TabbedContent(
        navFocusRequester = navFocusRequester,
        tabs = PersonalTopNavItem.entries.toList(),
        initialTab = Prefs.firstPersonalTopNavItem,
        displayName = { it.displayName },
        animated = true,
        onRefresh = viewModel::refresh,
    ) { tab ->
        when (tab) {
            PersonalTopNavItem.ToView ->
                ToViewScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            PersonalTopNavItem.History ->
                HistoryScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            PersonalTopNavItem.Favorite ->
                FavoriteScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            PersonalTopNavItem.Subscription ->
                SubscriptionScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
            PersonalTopNavItem.FollowingSeason ->
                FollowingSeasonScreen(
                    viewModel = viewModel,
                    navController = navController,
                    focusSaver = focusSaver,
                )
        }
    }
}
