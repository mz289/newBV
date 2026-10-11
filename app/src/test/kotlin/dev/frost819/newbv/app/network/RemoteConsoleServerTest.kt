package dev.frost819.newbv.app.network

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.HttpURLConnection
import java.net.URL

/**
 * [RemoteConsoleServer] 的单元测试。
 *
 * 验证统一远程控制台的页面按注册功能拼装、动作分发透传 body、
 * 未注册功能/未知动作的明确报错，以及注册注销的标签可见性。
 *
 * 服务器固定端口段内启停（生产为常驻），CIO 关闭异步释放端口有延迟，
 * 类内逐用例快速启停会因端口未释放产生 BindException 未捕获异常并
 * 污染同 JVM 后续测试——因此整个类只启停一次。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemoteConsoleServerTest {
    private lateinit var context: Context
    private lateinit var url: String

    @BeforeAll
    fun setUp() {
        // 用内联模板替身：验证按注册功能拼装（标记被替换）而非真实 assets 内容
        val template =
            "<body><div class=\"app-body\"><!--REMOTE_PANELS--></div>" +
                "<nav class=\"md-nav-bar\"><!--REMOTE_NAV_ITEMS--></nav></body>"
        context =
            mockk {
                every { applicationContext } returns this@mockk
                every { assets } returns
                    mockk {
                        every { open(any()) } answers { template.byteInputStream() }
                    }
            }
        RemoteConsoleServer.start(context)
        RemoteConsoleServer.register(echoFeature())
        url = checkNotNull(RemoteConsoleServer.getUrl())
    }

    @AfterAll
    fun tearDown() {
        RemoteConsoleServer.stop()
    }

    private fun echoFeature() =
        RemoteConsoleFeature(
            id = "test_feature",
            label = "测试功能",
            iconPath = "M0 0h24v24H0z",
            panelHtml = "<div id=\"test_panel\">TEST_PANEL_MARKER</div>",
            actions =
                mapOf(
                    "echo" to { body -> "echo:$body" },
                    "boom" to { throw IllegalStateException("爆炸了") },
                ),
        )

    private fun get(
        path: String,
    ): Pair<Int, String> {
        val connection = URL("$url$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 5000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return connection.responseCode to body
        } finally {
            connection.disconnect()
        }
    }

    private fun post(
        path: String,
        body: String,
    ): Pair<Int, String> {
        val connection = URL("$url$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 5000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            return connection.responseCode to responseBody
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `page assembles nav and panel from registered features`() {
        val (code, page) = get("/")
        assertThat(code).isEqualTo(200)
        assertThat(page).contains("tab_test_feature")
        assertThat(page).contains("panel_test_feature")
        assertThat(page).contains("TEST_PANEL_MARKER")
        assertThat(page).contains("测试功能")
    }

    @Test
    fun `unregistered feature disappears from page`() {
        RemoteConsoleServer.unregister("test_feature")

        val (_, page) = get("/")

        assertThat(page).doesNotContain("TEST_PANEL_MARKER")
        assertThat(page).doesNotContain("tab_test_feature")

        // 类内仅注册一次（PER_CLASS），用例后恢复注册避免污染字母序靠后的用例
        RemoteConsoleServer.register(echoFeature())
    }

    @Test
    fun `action dispatch passes body to handler and returns result`() =
        runBlocking {
            val (code, body) = post("/action?do=test_feature:echo", "hello")

            assertThat(code).isEqualTo(200)
            assertThat(body).isEqualTo("echo:hello")
        }

    @Test
    fun `action for unregistered feature reports it`() {
        val (code, body) = post("/action?do=nope:echo", "x")

        assertThat(code).isEqualTo(200)
        assertThat(body).isEqualTo("功能未启用：nope")
    }

    @Test
    fun `unknown action reports it`() {
        val (code, body) = post("/action?do=test_feature:nope", "x")

        assertThat(code).isEqualTo(200)
        assertThat(body).isEqualTo("未知动作：nope")
    }

    @Test
    fun `handler failure surfaces as error response`() {
        val (code, body) = post("/action?do=test_feature:boom", "x")

        assertThat(code).isEqualTo(200)
        assertThat(body).contains("爆炸了")
    }

    @Test
    fun `cookie login feature submits through remote console`() {
        RemoteConsoleServer.register(CookieLoginRemoteFeature.create { "登录成功，本页可以关闭了" })

        val (code, body) = post("/action?do=cookie_login:submit", "SESSDATA=abc")

        assertThat(code).isEqualTo(200)
        assertThat(body).contains("登录成功")
    }
}
