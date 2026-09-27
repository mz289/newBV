package dev.frost819.newbv.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.component.search.SearchResultFilter
import dev.frost819.newbv.app.ui.component.search.SoftKeyboard
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.SettingsMenuSelectItem
import dev.frost819.newbv.biliapi.repositories.SearchFilterDuration
import dev.frost819.newbv.biliapi.repositories.SearchFilterOrderType
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 跨页面实际控件的外框像素、遥控器点击和选中状态回归，不依赖网络。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UnifiedControlFocusTest {
    @get:Rule
    val rule = createComposeRule()
    private var borderColor = Color.Transparent

    private fun show(
        theme: ThemeMode = ThemeMode.Dark,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            BVTheme(themeMode = theme, density = 2f) {
                borderColor = androidx.tv.material3.MaterialTheme.colorScheme.border
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { content() }
            }
        }
    }

    @Test
    fun keyboard_edge_keys_keep_outer_ring_and_remote_click() {
        var typed = ""
        var searches = 0
        show {
            SoftKeyboard(
                firstButtonFocusRequester = FocusRequester(),
                onClick = { typed += it },
                onClear = { typed = "" },
                onDelete = { typed = typed.dropLast(1) },
                onSearch = { searches++ },
            )
        }
        val a = rule.onNodeWithText("A")
        focus(a)
        assertRing(a)
        a.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        rule.runOnIdle { assertThat(typed).isEqualTo("B") }
        val edge = rule.onNodeWithText("F")
        focus(edge)
        assertRing(edge)
        focus(rule.onNodeWithText("搜索"))
        rule.onNodeWithText("搜索").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertThat(searches).isEqualTo(1) }
        screenshot("global-focus-keyboard.png")
    }

    @Test
    fun settings_and_selected_options_keep_outer_ring_in_light_theme() {
        var clicks = 0
        show(ThemeMode.Light) {
            SettingListItem(Modifier.width(350.dp), "画面设置", "默认画质", onClick = { clicks++ })
            SettingsMenuSelectItem(Modifier.width(350.dp), "自动", selected = true, onClick = { clicks++ })
        }
        val setting = rule.onNodeWithText("画面设置")
        focus(setting)
        assertRing(setting)
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        val option = rule.onNodeWithText("自动")
        focus(option)
        assertRing(option)
        option.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertThat(clicks).isEqualTo(2) }
        screenshot("global-focus-settings.png")
    }

    @Test
    fun top_navigation_uses_remote_focus_and_activation() {
        var selected = ""
        var activated = ""
        val tabs =
            listOf("推荐", "热门", "动态").map { label ->
                object : TopNavItem {
                    override val displayName = label
                }
            }
        show {
            TopNav(
                items = tabs,
                isLargePadding = false,
                onSelectedChanged = { selected = it.displayName },
                onClick = { activated = it.displayName },
            )
        }
        focus(rule.onNodeWithText("推荐"))
        rule.onNodeWithText("推荐").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        rule.onNodeWithText("热门").assertIsFocused()
        rule.runOnIdle {
            assertThat(selected).isEqualTo("热门")
            assertThat(activated).isEqualTo("热门")
        }
        screenshot("global-focus-navigation.png")
    }

    @Test
    fun filter_chips_and_dialog_confirmation_keep_selection() {
        var result: SearchFilterOrderType? = null
        show {
            SearchResultFilter(
                SearchFilterOrderType.ComprehensiveSort,
                SearchFilterDuration.All,
                onConfirm = { order, _ -> result = order },
                onDismiss = {},
            )
        }
        val chip = rule.onNodeWithText("最多点击")
        focus(chip)
        assertRing(chip, dialog = true)
        chip.performClick()
        val confirm = rule.onNodeWithText("确定")
        focus(confirm)
        assertRing(confirm, dialog = true)
        screenshot("global-focus-filter.png", dialog = true)
        confirm.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertThat(result).isEqualTo(SearchFilterOrderType.MostClicks) }
    }

    private fun focus(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.waitForIdle()
        node.assertIsFocused()
    }

    private fun assertRing(
        node: SemanticsNodeInteraction,
        dialog: Boolean = false,
    ) {
        val root = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val image = root.captureToImage().toPixelMap()
        val pixel = image[bounds.center.x.toInt(), bounds.top.toInt() - 10]
        val distance =
            kotlin.math.abs(pixel.red - borderColor.red) + kotlin.math.abs(pixel.green - borderColor.green) +
                kotlin.math.abs(pixel.blue - borderColor.blue)
        assertThat(distance).isLessThan(0.04f)
    }

    private fun screenshot(
        name: String,
        dialog: Boolean = false,
    ) {
        val root = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        val bitmap = root.captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(
            context.getExternalFilesDir(null),
            name,
        ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
