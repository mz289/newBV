package dev.frost819.newbv.app.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.frost819.newbv.app.util.PlayerConstants
import kotlinx.coroutines.delay

/** 可见时开始倒计时，每次交互续期；隐藏或拖拽期间由调用方停用。 */
@Composable
fun rememberControllerAutoHide(
    visible: Boolean,
    timeoutMs: Long = PlayerConstants.CONTROLLER_AUTO_HIDE_MS,
    onHide: () -> Unit,
): () -> Unit {
    val interactionTick = remember { mutableIntStateOf(0) }
    val currentOnHide by rememberUpdatedState(onHide)

    LaunchedEffect(visible, interactionTick.intValue, timeoutMs) {
        if (!visible) return@LaunchedEffect
        delay(timeoutMs)
        currentOnHide()
    }

    return remember { { interactionTick.intValue++ } }
}
