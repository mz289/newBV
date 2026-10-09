package dev.frost819.newbv.app.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.navigation.LoginRoute
import dev.frost819.newbv.app.viewmodel.login.LoginViewModel
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 登录页面导航注册。
 *
 * 支持扫码登录与 Cookie 登录两种方式，登录成功后自动返回上一页。
 */
fun NavGraphBuilder.loginScreen(navController: NavController) {
    composable<LoginRoute> {
        LoginContent(
            onLoginSuccess = { navController.popBackStack() },
        )
    }
}

/**
 * 登录页面内容：扫码登录 / Cookie 登录切换。
 *
 * Cookie 登录适用于无法扫码的场景与双账号（解析账号）添加；
 * 切换 Tab 时扫码轮询随 [QrLoginContent] 离开组合自动取消。
 */
@Composable
private fun LoginContent(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    var showCookieLogin by remember { mutableStateOf(false) }
    val focusSaver = rememberFocusSaver()
    focusSaver.RestoreFocus()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginModeTabs(
            showCookieLogin = showCookieLogin,
            onModeChange = { showCookieLogin = it },
            focusSaver = focusSaver,
        )

        if (showCookieLogin) {
            CookieLoginContent(
                viewModel = viewModel,
                onLoginSuccess = onLoginSuccess,
            )
        } else {
            QrLoginContent(
                viewModel = viewModel,
                onLoginSuccess = onLoginSuccess,
            )
        }
    }
}

/**
 * 登录方式切换 Tab。
 */
@Composable
private fun LoginModeTabs(
    showCookieLogin: Boolean,
    onModeChange: (Boolean) -> Unit,
    focusSaver: FocusSaver,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoginTabButton(
            text = stringResource(R.string.login_tab_qr),
            selected = !showCookieLogin,
            onClick = { onModeChange(false) },
            modifier = Modifier.focusSaverItem(focusSaver, "login_tab_qr"),
        )
        LoginTabButton(
            text = stringResource(R.string.login_tab_cookie),
            selected = showCookieLogin,
            onClick = { onModeChange(true) },
            modifier = Modifier.focusSaverItem(focusSaver, "login_tab_cookie"),
        )
    }
}

/**
 * 登录方式 Tab 按钮（选中态用主色容器高亮）。
 */
@Composable
private fun LoginTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = outerFocusBorder(8.dp),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
