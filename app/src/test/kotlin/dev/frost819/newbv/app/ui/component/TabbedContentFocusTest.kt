package dev.frost819.newbv.app.ui.component

import android.app.Application
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tab 常驻页焦点隔离回归测试。
 *
 * 页内容按真实网格结构构造：可聚焦卡片外包一层 `focusGroup()` —— 它会生成一个
 * `Focusability.Never` 的中间 FocusTarget。两条回归线：
 * 1. 页根不能再写 `focusProperties { canFocus = true }`：父级属性覆盖子级，会把
 *    focusGroup 的 Never 变成可聚焦，方向键停在看不见的组节点上（焦点丢失、无高亮）；
 * 2. 隐藏页在退出动画后只测量不放置，未放置节点被 isEligibleForFocusSearch 排除，
 *    方向键不可能落进已隐藏的页。
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class TabbedContentFocusTest {
    @get:Rule val compose = createComposeRule()

    private val tabs = listOf("A", "B")

    /** 页内容：顶部留白模拟骨架区，下方卡片包在 focusGroup 里（等价 GridFocusNavigation.Item）。 */
    @Composable
    private fun PageContent(tab: String) {
        val itemRequester = remember { FocusRequester() }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(220.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .focusRequester(itemRequester)
                    .focusProperties { onEnter = { itemRequester.restoreFocusedChild() } }
                    .focusGroup(),
            ) {
                Box(
                    Modifier
                        .testTag("card-$tab")
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(8.dp)
                        .focusable(),
                )
            }
        }
    }

    @Composable
    private fun Shell(navFocus: FocusRequester) {
        BVTheme {
            TabbedContent(
                navFocusRequester = navFocus,
                tabs = tabs,
                initialTab = tabs.first(),
                displayName = { it },
                onRefresh = {},
                animated = true,
            ) { tab -> PageContent(tab) }
        }
    }

    private fun focused(tag: String) =
        runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess

    /** 从顶部 Tab 栏下移：必须落在当前可见页的卡片上，不能落进已隐藏的旧页。 */
    @Test
    fun downFromNavEntersVisiblePageNotHiddenPage() {
        val navFocus = FocusRequester()
        compose.setContent { Shell(navFocus) }
        compose.waitForIdle()
        compose.runOnIdle { navFocus.requestFocus() }

        // 先切到 B 再切回 A：B 页此时已组合并保持，旧实现里它的卡片仍可聚焦。
        // （Tab 切换有 200ms 焦点防抖：切键后先冲刷窗口让 B 提交，↓ 才会落进 B）
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("card-B").assertIsFocused()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        org.junit.Assert.assertTrue(
            "焦点应进入可见页 A；实际 A=${focused("card-A")} B=${focused("card-B")}",
            focused("card-A"),
        )
        org.junit.Assert.assertFalse("焦点不应落入隐藏页 B", focused("card-B"))
    }

    /** 可见页自身仍可正常获得焦点（隔离不能误伤当前页）。 */
    @Test
    fun downFromNavEntersCurrentPage() {
        val navFocus = FocusRequester()
        compose.setContent { Shell(navFocus) }
        compose.waitForIdle()
        compose.runOnIdle { navFocus.requestFocus() }
        compose.waitForIdle()

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("card-A").assertIsFocused()
    }
}
