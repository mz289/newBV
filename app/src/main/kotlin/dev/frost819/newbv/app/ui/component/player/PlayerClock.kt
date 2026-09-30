package dev.frost819.newbv.app.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Calendar

private const val CLOCK_REFRESH_INTERVAL_MS = 30_000L

private fun nowClock(): Pair<Int, Int> {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
}

/**
 * 当前时分（hour, minute），每 30 秒自刷新。
 *
 * 时钟是纯 UI 展示信息，放在组件内部自刷新即可，
 * 不应进入 [dev.frost819.newbv.app.ui.state.player.PlayerUiState] 触发全量重组。
 */
@Composable
fun rememberClock(): Pair<Int, Int> {
    var clock by remember { mutableStateOf(nowClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_REFRESH_INTERVAL_MS)
            clock = nowClock()
        }
    }
    return clock
}
