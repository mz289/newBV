package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
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
 * 内容保持采用"常驻组合"（Telegram ViewPagerFixed 思想）：已访问过的 Tab 页面
 * 常驻组合树、切换仅做绘制层过渡（graphicsLayer alpha/位移——零内容重组、零布局），
 * 切回即原位（滚动位置、焦点记忆、缩略图缓存天然保留）；未访问的 Tab 首次选中才
 * 组合（懒初始化，保住首屏）。隐藏页经 focusProperties canFocus=false 整体移出
 * 焦点树，焦点搜索不会命中隐藏页。
 *
 * @param tabs 全部 Tab 项。
 * @param initialTab 初始（上次记住的）Tab。
 * @param displayName Tab 显示名称。
 * @param onRefresh 点击 Tab 或按菜单键时的刷新回调。
 * @param animated Tab 是否为多子页壳（true：常驻叠放切换；false：单内容直接组合）。
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

    val reorderedTabs =
        remember {
            val startIndex = tabs.indexOf(initialTab)
            if (startIndex == -1) tabs else tabs.drop(startIndex) + tabs.take(startIndex)
        }

    // 已访问 Tab：首次选中才组合（懒初始化），此后常驻组合树（切回零重建）。
    val visitedTabs = remember { mutableStateListOf<T>(initialTab) }
    if (selectedTab !in visitedTabs) visitedTabs.add(selectedTab)

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
                        if (tab !in visitedTabs) visitedTabs.add(tab)
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
                reorderedTabs.forEach { tab ->
                    if (tab in visitedTabs) {
                        key(tab) {
                            Box(Modifier.zIndex(if (tab == selectedTab) 1f else 0f)) {
                                KeptTabPage(visible = tab == selectedTab) { content(tab) }
                            }
                        }
                    }
                }
            } else {
                content(selectedTab)
            }
        }
    }
}

/**
 * 常驻 Tab 页：叠放于同一 Box，切换只做绘制层过渡（alpha + 轻位移）。
 * - 动画值仅经 graphicsLayer lambda 读取：只触发绘制阶段，无重组/布局开销；
 * - 隐藏页 canFocus=false 整树移出焦点搜索；若切换瞬间仍持有焦点会被系统清除；
 * - 稳态隐藏（alpha≈0）时 drawWithContent 跳过绘制，不占渲染预算。
 */
@Composable
private fun KeptTabPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        alpha.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                150,
                easing = if (visible) LinearOutSlowInEasing else FastOutSlowInEasing,
            ),
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .focusProperties { canFocus = visible }
            .graphicsLayer {
                this.alpha = alpha.value
                // Telegram 式轻位移：进入页自右侧 10% 屏宽滑入，退出页反向滑出
                val slide = (1f - alpha.value) * size.width * 0.1f
                translationX = if (visible) slide else -slide
            }
            .drawWithContent { if (alpha.value > 0.01f) drawContent() },
    ) {
        content()
    }
}
