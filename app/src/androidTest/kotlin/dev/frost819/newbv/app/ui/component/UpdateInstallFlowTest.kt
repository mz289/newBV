package dev.frost819.newbv.app.ui.component

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.component.settings.UpdateInstallLauncher
import dev.frost819.newbv.app.ui.component.settings.rememberUpdateInstallLauncher
import dev.frost819.newbv.core.theme.BVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 验证下载完成后的直通安装流程：
 * 有授权直接调起安装器并回调收起弹窗；缺授权先转授权页、授权返回自动续装；
 * 调起失败/文件缺失有明确提示且不锁死流程，重试复用同一 APK。
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallFlowTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry =
        object : ActivityResultRegistry() {
            var request = 0
            val intents = mutableListOf<Intent>()
            var fail = false

            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                if (fail) throw android.content.ActivityNotFoundException("No installer")
                request = requestCode
                intents += contract.createIntent(context, input)
            }
        }

    private fun launcherUnderTest(
        granted: () -> Boolean,
        needPermission: (Boolean) -> Unit = {},
        failed: (String) -> Unit = {},
        launched: () -> Unit = {},
    ): UpdateInstallLauncher {
        lateinit var launcher: UpdateInstallLauncher
        rule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry = registry
                    },
            ) {
                BVTheme {
                    launcher = rememberUpdateInstallLauncher(
                        canInstall = granted,
                        onNeedPermission = needPermission,
                        onFailed = failed,
                        onLaunched = launched,
                    )
                }
            }
        }
        return launcher
    }

    private fun apk(): File {
        val directory = File(context.cacheDir, "update_downloader").apply { mkdirs() }
        return File(directory, "install-test.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @Test fun granted_launches_installer_directly_and_reports_launched() {
        val file = apk()
        var launched = 0
        var needPermissionCalls = 0
        val launcher = launcherUnderTest(granted = { true }, launched = { launched += 1 }, needPermission = { needPermissionCalls += 1 })
        rule.runOnIdle { launcher(file) }
        rule.runOnIdle {
            assertThat(registry.intents.last().action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(registry.intents.last().type).isEqualTo("application/vnd.android.package-archive")
            assertThat(registry.intents.last().data?.scheme).isEqualTo("content")
            assertThat(registry.intents.last().flags.and(Intent.FLAG_GRANT_READ_URI_PERMISSION)).isEqualTo(1)
            assertThat(launched).isEqualTo(1)
            assertThat(needPermissionCalls).isEqualTo(0)
            // 安装器关闭（无论是否装完）不再改变状态
            registry.dispatchResult(registry.request, Activity.RESULT_CANCELED, null)
            assertThat(launched).isEqualTo(1)
        }
        file.delete()
    }

    @Test fun denied_permission_prompts_then_granted_return_auto_installs() {
        val file = apk()
        var granted = false
        val needPermissionCalls = mutableListOf<Boolean>()
        var launched = 0
        val launcher = launcherUnderTest(
            granted = { granted },
            needPermission = { stillDenied -> needPermissionCalls += stillDenied },
            launched = { launched += 1 },
        )
        rule.runOnIdle {
            launcher(file)
            assertThat(registry.intents).isEmpty()
            assertThat(needPermissionCalls).containsExactly(false)
        }
        rule.runOnIdle { launcher.requestPermission() }
        rule.runOnIdle {
            assertThat(registry.intents.last().action).isEqualTo(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            assertThat(registry.intents.last().dataString).isEqualTo("package:${context.packageName}")
        }
        rule.runOnIdle {
            granted = true
            registry.dispatchResult(registry.request, Activity.RESULT_OK, null)
            assertThat(registry.intents.last().action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(registry.intents.last().data?.scheme).isEqualTo("content")
            assertThat(launched).isEqualTo(1)
            assertThat(needPermissionCalls).containsExactly(false)
        }
        file.delete()
    }

    @Test fun denied_return_prompts_again_with_still_denied() {
        val file = apk()
        var granted = false
        val needPermissionCalls = mutableListOf<Boolean>()
        val launcher = launcherUnderTest(granted = { granted }, needPermission = { needPermissionCalls += it })
        rule.runOnIdle {
            launcher(file)
            registry.dispatchResult(registry.request, Activity.RESULT_CANCELED, null)
            assertThat(registry.intents.single().action).isEqualTo(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            assertThat(needPermissionCalls).containsExactly(false, true).inOrder()
        }
        file.delete()
    }

    @Test fun missing_installer_reports_failure_and_retry_reuses_file() {
        val file = apk()
        val failures = mutableListOf<String>()
        var launched = 0
        val launcher = launcherUnderTest(granted = { true }, failed = { failures += it }, launched = { launched += 1 })
        rule.runOnIdle { launcher(file) }
        rule.runOnIdle {
            assertThat(failures).containsExactly("无法打开系统安装界面。请检查设备的安装限制后重试。")
            assertThat(registry.intents).isEmpty()
            assertThat(launched).isEqualTo(0)
        }
        rule.runOnIdle {
            registry.fail = false
            launcher(file)
        }
        rule.runOnIdle {
            assertThat(registry.intents).hasSize(2)
            assertThat(registry.intents.last().action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(launched).isEqualTo(1)
            // 重试复用同一 APK：两次调起的 FileProvider URI 一致
            assertThat(registry.intents[1].data).isEqualTo(registry.intents[0].data)
            assertThat(registry.intents[0].data?.scheme).isEqualTo("content")
        }
        file.delete()
    }

    @Test fun deleted_download_reports_file_error() {
        val failures = mutableListOf<String>()
        val launcher = launcherUnderTest(granted = { true }, failed = { failures += it })
        rule.runOnIdle { launcher(File(context.cacheDir, "missing-update.apk")) }
        rule.runOnIdle {
            assertThat(failures).containsExactly("下载文件不存在或为空，请关闭弹窗后重新下载。")
            assertThat(registry.intents).isEmpty()
        }
    }

    @Test fun installed_manifest_declares_install_permission() {
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
        assertThat(info.requestedPermissions?.toList()).contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES)
    }
}
