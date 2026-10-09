package dev.frost819.newbv.app.ui.component

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.screen.main.LeftNaviContent
import dev.frost819.newbv.data.datastore.LeftNaviItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land")
class GridSidebarNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var saver: FocusSaver
    private val columnCount = mutableStateOf(2)
    private val entries = mutableStateOf((0..5).toList())
    private val headerVisible = mutableStateOf(false)

    private fun showGrid(
        withHeader: Boolean = false,
        withSidebar: Boolean = true,
        count: Int = 6,
        height: Int = 300,
        sections: Boolean = false,
    ) {
        saver = FocusSaver(mutableStateOf(""))
        entries.value = (0 until count).toList()
        headerVisible.value = withHeader
        val sidebar = FocusRequester()
        compose.setContent {
            Row {
                Box(
                    Modifier
                        .size(80.dp, 300.dp)
                        .testTag("sidebar")
                        .focusRequester(sidebar)
                        .focusSaverItem(saver, "sidebar")
                        .focusable(),
                )
                TvLazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount.value),
                    modifier = Modifier.size(400.dp, height.dp),
                    leftExitRequester = if (withSidebar) sidebar else null,
                ) {
                    if (headerVisible.value) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.height(40.dp).width(400.dp))
                        }
                    }
                    if (sections) {
                        items(entries.value.take(3), key = { it }) { index ->
                            Box(
                                Modifier
                                    .height(60.dp)
                                    .testTag("card_$index")
                                    .focusSaverItem(saver, "card_$index")
                                    .focusable(),
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.height(40.dp))
                        }
                    }
                    itemsIndexed(
                        items = if (sections) entries.value.drop(3) else entries.value,
                        key = { _, item -> item },
                    ) { _, index ->
                        Box(
                            Modifier
                                .height(60.dp)
                                .testTag("card_$index")
                                .focusSaverItem(saver, "card_$index")
                                .focusable(),
                        )
                    }
                }
                Box(Modifier.size(60.dp).testTag("other").focusable())
            }
        }
        compose.runOnIdle { saver.focusRequesterFor("card_0").requestFocus() }
        compose.onNodeWithTag("card_0").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
    }

    private fun focus(index: Int) {
        compose.runOnIdle { saver.focusRequesterFor("card_$index").requestFocus() }
        compose.onNodeWithTag("card_$index").assertIsFocused()
    }

    @Test
    fun `首行第一张卡片按左键进入侧边栏`() {
        showGrid()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `第二行可左右移动并从首列返回侧边栏`() {
        showGrid()
        press(Key.DirectionDown)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_3").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `含跨列标题的网格仍可从首列返回侧边栏`() {
        showGrid(withHeader = true)
        press(Key.DirectionDown)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `各行右边缘和不满行的末尾不会跨行或跳到外部控件`() {
        showGrid(count = 5)
        for (index in listOf(1, 3, 4)) {
            focus(index)
            repeat(5) { press(Key.DirectionRight) }
            compose.onNodeWithTag("card_$index").assertIsFocused()
        }
    }

    @Test
    fun `独立页面没有左侧出口时首列保持焦点`() {
        showGrid(withSidebar = false, count = 5)
        for (index in listOf(0, 2, 4)) {
            focus(index)
            repeat(3) { press(Key.DirectionLeft) }
            compose.onNodeWithTag("card_$index").assertIsFocused()
        }
    }

    @Test
    fun `跨列标题和多个列表段的边界使用真实行号`() {
        showGrid(withHeader = true, sections = true)
        for (index in listOf(1, 2, 4, 5)) {
            focus(index)
            press(Key.DirectionRight)
            compose.onNodeWithTag("card_$index").assertIsFocused()
        }
        focus(3)
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_4").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_3").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `改变列数后使用新布局的行边界`() {
        showGrid()
        focus(1)
        compose.runOnIdle { columnCount.value = 3 }
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_2").assertIsFocused()
    }

    @Test
    fun `插入标题和移除前项后焦点索引不会错位`() {
        showGrid()
        focus(2)
        compose.runOnIdle {
            headerVisible.value = true
            entries.value = entries.value.drop(1)
        }
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_1").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `连续向下滚动后左右导航和右侧边界仍正确`() {
        showGrid(count = 100, height = 180)
        repeat(20) { press(Key.DirectionDown) }
        compose.onNodeWithTag("card_40").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_41").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_41").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
    }

    @Test
    fun `项目内按钮可左右移动且边缘不会离开本行`() {
        val first = FocusRequester()
        compose.setContent {
            TvLazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(400.dp, 200.dp)) {
                items(2) { card ->
                    Row {
                        repeat(2) { action ->
                            Box(
                                Modifier
                                    .size(80.dp, 60.dp)
                                    .testTag("action_${card}_$action")
                                    .then(if (card == 0 && action == 0) Modifier.focusRequester(first) else Modifier)
                                    .focusable(),
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        for (tag in listOf("action_0_1", "action_1_0", "action_1_1", "action_1_1")) {
            press(Key.DirectionRight)
            compose.onNodeWithTag(tag).assertIsFocused()
        }
        for (tag in listOf("action_1_0", "action_0_1", "action_0_0", "action_0_0")) {
            press(Key.DirectionLeft)
            compose.onNodeWithTag(tag).assertIsFocused()
        }
    }

    @Test
    fun `出口切换与移除后使用最新目标且保留同行导航`() {
        val first = FocusRequester()
        val exits = listOf(FocusRequester(), FocusRequester())
        val selectedExit = mutableStateOf<FocusRequester?>(exits[0])
        compose.setContent {
            Row {
                Column {
                    exits.forEachIndexed { index, requester ->
                        Box(
                            Modifier
                                .size(80.dp, 60.dp)
                                .testTag("exit_$index")
                                .focusRequester(requester)
                                .focusable(),
                        )
                    }
                }
                TvLazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.size(400.dp, 200.dp),
                    leftExitRequester = selectedExit.value,
                ) {
                    items(2) { index ->
                        Box(
                            Modifier
                                .height(60.dp)
                                .testTag("card_$index")
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .focusable(),
                        )
                    }
                }
            }
        }
        for (index in exits.indices) {
            compose.runOnIdle { selectedExit.value = exits[index] }
            compose.runOnIdle { first.requestFocus() }
            press(Key.DirectionRight)
            compose.onNodeWithTag("card_1").assertIsFocused()
            press(Key.DirectionLeft)
            press(Key.DirectionLeft)
            compose.onNodeWithTag("exit_$index").assertIsFocused()
        }
        compose.runOnIdle { selectedExit.value = null }
        compose.runOnIdle { first.requestFocus() }
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_0").assertIsFocused()
    }

    @Test
    fun `上下键仍可进入顶部导航和底部按钮`() {
        val first = FocusRequester()
        compose.setContent {
            Column {
                Box(Modifier.size(400.dp, 40.dp).testTag("top").focusable())
                TvLazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(400.dp, 200.dp)) {
                    items(2) { index ->
                        Box(
                            Modifier
                                .height(60.dp)
                                .testTag("card_$index")
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .focusable(),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.height(40.dp).testTag("footer").focusable())
                    }
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        press(Key.DirectionUp)
        compose.onNodeWithTag("top").assertIsFocused()
        compose.runOnIdle { first.requestFocus() }
        press(Key.DirectionDown)
        compose.onNodeWithTag("footer").assertIsFocused()
    }

    @Test
    fun `主页面左边缘进入当前选中的真实侧边栏项且切换后仍正确`() {
        val first = FocusRequester()
        val sidebar = FocusRequester()
        val selected = mutableStateOf(LeftNaviItem.Home)
        val focusSaver = FocusSaver(mutableStateOf(""))
        compose.setContent {
            MaterialTheme {
                NavigationDrawer(
                    drawerContent = {
                        LeftNaviContent(
                            selectedItem = selected.value,
                            onLeftNaviItemChanged = { selected.value = it },
                            onOpenSettings = {},
                            onShowUserPanel = {},
                            onFocusToContent = { first.requestFocus() },
                            onLogin = {},
                            focusSaver = focusSaver,
                            selectedItemFocusRequester = sidebar,
                        )
                    },
                ) {
                    CompositionLocalProvider(LocalGridLeftExit provides sidebar) {
                        TvLazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(400.dp, 200.dp)) {
                            item {
                                Box(Modifier.size(100.dp, 60.dp).focusRequester(first).focusable())
                            }
                        }
                    }
                }
            }
        }
        for (item in listOf(LeftNaviItem.Home, LeftNaviItem.Personal, LeftNaviItem.Live)) {
            compose.runOnIdle { selected.value = item }
            compose.runOnIdle { first.requestFocus() }
            press(Key.DirectionLeft)
            compose.runOnIdle {
                assertThat(sidebar.captureFocus()).isTrue()
                sidebar.freeFocus()
            }
            press(Key.DirectionRight)
            compose.runOnIdle {
                assertThat(first.captureFocus()).isTrue()
                first.freeFocus()
            }
        }
    }

    @Test
    fun `同行不可聚焦项目可跳过但不能越过整行边界`() {
        val first = FocusRequester()
        compose.setContent {
            TvLazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.size(400.dp, 200.dp)) {
                items(5) { index ->
                    Box(
                        Modifier
                            .height(60.dp)
                            .testTag("card_$index")
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .then(if (index == 0 || index == 2 || index == 4) Modifier.focusable() else Modifier),
                    )
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_2").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_0").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_0").assertIsFocused()
    }

    @Test
    fun `真实视频卡片边缘停留且长按操作按钮仍可导航`() {
        saver = FocusSaver(mutableStateOf(""))
        val sidebar = FocusRequester()
        compose.setContent {
            MaterialTheme {
                Row {
                    Box(
                        Modifier
                            .size(80.dp, 600.dp)
                            .testTag("sidebar")
                            .focusRequester(sidebar)
                            .focusable(),
                    )
                    TvLazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.size(600.dp, 600.dp).testTag("video_grid"),
                        leftExitRequester = sidebar,
                    ) {
                        items(5) { index ->
                            SmallVideoCard(
                                modifier = Modifier.testTag("card_$index").focusSaverItem(saver, "card_$index"),
                                data =
                                    VideoCardData(
                                        avid = index.toLong(),
                                        title = "视频$index",
                                        cover = "",
                                        upName = "测试",
                                    ),
                                onClick = {},
                                onAddWatchLater = {},
                                onGoToDetailPage = {},
                            )
                        }
                    }
                }
            }
        }
        for (index in listOf(1, 3, 4)) {
            focus(index)
            press(Key.DirectionRight)
            compose.onNodeWithTag("card_$index").assertIsFocused()
        }
        press(Key.DirectionLeft)
        compose.onNodeWithTag("sidebar").assertIsFocused()
        compose.onNodeWithTag("video_grid").performScrollToIndex(0)
        focus(0)
        compose.onNodeWithTag("card_0").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithContentDescription("稍后再看").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("详情").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("card_1").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("card_0").assertIsFocused()
    }

    @Test
    fun `网格内横向列表使用自己的作用域且末尾不能跳到其他板块`() {
        val first = FocusRequester()
        compose.setContent {
            TvLazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(400.dp, 300.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow {
                        items(listOf(0, 1, 2), key = { it }) { index ->
                            Box(
                                Modifier
                                    .size(100.dp, 60.dp)
                                    .testTag("rank_$index")
                                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                    .focusable(),
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.size(400.dp, 60.dp).testTag("other_section").focusable())
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        press(Key.DirectionRight)
        compose.onNodeWithTag("rank_1").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("rank_2").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("rank_2").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("other_section").assertIsFocused()
    }
}
