package dev.frost819.newbv.app.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.network.RemoteConsoleServer

/**
 * 手机远程输入：展示常驻远程控制台的地址二维码。
 * 手机页面提交后直接触发登录并回传结果（提交处理由应用启动时常驻注册的
 * [dev.frost819.newbv.app.network.CookieLoginRemoteFeature] 完成）。
 */
@Composable
internal fun CookieRemoteInput(
    modifier: Modifier = Modifier,
) {
    // 统一远程控制台常驻（应用启动即运行），本页仅展示控制台地址二维码
    val url = remember { RemoteConsoleServer.getUrl() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.login_cookie_remote_title),
            style = MaterialTheme.typography.titleMedium,
        )
        url?.let {
            QrCodeImage(url = it, modifier = Modifier.size(220.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.login_cookie_remote_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
