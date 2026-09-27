package dev.frost819.newbv.app.ui.screen.pgc

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 选集弹窗的长篇分段、历史定位、遥控器与边界数据回归验证。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SeasonEpisodeDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var selected: Episode? = null
    private var dismissed = false

    private fun show(
        count: Int = 1274,
        history: Int = 21,
        theme: ThemeMode = ThemeMode.Dark,
    ) {
        composeRule.setContent {
            BVTheme(themeMode = theme, density = 2f) {
                SeasonEpisodeDialog(
                    seasonTitle = "名侦探柯南（中配）",
                    sectionTitle = "正片",
                    episodes =
                        (1..count).map {
                            Episode(it, it.toLong(), "", it.toLong(), it, "第 $it 话", "新的旅程与未解之谜", "", 1440, null)
                        },
                    lastPlayedCid = history.toLong(),
                    lastPlayedTime = 756,
                    onDismiss = { dismissed = true },
                    onSelect = { selected = it },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
    }

    @Test
    fun opens_at_history_and_remote_selects_next_episode() {
        show()
        composeRule.onNodeWithTag("episode_choice_21").assertIsFocused()
        composeRule.onNodeWithText("方向键", substring = true).assertDoesNotExist()
        screenshot("episodes-dark.png")
        composeRule.onNodeWithTag("episode_choice_21").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule
            .onNodeWithTag(
                "episode_choice_22",
            ).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertThat(selected?.id).isEqualTo(22) }
    }

    @Test
    fun switches_group_and_locates_history_across_groups() {
        show(history = 76)
        composeRule.onNodeWithTag("episode_choice_76").assertIsFocused()
        composeRule.onNodeWithTag("episode_group_2").performClick()
        composeRule.onNodeWithTag("episode_choice_101").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_locate").performClick()
        composeRule.onNodeWithTag("episode_choice_76").assertIsFocused()
        // 定位按钮也须支持当前分段滚动后再次定位。
        composeRule.onNodeWithTag("episode_grid").performScrollToIndex(0)
        composeRule.onNodeWithTag("episode_locate").performClick()
        composeRule.onNodeWithTag("episode_choice_76").assertIsFocused()
    }

    @Test
    fun remote_moves_between_grid_and_group_without_losing_selection() {
        show()
        composeRule.onNodeWithTag("episode_choice_21").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("episode_group_0").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("episode_choice_51").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_group_1").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("episode_choice_51").assertIsFocused()
    }

    @Test
    fun group_focus_switches_content_without_confirm() {
        show()
        composeRule.onNodeWithTag("episode_choice_21").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("episode_group_0").assertIsFocused()
        // 焦点移到 51–100 分段时内容立即切换，无需按确认键，且焦点不被抢回网格。
        composeRule.onNodeWithTag("episode_group_1").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("episode_group_1").assertIsFocused()
        composeRule.onNodeWithText("51–100 话").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_choice_51").assertIsDisplayed()
    }

    @Test
    fun partial_final_group_is_bounded_and_playable() {
        show(history = 1274)
        composeRule.onNodeWithTag("episode_choice_1274").assertIsFocused().performClick()
        composeRule.onNodeWithText("1251–1274 话").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(selected?.id).isEqualTo(1274) }
    }

    @Test
    fun short_season_and_unknown_history_hide_unnecessary_controls() {
        show(count = 8, history = 999, theme = ThemeMode.Light)
        composeRule.onNodeWithTag("episode_choice_1").assertIsFocused()
        composeRule.onNodeWithTag("episode_group_0").assertDoesNotExist()
        composeRule.onNodeWithTag("episode_locate").assertDoesNotExist()
        composeRule.onNodeWithTag("episode_choice_8").performClick()
        composeRule.runOnIdle { assertThat(selected?.id).isEqualTo(8) }
        screenshot("episodes-light.png")
    }

    @Test
    fun empty_section_can_be_closed() {
        show(count = 0, history = 0)
        composeRule.onNodeWithText("暂无选集").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_close").assertDoesNotExist()
        androidx.test.espresso.Espresso
            .pressBack()
        composeRule.runOnIdle { assertThat(dismissed).isTrue() }
    }

    private fun screenshot(name: String) {
        val bitmap = composeRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(
            context.getExternalFilesDir(null),
            name,
        ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
