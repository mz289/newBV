package dev.frost819.newbv.app.ui.component

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.core.theme.BVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 双槽位页面缓冲验收测试（§10）：
 * - 生命周期：连续访问 5 个 Tab 后存活 Composition ≤ 2；槽位交换后页面身份不串位；
 * - 复用：目标已在备用槽位时不重建（remember 实例同址）；
 * - 状态：被淘汰的 Tab 重进后 LazyGridState 滚动位经 SaveableStateHolder 恢复；
 * - 焦点：↓ 进当前页、切换动画中途 ↓ 落最新目标页、备份页不可达；
 * - 快速切换与动画竞争；animated=false 保持旧行为。
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class TabbedTwoSlotTest {
    @get:Rule val compose = createComposeRule()

    private val tabs = listOf("A", "B", "C", "D", "E")

    // —— 每个用例独立（JUnit 每方法新建实例）——
    private val creations = mutableMapOf<String, Int>()
    private val identity = mutableMapOf<String, Any>()
    private val liveTabs = mutableSetOf<String>()
    private val gridStates = mutableMapOf<String, LazyGridState>()
    private val scrollTargets = mutableMapOf<String, Int>()
    private val scrolledOnce = mutableSetOf<String>()
    private val selectedLog = mutableListOf<String>()
    private val disposeSnapshots = mutableMapOf<String, Pair<Int, Int>>()

    @Composable
    private fun PageContent(tab: String) {
        DisposableEffect(tab) {
            liveTabs.add(tab)
            onDispose { liveTabs.remove(tab) }
        }
        // remember 执行一次 = 一次页面组合创建；实例身份用于判定"未重建"
        remember {
            creations.merge(tab, 1, Int::plus)
            identity[tab] = Any()
        }
        val grid = rememberLazyGridState()
        DisposableEffect(tab) {
            gridStates[tab] = grid
            onDispose {
                disposeSnapshots[tab] = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
                if (gridStates[tab] === grid) gridStates.remove(tab)
            }
        }
        // 仅首次组合滚动到位：被淘汰重进的页面若断言仍是 target，只能来自 saveable 恢复
        LaunchedEffect(Unit) {
            val target = scrollTargets[tab] ?: 0
            if (target > 0 && tab !in scrolledOnce) {
                scrolledOnce.add(tab)
                grid.scrollToItem(target)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.testTag("card-$tab").fillMaxWidth().height(120.dp).focusable())
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = grid,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(30) { index ->
                    Box(Modifier.height(80.dp).fillMaxWidth())
                }
            }
        }
    }

    @Composable
    private fun Shell(navFocus: FocusRequester, animated: Boolean = true) {
        BVTheme {
            TabbedContent(
                navFocusRequester = navFocus,
                tabs = tabs,
                initialTab = tabs.first(),
                displayName = { it },
                onRefresh = {},
                animated = animated,
            ) { tab ->
                selectedLog.add(tab)
                PageContent(tab)
            }
        }
    }

    private fun focused(tag: String) =
        runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun setUpShell(animated: Boolean = true): FocusRequester {
        val navFocus = FocusRequester()
        compose.setContent { Shell(navFocus, animated) }
        compose.waitForIdle()
        compose.runOnIdle { navFocus.requestFocus() }
        compose.waitForIdle()
        return navFocus
    }

    /** 连续访问 5 个 Tab：存活 Composition ≤ 2；回到已淘汰重进的 Tab 身份正确、无重复创建历史。 */
    @Test
    fun fiveTabsVisited_compositionBoundedAtTwo() {
        setUpShell()
        // 初始只有当前页（初始预热可能在空闲期把下一个 Tab 装入备用槽）
        assertTrue("A" in liveTabs)
        assertTrue(liveTabs.size <= 2)

        repeat(4) { press(Key.DirectionRight) } // A→B→C→D→E
        assertEquals(
            "selected=$selectedLog live=$liveTabs creations=$creations",
            setOf("D", "E"), liveTabs,
        )
        // 每个访问过的 Tab 都只组合过一次（淘汰 ≠ 重建，重进才会再建）
        assertEquals(1, creations["A"])
        assertEquals(1, creations["B"])
        assertEquals(1, creations["C"])
        assertEquals(1, creations["D"])
        assertEquals(1, creations["E"])

        // E→D→C：C 已被淘汰（备用槽被 E 占据）→ 重进重建；备用槽语义=刚离开的上一页
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertEquals(setOf("C", "D"), liveTabs)
        assertEquals("C 在 R4 被淘汰、重进重建", 2, creations["C"])
        assertEquals(1, creations["D"])
        assertEquals(1, creations["E"])
    }

    /** 目标已在备用槽位（A→B→A）：零重建，remember 实例同址。 */
    @Test
    fun targetInBackupSlot_reusedNotRecreated() {
        setUpShell()
        val aIdentity = identity["A"]

        press(Key.DirectionRight) // → B
        press(Key.DirectionLeft) // → A（备用槽复用）

        assertEquals(1, creations["A"])
        assertEquals("复用时页面实例必须原封不动", aIdentity, identity["A"])
        assertEquals(setOf("A", "B"), liveTabs)
    }

    /** 被淘汰的 Tab 重进：新组合 + LazyGridState 滚动位经 SaveableStateHolder 恢复到淘汰时快照。 */
    @Test
    fun evictedTab_scrollRestoredFromSaveableState() {
        scrollTargets["A"] = 8
        setUpShell()
        compose.waitUntil(10_000) { (gridStates["A"]?.firstVisibleItemIndex ?: 0) >= 5 }
        val aGridBefore = gridStates["A"]!!

        press(Key.DirectionRight) // → B（A 进备用槽）
        press(Key.DirectionRight) // → C（淘汰 A）
        assertFalse("A 应已被淘汰出 Composition", "A" in liveTabs)
        val saved = disposeSnapshots["A"] ?: error("A 淘汰时必须产生保存快照")

        press(Key.DirectionLeft) // → B（复用）
        press(Key.DirectionLeft) // → A（重进：新组合 + 恢复）

        val reloaded = gridStates["A"]!!
        assertEquals(
            "重进恢复值必须等于淘汰时保存的快照",
            saved,
            reloaded.firstVisibleItemIndex to reloaded.firstVisibleItemScrollOffset,
        )
        assertNotEquals("重进是重新组合（新 LazyGridState 实例）", aGridBefore, reloaded)
        assertTrue("恢复后应停留在已滚过的位置", reloaded.firstVisibleItemIndex >= 4)
        assertEquals(2, liveTabs.size)
    }

    /** 切换动画进行中按 ↓：焦点必须落在最新目标页，退场页不得抢占。 */
    @Test
    fun midAnimation_focusLandsOnNewestTarget() {
        setUpShell()

        press(Key.DirectionRight) // A→B，动画已启动（waitForIdle 只推进到动画中段之前）
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.runOnIdle { }
            assertTrue("动画中 ↓ 应进入新页 B", focused("card-B"))
            assertFalse("退场中的 A 不得抢焦点", focused("card-A"))
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }

    /** 快速连续切换（动画竞争）：焦点最终落在最新目标，槽位不串位。 */
    @Test
    fun rapidSwitching_focusFollowsLatestTarget() {
        setUpShell()

        press(Key.DirectionRight) // → B
        press(Key.DirectionRight) // → C（B 动画中再次切换）
        press(Key.DirectionDown)

        assertTrue(focused("card-C"))
        assertFalse(focused("card-A"))
        assertFalse(focused("card-B"))
        assertEquals(2, liveTabs.size)
    }

    /** 同 Tab 反复聚焦/往返：无槽位抖动、无重建。 */
    @Test
    fun backAndForth_noChurn() {
        setUpShell()

        press(Key.DirectionRight) // → B
        press(Key.DirectionLeft) // → A
        press(Key.DirectionRight) // → B
        press(Key.DirectionLeft) // → A

        assertEquals(1, creations["A"])
        assertEquals(1, creations["B"])
        assertEquals(setOf("A", "B"), liveTabs)
    }

    /** animated=false：保持旧行为——单内容直接组合、切换即重建、不保留备用页。 */
    @Test
    fun nonAnimatedMode_legacyBehavior() {
        setUpShell(animated = false)
        press(Key.DirectionDown)
        assertTrue(focused("card-A"))

        press(Key.DirectionUp)
        press(Key.DirectionRight) // → B
        press(Key.DirectionDown)
        assertTrue(focused("card-B"))
        assertEquals(setOf("B"), liveTabs)

        press(Key.DirectionUp)
        press(Key.DirectionLeft) // → A
        press(Key.DirectionDown)
        assertTrue(focused("card-A"))
        // 旧行为：非动画模式无页面保留——切走后旧页即离场
        assertEquals(setOf("A"), liveTabs)
    }

    /** key(tab) 作用域抽查：SaveableStateProvider 键为 Tab 身份，内容 lambda 重组不串位。 */
    @Test
    fun pageIdentityBoundToTabNotSlot() {
        setUpShell()
        repeat(3) { press(Key.DirectionRight) } // A→B→C→D
        val dIdentity = identity["D"]!!
        press(Key.DirectionLeft) // → C
        press(Key.DirectionRight) // → D
        assertEquals("D 的实例身份不因槽位角色交换而改变", dIdentity, identity["D"])
        assertEquals(1, creations["D"])
    }
}
