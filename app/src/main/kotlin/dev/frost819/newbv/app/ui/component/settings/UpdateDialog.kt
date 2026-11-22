package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.GithubApi
import dev.frost819.newbv.app.network.UpdateChannel
import dev.frost819.newbv.app.network.entity.GithubRelease
import dev.frost819.newbv.app.network.entity.findApkAsset
import dev.frost819.newbv.app.network.entity.parseVersionCode
import dev.frost819.newbv.app.util.CacheManager
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 更新检查弹窗。
 *
 * 打开后自动检查最新版本，支持下载 APK 并调起安装。
 * 下载进度通过 [LinearProgressIndicator] 实时显示。
 * 下载完成后不再显示“更新已下载”确认框：有安装来源授权时直接调起系统安装器，
 * 缺授权先引导授权，授权返回自动继续安装（见 [rememberUpdateInstallLauncher]）。
 *
 * 更新源统一为本仓库 Releases：稳定版取 `releases/latest`，
 * 开启"接收预发布版本"后包含 CI 自动构建的 debug 预发布（`v<versionName>` 版本标签）；
 * 并按当前构建 variant 匹配 APK 附件（debug 构建只认 debug 附件）。
 *
 * @param show 是否显示弹窗。
 * @param onHideDialog 关闭弹窗回调。
 */
@Composable
fun UpdateDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = Loggers.get("UpdateDialog")

    // 与当前构建 variant 一致的更新渠道：debug 包只能被 debug 附件覆盖安装
    val updateChannel = if (BuildConfig.DEBUG) UpdateChannel.DEBUG else UpdateChannel.RELEASE

    var downloadedApkPath by rememberSaveable { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf(UpdateStatus.UpdatingInfo) }
    var bytesSentTotal by remember { mutableLongStateOf(0L) }
    var contentLength by remember { mutableLongStateOf(0L) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(targetValue = targetProgress, label = "update progress")
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var latestRelease by remember { mutableStateOf<GithubRelease?>(null) }
    var latestAsset by remember { mutableStateOf<GithubRelease.Asset?>(null) }

    // 直通安装的异常路径提示（缺授权 / 调起失败）；正常路径下载完成即调起安装器
    var permissionPrompt by rememberSaveable { mutableStateOf(false) }
    var permissionDeniedBefore by rememberSaveable { mutableStateOf(false) }
    var installError by rememberSaveable { mutableStateOf<String?>(null) }
    val installLauncher =
        rememberUpdateInstallLauncher(
            onNeedPermission = { stillDenied ->
                permissionDeniedBefore = stillDenied
                permissionPrompt = true
            },
            onFailed = { message ->
                permissionPrompt = false
                permissionDeniedBefore = false
                installError = message
            },
            onLaunched = {
                // 已交给系统安装器：收起更新弹窗，安装结果由系统界面呈现
                permissionPrompt = false
                permissionDeniedBefore = false
                installError = null
                downloadedApkPath = null
                onHideDialog()
            },
        )

    DisposableEffect(show) {
        if (!show) {
            downloadJob?.cancel()
        }
        onDispose {
            downloadJob?.cancel()
        }
    }

    val checkUpdate: () -> Unit = {
        updateStatus = UpdateStatus.UpdatingInfo
        latestAsset = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                val release =
                    GithubApi.getLatestBuild(
                        includePrerelease = Prefs.acceptPrerelease,
                        assetChannels = updateChannel.assetKeywords,
                    )
                latestRelease = release
                // 仓库无 Release 或无匹配当前 variant 的附件：视为无可用更新
                if (release == null) {
                    updateStatus = UpdateStatus.NoAvailableUpdate
                    return@launch
                }
                val asset = release.findApkAsset(updateChannel.assetKeywords)
                latestAsset = asset
                val revision =
                    asset?.parseVersionCode()
                        ?: error("Update asset not found in release ${release.name}")
                if (revision <= BuildConfig.VERSION_CODE) {
                    updateStatus = UpdateStatus.NoAvailableUpdate
                    return@launch
                }
            }.onFailure {
                logger.error(it) { "Failed to get latest version" }
                updateStatus = UpdateStatus.CheckError
            }.onSuccess {
                logger.info { "Find latest version ${latestRelease?.name}" }
                updateStatus = UpdateStatus.Ready
            }
        }
    }

    val startUpdate: () -> Unit = {
        val release = latestRelease
        val asset = latestAsset
        if (release == null || asset == null) {
            updateStatus = UpdateStatus.DownloadError
        } else {
            updateStatus = UpdateStatus.Downloading
            downloadJob =
                scope.launch(Dispatchers.IO) {
                    val tempDir = File(context.cacheDir, "update_downloader")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val tempFile = File(tempDir, asset.name)
                    tempFile.createNewFile()
                    runCatching {
                        GithubApi.downloadUpdate(release, tempFile, channel = updateChannel) { downloaded, total ->
                            bytesSentTotal = downloaded
                            contentLength = total
                            targetProgress =
                                if (total > 0) {
                                    downloaded.toFloat() / total
                                } else {
                                    0f
                                }
                        }
                        // 缓存写入后检查阈值，保留刚下载的 APK 待安装
                        CacheManager(context).checkCache(preserve = tempFile)
                        withContext(Dispatchers.Main) { downloadedApkPath = tempFile.absolutePath }
                    }.onFailure {
                        if (it is CancellationException) throw it
                        logger.error(it) { "Failed to download update" }
                        updateStatus = UpdateStatus.DownloadError
                    }
                }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            checkUpdate()
        } else {
            updateStatus = UpdateStatus.UpdatingInfo
        }
    }

    // 下载完成即调起安装器（缺授权时先转授权页）；路径置空/变化前只触发一次
    LaunchedEffect(downloadedApkPath) {
        val path = downloadedApkPath ?: return@LaunchedEffect
        installLauncher(File(path))
    }

    if (show && installError != null) {
        UpdateInstallPromptDialog(
            title = "安装失败",
            message = requireNotNull(installError),
            confirmText = "重试安装",
            onConfirm = {
                installError = null
                downloadedApkPath?.let { installLauncher(File(it)) }
            },
            onDismiss = {
                installError = null
                permissionPrompt = false
                permissionDeniedBefore = false
                downloadedApkPath = null
                onHideDialog()
            },
        )
    } else if (show && permissionPrompt) {
        UpdateInstallPromptDialog(
            title = "需要安装权限",
            message =
                if (permissionDeniedBefore) {
                    "仍未允许安装此来源的应用。请开启权限后重试安装。"
                } else {
                    "更新已下载，但尚未允许安装此来源的应用。请开启权限，返回后将继续安装。"
                },
            confirmText = "去授权",
            onConfirm = { installLauncher.requestPermission() },
            onDismiss = {
                permissionPrompt = false
                permissionDeniedBefore = false
                installError = null
                downloadedApkPath = null
                onHideDialog()
            },
        )
    } else if (show) {
        AlertDialog(
            modifier = modifier.width(400.dp),
            onDismissRequest = onHideDialog,
            title = {
                Text(
                    text =
                        when (updateStatus) {
                            UpdateStatus.UpdatingInfo -> "获取更新信息中"
                            UpdateStatus.Ready -> latestRelease?.versionLabel ?: "Loading..."
                            UpdateStatus.Downloading -> "下载中"
                            UpdateStatus.NoAvailableUpdate -> "无可用更新"
                            UpdateStatus.CheckError -> "检查更新失败"
                            UpdateStatus.DownloadError -> "下载失败"
                        },
                )
            },
            text = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo -> {
                        Text(text = "检查更新中...")
                    }

                    UpdateStatus.Ready -> {
                        Text(text = latestRelease?.body ?: "Empty content")
                    }

                    UpdateStatus.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = "${bytesSentTotal.toMBString()}/${contentLength.toMBString()}",
                                )
                            }
                        }
                    }

                    UpdateStatus.DownloadError -> {
                        Text(text = "下载失败")
                    }

                    UpdateStatus.CheckError -> {
                        Text(text = "获取更新信息失败")
                    }

                    UpdateStatus.NoAvailableUpdate -> {
                        Text(text = "真没更新，骗你是小狗！")
                    }
                }
            },
            confirmButton = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo, UpdateStatus.NoAvailableUpdate,
                    UpdateStatus.Downloading,
                    -> {}

                    UpdateStatus.Ready -> {
                        Button(
                            onClick = startUpdate,
                            modifier = Modifier.touchClickable(onClick = startUpdate),
                            shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ControlFocusDefaults.buttonColors(),
                            border = ControlFocusDefaults.buttonBorder(),
                        ) {
                            Text(text = "立即更新")
                        }
                    }

                    UpdateStatus.DownloadError, UpdateStatus.CheckError -> {
                        Button(
                            onClick = checkUpdate,
                            modifier = Modifier.touchClickable(onClick = checkUpdate),
                            shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ControlFocusDefaults.buttonColors(),
                            border = ControlFocusDefaults.buttonBorder(),
                        ) {
                            Text(text = "再试一次")
                        }
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled =
                        updateStatus !in
                            setOf(
                                UpdateStatus.Downloading,
                            ),
                    onClick = onHideDialog,
                    modifier =
                        Modifier.touchClickable(onClick = {
                            if (updateStatus !in
                                setOf(
                                    UpdateStatus.Downloading,
                                )
                            ) {
                                onHideDialog()
                            }
                        }),
                    shape = ButtonDefaults.shape(shape = ControlFocusDefaults.shape),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors =
                        ControlFocusDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    border = ControlFocusDefaults.buttonBorder(),
                ) {
                    Text(
                        text =
                            when (updateStatus) {
                                UpdateStatus.UpdatingInfo -> "我点错了"
                                UpdateStatus.Ready -> "打死不更"
                                UpdateStatus.NoAvailableUpdate -> "走了走了"
                                UpdateStatus.CheckError, UpdateStatus.DownloadError,
                                -> "算了算了"
                                UpdateStatus.Downloading -> "你已经无路可逃！"
                            },
                    )
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
    }
}

/** 更新状态。 */
enum class UpdateStatus {
    UpdatingInfo,
    Ready,
    Downloading,
    NoAvailableUpdate,
    CheckError,
    DownloadError,
}

private fun Long.toMBString(): String =
    when {
        this >= 1_000_000 -> "%.1f MB".format(this / 1_000_000.0)
        this >= 1_000 -> "%.1f KB".format(this / 1_000.0)
        else -> "$this B"
    }
