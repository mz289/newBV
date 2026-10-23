package dev.frost819.newbv.app.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Calendar

private const val CLOCK_REFRESH_INTERVAL_MS = 30_000L

/** 视频与直播控制器共用的时钟展示。 */
@Composable
internal fun Clock(
    modifier: Modifier = Modifier,
    hour: Int,
    minute: Int,
) {
    Text(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 1f)),
        text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
    )
}

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
