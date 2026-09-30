package dev.frost819.newbv.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** 跨页面实际控件的外框/底色像素、遥控器点击和选中状态回归，不依赖网络。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UnifiedControlFocusTest {
    @get:Rule
    val rule = createComposeRule()
    private var borderColor = Color.Transparent
    private var fillColor = Color.Transparent

    private fun show(
        theme: ThemeMode = ThemeMode.Dark,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            BVTheme(themeMode = theme, density = 2f) {
                borderColor = androidx.tv.material3.MaterialTheme.colorScheme.border
                fillColor = androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer
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
    fun settings_and_selected_options_use_background_fill_for_focus() {
        var clicks = 0
        show(ThemeMode.Light) {
            SettingListItem(Modifier.width(350.dp), "画面设置", "默认画质", onClick = { clicks++ })
            SettingsMenuSelectItem(Modifier.width(350.dp), "自动", selected = true, onClick = { clicks++ })
        }
        val setting = rule.onNodeWithText("画面设置")
        focus(setting)
        assertFill(setting)
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        val option = rule.onNodeWithText("自动")
        focus(option)
        assertFill(option)
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
        assertRing(rule.onNodeWithText("热门"))
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

    @Test
    fun player_categories_use_selection_background_without_duplicate_ring() {
        var selected by androidx.compose.runtime.mutableStateOf(false)
        var clicks = 0
        show {
            dev.frost819.newbv.app.ui.component.player.menu.component.MenuListItem(
                text = "画质分类",
                selected = selected,
                selectionFollowsFocus = true,
                onFocus = { selected = true },
                onClick = { clicks++ },
            )
        }
        val category = rule.onNodeWithText("画质分类")
        focus(category)
        assertRing(category, expected = false)
        category.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle {
            assertThat(selected).isTrue()
            assertThat(clicks).isEqualTo(1)
        }
        screenshot("global-focus-category.png")
    }

    private fun focus(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.waitForIdle()
        node.assertIsFocused()
    }

    /**
     * 断言列表项节点左缘内侧存在（或不存在）焦点底色填充。
     *
     * 列表类控件聚焦改用背景填充而非描边；在节点中线高度沿左缘向右扫描一段，
     * 命中主题填充色即视为生效。扫描窗避开了纵向圆角与深层内容。
     */
    private fun assertFill(
        node: SemanticsNodeInteraction,
        expected: Boolean = true,
    ) {
        val image = rule.onRoot().captureToImage().toPixelMap()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val y =
            bounds.center.y
                .toInt()
                .coerceIn(0, image.height - 1)
        val hasFill =
            ((bounds.left.toInt() + 2)..(bounds.left.toInt() + bounds.width.toInt() / 6)).any { x ->
                x in 0 until image.width &&
                    run {
                        val pixel = image[x, y]
                        kotlin.math.abs(pixel.red - fillColor.red) +
                            kotlin.math.abs(pixel.green - fillColor.green) +
                            kotlin.math.abs(pixel.blue - fillColor.blue) < 0.04f
                    }
            }
        if (expected) assertThat(hasFill).isTrue() else assertThat(hasFill).isFalse()
    }

    /**
     * 断言控件外缘存在（或不存在）焦点描边。
     *
     * 描边只有 2dp 宽：TV Border 画在控件外缘，[controlFocusOutline] 画进节点自带的
     * 间距内，固定采样点会因密度与控件内边距差异落空，故沿节点中线整列扫描，
     * 命中主题描边色即视为存在外框。
     */
    private fun assertRing(
        node: SemanticsNodeInteraction,
        dialog: Boolean = false,
        expected: Boolean = true,
    ) {
        val root = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val image = root.captureToImage().toPixelMap()
        val x =
            bounds.center.x
                .toInt()
                .coerceIn(0, image.width - 1)
        val hasRing =
            (bounds.top.toInt() - 12..bounds.bottom.toInt() + 12).any { y ->
                y in 0 until image.height &&
                    run {
                        val pixel = image[x, y]
                        kotlin.math.abs(pixel.red - borderColor.red) +
                            kotlin.math.abs(pixel.green - borderColor.green) +
                            kotlin.math.abs(pixel.blue - borderColor.blue) < 0.04f
                    }
            }
        if (expected) assertThat(hasRing).isTrue() else assertThat(hasRing).isFalse()
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
