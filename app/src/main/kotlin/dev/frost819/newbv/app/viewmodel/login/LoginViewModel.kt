package dev.frost819.newbv.app.viewmodel.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.AccountRepositoryImpl
import dev.frost819.newbv.app.data.CookieLoginParser
import dev.frost819.newbv.app.viewmodel.common.LOAD_TIMEOUT_MS
import dev.frost819.newbv.app.viewmodel.common.rethrowUnlessTimeout
import dev.frost819.newbv.biliapi.entity.login.QrLoginState
import dev.frost819.newbv.biliapi.entity.login.WebCookies
import dev.frost819.newbv.biliapi.repositories.LoginRepository
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * 扫码登录 UI 状态。
 *
 * @property state QR 登录状态机当前状态。
 * @property qrUrl 二维码内容 URL（用于渲染二维码图片）。
 * @property errorMessage 错误信息（仅 Error 状态有值）。
 */
data class QrLoginUiState(
    val state: QrLoginState = QrLoginState.Ready,
    val qrUrl: String = "",
    val errorMessage: String = "",
)

/**
 * Cookie 登录 UI 状态。
 *
 * @property submitting 是否正在提交登录。
 * @property success 是否登录成功。
 * @property errorMessage 错误信息（解析失败/保存失败时非空）。
 */
data class CookieLoginUiState(
    val submitting: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String = "",
)

/**
 * 扫码登录 ViewModel。
 *
 * 支持 TV QR 登录（App 接口）和 Web QR 登录（Web 接口）。
 *
 * QR 登录流程：
 * 1. [requestQrCode]：请求二维码 URL，生成二维码图片
 * 2. 自动启动轮询，每 1 秒检查扫码状态
 * 3. 状态流转：Ready → RequestingQRCode → WaitingForScan → WaitingForConfirm → Success
 * 4. Success 时：构造 [AuthData]，调用 [AccountRepositoryImpl.addUser] 持久化
 *
 * 另支持 Cookie 登录（[loginWithCookie]）：粘贴浏览器 Cookie 串或凭证 JSON 直接登录，
 * 适用于无法扫码的场景与双账号（解析账号）添加。
 *
 * @property loginRepository 登录接口仓库。
 * @property accountRepository 账户管理仓库。
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val loginRepository: LoginRepository,
        private val accountRepository: AccountRepositoryImpl,
    ) : ViewModel() {
        private val logger = Loggers.get("LoginViewModel")

        private val _uiState = MutableStateFlow(QrLoginUiState())
        val uiState: StateFlow<QrLoginUiState> = _uiState.asStateFlow()

        private val _cookieUiState = MutableStateFlow(CookieLoginUiState())
        val cookieUiState: StateFlow<CookieLoginUiState> = _cookieUiState.asStateFlow()

        /** 轮询协程 Job，用于取消轮询。 */
        private var pollingJob: Job? = null

        /**
         * 请求 TV QR 二维码。
         *
         * 调用 App 接口获取二维码 URL 和 authCode，启动轮询。
         */
        fun requestAppQrCode() {
            cancelPolling()
            _uiState.value = QrLoginUiState(state = QrLoginState.RequestingQRCode)
            pollingJob = viewModelScope.launch {
                runCatching {
                    val qrData = withTimeout(LOAD_TIMEOUT_MS) { loginRepository.requestAppQrLogin() }
                    currentCoroutineContext().ensureActive()
                    _uiState.update {
                        it.copy(
                            state = QrLoginState.WaitingForScan,
                            qrUrl = qrData.url,
                        )
                    }
                    poll(qrData.key)
                }.onFailure { error ->
                    error.rethrowUnlessTimeout()
                    currentCoroutineContext().ensureActive()
                    logger.error(error) { "QR login failed" }
                    _uiState.update {
                        it.copy(
                            state = QrLoginState.Error,
                            errorMessage = error.message ?: "登录失败，请重试",
                        )
                    }
                }
            }
        }

        /**
         * 启动轮询检查扫码状态。
         *
         * 每 1 秒轮询一次，根据返回状态更新 UI。
         * Success 时自动取消轮询并保存登录凭证。
         *
         * @param key TV 扫码登录的 authCode。
         */
        private suspend fun poll(key: String) {
            while (true) {
                delay(1000)
                val result =
                    withTimeout(LOAD_TIMEOUT_MS) {
                        loginRepository.checkAppQrLoginState(key)
                    }
                currentCoroutineContext().ensureActive()
                when (result.state) {
                    QrLoginState.WaitingForScan,
                    QrLoginState.WaitingForConfirm,
                    -> _uiState.update { it.copy(state = result.state) }

                    QrLoginState.Expired -> {
                        _uiState.update { it.copy(state = QrLoginState.Expired) }
                        return
                    }

                    QrLoginState.Success -> {
                        handleLoginSuccess(result.cookies, result.accessToken, result.refreshToken)
                        return
                    }

                    else -> logger.warn { "Unknown QR login state: ${result.state}" }
                }
            }
        }

        /**
         * 处理登录成功。
         *
         * 从 [WebCookies] 构造 [AuthData]，调用 [AccountRepositoryImpl.addUser] 持久化。
         *
         * @param cookies 登录返回的 Cookie 信息。
         * @param accessToken App 接口 token（WebQR 为 null）。
         * @param refreshToken App 接口 refresh token（WebQR 为 null）。
         */
        private suspend fun handleLoginSuccess(
            cookies: WebCookies?,
            accessToken: String?,
            refreshToken: String?,
        ) {
            val cookie =
                cookies ?: run {
                    _uiState.update {
                        it.copy(
                            state = QrLoginState.Error,
                            errorMessage = "登录成功但 Cookie 为空",
                        )
                    }
                    return
                }
            val authData =
                dev.frost819.newbv.app.data.AuthData(
                    uid = cookie.dedeUserId,
                    uidCkMd5 = cookie.dedeUserIdCkMd5,
                    sid = cookie.sid,
                    biliJct = cookie.biliJct,
                    sessData = cookie.sessData,
                    tokenExpiredDate = cookie.expiredDate.time,
                    accessToken = accessToken ?: "",
                    refreshToken = refreshToken ?: "",
                )
            accountRepository.addUser(authData)
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(state = QrLoginState.Success) }
        }

        /**
         * Cookie 登录。
         *
         * 解析输入（浏览器 Cookie 串 / 凭证 JSON，见 [CookieLoginParser]）后
         * 调用 [AccountRepositoryImpl.addUser] 持久化并登录。
         * Cookie 登录无 access_token，仅 Web 通道可用（不影响播放解析账号用途）。
         *
         * @param input Cookie 串或凭证 JSON。
         */
        fun loginWithCookie(input: String) {
            if (_cookieUiState.value.submitting) return
            _cookieUiState.value = CookieLoginUiState(submitting = true)
            viewModelScope.launch {
                runCatching { CookieLoginParser.parse(input) }
                    .mapCatching { authData ->
                        accountRepository.addUser(authData)
                    }.fold(
                        onSuccess = {
                            logger.info { "cookie login success" }
                            _cookieUiState.value = CookieLoginUiState(success = true)
                        },
                        onFailure = { error ->
                            logger.error(error) { "cookie login failed" }
                            _cookieUiState.value =
                                CookieLoginUiState(
                                    errorMessage = error.message ?: "登录失败，请检查 Cookie",
                                )
                        },
                    )
            }
        }

        /**
         * 重置 Cookie 登录状态（清空提交中/错误信息）。
         */
        fun resetCookieLogin() {
            _cookieUiState.value = CookieLoginUiState()
        }

        /**
         * 取消轮询。
         *
         * 在页面退出或重新请求二维码时调用。
         */
        fun cancelPolling() {
            pollingJob?.cancel()
            pollingJob = null
        }

        override fun onCleared() {
            super.onCleared()
            cancelPolling()
        }
    }
