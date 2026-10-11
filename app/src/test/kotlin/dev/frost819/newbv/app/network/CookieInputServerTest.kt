package dev.frost819.newbv.app.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL

class CookieInputServerTest {
    private lateinit var server: CookieInputServer
    private lateinit var url: String

    @BeforeEach
    fun setUp() {
        server = CookieInputServer(pageProvider = { "<html>Cookie input</html>" }, onInput = { "已登录: $it" })
        url = server.start("127.0.0.1")
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `session page is served without caching`() {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            assertThat(connection.responseCode).isEqualTo(200)
            assertThat(connection.getHeaderField("Cache-Control")).isEqualTo("no-store")
            assertThat(connection.inputStream.bufferedReader().use { it.readText() }).contains("Cookie input")
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `remote cookie and JSON are forwarded to login handler`() =
        runBlocking {
            for (input in listOf("SESSDATA=abc; bili_jct=def; DedeUserID=123", "{\"uid\":123,\"sessData\":\"abc\"}")) {
                val (code, body) = postForBody("${url}input", "  $input\n")
                assertThat(code).isEqualTo(200)
                assertThat(body).isEqualTo("已登录: $input")
            }
        }

    @Test
    fun `handler error is returned as processing failure`() =
        runBlocking {
            server.stop()
            server =
                CookieInputServer(
                    pageProvider = { "page" },
                    onInput = { throw IllegalArgumentException("Cookie 已失效或无效") },
                )
            url = server.start("127.0.0.1")

            val (code, body) = postForBody("${url}input", "SESSDATA=dead")

            assertThat(code).isEqualTo(200)
            assertThat(body).isEqualTo("处理失败：Cookie 已失效或无效")
        }

    @Test
    fun `blank and oversized input are rejected`() {
        assertThat(post("${url}input", " \n ")).isEqualTo(400)
        assertThat(post("${url}input", "x".repeat(CookieInputServer.MAX_INPUT_LENGTH + 1))).isEqualTo(413)
    }

    @Test
    fun `input endpoint requires the session URL`() {
        val root = URL(url).let { "http://${it.host}:${it.port}/" }
        assertThat(post("${root}input", "SESSDATA=abc")).isEqualTo(404)
    }

    @Test
    fun `new session rejects old session path`() {
        val oldPath = URL(url).path
        server.stop()
        server =
            CookieInputServer(
                pageProvider = { "new session" },
                onInput = { "已登录: $it" },
            )
        url = server.start("127.0.0.1")
        val base = URL(url).let { "http://${it.host}:${it.port}" }
        assertThat(post("${base}${oldPath}input", "SESSDATA=abc")).isEqualTo(404)
        assertThat(post("${url}input", "SESSDATA=abc")).isEqualTo(200)
    }

    private fun postForBody(
        target: String,
        body: String,
    ): Pair<Int, String> {
        val connection = URL(target).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 5000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return connection.responseCode to response
        } finally {
            connection.disconnect()
        }
    }

    private fun post(
        target: String,
        body: String,
    ): Int {
        val connection = URL(target).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
