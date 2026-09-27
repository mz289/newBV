package dev.frost819.newbv.app.ui.component

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.ui.component.settings.UpdateInstallDialog
import dev.frost819.newbv.core.theme.BVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 验证授权返回、安装取消和启动失败不会锁住更新弹窗，且重试复用同一 APK。 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallDialogTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry =
        object : ActivityResultRegistry() {
            var request = 0
            var intent: Intent? = null
            var fail = false

            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                if (fail) throw android.content.ActivityNotFoundException("No installer")
                request = requestCode
                intent = contract.createIntent(context, input)
            }
        }

    private fun show(
        file: File,
        permission: () -> Boolean,
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry = registry
                    },
            ) {
                BVTheme { UpdateInstallDialog(file, onDismiss, permission) }
            }
        }
    }

    private fun apk(): File {
        val directory = File(context.cacheDir, "update_downloader").apply { mkdirs() }
        return File(directory, "install-test.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @Test fun denied_permission_then_granted_then_cancelled_can_retry_and_close() {
        val file = apk()
        var granted = false
        var closed = false
        show(file, { granted }, { closed = true })
        rule.onNodeWithText("去授权").performClick()
        assertThat(registry.intent?.action).isEqualTo(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        assertThat(registry.intent?.dataString).isEqualTo("package:${context.packageName}")
        rule.runOnIdle { registry.dispatchResult(registry.request, Activity.RESULT_CANCELED, null) }
        rule.onNodeWithText("尚未允许安装此来源的应用。请开启权限后继续安装。").assertIsDisplayed()
        rule.onNodeWithText("关闭").assertIsEnabled()
        rule.onNodeWithText("去授权").performClick()
        rule.runOnIdle {
            granted = true
            registry.dispatchResult(registry.request, Activity.RESULT_OK, null)
        }
        rule.onNodeWithText("继续安装").performClick()
        val firstUri = registry.intent?.data
        assertThat(registry.intent?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(registry.intent?.type).isEqualTo("application/vnd.android.package-archive")
        assertThat(firstUri?.scheme).isEqualTo("content")
        assertThat(registry.intent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION)).isEqualTo(1)
        rule.runOnIdle { registry.dispatchResult(registry.request, Activity.RESULT_CANCELED, null) }
        rule.onNodeWithText("已从系统安装界面返回。如未完成更新，可重试安装或关闭。").assertIsDisplayed()
        rule.onNodeWithText("继续安装").performClick()
        assertThat(registry.intent?.data).isEqualTo(firstUri)
        rule.onNodeWithText("关闭").performClick()
        rule.runOnIdle { assertThat(closed).isTrue() }
        file.delete()
    }

    @Test fun missing_installer_shows_error_and_keeps_close_enabled() {
        val file = apk()
        registry.fail = true
        show(file, { true })
        rule.onNodeWithText("继续安装").performClick()
        rule.onNodeWithText("无法打开系统安装或授权界面。请检查设备的安装限制后重试。").assertIsDisplayed()
        rule.onNodeWithText("关闭").assertIsEnabled()
        rule.onNodeWithText("继续安装").assertIsEnabled()
        file.delete()
    }

    @Test fun deleted_download_shows_recovery_message() {
        show(File(context.cacheDir, "missing-update.apk"), { true })
        rule.onNodeWithText("继续安装").performClick()
        rule.onNodeWithText("下载文件不存在或为空，请关闭弹窗后重新下载。").assertIsDisplayed()
        assertThat(registry.intent).isNull()
        rule.onNodeWithText("关闭").assertIsEnabled()
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
