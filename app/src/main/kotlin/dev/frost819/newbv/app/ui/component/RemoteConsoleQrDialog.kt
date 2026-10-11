package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.frost819.newbv.app.network.RemoteConsoleServer
import dev.frost819.newbv.app.ui.screen.login.QrCodeImage
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 统一远程控制台二维码弹窗（各远程输入入口复用）。
 *
 * 展示常驻控制台的地址二维码与文字地址，手机扫码后进入控制台页，
 * 页面按当前注册的功能动态渲染标签。
 *
 * @param title 弹窗标题（如「远程管理弹幕屏蔽」「手机远程搜索」）。
 * @param onDismiss 关闭回调。
 */
@Composable
fun RemoteConsoleQrDialog(
    title: String,
    onDismiss: () -> Unit,
) {
    var showUrlText by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                colors =
                    SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    RemoteConsoleQrContent(qrSize = 200.dp, showUrl = showUrlText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.tv.material3.Button(
                            modifier =
                                Modifier.touchClickable(onClick = { showUrlText = !showUrlText }),
                            onClick = { showUrlText = !showUrlText },
                        ) {
                            Text(if (showUrlText) "隐藏地址" else "显示地址")
                        }
                        androidx.tv.material3.Button(
                            modifier = Modifier.touchClickable(onClick = onDismiss),
                            onClick = onDismiss,
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 控制台地址二维码内容（可嵌入既有布局复用）。
 *
 * @param qrSize 二维码边长。
 * @param showUrl 是否显示文字地址。
 */
@Composable
fun RemoteConsoleQrContent(
    qrSize: Dp = 200.dp,
    showUrl: Boolean = false,
) {
    val url = remember { RemoteConsoleServer.getUrl() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(qrSize + 20.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                QrCodeImage(url = url, modifier = Modifier.size(qrSize))
            } else {
                Text(
                    text = "远程控制台未运行",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (showUrl) {
            Text(
                text = url ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
