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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/** 每次打开 Cookie 登录页创建独立会话，仅接收输入，不提供凭证读取接口。 */
class CookieInputServer(
    private val pageProvider: () -> String,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val pendingInput = Channel<String>(Channel.CONFLATED)
    val inputs = pendingInput.receiveAsFlow()

    /** 在 IO 调度器调用；随机路径作为本次会话的访问凭证。 */
    fun start(host: String): String {
        check(server == null)
        val path = "/${UUID.randomUUID()}/"
        val page = pageProvider()
        val engine =
            embeddedServer(CIO, host = host, port = 0) {
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
                            pendingInput.trySend(input).isSuccess ->
                                call.respondText("已发送到电视，请在电视上点击登录")
                            else -> call.respondText("登录页面已关闭，请重新扫码", status = HttpStatusCode.Gone)
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
        pendingInput.close()
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
