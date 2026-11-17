package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.Scaffold as Material3Scaffold

/**
 * 顶部 Tab 内容壳（[TopNav] + 内容区 + 菜单键刷新）。
 *
 * Tab 按首选项把 [initialTab] 旋转到首位；内容区获得焦点后 TopNav 收紧内边距；
 * 菜单键刷新当前 Tab 并把焦点交还 TopNav。
 *
 * @param tabs 全部 Tab 项。
 * @param initialTab 初始（上次记住的）Tab。
 * @param displayName Tab 显示名称。
 * @param onRefresh 点击 Tab 或按菜单键时的刷新回调。
 * @param animated Tab 切换是否使用横向滑动动画（多子页壳为 true，数据切换单内容壳为 false）。
 * @param onTabSelected Tab 切换回调（如懒加载），同 Tab 重复聚焦不触发。
 * @param content Tab 内容。
 */
@Composable
fun <T> TabbedContent(
    navFocusRequester: FocusRequester,
    tabs: List<T>,
    initialTab: T,
    displayName: (T) -> String,
    onRefresh: (T) -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    onTabSelected: (T) -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var focusOnContent by remember { mutableStateOf(false) }

    // 各 Tab 的组合状态（滚动位置、焦点记忆等 rememberSaveable 状态）跨切换保留：
    // 切回不再全页从头组合，Lazy 网格直接恢复到原位置——"切回秒回原位"。
    val stateHolder = rememberSaveableStateHolder()

    // key 用固定映射的 displayName：应用内稳定唯一，不随用户设置漂移。
    // SaveableStateProvider 是 @Composable 扩展，须在组合上下文直接调用。

    val reorderedTabs =
        remember {
            val startIndex = tabs.indexOf(initialTab)
            if (startIndex == -1) tabs else tabs.drop(startIndex) + tabs.take(startIndex)
        }

    Material3Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier.focusRequester(navFocusRequester),
                items = reorderedTabs,
                displayName = displayName,
                selectedIndex = reorderedTabs.indexOf(selectedTab),
                isLargePadding = !focusOnContent,
                onSelectedChanged = { tab ->
                    if (tab != selectedTab) {
                        selectedTab = tab
                        onTabSelected(tab)
                    }
                },
                onClick = onRefresh,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                modifier
                    .padding(innerPadding)
                    .onFocusChanged { focusOnContent = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                            onRefresh(selectedTab)
                            navFocusRequester.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                        false
                    },
        ) {
            if (animated) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "tabbed-content",
                    transitionSpec = {
                        // 固定 150ms 标准曲线：默认 spring 无明确时长，低端盒子感知拖沓
                        val fade = tween<Float>(150, easing = FastOutSlowInEasing)
                        val slide = tween<IntOffset>(150, easing = FastOutSlowInEasing)
                        val coefficient = 10
                        if (reorderedTabs.indexOf(targetState) < reorderedTabs.indexOf(initialState)) {
                            fadeIn(fade) + slideInHorizontally(slide) { -it / coefficient } togetherWith
                                fadeOut(fade) + slideOutHorizontally(slide) { it / coefficient }
                        } else {
                            fadeIn(fade) + slideInHorizontally(slide) { it / coefficient } togetherWith
                                fadeOut(fade) + slideOutHorizontally(slide) { -it / coefficient }
                        }
                    },
                ) { tab ->
                    stateHolder.SaveableStateProvider(key = displayName(tab)) { content(tab) }
                }
            } else {
                stateHolder.SaveableStateProvider(key = displayName(selectedTab)) { content(selectedTab) }
            }
        }
    }
}
