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
import androidx.compose.runtime.rememberUpdatedState
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
 * 更新流程的异常路径提示弹窗（缺安装来源授权 / 调起失败重试）。
 * 正文与按钮文案由调用方给定；正常路径不经过本弹窗——下载完成后直接调起系统安装器。
 */
@Composable
internal fun UpdateInstallPromptDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.touchClickable(onClick = onConfirm),
                shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                colors = ControlFocusDefaults.buttonColors(),
                border = ControlFocusDefaults.buttonBorder(),
            ) { Text(confirmText) }
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

/** 直通安装的对外操作面：用已下载的 APK 调起安装器，或在缺授权时打开授权页。 */
internal class UpdateInstallLauncher internal constructor(
    private val installAction: (File) -> Unit,
    private val permissionAction: () -> Unit,
) {
    /** 用已下载的 APK 调起系统安装器；缺安装来源授权时先转授权页。 */
    operator fun invoke(file: File) = installAction(file)

    /** 打开系统的“安装未知应用”授权页，授权返回后自动继续安装。 */
    fun requestPermission() = permissionAction()
}

/**
 * 下载完成后的直通安装器调起。
 *
 * 权限齐备时直接调起系统安装器并回调 [onLaunched]（调用方借此收起更新弹窗），
 * 不再显示“更新已下载”的中间确认框；缺授权先转授权页（[onNeedPermission]），
 * 授权返回自动继续安装，仍未授权则以 `stillDenied = true` 再回调；
 * 调起失败回调 [onFailed]，APK 保留供重试。系统安装器返回码在厂商 ROM 间不一致，
 * 不把打开安装器当作安装成功。
 *
 * @param canInstall 安装来源授权检查，默认使用当前设备的系统权限；测试可注入替身。
 */
@Composable
internal fun rememberUpdateInstallLauncher(
    canInstall: (() -> Boolean)? = null,
    onNeedPermission: (stillDenied: Boolean) -> Unit = {},
    onFailed: (message: String) -> Unit = {},
    onLaunched: () -> Unit = {},
): UpdateInstallLauncher {
    val context = LocalContext.current
    val permissionGranted =
        rememberUpdatedState(
            canInstall ?: {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    context.packageManager.canRequestPackageInstalls()
            },
        )
    val currentOnNeedPermission = rememberUpdatedState(onNeedPermission)
    val currentOnFailed = rememberUpdatedState(onFailed)
    val currentOnLaunched = rememberUpdatedState(onLaunched)
    // 路径用 saveable 持有：授权页跳转期间进程被杀后，授权返回仍能续装
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val logger = Loggers.get("UpdateInstallLauncher")

    val installer =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 安装器关闭即返回应用；返回码不作为安装结果。
        }
    val tryInstall: (File) -> Unit = { file ->
        pendingPath = file.absolutePath
        runCatching {
            check(file.isFile && file.length() > 0) { "下载文件不存在或为空，请关闭弹窗后重新下载。" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            installer.launch(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onSuccess {
            currentOnLaunched.value()
        }.onFailure {
            logger.error(it) { "Failed to launch update installation" }
            currentOnFailed.value(
                if (!file.isFile || file.length() == 0L) {
                    "下载文件不存在或为空，请关闭弹窗后重新下载。"
                } else {
                    "无法打开系统安装界面。请检查设备的安装限制后重试。"
                },
            )
        }
    }
    val permissionSettings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (permissionGranted.value()) {
                // 授权返回：直接继续安装，不再要确认
                pendingPath?.let { tryInstall(File(it)) }
            } else {
                currentOnNeedPermission.value(true)
            }
        }
    val requestPermission: () -> Unit = {
        runCatching {
            permissionSettings.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }.onFailure {
            logger.error(it) { "Failed to launch install-permission settings" }
            currentOnFailed.value("无法打开系统授权界面。请检查设备的安装限制后重试。")
        }
    }
    val install: (File) -> Unit = { file ->
        pendingPath = file.absolutePath
        if (permissionGranted.value()) {
            tryInstall(file)
        } else {
            currentOnNeedPermission.value(false)
        }
    }
    return UpdateInstallLauncher(install, requestPermission)
}
