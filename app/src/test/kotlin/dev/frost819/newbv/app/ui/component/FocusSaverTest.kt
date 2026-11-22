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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.core.theme.BVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FocusSaver 恢复等待循环的状态契约测试（事件驱动语义）。
 *
 * 真实时序（返回导航后）：框架先把焦点落到首个可聚焦控件（如左侧栏头像）；
 * 目标元素的 FocusRequester 由 Lazy 容器在测量期才注册，壳层冷装载还会把内容
 * 推迟一帧。历史失败线：默认控件的 onFocusChanged 在恢复完成前覆写 savedKey
 * → 恢复被劫持到默认控件。
 *
 * 语义：无帧数/时长窗口。噪声与用户接管以**因果**区分——恢复等待期间观测到
 * 方向键（[UserFocusIntentSignal]，白盒注入）之后的写入才是用户接管；无按键
 * 写入是框架默认聚焦噪声，被拦截且不改变等待。等待只终止于三种状态：目标
 * 注册（恢复）/ 稳态且目标缺失（兜底）/ 按键后焦点写入（让位）。
 *
 * 时钟模型（references/android-build-env.md）：停钟（autoAdvance=false）下
 * runOnUiThread 写状态**不触发重组**——多阶段挂载/卸载 harness 会整体空转，
 * 本套件只用**单次 composition** 建模「返回时列表尚在骨架/重载」的等待形态：
 * 挂载即 pending（目标未注册 + 非稳态），循环帧由 advanceTimeBy 驱动，
 * 注入全部走白盒（saveFocusedKey / noteDirectionalKey）。循环存活性以
 * contentSteady 调用计数断言（增长=等待中；停止=已终结）。涉及「列表就绪
 * 后注册→恢复」「恢复后回写」的重组类时序无法在停钟下确定性复现，由模拟器
 * 端到端验证（热门 → 卡片 → 详情 → 返回，焦点须回原卡片）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class FocusSaverTest {
    @get:Rule val compose = createComposeRule()

    /** 模拟「常驻控件」（如左侧栏头像）：挂载期间始终组合，先于网格项注册。 */
    private val alwaysKey = "user"

    /** 模拟网格卡片：本套件中恒不组合（等待形态），由模拟器 E2E 覆盖注册恢复。 */
    private val gridKey = "item_7"

    /** 模拟失效 key：没有任何元素注册它。 */
    private val ghostKey = "ghost"

    private val savedKey = mutableStateOf("")
    private var mounted by mutableStateOf(false)
    private var itemsReady by mutableStateOf(true)
    private var itemsSettled by mutableStateOf(true)
    private var restoreFailedCount by mutableStateOf(0)

    /** contentSteady 调用计数：循环存活的直接证据（每帧至少 +1）。 */
    private var steadyCalls = 0
    private var restoreActive = false
    private var fsRef: FocusSaver? = null
    private var signalRef: UserFocusIntentSignal? = null

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
                    val intentSignal = remember { UserFocusIntentSignal() }
                    val fs = remember { FocusSaver(savedKey, intentSignal) }
                    fsRef = fs
                    signalRef = intentSignal
                    fs.RestoreFocus(
                        onRestoreFailed = { restoreFailedCount++ },
                        contentSteady = {
                            steadyCalls++
                            restoreActive = true
                            itemsSettled
                        },
                    )
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

    /**
     * 挂载「返回时列表尚在骨架/重载」的等待形态：capturedKey=gridKey、目标未注册、
     * 稳态未声明。停钟推帧驱动等待循环，并确认其激活。
     */
    private fun mountPending() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mounted = true
            itemsReady = false // 目标未注册：恢复持续等待
            itemsSettled = false // 非稳态：不得兜底
            savedKey.value = gridKey
        }
        compose.setContent { Screen() }
        compose.mainClock.advanceTimeBy(1_000)
        awaitRestoreActive()
    }

    /** 停钟推帧直到恢复循环激活（contentSteady 首调），时序敏感注入的前置。 */
    private fun awaitRestoreActive() {
        var guard = 0
        while (!restoreActive && guard < 100) {
            compose.mainClock.advanceTimeBy(100)
            guard++
        }
        assertTrue("恢复等待循环应在推进帧后激活", restoreActive)
    }

    /** 断言等待循环仍在运行（contentSteady 持续被调用）。 */
    private fun assertLoopAlive() {
        val calls = steadyCalls
        compose.mainClock.advanceTimeBy(2_000)
        assertTrue("恢复等待循环应仍在等待（兜底不该发生）", steadyCalls > calls)
    }

    /** 断言等待循环已终结（contentSteady 不再被调用）。 */
    private fun assertLoopDead() {
        val calls = steadyCalls
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("恢复等待循环应已终结", calls.toLong(), steadyCalls.toLong())
    }

    /** 稳态下 key 失效（目标不存在）→ 兜底回调一次；之后正常保存恢复工作。 */
    @Test
    fun failedRestoreFallsBackAndResumesSaving() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mounted = true
            itemsReady = true
            itemsSettled = true
            savedKey.value = ghostKey
        }
        compose.setContent { Screen() }
        compose.mainClock.advanceTimeBy(2_000) // 冲刷稳态判定

        assertEquals("失效 key 应恰好触发一次兜底", 1, restoreFailedCount)
        compose.runOnUiThread { fsRef!!.focusRequesterFor(alwaysKey).requestFocus() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("窗口关闭后正常保存恢复", alwaysKey, savedKey.value)
        assertNotEquals(ghostKey, savedKey.value)
    }

    /**
     * 无按键噪声写入：等待期间框架默认聚焦覆写 key（白盒注入，等价 onFocusChanged）
     * 须被拦截——不改 savedKey、不判接管、不终止等待。
     */
    @Test
    fun noiseWritesDuringPendingAreSuppressedAndDoNotEndWait() {
        mountPending()
        val baseline = savedKey.value
        compose.runOnUiThread { fsRef!!.saveFocusedKey(alwaysKey) }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("无按键写入须被拦截，不得改写 savedKey", baseline, savedKey.value)
        assertEquals("噪声不得触发兜底", 0, restoreFailedCount)
        assertLoopAlive()
    }

    /**
     * 用户接管让位：方向键（白盒注入信号）之后的真实焦点移动 → 立即放弃恢复
     * 且不触发兜底；循环终结后（finally 关闭 restoring）保存口径恢复正常。
     */
    @Test
    fun userKeystrokeThenFocusWriteAbortsRestoreWithoutFallback() {
        mountPending()
        compose.runOnUiThread {
            signalRef!!.noteDirectionalKey() // 用户按下方向键
            fsRef!!.saveFocusedKey(alwaysKey) // 随之而来的焦点写入（等价 onFocusChanged）
        }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("用户接管后恢复须让位且不触发兜底", 0, restoreFailedCount)
        assertEquals("用户接管写入的 key 须保留", alwaysKey, savedKey.value)
        assertLoopDead()
        // finally 已关闭 restoring：之后的写入照常保存
        compose.runOnUiThread { fsRef!!.saveFocusedKey("later_move") }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("让位后保存口径须恢复正常", "later_move", savedKey.value)
    }

    /** 按键但焦点未动（如骨架屏上按方向键落空）：不算接管，等待循环不得终止。 */
    @Test
    fun keystrokeWithoutFocusWriteDoesNotAbandon() {
        mountPending()
        compose.runOnUiThread { signalRef!!.noteDirectionalKey() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("未发生焦点写入不得让位或兜底", 0, restoreFailedCount)
        assertEquals("落空的按键不得改写 savedKey", gridKey, savedKey.value)
        assertLoopAlive()
    }

    /** 非稳态等待不触发兜底：列表重载多久就等多久，等待循环保持运行。 */
    @Test
    fun nonSettledContentDoesNotFallBack() {
        mountPending()
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("非稳态期间不得触发兜底", 0, restoreFailedCount)
        assertLoopAlive()
    }

    /** 用户接管判定不得抢在恢复前执行错对象：无按键时 savedKey 污染不判接管。 */
    @Test
    fun preRestorePollutionAloneDoesNotAbandon() {
        mountPending()
        // 挂载帧框架默认聚焦先行写入（等价：覆写发生在 effect 激活前）
        compose.runOnUiThread { savedKey.value = alwaysKey }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("挂载帧污染不得触发兜底", 0, restoreFailedCount)
        assertLoopAlive()
    }
}
