package dev.frost819.newbv.app.ui.screen.pgc

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class SeasonGridNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        count: Int,
        history: Int,
    ) {
        val episodes =
            List(count) { index ->
                val id = index + 1
                Episode(
                    id = id,
                    aid = id.toLong(),
                    bvid = "BV$id",
                    cid = id.toLong(),
                    title = "$id",
                    longTitle = "",
                    cover = "",
                    duration = 100,
                    dimension = null,
                )
            }
        compose.setContent {
            MaterialTheme {
                SeasonEpisodeDialog(
                    seasonTitle = "测试番剧",
                    sectionTitle = "正片",
                    episodes = episodes,
                    lastPlayedCid = history.toLong(),
                    lastPlayedTime = 0,
                    onDismiss = {},
                    onSelect = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("episode_choice_$history").assertIsFocused()
    }

    private fun press(
        tag: String,
        key: Key,
    ) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
    }

    @Test
    fun `选集右边缘停留且左边缘返回分段列表并能重新进入网格`() {
        show(80, 25)
        repeat(3) { press("episode_choice_25", Key.DirectionRight) }
        compose.onNodeWithTag("episode_choice_25").assertIsFocused()
        press("episode_choice_25", Key.DirectionDown)
        compose.onNodeWithTag("episode_choice_30").assertIsFocused()
        press("episode_choice_30", Key.DirectionRight)
        compose.onNodeWithTag("episode_choice_30").assertIsFocused()
        for (id in 30 downTo 26) press("episode_choice_$id", Key.DirectionLeft)
        compose.onNodeWithTag("episode_group_0").assertIsFocused()
        press("episode_group_0", Key.DirectionDown)
        compose.onNodeWithTag("episode_group_1").assertIsFocused()
        press("episode_group_1", Key.DirectionRight)
        compose.onNodeWithTag("episode_choice_51").assertIsFocused()
    }

    @Test
    fun `末段不足一行时不能跳入其他控件`() {
        show(54, 1)
        compose.onNodeWithTag("episode_group_1").performClick()
        press("episode_group_1", Key.DirectionRight)
        for (id in 51..53) press("episode_choice_$id", Key.DirectionRight)
        compose.onNodeWithTag("episode_choice_54").assertIsFocused()
        repeat(3) { press("episode_choice_54", Key.DirectionRight) }
        compose.onNodeWithTag("episode_choice_54").assertIsFocused()
        for (id in 54 downTo 51) press("episode_choice_$id", Key.DirectionLeft)
        compose.onNodeWithTag("episode_group_1").assertIsFocused()
    }

    @Test
    fun `没有分段列表时首列左键停留`() {
        show(8, 5)
        press("episode_choice_5", Key.DirectionRight)
        compose.onNodeWithTag("episode_choice_5").assertIsFocused()
        for (id in 5 downTo 2) press("episode_choice_$id", Key.DirectionLeft)
        repeat(3) { press("episode_choice_1", Key.DirectionLeft) }
        compose.onNodeWithTag("episode_choice_1").assertIsFocused()
    }
}
