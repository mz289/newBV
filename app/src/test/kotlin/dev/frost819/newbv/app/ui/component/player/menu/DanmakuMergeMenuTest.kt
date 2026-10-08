package dev.frost819.newbv.app.ui.component.player.menu

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.component.player.menu.component.DanmakuMergeMenuPanel
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class DanmakuMergeMenuTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `开关和常用设置在上一级高级设置内可以配置并返回`() {
        var mode by mutableStateOf(DanmakuMergeMode.Off)
        var config by mutableStateOf(DanmakuMergeConfig())
        compose.setContent {
            MaterialTheme {
                DanmakuMergeMenuPanel(
                    mode = mode,
                    config = config,
                    onModeChange = { mode = it },
                    onConfigChange = { config = it },
                    onFocusBackToParent = {},
                )
            }
        }
        compose.onNodeWithText("开关").assertExists()
        compose.onNodeWithText("放过高级弹幕").assertDoesNotExist()
        compose.onNodeWithText("数量标记阈值").assertDoesNotExist()
        compose.onNodeWithText("合并后增大字号").assertDoesNotExist()
        compose.onNodeWithText("关闭").assertExists()
        compose.onNodeWithText("开启").performClick()
        compose.runOnIdle { assertThat(mode).isEqualTo(DanmakuMergeMode.Similar) }
        compose.onNodeWithText("时间窗口").performClick()
        compose.onNodeWithText("20 秒").assertExists()
        compose.onNodeWithText("识别谐音弹幕").assertDoesNotExist()
        compose.onNodeWithText("高级设置").performScrollTo().performClick()
        compose.onNodeWithText("时间窗口").assertDoesNotExist()
        compose.onNodeWithText("编辑距离阈值").assertExists()
        compose.onNodeWithText("词频向量阈值").assertExists()
        compose.onNodeWithText("显示时间百分位").assertDoesNotExist()
        compose.onNodeWithText("优先固定弹幕").assertDoesNotExist()
        compose.onNodeWithText("识别谐音弹幕").assertExists()
        compose.onNodeWithContentDescription("增加").performClick()
        compose.runOnIdle { assertThat(config.editDistanceThreshold).isEqualTo(6) }
        compose.onNodeWithText("返回").performScrollTo().performClick()
        compose.onNodeWithText("开关").assertExists()
        compose.onNodeWithText("识别谐音弹幕").assertDoesNotExist()
        compose.runOnIdle { assertThat(config.editDistanceThreshold).isEqualTo(6) }
        // 返回后由方向键重新进入高级设置，调整数值并逐层返回。
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("识别谐音弹幕").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertThat(config.editDistanceThreshold).isEqualTo(7) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("时间窗口").assertExists()
    }

    @Test
    fun `优选和滚动配置生效且高级菜单不再提供屏蔽顺序开关`() {
        var config by mutableStateOf(DanmakuMergeConfig())
        compose.setContent {
            MaterialTheme {
                DanmakuMergeMenuPanel(
                    mode = DanmakuMergeMode.Off,
                    config = config,
                    onModeChange = {},
                    onConfigChange = { config = it },
                    onFocusBackToParent = {},
                )
            }
        }
        compose.onNodeWithText("自动弹幕优选").performScrollTo().performClick()
        compose.onNodeWithContentDescription("增加").performClick()
        compose.runOnIdle { assertThat(config.dropThreshold).isEqualTo(1) }
        compose.onNodeWithText("超长固定弹幕转滚动").performScrollTo().performClick()
        compose.onNodeWithContentDescription("增加").performClick()
        compose.runOnIdle { assertThat(config.scrollThreshold).isEqualTo(1) }
        compose.onNodeWithText("高级设置").performScrollTo().performClick()
        compose.onNodeWithText("编辑距离阈值").assertExists()
        compose.onNodeWithText("合并前执行屏蔽").assertDoesNotExist()
    }
}
