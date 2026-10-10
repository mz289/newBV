package dev.frost819.newbv.app.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.network.CookieInputServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 页面离开组合时关闭服务，切换登录方式后旧二维码即失效。 */
@Composable
internal fun CookieRemoteInput(
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val receiveInput by rememberUpdatedState(onInput)
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        url = null
        failed = false
        val server =
            CookieInputServer {
                context.assets
                    .open("cookie_input_ui/index.html")
                    .bufferedReader()
                    .use { it.readText() }
            }
        try {
            url =
                withContext(Dispatchers.IO) {
                    val host = checkNotNull(CookieInputServer.localAddress())
                    server.start(host)
                }
            server.inputs.collect { receiveInput(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { server.stop() }
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
