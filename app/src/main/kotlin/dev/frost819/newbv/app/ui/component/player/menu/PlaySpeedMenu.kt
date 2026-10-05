package dev.frost819.newbv.app.ui.component.player.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.ui.component.player.ifElse
import dev.frost819.newbv.app.ui.component.player.menu.component.MenuListItem
import dev.frost819.newbv.app.ui.state.player.MenuFocusState
import dev.frost819.newbv.data.datastore.PlaySpeed

/** 倍速菜单展示顺序（从高到低）。 */
private val PLAY_SPEED_MENU_ORDER = listOf(PlaySpeed.X2, PlaySpeed.X1_5, PlaySpeed.X1_25, PlaySpeed.X1, PlaySpeed.X0_5)

/** [PlaySpeed] 的菜单显示名（显示名归 app 层，data 层只存编码与倍速值）。 */
private val PlaySpeed.menuName: String
    get() = when (this) {
        PlaySpeed.X2 -> "2.0x"
        PlaySpeed.X1_5 -> "1.5x"
        PlaySpeed.X1_25 -> "1.25x"
        PlaySpeed.X1 -> "1.0x"
        PlaySpeed.X0_5 -> "0.5x"
    }

/**
 * 倍速设置面板。
 *
 * 单列列表，5 个预设倍速选项。
 * 按方向右键返回导航列表。
 *
 * @param modifier 修饰符
 * @param selectedSpeed 当前选中的倍速
 * @param onPlaySpeedChange 倍速变化回调
 * @param onFocusStateChange 焦点状态变化回调
 */
@Composable
fun PlaySpeedMenuList(
    modifier: Modifier = Modifier,
    selectedSpeed: PlaySpeed,
    onPlaySpeedChange: (Float) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                                return@onPreviewKeyEvent false
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (it.key == Key.DirectionRight) {
                            onFocusStateChange(MenuFocusState.MenuNav)
                        }
                        false
                    }.focusRestorer(focusRequester),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(PLAY_SPEED_MENU_ORDER) { _, item ->
                MenuListItem(
                    modifier =
                        Modifier
                            .ifElse(
                                item == selectedSpeed,
                                Modifier.focusRequester(focusRequester),
                            ),
                    text = item.menuName,
                    selected = selectedSpeed == item,
                    onClick = { onPlaySpeedChange(item.speed) },
                )
            }
        }
    }
}
