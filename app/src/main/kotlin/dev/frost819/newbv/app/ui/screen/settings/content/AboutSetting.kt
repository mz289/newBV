package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.GithubApi
import dev.frost819.newbv.app.network.UpdateChannel
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.UpdateDialog
import dev.frost819.newbv.app.ui.screen.settings.SettingsMenuNavItem
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 关于页。
 *
 * 显示当前版本号、最新版本号，提供"接收预发布版本"开关，
 * 按钮打开更新弹窗。更新源统一为本仓库（fork）Releases。
 */
@Composable
fun AboutSetting(modifier: Modifier = Modifier) {
    val logger = Loggers.get("AboutSetting")

    // 与当前构建 variant 一致的更新渠道：debug 包只匹配 debug 附件
    val updateChannel = if (BuildConfig.DEBUG) UpdateChannel.DEBUG else UpdateChannel.RELEASE

    var showUpdateDialog by remember { mutableStateOf(false) }
    var acceptPrerelease by remember { mutableStateOf(Prefs.acceptPrerelease) }
    var latestVersionName by remember { mutableStateOf("Loading...") }

    LaunchedEffect(acceptPrerelease) {
        launch(Dispatchers.IO) {
            runCatching {
                latestVersionName =
                    GithubApi
                        .getLatestBuild(
                            includePrerelease = acceptPrerelease,
                            assetChannels = updateChannel.assetKeywords,
                        )?.name ?: "无可用更新"
                logger.info { "Find latest version $latestVersionName" }
            }.onFailure {
                logger.error(it) { "Failed to get latest version" }
                latestVersionName = "Error"
            }
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = SettingsMenuNavItem.About.displayName,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "当前版本：${BuildConfig.VERSION_NAME}")
                Text(text = "最新版本：$latestVersionName")
            }
            SettingListItem(
                title = "接收预发布版本",
                supportText = "检查更新时包含预发布版本（如 CI 自动构建）",
                trailingContent = {
                    Switch(
                        checked = acceptPrerelease,
                        onCheckedChange = {
                            acceptPrerelease = it
                            Prefs.acceptPrerelease = it
                        },
                    )
                },
                onClick = {
                    acceptPrerelease = !acceptPrerelease
                    Prefs.acceptPrerelease = acceptPrerelease
                },
            )
            Button(
                onClick = { showUpdateDialog = true },
                modifier = Modifier.touchClickable(onClick = { showUpdateDialog = true }),
            ) {
                Text(text = "检查更新")
            }
        }
        Text(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = "https://github.com/mz289/newBV",
        )
    }

    UpdateDialog(
        show = showUpdateDialog,
        onHideDialog = { showUpdateDialog = false },
    )
}
