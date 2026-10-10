package dev.frost819.newbv.app.ui.component.player.menu

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.tv.material3.MaterialTheme
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.state.player.LocalMenuFocusStateData
import dev.frost819.newbv.app.ui.state.player.MenuFocusState
import dev.frost819.newbv.app.ui.state.player.MenuFocusStateData
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.data.datastore.DanmakuType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class DanmakuMergeMenuTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `合并菜单只有开关且开启后使用相似模式`() {
        var mode by mutableStateOf(DanmakuMergeMode.Off)
        var focus by mutableStateOf(MenuFocusState.Menu)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMenuFocusStateData provides MenuFocusStateData(focusState = focus)) {
                    DanmakuMenuList(
                        currentEnabledTypes = listOf(DanmakuType.Rolling),
                        currentScale = 1f,
                        currentOpacity = 0.7f,
                        currentSpeedFactor = 1f,
                        currentArea = 0.5f,
                        currentMaskEnabled = false,
                        currentBlockEnabled = false,
                        currentBlockRules = emptyList(),
                        currentMergeMode = mode,
                        onDanmakuSwitchChange = {},
                        onDanmakuSizeChange = {},
                        onDanmakuOpacityChange = {},
                        onDanmakuSpeedFactorChange = {},
                        onDanmakuAreaChange = {},
                        onDanmakuMaskChange = {},
                        onBlockEnabledChange = {},
                        onBlockRulesChange = {},
                        onMergeModeChange = { mode = it },
                        onFocusStateChange = { focus = it },
                    )
                }
            }
        }
        compose.onNodeWithText("合并重复").performScrollTo().performClick()
        compose.onNodeWithText("开启").performScrollTo().performClick()
        compose.runOnIdle { assertThat(mode).isEqualTo(DanmakuMergeMode.Similar) }
        compose.onNodeWithText("相同文本").assertDoesNotExist()
        compose.onNodeWithText("高级设置").assertDoesNotExist()
        compose.onNodeWithText("关闭").performScrollTo().performClick()
        compose.runOnIdle { assertThat(mode).isEqualTo(DanmakuMergeMode.Off) }
    }
}
