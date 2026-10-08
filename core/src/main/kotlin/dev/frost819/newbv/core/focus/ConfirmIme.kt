package dev.frost819.newbv.core.focus

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/**
 * 输入框"确认键唤起输入法"的交互状态（单源）。
 *
 * TV 端遥控器用方向键导航，输入框一聚焦就拉起软键盘会打断浏览：
 * 聚焦只高亮输入框，焦点在输入框上时按确认键才唤起输入法；
 * 焦点离开后状态自动复位，再次聚焦仍保持静默，直到再次按确认键。
 *
 * 接入需要同时提供两部分：
 * - [confirmImeKeyboardOptions]：拦截 Compose 输入框"聚焦即拉起输入法"的默认行为；
 * - [Modifier.confirmOpenIme]：确认键唤起输入法并复位状态。
 *
 * 所有输入框都应接入这套交互。
 */
@Stable
class ConfirmImeBehavior internal constructor() {
    /** 输入法是否允许随输入框聚焦自动建立会话（确认键后置 true，焦点离开后复位 false）。 */
    internal var imeAllowed by mutableStateOf(false)
}

/** 创建输入框确认键唤起输入法的交互状态，作用域为输入框所在的组合。 */
@Composable
fun rememberConfirmImeBehavior(): ConfirmImeBehavior = remember { ConfirmImeBehavior() }

/**
 * 输入框键盘选项：[ConfirmImeBehavior] 未确认前不允许输入法随聚焦拉起，
 * 按确认键后（会话随键盘选项变化建立）才允许。
 *
 * @param imeAction 输入法动作；[ImeAction.Unspecified] 归一为 [ImeAction.Default]。
 */
fun confirmImeKeyboardOptions(
    behavior: ConfirmImeBehavior,
    imeAction: ImeAction = ImeAction.Unspecified,
): KeyboardOptions = KeyboardOptions(imeAction = imeAction, showKeyboardOnFocus = behavior.imeAllowed)

/**
 * 焦点在输入框上时按确认键唤起输入法。
 *
 * 确认键按下/抬起全部消费，避免输入框把 Enter 当作默认提交处理；
 * 抬起时置位 [ConfirmImeBehavior.imeAllowed] 并请求显示输入法：
 * 首次按下走键盘选项变化建立输入法会话（输入连接可靠建立后拉起），
 * 输入法被返回键收起而焦点仍在时直接重新请求显示。
 * 焦点离开后复位，输入框重新聚焦不会自动拉起输入法。
 *
 * 应与 [confirmImeKeyboardOptions] 成对使用，并放在焦点修饰符（如 focusRequester）之前。
 */
fun Modifier.confirmOpenIme(behavior: ConfirmImeBehavior): Modifier =
    composed {
        val keyboard = LocalSoftwareKeyboardController.current
        onPreviewKeyEvent { event ->
            if (!event.isConfirmKey()) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) {
                behavior.imeAllowed = true
                keyboard?.show()
            }
            true
        }.onFocusChanged { if (!it.isFocused) behavior.imeAllowed = false }
    }
