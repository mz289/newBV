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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.material3.Scaffold as Material3Scaffold
import kotlinx.coroutines.delay

/**
 * 顶部 Tab 内容壳（[TopNav] + 内容区 + 菜单键刷新）。
 *
 * Tab 按首选项把 [initialTab] 旋转到首位；内容区获得焦点后 TopNav 收紧内边距；
 * 菜单键刷新当前 Tab 并把焦点交还 TopNav。
 *
 * 内容保持采用"常驻组合"（Telegram ViewPagerFixed 思想）：已访问过的 Tab 页面
 * 常驻组合树、切换仅做绘制层过渡（graphicsLayer alpha/位移——零内容重组、零布局），
 * 切回即原位（滚动位置、焦点记忆、缩略图缓存天然保留）；未访问的 Tab 首帧后按帧
 * 空闲预热组合（同一帧内一次只组合一页，保首屏不抖），首个切换不再现场组合整页。
 * 隐藏页"只测量不放置"整体移出焦点搜索——[focusProperties] canFocus=false 会被页内的
 * focusGroup()/tv-material Card 等中间 FocusTarget 截断，单靠它挡不住卡片子项；
 * 未放置节点由 Compose 的 isEligibleForFocusSearch 直接排除，是硬保证。
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

    // 已访问 Tab：常驻组合树（切回零重建）。首屏只组合当前页，其余页在首帧后预热。
    val visitedTabs = remember { mutableStateListOf<T>(initialTab) }
    if (selectedTab !in visitedTabs) visitedTabs.add(selectedTab)

    // 预热未访问页：等首帧稳定后逐页组合（每页占一帧 + 小间隔，避免连帧抖动）。
    // 预热页只测量不放置——不进焦点搜索、不绘制，但网格会完成组合与首屏项测量，
    // 之后切换只做放置 + alpha 过渡，"第一次切换卡一下"随之消失。
    LaunchedEffect(animated, reorderedTabs) {
        if (!animated || reorderedTabs.size <= 1) return@LaunchedEffect
        withFrameNanos { }
        delay(400)
        reorderedTabs.forEach { tab ->
            if (tab !in visitedTabs) {
                visitedTabs.add(tab)
                withFrameNanos { }
                delay(60)
            }
        }
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
 * - 隐藏页 canFocus=false 挡住直接焦点请求；但页内容的中间 FocusTarget
 *   （focusGroup()/tv-material Card）会截断祖先 focusProperties，卡片子项仍可聚焦；
 * - 因此隐藏页在退出动画结束后"只测量不放置"：未放置节点被
 *   isEligibleForFocusSearch 排除，焦点搜索与方向键永远进不去隐藏页；
 * - 稳态隐藏仍保留组合与测量（预热/状态存活），切回只需重新放置。
 */
@Composable
private fun KeptTabPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    // 本页是否参与放置：可见页 + 退出动画中的旧页。动画结束即摘除内容放置。
    var placed by remember { mutableStateOf(visible) }
    if (visible) placed = true

    LaunchedEffect(visible) {
        if (visible) {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(150, easing = LinearOutSlowInEasing),
            )
        } else {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(150, easing = FastOutSlowInEasing),
            )
            placed = false
        }
    }
    Box(
        modifier
            .fillMaxSize()
            // 只对隐藏页关闭焦点；可见页不要写 canFocus=true —— 父级属性会覆盖子级，
            // 把 grid item 的 focusGroup()(Focusability.Never) 变成可聚焦目标，
            // 方向键会停在看不见的组节点上。
            .focusProperties { if (!visible) canFocus = false }
            .graphicsLayer {
                this.alpha = alpha.value
                // Telegram 式轻位移：进入页自右侧 10% 屏宽滑入，退出页反向滑出
                val slide = (1f - alpha.value) * size.width * 0.1f
                translationX = if (visible) slide else -slide
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (placed) placeable.place(0, 0)
                }
            }
            .drawWithContent { if (alpha.value > 0.01f) drawContent() },
    ) {
        content()
    }
}
