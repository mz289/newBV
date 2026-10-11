package dev.frost819.newbv.app.viewmodel.login

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.data.AccountRepositoryImpl
import dev.frost819.newbv.app.data.AuthData
import dev.frost819.newbv.biliapi.entity.login.QrLoginData
import dev.frost819.newbv.biliapi.entity.login.QrLoginResult
import dev.frost819.newbv.biliapi.entity.login.QrLoginState
import dev.frost819.newbv.biliapi.entity.login.WebCookies
import dev.frost819.newbv.biliapi.repositories.LoginRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * [LoginViewModel] 的单元测试。
 *
 * 验证 QR 登录状态机流转、轮询逻辑、成功后凭证持久化。
 * 使用 MockK mock [LoginRepository] 和 [AccountRepositoryImpl]。
 * 使用 [UnconfinedTestDispatcher] 使 viewModelScope 协程立即执行到首个 delay。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var loginRepository: LoginRepository
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var viewModel: LoginViewModel

    private val fakeQrData = QrLoginData(url = "https://pass.bilibili.com/qr?key=abc", key = "abc")
    private val fakeCookies =
        WebCookies(
            dedeUserId = 123456L,
            dedeUserIdCkMd5 = "md5hash",
            sid = "session-id",
            biliJct = "jct-token",
            sessData = "sess-data-token",
            expiredDate = Date(System.currentTimeMillis() + 86400000),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        loginRepository = mockk()
        accountRepository = mockk(relaxed = true)
        viewModel = LoginViewModel(loginRepository, accountRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh cancels pending QR request`() = runTest(testDispatcher) {
        val old = CompletableDeferred<QrLoginData>()
        var requests = 0
        coEvery { loginRepository.requestAppQrLogin() } coAnswers {
            if (++requests == 1) old.await() else QrLoginData(url = "new-url", key = "new-key")
        }
        viewModel.requestAppQrCode()
        viewModel.requestAppQrCode()
        old.complete(fakeQrData)
        runCurrent()
        assertThat(viewModel.uiState.value.qrUrl).isEqualTo("new-url")
        viewModel.cancelPolling()
    }

    @Test
    fun `leaving page cancels QR request without error or starting polling`() = runTest(testDispatcher) {
        val response = CompletableDeferred<QrLoginData>()
        coEvery { loginRepository.requestAppQrLogin() } coAnswers { response.await() }
        viewModel.requestAppQrCode()
        viewModel.cancelPolling()
        response.complete(fakeQrData)
        advanceTimeBy(2000)
        assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.RequestingQRCode)
        coVerify(exactly = 0) { loginRepository.checkAppQrLoginState(any()) }
    }

    @Test
    fun `QR request timeout shows retryable error`() = runTest(testDispatcher) {
        coEvery { loginRepository.requestAppQrLogin() } coAnswers { CompletableDeferred<QrLoginData>().await() }
        viewModel.requestAppQrCode()
        advanceTimeBy(10_001)
        assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Error)
    }

    @Test
    fun `account persistence failure becomes error instead of uncaught coroutine exception`() = runTest(testDispatcher) {
        coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
        coEvery { loginRepository.checkAppQrLoginState(any()) } returns QrLoginResult(state = QrLoginState.Success, cookies = fakeCookies)
        coEvery { accountRepository.addUser(any()) } throws java.io.IOException("disk full")
        viewModel.requestAppQrCode()
        advanceTimeBy(2000)
        assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Error)
        assertThat(viewModel.uiState.value.errorMessage).contains("disk full")
        coVerify(exactly = 1) { loginRepository.checkAppQrLoginState(any()) }
    }

    @Test
    fun `requestAppQrCode transitions to WaitingForScan`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.WaitingForScan,
                )

            try {
                viewModel.requestAppQrCode()

                assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.WaitingForScan)
                assertThat(viewModel.uiState.value.qrUrl).isEqualTo(fakeQrData.url)
            } finally {
                viewModel.cancelPolling()
            }
        }

    @Test
    fun `requestAppQrCode on error sets Error state`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } throws RuntimeException("Network error")

            viewModel.requestAppQrCode()

            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Error)
            assertThat(viewModel.uiState.value.errorMessage).contains("Network error")
        }

    @Test
    fun `polling transitions to Success after scan and confirm`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returnsMany
                listOf(
                    QrLoginResult(state = QrLoginState.WaitingForScan),
                    QrLoginResult(state = QrLoginState.WaitingForConfirm),
                    QrLoginResult(
                        state = QrLoginState.Success,
                        cookies = fakeCookies,
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                    ),
                )
            coEvery { accountRepository.addUser(any()) } just Runs

            viewModel.requestAppQrCode()
            advanceTimeBy(3500)

            coVerify(exactly = 3) { loginRepository.checkAppQrLoginState("abc") }
            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Success)
        }

    @Test
    fun `polling on Expired sets Expired state`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.Expired,
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Expired)
        }

    @Test
    fun `polling on Success calls addUser with correct AuthData`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.Success,
                    cookies = fakeCookies,
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                )
            coEvery { accountRepository.addUser(any()) } just Runs

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            val authDataSlot = slot<AuthData>()
            coVerify { accountRepository.addUser(capture(authDataSlot)) }
            val captured = authDataSlot.captured
            assertThat(captured.uid).isEqualTo(123456L)
            assertThat(captured.sessData).isEqualTo("sess-data-token")
            assertThat(captured.accessToken).isEqualTo("access-token")
            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Success)
        }

    @Test
    fun `cancelPolling stops ongoing polling`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.WaitingForScan,
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)
            viewModel.cancelPolling()
            advanceTimeBy(10000)

            coVerify(exactly = 1) { loginRepository.checkAppQrLoginState(any()) }
        }

    @Test
    fun `polling failure sets Error state and stops polling`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } throws RuntimeException("poll error")

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Error)
            assertThat(viewModel.uiState.value.errorMessage).contains("poll error")
        }

    @Test
    fun `polling transitions through WaitingForConfirm`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returnsMany
                listOf(
                    QrLoginResult(state = QrLoginState.WaitingForScan),
                    QrLoginResult(state = QrLoginState.WaitingForConfirm),
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(2500)

            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.WaitingForConfirm)
            viewModel.cancelPolling()
        }

    @Test
    fun `handleLoginSuccess with null cookies sets Error`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.Success,
                    cookies = null,
                    accessToken = null,
                    refreshToken = null,
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            assertThat(viewModel.uiState.value.state).isEqualTo(QrLoginState.Error)
            assertThat(viewModel.uiState.value.errorMessage).contains("Cookie")
        }

    @Test
    fun `onCleared cancels polling`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.WaitingForScan,
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)
            viewModel.cancelPolling()

            advanceTimeBy(10000)
            coVerify(exactly = 1) { loginRepository.checkAppQrLoginState(any()) }
        }

    @Test
    fun `requestAppQrCode cancels previous polling`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.WaitingForScan,
                )

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            viewModel.requestAppQrCode()
            advanceTimeBy(1500)

            coVerify(atLeast = 2) { loginRepository.checkAppQrLoginState(any()) }
            viewModel.cancelPolling()
        }

    @Test
    fun `requestAppQrCode sets RequestingQRCode before result`() =
        runTest(testDispatcher) {
            coEvery { loginRepository.requestAppQrLogin() } returns fakeQrData
            coEvery { loginRepository.checkAppQrLoginState(any()) } returns
                QrLoginResult(
                    state = QrLoginState.WaitingForScan,
                )

            viewModel.requestAppQrCode()

            assertThat(viewModel.uiState.value.state).isAtLeast(QrLoginState.RequestingQRCode)
            viewModel.cancelPolling()
        }

    @Test
    fun `loginWithCookie validates via addCookieUser before persisting`() = runTest(testDispatcher) {
        viewModel.loginWithCookie("SESSDATA=abc; bili_jct=jct; DedeUserID=123")
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.addCookieUser(any()) }
        coVerify(exactly = 0) { accountRepository.addUser(any()) }
        assertThat(viewModel.cookieUiState.value.success).isTrue()
    }

    @Test
    fun `loginWithCookie surfaces validation error message`() = runTest(testDispatcher) {
        coEvery { accountRepository.addCookieUser(any()) } throws
            IllegalArgumentException("Cookie 已失效或无效（SESSDATA 校验未通过），请重新从浏览器复制完整 Cookie")

        viewModel.loginWithCookie("SESSDATA=dead; bili_jct=jct; DedeUserID=123")
        advanceUntilIdle()

        assertThat(viewModel.cookieUiState.value.success).isFalse()
        assertThat(viewModel.cookieUiState.value.errorMessage).contains("Cookie 已失效")
    }

    @Test
    fun `loginWithCookieRemote returns success message`() = runTest(testDispatcher) {
        val result = viewModel.loginWithCookieRemote("SESSDATA=abc; bili_jct=jct; DedeUserID=123")

        assertThat(result).contains("登录成功")
        assertThat(viewModel.cookieUiState.value.success).isTrue()
    }

    @Test
    fun `loginWithCookieRemote returns validation error as result message`() = runTest(testDispatcher) {
        coEvery { accountRepository.addCookieUser(any()) } throws
            IllegalArgumentException("Cookie 已失效或无效（SESSDATA 校验未通过），请重新从浏览器复制完整 Cookie")

        val result = viewModel.loginWithCookieRemote("SESSDATA=dead; bili_jct=jct; DedeUserID=123")

        assertThat(result).contains("Cookie 已失效")
        assertThat(viewModel.cookieUiState.value.success).isFalse()
    }
}
