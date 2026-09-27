package dev.frost819.newbv.app.ui.component.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import java.io.File

/**
 * 已下载更新的安装入口。系统安装器负责最终确认，不把打开安装器当作安装成功。
 *
 * 从权限页或安装器返回后允许继续、重试或关闭；重试复用已下载文件。
 * @param file 已下载的 APK。
 * @param onDismiss 关闭更新弹窗。
 * @param canInstall 安装来源授权检查，默认使用当前设备的系统权限。
 */
@Composable
internal fun UpdateInstallDialog(
    file: File,
    onDismiss: () -> Unit,
    canInstall: (() -> Boolean)? = null,
) {
    val context = LocalContext.current
    val permissionGranted =
        canInstall ?: {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
        }
    var message by rememberSaveable {
        mutableStateOf("更新已下载。点击继续安装，在系统安装界面确认。")
    }
    var needsPermission by rememberSaveable { mutableStateOf(!permissionGranted()) }
    val installer =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // ACTION_VIEW 的返回码在不同厂商安装器中并不一致，不能据此断言安装成功。
            message = "已从系统安装界面返回。如未完成更新，可重试安装或关闭。"
        }
    val permissionSettings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            needsPermission = !permissionGranted()
            message =
                if (needsPermission) {
                    "尚未允许安装此来源的应用。请开启权限后继续安装。"
                } else {
                    "已获得安装权限，点击继续安装。"
                }
        }
    val proceed: () -> Unit = {
        runCatching {
            needsPermission = !permissionGranted()
            if (needsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                permissionSettings.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                )
                message = "请在系统设置中允许此应用安装更新，然后返回继续。"
            } else {
                check(file.isFile && file.length() > 0) { "下载文件不存在或为空，请关闭弹窗后重新下载。" }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                installer.launch(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
                message = "已请求打开系统安装界面，请在系统界面确认。若未出现，可重试或关闭。"
            }
        }.onFailure {
            Loggers.get("UpdateInstallDialog").error(it) { "Failed to launch update installation" }
            message =
                if (!file.isFile || file.length() == 0L) {
                    "下载文件不存在或为空，请关闭弹窗后重新下载。"
                } else {
                    "无法打开系统安装或授权界面。请检查设备的安装限制后重试。"
                }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (needsPermission) "需要安装权限" else "安装更新") },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = proceed,
                modifier = Modifier.touchClickable(onClick = proceed),
                shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.buttonColors(),
                border = ControlFocusDefaults.buttonBorder(),
            ) { Text(if (needsPermission) "去授权" else "继续安装") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.touchClickable(onClick = onDismiss),
                shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.buttonColors(),
                border = ControlFocusDefaults.buttonBorder(),
            ) { Text("关闭") }
        },
    )
}
