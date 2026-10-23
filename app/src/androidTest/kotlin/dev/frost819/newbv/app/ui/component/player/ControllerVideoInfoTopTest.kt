package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

/**
 * [ControllerVideoInfoTop] 的插桩测试。
 *
 * 验证标题、时钟和同时观看人数显示。
 */
@RunWith(AndroidJUnit4::class)
class ControllerVideoInfoTopTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        title: String,
        onlineWatching: String = "",
    ) {
        composeRule.setContent {
            TvMaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ControllerVideoInfoTop(
                        title = title,
                        onlineWatching = onlineWatching,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun displays_title() {
        setContent(title = "测试视频标题")

        composeRule.onNodeWithText("测试视频标题").assertIsDisplayed()
    }

    @Test
    fun displays_title_and_current_clock_together() {
        setContent(title = "我的视频")

        composeRule.onNodeWithText("我的视频").assertIsDisplayed()
        val clockPattern = Regex("[0-2][0-9]:[0-5][0-9]")
        composeRule.onNode(
            SemanticsMatcher("current clock") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { clockPattern.matches(it.text) } == true
            },
        ).assertIsDisplayed()
    }

    @Test
    fun displays_online_watching_count() {
        setContent(title = "标题", onlineWatching = "9.4万+")

        composeRule.onNodeWithText("9.4万+人正在看").assertIsDisplayed()
    }

    @Test
    fun hides_online_watching_when_blank() {
        setContent(title = "标题", onlineWatching = "")

        composeRule.onNodeWithText("人正在看", substring = true).assertDoesNotExist()
    }
}
