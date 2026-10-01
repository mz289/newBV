package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import dev.frost819.newbv.app.cast.server.CastNetworkUtil
import kotlinx.coroutines.delay

/**
 * 读取本机局域网 IPv4（用于设置页展示投屏描述地址）。
 *
 * 进设置页时网络接口可能尚未就绪（刚连上 Wi-Fi），短轮询兜底。
 */
@Composable
fun rememberLocalIpv4Address(): State<String> =
    produceState(initialValue = "") {
        repeat(10) {
            val address = CastNetworkUtil.localIpv4Address()
            if (address.isNotBlank() && address != "127.0.0.1") {
                value = address
                return@produceState
            }
            delay(500)
        }
        value = CastNetworkUtil.localIpv4Address()
    }
