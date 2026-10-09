package dev.frost819.newbv.biliapi.http

import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.login.qr.AppQRDataRequest
import dev.frost819.newbv.biliapi.http.entity.login.qr.AppQRLoginData
import dev.frost819.newbv.biliapi.http.plugins.BiliUserAgent
import dev.frost819.newbv.biliapi.http.util.encApiSign
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object BiliPassportHttpApi {
    private val client =
        HttpClient(OkHttp) {
            BiliUserAgent()
            install(ContentNegotiation) {
                json(
                    Json {
                        coerceInputValues = true
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    },
                )
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                url {
                    host = "passport.bilibili.com"
                    protocol = URLProtocol.HTTPS
                }
            }
        }.apply {
            encApiSign()
        }

    /**
     * 申请二维码（App）
     */
    suspend fun getAppQRUrl(
        localId: String? = null,
        ts: Int,
        mobiApp: String? = null,
    ): BiliResponse<AppQRDataRequest> =
        client
            .post("/x/passport-tv-login/qrcode/auth_code") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            localId?.let { append("local_id", it) }
                            append("ts", "$ts")
                            mobiApp?.let { append("mobi_app", it) }
                        },
                    ),
                )
            }.body()

    /**
     * 使用[authCode]进行二维码登录
     */
    suspend fun loginWithAppQR(
        authCode: String,
        localId: String? = null,
        ts: Int,
    ): BiliResponse<AppQRLoginData> =
        client
            .post("/x/passport-tv-login/qrcode/poll") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("auth_code", authCode)
                            localId?.let { append("local_id", it) }
                            append("ts", "$ts")
                        },
                    ),
                )
            }.body()
}

