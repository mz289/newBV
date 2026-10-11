package dev.frost819.newbv.app.network

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * 每次打开 Cookie 登录页创建独立会话，仅接收输入，不提供凭证读取接口。
 *
 * 参考 FongMi/TV 的远程输入模式（POST 提交即生效、结果回传页面）：
 * 收到提交后调用 [onInput] 直接触发登录，处理结果作为响应回传手机页面展示，
 * 无需在电视上再次确认。
 */
class CookieInputServer(
    private val pageProvider: () -> String,
    private val onInput: suspend (String) -> String,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** 在 IO 调度器调用；随机路径作为本次会话的访问凭证。 */
    fun start(host: String): String {
        check(server == null)
        val path = "/${UUID.randomUUID()}/"
        val page = pageProvider()
        // 监听所有网卡：电视可能同时具备有线/无线等多接口，
        // 绑定单一 IP 会导致手机从另一网段无法访问（二维码 URL 仍用检测到的局域网 IP）
        val engine =
            embeddedServer(CIO, port = 0) {
                routing {
                    get(path) {
                        call.response.header("Cache-Control", "no-store")
                        call.response.header("Referrer-Policy", "no-referrer")
                        call.respondText(page, ContentType.Text.Html.withCharset(Charsets.UTF_8))
                    }
                    post("${path}input") {
                        call.response.header("Cache-Control", "no-store")
                        val input = call.receiveText().trim()
                        when {
                            input.isBlank() ->
                                call.respondText(
                                    "请输入 Cookie 或凭证 JSON",
                                    status = HttpStatusCode.BadRequest,
                                )
                            input.length > MAX_INPUT_LENGTH ->
                                call.respondText(
                                    "输入内容过长",
                                    status = HttpStatusCode.PayloadTooLarge,
                                )
                            else -> call.respondText(runCatching { onInput(input) }.getOrElse { "处理失败：${it.message}" })
                        }
                    }
                }
            }
        server = engine
        engine.start(wait = false)
        val port =
            runBlocking {
                engine.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        return "http://$host:$port$path"
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        server = null
    }

    companion object {
        const val MAX_INPUT_LENGTH = 64 * 1024

        fun localAddress(): String? =
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
    }
}
