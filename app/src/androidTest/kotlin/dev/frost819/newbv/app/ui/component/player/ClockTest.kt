package dev.frost819.newbv.app.ui.component.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@RunWith(AndroidJUnit4::class)
class ClockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertClock(hour: Int, minute: Int, expected: String) {
        composeRule.setContent {
            TvMaterialTheme { Clock(hour = hour, minute = minute) }
        }
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun pads_hour_and_minute() = assertClock(9, 5, "09:05")

    @Test
    fun displays_midnight() = assertClock(0, 0, "00:00")

    @Test
    fun displays_last_minute_of_day() = assertClock(23, 59, "23:59")
}
