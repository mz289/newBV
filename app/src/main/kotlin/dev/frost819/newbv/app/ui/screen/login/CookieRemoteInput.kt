package dev.frost819.newbv.app.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.network.CookieLoginRemoteFeature
import dev.frost819.newbv.app.network.RemoteConsoleServer
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/**
 * 手机远程输入（参考 FongMi/TV 的提交即生效模式）：
 * 手机页面提交后直接触发登录并回传结果，无需在电视上再次确认。
 */
@Composable
internal fun CookieRemoteInput(
    onLogin: suspend (String) -> String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val logger = Loggers.get("CookieRemoteInput")
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        url = null
        failed = false
        // 统一远程控制台：注册 Cookie 登录标签（其他功能标签由各自入口注册）
        RemoteConsoleServer.start(context)
        RemoteConsoleServer.register(CookieLoginRemoteFeature.create(onLogin))
        try {
            url = RemoteConsoleServer.getUrl()
            logger.info { "remote input url: $url" }
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable) {
                RemoteConsoleServer.unregister(CookieLoginRemoteFeature.ID)
                RemoteConsoleServer.stop()
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.login_cookie_remote_title),
            style = MaterialTheme.typography.titleMedium,
        )
        url?.let { QrCodeImage(url = it, modifier = Modifier.size(220.dp)) }
        Text(
            text =
                stringResource(
                    if (failed) {
                        R.string.login_cookie_remote_error
                    } else if (url == null) {
                        R.string.login_cookie_remote_starting
                    } else {
                        R.string.login_cookie_remote_hint
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (failed) {
            CookieSecondaryButton(
                text = stringResource(R.string.login_retry),
                onClick = { attempt++ },
            )
        }
    }
}
