package dev.frost819.newbv.biliapi.http

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BiliPassportHttpApiTest {
    @Test
    fun `get app qr login url`() {
        val response =
            runBlocking {
                BiliPassportHttpApi.getAppQRUrl(
                    localId = "1",
                    ts = (System.currentTimeMillis() / 1000).toInt(),
                    mobiApp = "android_hd",
                )
            }
        println(response)
        println("qr url: ${response.data?.url}")
        println("qr key: ${response.data?.authCode}")
        assertEquals(0, response.code)
    }

    @Disabled("交互式 QR 登录测试，需手动扫码，不在 CI 中运行")
    @Test
    fun `request app qr login result`() {
        val qrUrlResponse =
            runBlocking {
                BiliPassportHttpApi.getAppQRUrl(
                    localId = "1",
                    ts = (System.currentTimeMillis() / 1000).toInt(),
                    mobiApp = "android_hd",
                )
            }
        val url = qrUrlResponse.data?.url
        val key = qrUrlResponse.data?.authCode
        println("qr url: $url")
        println("qr key: $key")
        var loop = true
        while (loop) {
            val loginResponse =
                runBlocking {
                    BiliPassportHttpApi.loginWithAppQR(
                        authCode = key!!,
                        localId = "1",
                        ts = (System.currentTimeMillis() / 1000).toInt(),
                    )
                }
            println(loginResponse)
            when (val result = loginResponse.code) {
                0 -> {
                    loop = false
                    println("login success")
                }

                86039 -> println("wait to scan")
                86090 -> println("wait to confirm")
                86038 -> {
                    loop = false
                    println("qr expired")
                }

                else -> {
                    loop = false
                    println("unknown code: $result")
                }
            }
            if (loop) {
                runBlocking { delay(1000) }
            } else {
                println(loginResponse)
            }
        }
    }
}
