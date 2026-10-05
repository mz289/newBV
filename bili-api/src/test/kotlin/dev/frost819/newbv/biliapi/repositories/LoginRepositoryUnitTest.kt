package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.login.QrLoginState
import dev.frost819.newbv.biliapi.http.BiliPassportHttpApi
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.login.qr.AppQRDataRequest
import dev.frost819.newbv.biliapi.http.entity.login.qr.AppQRLoginData
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [LoginRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliPassportHttpApi] 单例，验证 App 扫码登录流程。不依赖真实网络。
 */
class LoginRepositoryUnitTest {
    private lateinit var repository: LoginRepository

    @BeforeEach
    fun setUp() {
        mockkObject(BiliPassportHttpApi)
        repository = LoginRepository()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliPassportHttpApi)
    }

    // ------------------------------------------------------------------
    // requestAppQrLogin
    // ------------------------------------------------------------------

    @Test
    fun `requestAppQrLogin maps url and authCode`() =
        runTest {
            coEvery { BiliPassportHttpApi.getAppQRUrl(any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        AppQRDataRequest(
                            url = "https://example.com/app-qr",
                            authCode = "auth-code-xyz",
                        ),
                )

            val result = repository.requestAppQrLogin()

            assertThat(result.url).isEqualTo("https://example.com/app-qr")
            assertThat(result.key).isEqualTo("auth-code-xyz")
        }

    // ------------------------------------------------------------------
    // checkAppQrLoginState
    // ------------------------------------------------------------------

    @Test
    fun `checkAppQrLoginState returns WaitingForScan when response code is 86039`() =
        runTest {
            coEvery { BiliPassportHttpApi.loginWithAppQR(any(), any(), any()) } returns
                BiliResponse(code = 86039, message = "请使用B站手机APP扫码登录")

            val result = repository.checkAppQrLoginState("test-auth-code")

            assertThat(result.state).isEqualTo(QrLoginState.WaitingForScan)
            assertThat(result.cookies).isNull()
        }

    @Test
    fun `checkAppQrLoginState returns WaitingForConfirm when response code is 86090`() =
        runTest {
            coEvery { BiliPassportHttpApi.loginWithAppQR(any(), any(), any()) } returns
                BiliResponse(code = 86090, message = "请确认登录")

            val result = repository.checkAppQrLoginState("test-auth-code")

            assertThat(result.state).isEqualTo(QrLoginState.WaitingForConfirm)
        }

    @Test
    fun `checkAppQrLoginState returns Expired when response code is 86038`() =
        runTest {
            coEvery { BiliPassportHttpApi.loginWithAppQR(any(), any(), any()) } returns
                BiliResponse(code = 86038, message = "二维码已失效")

            val result = repository.checkAppQrLoginState("test-auth-code")

            assertThat(result.state).isEqualTo(QrLoginState.Expired)
        }

    @Test
    fun `checkAppQrLoginState returns Unknown for unrecognized response code`() =
        runTest {
            coEvery { BiliPassportHttpApi.loginWithAppQR(any(), any(), any()) } returns
                BiliResponse(code = 99999, message = "未知状态")

            val result = repository.checkAppQrLoginState("test-auth-code")

            assertThat(result.state).isEqualTo(QrLoginState.Unknown)
        }

    @Test
    fun `checkAppQrLoginState returns Success with cookies and tokens when response code is 0`() =
        runTest {
            val appQrLoginData =
                AppQRLoginData(
                    isNew = false,
                    mid = 12345L,
                    accessToken = "access-token-123",
                    refreshToken = "refresh-token-456",
                    expiresIn = 7776000,
                    tokenInfo =
                        AppQRLoginData.TokenInfo(
                            mid = 12345L,
                            expiresIn = 7776000,
                            accessToken = "access-token-123",
                            refreshToken = "refresh-token-456",
                        ),
                    cookieInfo =
                        AppQRLoginData.CookieInfo(
                            cookies =
                                listOf(
                                    AppQRLoginData.CookieInfo.Cookie(
                                        name = "DedeUserID",
                                        value = "12345",
                                        httpOnly = 1,
                                        expires = 1700000000,
                                        secure = 1,
                                    ),
                                    AppQRLoginData.CookieInfo.Cookie(
                                        name = "DedeUserID__ckMd5",
                                        value = "ckmd5abc",
                                        httpOnly = 1,
                                        expires = 0,
                                        secure = 1,
                                    ),
                                    AppQRLoginData.CookieInfo.Cookie(
                                        name = "sid",
                                        value = "sid123",
                                        httpOnly = 1,
                                        expires = 0,
                                        secure = 1,
                                    ),
                                    AppQRLoginData.CookieInfo.Cookie(
                                        name = "bili_jct",
                                        value = "jct456",
                                        httpOnly = 1,
                                        expires = 0,
                                        secure = 1,
                                    ),
                                    AppQRLoginData.CookieInfo.Cookie(
                                        name = "SESSDATA",
                                        value = "sess789",
                                        httpOnly = 1,
                                        expires = 0,
                                        secure = 1,
                                    ),
                                ),
                            domains = listOf(".bilibili.com"),
                        ),
                )
            coEvery { BiliPassportHttpApi.loginWithAppQR(any(), any(), any()) } returns
                BiliResponse(code = 0, message = "成功", data = appQrLoginData)

            val result = repository.checkAppQrLoginState("test-auth-code")

            assertThat(result.state).isEqualTo(QrLoginState.Success)
            assertThat(result.cookies).isNotNull()
            assertThat(result.cookies!!.dedeUserId).isEqualTo(12345L)
            assertThat(result.cookies.dedeUserIdCkMd5).isEqualTo("ckmd5abc")
            assertThat(result.cookies.sid).isEqualTo("sid123")
            assertThat(result.cookies.biliJct).isEqualTo("jct456")
            assertThat(result.cookies.sessData).isEqualTo("sess789")
            assertThat(result.accessToken).isEqualTo("access-token-123")
            assertThat(result.refreshToken).isEqualTo("refresh-token-456")
        }
}
