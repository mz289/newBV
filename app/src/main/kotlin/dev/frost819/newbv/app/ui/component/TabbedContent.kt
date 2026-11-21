package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
 * 内容采用"双槽位页面缓冲"（参考 Telegram Dialogs 文件夹切换）：
 * - 固定两个页面槽位，常驻 Composition ≤ 2，不随访问过的 Tab 数增长；
 * - 页面身份 = Tab 本身（经 [SaveableStateHolder] 按 Tab 稳定 key 保存/恢复），
 *   槽位只是容器，角色交换只翻转 active 标志、页面实例不搬家；
 * - 目标 Tab 已在备用槽位 → 直接复用（零内容重组）；不在任何槽位 → 替换备用槽位
 *   （旧 Tab 的 saveable 状态入 Holder，重进时恢复滚动位/焦点 key）；
 * - 切换动画复用 150ms 定长 alpha + 10% 轻位移（graphicsLayer，仅绘制期读取）；
 * - 焦点隔离：可见页不写 canFocus（父级属性会覆盖子级 focusGroup 的 Never），
 *   只对非激活页写 canFocus=false；页面"始终测量、按需放置"，未放置节点被
 *   isEligibleForFocusSearch 排除；退出动画中的页面以屏外布局偏移停放、
 *   graphicsLayer 视觉补偿——参与退出动画但不参与焦点搜索、不抢焦点；
 * - 初始备用槽位预热装载"下一个" Tab（用户尚未切换时），首个切换零重建。
 *
 * @param tabs 全部 Tab 项。
 * @param initialTab 初始（上次记住的）Tab。
 * @param displayName Tab 显示名称。
 * @param onRefresh 点击 Tab 或按菜单键时的刷新回调。
 * @param animated Tab 是否为多子页壳（true：双槽位缓冲 + 切换动画；false：单内容直接组合）。
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
    val stateHolder = rememberSaveableStateHolder()

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
                TwoSlotPageBuffer(
                    selectedTab = selectedTab,
                    tabs = tabs,
                    reorderedTabs = reorderedTabs,
                    stateHolder = stateHolder,
                    content = content,
                )
            } else {
                content(selectedTab)
            }
        }
    }
}

/**
 * 双槽位页面缓冲。槽位身份固定（index 0/1），页面身份 = Tab：
 * 切换只是翻转两个槽位的 active 标志，页面实例不搬家，Composition identity 随 Tab 保持。
 */
@Composable
private fun <T> TwoSlotPageBuffer(
    selectedTab: T,
    tabs: List<T>,
    reorderedTabs: List<T>,
    stateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val slots = remember { List(2) { PageSlot<T>(it) } }
    // 切换代次：每次真实切换 +1，守卫初始预热不覆盖"上一页"备用槽
    var switchGeneration by remember { mutableIntStateOf(0) }

    // 槽位指派（同步跟随 selectedTab 收敛）：
    // 目标已在某槽 → 只翻转 active（备用槽直接复用）；不在任何槽 → 替换非激活槽（冷启动时装载空槽）
    val current = slots.firstOrNull { it.tab == selectedTab }
    if (current == null) {
        (slots.firstOrNull { !it.active } ?: slots.first()).let { target ->
            target.tab = selectedTab
            target.revision++
        }
        switchGeneration++
    }
    slots.forEach { it.active = it.tab == selectedTab }

    // 初始备用槽预热：装载"下一个" Tab。仅在用户尚未切换时执行，
    // 否则会把刚离开的上一页挤出备用槽（快速往返 A↔B 就得重建）。
    LaunchedEffect(reorderedTabs) {
        if (reorderedTabs.size <= 1) return@LaunchedEffect
        withFrameNanos {}
        delay(400)
        if (switchGeneration > 0) return@LaunchedEffect
        val next = reorderedTabs[1]
        if (slots.none { it.tab == next }) {
            slots.firstOrNull { !it.active }?.tab = next
        }
    }

    Box(modifier.fillMaxSize()) {
        slots.forEach { slot ->
            val tab = slot.tab ?: return@forEach
            key(slot.index) {
                Box(Modifier.zIndex(if (slot.active) 1f else 0f)) {
                    KeptTabPage(slot = slot) {
                        stateHolder.SaveableStateProvider(pageKey(tabs, tab)) { content(tab) }
                    }
                }
            }
        }
    }
}

/** 页面身份 key：Tab 在原始列表中的下标（稳定、可保存）。 */
private fun <T> pageKey(
    tabs: List<T>,
    tab: T,
): Int = tabs.indexOf(tab)

/**
 * 页面槽位状态。槽位身份固定（index），页面身份 = [tab]；[revision] 随装载递增，
 * 作为"页面身份变化"信号（Alpha 归零等）——槽位下标绝不充当页面身份。
 */
private class PageSlot<T>(
    val index: Int,
) {
    var tab by mutableStateOf<T?>(null)
    var active by mutableStateOf(false)
    var placed by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    val alpha = Animatable(0f)
}

/**
 * 槽位页面：切换只做绘制层过渡（alpha + 10% 轻位移）。
 *
 * - [PageSlot.alpha] 仅经 graphicsLayer/layout lambda 读取：动画只触发绘制，稳态零重组/零布局；
 * - 页面身份变化（备用槽装载新 Tab，revision 递增）时 Alpha 归零，不沿用上一页动画值；
 * - 非激活页只写 canFocus=false（可见页不碰 canFocus——父级属性覆盖子级，会把 grid item
 *   focusGroup 的 Focusability.Never 提升为可聚焦，方向键停在无视觉组节点上）；
 * - 布局放置门：每次恰好测量一次、按需放置——未放置节点被 isEligibleForFocusSearch 排除；
 *   退出动画中的页面以屏外布局偏移停放、graphicsLayer 反向补偿（绘制位置不变、焦点矩形
 *   落在屏外）——参与退出动画但不参与焦点搜索、不抢焦点；
 * - 稳态隐藏保留组合与测量（预热/状态存活），切回只需重新放置。
 */
@Composable
private fun <T> KeptTabPage(
    slot: PageSlot<T>,
    content: @Composable () -> Unit,
) {
    val tab = slot.tab ?: return
    val visible = slot.active
    if (visible && !slot.placed) slot.placed = true

    // 页面身份变化（备用槽装载新 Tab）：Alpha 归零。先于可见性动画效果执行。
    LaunchedEffect(slot.revision) {
        slot.alpha.snapTo(0f)
    }

    LaunchedEffect(visible) {
        if (visible) {
            slot.alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(150, easing = LinearOutSlowInEasing),
            )
        } else {
            slot.alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(150, easing = FastOutSlowInEasing),
            )
            slot.placed = false
        }
    }
    DisposableEffect(tab) {
        onDispose {
            // 槽位被替换（页面身份离场）：清掉退场标记，槽位可安全装载下一页
            slot.placed = false
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusProperties { if (!visible) canFocus = false }
            .graphicsLayer {
                this.alpha = slot.alpha.value
                // 退出动画中的页面被停放到屏外布局偏移（见下方 layout），此处反向补偿：
                // 继续参与退出动画（轻位移 + 淡出），但焦点矩形在屏外、不进焦点搜索。
                val exiting = !visible && slot.placed
                val park = if (exiting) size.width else 0f
                // Telegram 式轻位移：进入页自右侧 10% 屏宽滑入，退出页反向滑出
                val slide = (1f - slot.alpha.value) * size.width * 0.1f
                translationX = -park + (if (visible) slide else -slide)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (slot.placed) {
                        val exiting = !slot.active
                        placeable.place(x = if (exiting) constraints.maxWidth else 0, y = 0)
                    }
                }
            }
            .drawWithContent { if (slot.alpha.value > 0.01f) drawContent() },
    ) {
        content()
    }
}
