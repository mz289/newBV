package dev.frost819.newbv.app.ui.component

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.core.theme.BVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FocusSaver 跨导航焦点恢复回归测试。
 *
 * 真实时序（返回导航后）：框架先把焦点落到首个可聚焦控件（如左侧栏头像）；
 * 目标元素的 FocusRequester 由 Lazy 容器在测量期才注册，壳层冷装载还会把内容
 * 推迟一帧。两条历史失败线：
 * 1. RestoreFocus 首跑查表得到 null（目标未注册）→ 静默放弃，焦点留在默认控件；
 * 2. 默认控件的 onFocusChanged 在恢复完成前覆写 savedKey → 恢复被劫持到默认控件。
 *
 * 时钟模型：停钟（autoAdvance=false）+ runOnUiThread 写状态 + advanceTimeBy 推帧。
 * ≥75ms 的推进才可靠跨帧（见 references/android-build-env.md）。
 *
 * 覆盖范围：覆盖「恢复等待延迟注册」「失效 key 兜底 + 窗口关闭后恢复保存」
 * 「恢复后正常保存」三条契约。框架默认聚焦在恢复窗口前/内覆写 key 的劫持路径
 * 依赖 main-looper 效应调度时序，停钟模型下无法确定性复现，由模拟器端到端验证
 * （热门 → 卡片 → 详情 → 返回，焦点须回原卡片且再次进出仍回原位）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class FocusSaverTest {
    @get:Rule val compose = createComposeRule()

    /** 模拟「常驻控件」（如左侧栏头像）：挂载期间始终组合，先于网格项注册。 */
    private val alwaysKey = "user"

    /** 模拟网格卡片：可被延迟组合（壳帧）。 */
    private val gridKey = "item_7"

    /** 模拟失效 key：没有任何元素注册它。 */
    private val ghostKey = "ghost"

    private val savedKey = mutableStateOf("")
    private var mounted by mutableStateOf(false)
    private var itemsReady by mutableStateOf(true)
    private var restoreFailedCount by mutableStateOf(0)
    private var fsRef: FocusSaver? = null

    @Composable
    private fun TestItem(key: String) {
        val fs = fsRef!!
        Box(
            Modifier
                .size(64.dp)
                .focusRequester(fs.focusRequesterFor(key))
                .onFocusChanged { if (it.hasFocus) fs.saveFocusedKey(key) }
                .focusable()
                .testTag(key),
        )
    }

    @Composable
    private fun Screen() {
        BVTheme {
            Box {
                if (mounted) {
                    // 每次挂载都是全新 FocusSaver（requesters 空），等价跨导航返回；
                    // savedKey 由外部持有（等价 rememberSaveable 存活）
                    val fs = remember { FocusSaver(savedKey) }
                    fsRef = fs
                    fs.RestoreFocus(onRestoreFailed = { restoreFailedCount++ })
                    TestItem(alwaysKey)
                    if (itemsReady) {
                        Row {
                            TestItem(gridKey)
                        }
                    }
                }
            }
        }
    }

    /** 前置：挂载 + 焦点落 gridKey + 卸载（模拟进入新页面），返回后由各用例控制再挂载形态。 */
    private fun setupFocusedThenNavigateAway() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mounted = true
            itemsReady = true
        }
        compose.setContent { Screen() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnUiThread { fsRef!!.focusRequesterFor(gridKey).requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(gridKey, savedKey.value)
        compose.runOnUiThread { mounted = false }
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** 延迟注册恢复：壳帧（网格未组合）后返回，恢复仍须落在 gridKey。 */
    @Test
    fun restoreWaitsForLateRegistration() {
        setupFocusedThenNavigateAway()
        compose.runOnUiThread {
            mounted = true
            itemsReady = false // 壳帧：网格内容延迟一帧组合
        }
        compose.mainClock.advanceTimeBy(128) // effect 首跑：查表 null → 进入逐帧等待
        compose.runOnUiThread { itemsReady = true }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(gridKey).assertIsFocused()
        assertEquals(gridKey, savedKey.value)
    }

    /** key 失效：重试用尽 → 兜底回调一次；窗口关闭后正常保存恢复工作。 */
    @Test
    fun failedRestoreFallsBackAndResumesSaving() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mounted = true
            itemsReady = true
            savedKey.value = ghostKey
        }
        compose.setContent { Screen() }
        compose.mainClock.advanceTimeBy(2_000) // 冲刷 12 帧重试上限

        assertEquals("失效 key 应恰好触发一次兜底", 1, restoreFailedCount)
        compose.runOnUiThread { fsRef!!.focusRequesterFor(alwaysKey).requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("窗口关闭后正常保存恢复", alwaysKey, savedKey.value)
        assertTrue(savedKey.value != ghostKey)
    }

    /** 恢复成功后，用户正常移动焦点的保存不受影响（窗口只覆盖恢复过程）。 */
    @Test
    fun normalSavingWorksAfterRestore() {
        setupFocusedThenNavigateAway()
        compose.runOnUiThread { mounted = true }
        compose.mainClock.advanceTimeBy(1_000) // itemsReady 未关，gridKey 直接恢复
        compose.onNodeWithTag(gridKey).assertIsFocused()
        // 用户移动到常驻控件
        compose.runOnUiThread { fsRef!!.focusRequesterFor(alwaysKey).requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("恢复完成后的焦点移动须正常保存", alwaysKey, savedKey.value)
    }

    /**
     * 接管窗口让位：恢复窗口内用户真实移动焦点（接管窗口阶段 key 变化）→
     * 立即放弃恢复且不触发兜底，用户导航不被恢复抢夺。
     */
    @Test
    fun userTakeoverAbortsRestoreWithoutFallback() {
        setupFocusedThenNavigateAway()
        compose.runOnUiThread {
            mounted = true
            itemsReady = false // 目标始终未注册：窗口会持续等待
        }
        compose.mainClock.advanceTimeBy(400) // 越过静默窗口（12 帧 ≈192ms）进入接管窗口
        // 用户接管：真实焦点移动写入新 key（白盒注入，等价 onFocusChanged）
        compose.runOnUiThread { fsRef!!.saveFocusedKey(alwaysKey) }
        compose.mainClock.advanceTimeBy(2_000) // 冲刷整个窗口（72 帧 ≈1.15s）
        assertEquals("用户接管后恢复须让位且不触发兜底", 0, restoreFailedCount)
        assertEquals("用户接管写入的 key 须保留", alwaysKey, savedKey.value)
    }

    /** 接管窗口内目标终于注册（列表重载完成场景）→ 恢复仍落到 gridKey。 */
    @Test
    fun restoreSucceedsInTakeoverWindow() {
        setupFocusedThenNavigateAway()
        compose.runOnUiThread {
            mounted = true
            itemsReady = false // 模拟返回时列表还在骨架/重载
        }
        compose.mainClock.advanceTimeBy(400) // 越过静默窗口（列表尚未就绪）
        compose.runOnUiThread { itemsReady = true } // 列表重载完成，目标注册
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag(gridKey).assertIsFocused()
        assertEquals("接管窗口内的迟到注册仍须恢复", gridKey, savedKey.value)
    }
}
