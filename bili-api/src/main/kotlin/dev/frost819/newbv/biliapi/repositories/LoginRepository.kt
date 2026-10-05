package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.login.QrLoginData
import dev.frost819.newbv.biliapi.entity.login.QrLoginResult
import dev.frost819.newbv.biliapi.entity.login.QrLoginState
import dev.frost819.newbv.biliapi.entity.login.WebCookies
import dev.frost819.newbv.biliapi.http.BiliPassportHttpApi
import java.util.Date

/**
 * 登录仓库。
 *
 * TV 端仅支持 App 扫码登录（passport-tv-login 通道）。
 */
class LoginRepository {
    /**
     * 请求扫码登录的二维码
     */
    suspend fun requestAppQrLogin(): QrLoginData {
        val response =
            BiliPassportHttpApi
                .getAppQRUrl(
                    localId = "0",
                    ts = (System.currentTimeMillis() / 1000).toInt(),
                    mobiApp = "android_hd",
                ).getResponseData()
        return QrLoginData(
            url = response.url,
            key = response.authCode,
        )
    }

    /**
     * 检查扫码登录情况
     *
     * @param authCode 二维码内容
     */
    suspend fun checkAppQrLoginState(authCode: String): QrLoginResult {
        val response =
            BiliPassportHttpApi.loginWithAppQR(
                authCode = authCode,
                localId = "0",
                ts = (System.currentTimeMillis() / 1000).toInt(),
            )
        var resultCookies: WebCookies? = null
        val resultState =
            when (response.code) {
                0 -> {
                    resultCookies =
                        WebCookies(
                            dedeUserId =
                                response
                                    .getResponseData()
                                    .cookieInfo.cookies
                                    .find { it.name == "DedeUserID" }
                                    ?.value
                                    ?.toLong()
                                    ?: throw IllegalArgumentException("Cookie DedeUserID not found"),
                            dedeUserIdCkMd5 =
                                response
                                    .getResponseData()
                                    .cookieInfo.cookies
                                    .find { it.name == "DedeUserID__ckMd5" }
                                    ?.value
                                    ?: throw IllegalArgumentException("Cookie DedeUserID__ckMd5 not found"),
                            sid =
                                response
                                    .getResponseData()
                                    .cookieInfo.cookies
                                    .find { it.name == "sid" }
                                    ?.value
                                    ?: throw IllegalArgumentException("Cookie sid not found"),
                            biliJct =
                                response
                                    .getResponseData()
                                    .cookieInfo.cookies
                                    .find { it.name == "bili_jct" }
                                    ?.value
                                    ?: throw IllegalArgumentException("Cookie bili_jct not found"),
                            sessData =
                                response
                                    .getResponseData()
                                    .cookieInfo.cookies
                                    .find { it.name == "SESSDATA" }
                                    ?.value
                                    ?: throw IllegalArgumentException("Cookie SESSDATA not found"),
                            expiredDate =
                                Date(
                                    response
                                        .getResponseData()
                                        .cookieInfo.cookies
                                        .first()
                                        .expires * 1000L,
                                ),
                        )
                    QrLoginState.Success
                }

                86039 -> QrLoginState.WaitingForScan
                86090 -> QrLoginState.WaitingForConfirm
                86038 -> QrLoginState.Expired
                else -> QrLoginState.Unknown
            }
        return QrLoginResult(
            state = resultState,
            accessToken = response.data?.accessToken,
            refreshToken = response.data?.refreshToken,
            cookies = resultCookies,
        )
    }
}
