package dev.frost819.newbv.app.ui.screen.login

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.viewmodel.login.LoginViewModel
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.confirmImeKeyboardOptions
import dev.frost819.newbv.core.focus.confirmOpenIme
import dev.frost819.newbv.core.focus.rememberConfirmImeBehavior
import dev.frost819.newbv.core.focus.touchClickable

/**
 * Cookie 登录内容。
 *
 * 粘贴浏览器 Cookie 串（SESSDATA/bili_jct/DedeUserID 等）或凭证 JSON（newBV/NeoBV
 * 导出格式）后点击登录；也可从文件导入凭证内容到输入框。
 * 登录成功后回调 [onLoginSuccess]。
 *
 * @param viewModel 登录 ViewModel。
 * @param onLoginSuccess 登录成功回调。
 */
@Composable
fun CookieLoginContent(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.cookieUiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }
    val confirmIme = rememberConfirmImeBehavior()
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 返回键先收起输入法并清除输入框焦点（此时再次返回才退出登录页）
    var imeCollapsed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fileImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()?.let { text ->
                    input = text.trim()
                    viewModel.resetCookieLogin()
                }
            }
        }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onLoginSuccess()
    }

    // 打开即聚焦输入框；软键盘不随聚焦弹出，按确认键才唤起（confirmOpenIme）
    LaunchedEffect(Unit) {
        runCatching { inputFocusRequester.requestFocus() }
    }

    BackHandler {
        if (imeCollapsed) {
            onLoginSuccess()
        } else {
            imeCollapsed = true
            softwareKeyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_cookie_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) imeCollapsed = false }
                            .confirmOpenIme(confirmIme)
                            .focusRequester(inputFocusRequester),
                    value = input,
                    onValueChange = { input = it },
                    minLines = 3,
                    maxLines = 6,
                    singleLine = false,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = confirmImeKeyboardOptions(confirmIme, ImeAction.Done),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.border,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CookieSecondaryButton(
                        text = stringResource(R.string.login_cookie_import_file),
                        onClick = {
                            fileImporter.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    )
                    CookieSecondaryButton(
                        text = stringResource(R.string.login_cookie_clear),
                        onClick = {
                            input = ""
                            viewModel.resetCookieLogin()
                        },
                    )
                    CookiePrimaryButton(
                        text =
                            if (uiState.submitting) {
                                stringResource(R.string.login_cookie_submitting)
                            } else {
                                stringResource(R.string.login_cookie_submit)
                            },
                        enabled = !uiState.submitting && input.isNotBlank(),
                        onClick = { viewModel.loginWithCookie(input) },
                    )
                }

                if (uiState.errorMessage.isNotEmpty()) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CookieRemoteInput(
                modifier = Modifier.width(240.dp),
                onInput = { text ->
                    if (!uiState.submitting && !uiState.success) {
                        input = text
                        viewModel.resetCookieLogin()
                        softwareKeyboard?.hide()
                    }
                },
            )
        }
    }
}

/**
 * Cookie 登录次级按钮（导入文件 / 清空）。
 */
@Composable
internal fun CookieSecondaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.touchClickable(onClick = onClick),
        shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.medium),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors =
            ControlFocusDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = ControlFocusDefaults.buttonBorder(),
    ) {
        Text(text = text)
    }
}

/**
 * Cookie 登录主按钮。
 */
@Composable
private fun CookiePrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (enabled) Modifier.touchClickable(onClick = onClick) else Modifier,
        shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.medium),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors =
            ControlFocusDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        border = ControlFocusDefaults.buttonBorder(),
    ) {
        Text(text = text)
    }
}
