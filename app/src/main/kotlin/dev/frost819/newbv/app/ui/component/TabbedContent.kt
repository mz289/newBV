package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.material3.Scaffold as Material3Scaffold

/**
 * 顶部 Tab 内容壳（[TopNav] + 内容区 + 菜单键刷新）。
 *
 * Tab 按首选项把 [initialTab] 旋转到首位；内容区获得焦点后 TopNav 收紧内边距；
 * 菜单键刷新当前 Tab 并把焦点交还 TopNav。
 *
 * 内容采用"双槽位页面缓冲"（参考 Telegram Dialogs 文件夹切换），切换为**即时替换、
 * 无过渡动画**（旧页立即退场，不存在新旧页并存的过渡期）：
 * - 固定两个页面槽位，常驻 Composition ≤ 2，不随访问过的 Tab 数增长；
 * - 页面身份 = Tab 本身（经 [SaveableStateHolder] 按 Tab 稳定 key 保存/恢复），
 *   槽位只是容器，角色交换只翻转 active 标志、页面实例不搬家；
 * - 目标 Tab 已在备用槽位 → 直接复用（零内容重组）；不在任何槽位 → 替换备用槽位
 *   （旧 Tab 的 saveable 状态入 Holder，重进时恢复滚动位/焦点 key）；冷装载的页面
 *   先上一帧空壳再组合真实内容——tab 选中/焦点当帧响应，不被整页组合（单帧
 *   数百 ms 级）阻塞，焦点的切换不等待页面的加载；
 * - 焦点隔离：可见页不写 canFocus（父级属性会覆盖子级 focusGroup 的 Never），
 *   只对非激活页写 canFocus=false；页面"始终测量、仅激活页放置"，未放置节点被
 *   isEligibleForFocusSearch 排除——非激活/备用页不进焦点搜索、不抢焦点、不绘制；
 * - 初始备用槽位预热装载"下一个" Tab（用户尚未切换时），首个切换零重建。
 *
 * @param tabs 全部 Tab 项。
 * @param initialTab 初始（上次记住的）Tab。
 * @param displayName Tab 显示名称。
 * @param onRefresh 点击 Tab 或按菜单键时的刷新回调。
 * @param animated Tab 是否为多子页壳（true：双槽位缓冲；false：单内容直接组合）。
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
 * 切换即时替换——只翻转槽位 active 标志，页面实例不搬家，Composition identity 随 Tab 保持；
 * 无过渡动画，旧页在切换当帧即退出放置。
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
    // 用户是否已实际切换过：初始装载不计入；
    // 预热仅在尚无用户切换时执行（否则会挤掉刚离开的上一页）
    var userSwitched by remember { mutableStateOf(false) }

    // 槽位指派（同步跟随 selectedTab 收敛）：
    // 目标已在某槽 → 只翻转 active（备用槽直接复用）；不在任何槽 → 替换非激活槽（冷启动时装载空槽）
    // 冷装载置 deferContent：本帧只上空壳，tab 高亮/焦点当帧落地，重内容下一帧再组合——
    // 焦点切换不等待页面加载（整页组合是单帧 400ms 级的重活，不能和选中反馈捆在同一帧）。
    val current = slots.firstOrNull { it.tab == selectedTab }
    if (current == null) {
        val slot = slots.firstOrNull { !it.active } ?: slots.first()
        slot.tab = selectedTab
        slot.deferContent = true
    }
    slots.forEach { it.active = it.tab == selectedTab }

    // 初始备用槽预热：等首屏渲染稳定（30 帧 ≈ 500ms）后装载"下一个" Tab。
    // 仅在用户尚未切换时执行，否则会把刚离开的上一页挤出备用槽（快速往返 A↔B 就得重建）。
    // 按帧计数而非 delay：随帧时钟推进，测试时钟下确定性快进。
    LaunchedEffect(reorderedTabs) {
        println("PROBE prewarm effect enter")
        if (reorderedTabs.size <= 1) return@LaunchedEffect
        repeat(30) { withFrameNanos {} }
        println("PROBE prewarm frames done userSwitched=$userSwitched")
        if (userSwitched) return@LaunchedEffect
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
 * 页面槽位状态。槽位身份固定（index），页面身份 = [tab]；无动画状态，
 * active 即"是否放置/是否可聚焦"的唯一事实来源。
 */
private class PageSlot<T>(
    val index: Int,
) {
    var tab by mutableStateOf<T?>(null)
    var active by mutableStateOf(false)

    /** 冷装载页延迟一帧再组合内容：选中帧只渲染空壳，焦点/高亮不被整页组合阻塞。 */
    var deferContent by mutableStateOf(false)
}

/**
 * 槽位页面：无过渡动画，切换即替换。
 *
 * - 非激活页只写 canFocus=false（可见页不碰 canFocus——父级属性覆盖子级，会把 grid item
 *   focusGroup 的 Focusability.Never 提升为可聚焦，方向键停在无视觉组节点上）；
 * - 布局放置门：每次恰好测量一次、仅激活页放置——未放置节点被 isEligibleForFocusSearch
 *   排除，备用页/旧页不进焦点搜索、不抢焦点、不绘制；
 * - 稳态隐藏保留组合与测量（预热/状态存活），切回只需重新放置。
 */
@Composable
private fun <T> KeptTabPage(
    slot: PageSlot<T>,
    content: @Composable () -> Unit,
) {
    slot.tab ?: return
    // 冷装载延迟提交：空壳先占满一帧（选中帧只画 tab 高亮，不被整页组合拖住），
    // 一个 vsync 周期后（下一帧窗口）再组合真实内容。预热装载不经此路径（deferContent
    // 保持 false）。用 delay 而非 withFrameNanos：两者生产语义等价，但 delay 走协程
    // 虚拟时钟，Robolectric 停钟下 advanceTimeBy 无法把 withFrameNanos 推进切成单帧
    // （一次 advance 会连排空多帧），壳态不可断言。
    LaunchedEffect(slot.tab) {
        if (slot.deferContent) {
            delay(16)
            slot.deferContent = false
        }
    }
    CompositionLocalProvider(LocalPageActive provides slot.active) {
        Box(
            Modifier
                .fillMaxSize()
                .focusProperties { if (!slot.active) canFocus = false }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (slot.active) placeable.place(0, 0)
                    }
                },
        ) {
            if (slot.deferContent) {
                // 壳帧：仅占位，无内容组合——空 Box 不可聚焦，焦点留在 Tab 行
                Box(Modifier.fillMaxSize())
            } else {
                content()
            }
        }
    }
}
