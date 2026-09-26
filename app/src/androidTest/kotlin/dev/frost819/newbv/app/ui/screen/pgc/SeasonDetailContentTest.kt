package dev.frost819.newbv.app.ui.screen.pgc

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailUiState
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.biliapi.entity.video.season.PgcSeason
import dev.frost819.newbv.biliapi.entity.video.season.SeasonDetail
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 验证番剧详情的续播、空数据、主题、选集与遥控器操作，不依赖网络或登录账号。 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SeasonDetailContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var playCount = 0
    private var followCount = 0
    private var selectedEpisode: Episode? = null
    private var selectedSeason = 0

    private fun show(
        detail: SeasonDetail = fixture(),
        historyCid: Long = 0,
        historyTime: Int = 0,
        theme: ThemeMode = ThemeMode.Dark,
    ) {
        composeRule.setContent {
            BVTheme(themeMode = theme, density = 2f) {
                SeasonDetailContent(
                    detail = detail,
                    state =
                        SeasonDetailUiState(
                            seasonDetail = detail,
                            historyLastPlayedCid = historyCid,
                            historyLastPlayedTime = historyTime,
                        ),
                    onPlay = { playCount++ },
                    onToggleFollow = { followCount++ },
                    onPlayEpisode = { selectedEpisode = it },
                    onSwitchSeason = { selectedSeason = it },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
    }

    @Test
    fun remote_starts_on_play_and_moves_to_follow() {
        // Given
        show()
        // When / Then
        composeRule.onNodeWithText("返回").assertDoesNotExist()
        composeRule.onNodeWithText("new BV").assertDoesNotExist()
        composeRule.onNodeWithText("番剧详情").assertDoesNotExist()
        composeRule.onNodeWithTag("season_play").assertIsFocused()
        composeRule.onNodeWithTag("season_play").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertThat(playCount).isEqualTo(1) }
        composeRule.onNodeWithTag("season_play").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("追番").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertThat(followCount).isEqualTo(1) }
    }

    @Test
    fun resume_shows_episode_position_and_visible_episode_row() {
        // Given
        show(historyCid = 102, historyTime = 756)
        // Then
        composeRule.onNodeWithText("继续播放 第 2 话").assertIsDisplayed()
        composeRule.onNodeWithText("上次看到 12:36").assertIsDisplayed()
        composeRule.onNodeWithText("上次观看").assertIsDisplayed()
        composeRule.onNodeWithText("第 2 话").assertIsDisplayed()
        saveScreenshot("season-dark.png")
    }

    @Test
    fun missing_history_does_not_show_stale_resume() {
        // Given
        show(historyCid = 999, historyTime = 500)
        // Then
        composeRule.onNodeWithText("立即播放").assertIsDisplayed()
        composeRule.onNodeWithText("上次看到", substring = true).assertDoesNotExist()
    }

    @Test
    fun empty_season_disables_play_but_keeps_follow_available() {
        // Given
        show(detail = fixture().copy(episodes = emptyList(), seasons = emptyList()))
        // When
        composeRule.onNodeWithTag("season_play").assertIsNotEnabled()
        composeRule.onNodeWithText("追番").performClick()
        // Then
        composeRule.onNodeWithText("正片").assertDoesNotExist()
        composeRule.runOnIdle { assertThat(followCount).isEqualTo(1) }
    }

    @Test
    fun touch_switches_season_and_plays_episode() {
        // Given
        show()
        // When
        composeRule.onNodeWithText("第二季").performTouchInput { click() }
        composeRule.onNodeWithTag("season_episode_1").performScrollTo().performClick()
        // Then
        composeRule.runOnIdle {
            assertThat(selectedSeason).isEqualTo(2)
            assertThat(selectedEpisode?.cid).isEqualTo(101)
        }
    }

    @Test
    fun all_episodes_dialog_selects_episode_even_for_short_season() {
        // Given
        show()
        // When
        composeRule.onNodeWithText("全部选集").performClick()
        composeRule.onNodeWithText("第 8 话").performScrollTo().performClick()
        // Then
        composeRule.runOnIdle { assertThat(selectedEpisode?.cid).isEqualTo(108) }
        composeRule.onNodeWithText("全部选集").assertIsDisplayed()
    }

    @Test
    fun light_theme_handles_long_title_and_completed_history() {
        // Given
        show(
            detail = fixture().copy(title = "关于在漫长旅途中与伙伴重逢并再次出发的故事 第二季"),
            historyCid = 101,
            historyTime = -1,
            theme = ThemeMode.Light,
        )
        // Then
        composeRule.onNodeWithText("上次已看完").assertIsDisplayed()
        composeRule.onNodeWithText("追番").assertIsDisplayed()
        composeRule.onNodeWithText("全部选集").assertIsDisplayed()
        saveScreenshot("season-light-long-title.png")
    }

    @Test
    fun return_from_play_restores_episode_focus() {
        var playing by mutableStateOf(false)
        // Given: imitate Navigation removing and reattaching the destination with saved state.
        composeRule.setContent {
            val holder =
                androidx.compose.runtime.saveable
                    .rememberSaveableStateHolder()
            BVTheme(themeMode = ThemeMode.Dark, density = 2f) {
                if (playing) {
                    androidx.tv.material3.Text("播放器")
                } else {
                    holder.SaveableStateProvider("detail") {
                        SeasonDetailContent(fixture(), SeasonDetailUiState(), {}, {}, { playing = true }, {})
                    }
                }
            }
        }
        // When
        composeRule.onNodeWithTag("season_episode_2").performScrollTo().performTouchInput { click() }
        composeRule.onNodeWithText("播放器").assertIsDisplayed()
        composeRule.runOnIdle { playing = false }
        // Then
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        saveScreenshot("season-return-focus.png")
        composeRule.onNodeWithTag("season_episode_2").assertIsFocused()
        composeRule.onNodeWithText("第 2 话").assertIsDisplayed()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("播放器").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    private fun fixture() =
        SeasonDetail(
            title = "星旅物语",
            styles = listOf("奇幻", "冒险", "治愈"),
            cover = InstrumentationRegistry.getArguments().getString("previewCover").orEmpty(),
            description = "旅途的终点，也是新故事的起点。与伙伴穿越群山，在时光中寻找那些未曾说出口的心意。",
            subType = 1,
            seasonId = 1,
            userStatus = SeasonDetail.UserStatus(false, false, null),
            publish = SeasonDetail.Publish(true, ""),
            newEpDesc = "全 8 话",
            seasons =
                listOf(
                    PgcSeason(1, "第一季", "第一季", "", InstrumentationRegistry.getArguments().getString("previewBackdrop")),
                    PgcSeason(2, "第二季", "第二季", "", null),
                ),
            episodes =
                (1..8).map {
                    Episode(
                        it,
                        it.toLong(),
                        "",
                        100L + it,
                        it,
                        "第 $it 话",
                        "新的旅程",
                        InstrumentationRegistry.getArguments().getString("previewBackdrop").orEmpty(),
                        1440,
                        null,
                    )
                },
        )
}
