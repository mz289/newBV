package dev.frost819.newbv.app.network

import android.content.Context
import dev.frost819.newbv.core.log.Loggers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一远程控制台服务器（参考 FongMi/TV 的远程输入架构）。
 *
 * 所有远程输入功能共享一个服务与一个控制台页面：功能方通过
 * [register] 注册 [RemoteConsoleFeature]（底部导航标签 + 动作处理器），
 * 控制台页按已注册功能动态组装标签；手机页面统一 `POST /action?do=<功能id>:<动作>`
 * 提交，服务端分发到对应处理器并把结果回传页面——提交即生效，无需电视端确认。
 *
 * 生命周期采用引用计数：每个使用方
 * start(context)/register(...) 与 unregister(...)/stop() 配对，
 * 引用归零时服务真正停止。
 *
 * 路由：
 * - `GET /` — 控制台页（assets/remote_console_ui/index.html 模板按注册功能拼装）
 * - `POST|GET /action?do=<功能id>:<动作>` — 动作分发（body 原样透传给处理器）
 */
object RemoteConsoleServer {
    /** 优先尝试的固定端口段下界。 */
    const val PORT_MIN = 8765

    /** 固定端口段上界。 */
    const val PORT_MAX = 8788
    private val logger = Loggers.get("RemoteConsoleServer")

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var resolvedPort: Int? = null

    @Volatile
    private var assetProvider: (String) -> ByteArray? = { null }

    /** 已注册功能（key = feature.id），保序便于页面标签稳定。 */
    private val features = ConcurrentHashMap<String, RemoteConsoleFeature>()

    /** 注册功能（已存在时覆盖）。注册即出现在控制台页标签中。 */
    fun register(feature: RemoteConsoleFeature) {
        features[feature.id] = feature
    }

    /** 注销功能，控制台页对应标签随移除。 */
    fun unregister(id: String) {
        features.remove(id)
    }

    /**
     * 启动服务器（常驻）。
     *
     * 参考 FongMi/TV：应用启动时调用一次，随进程存活，不随页面启停；
     * 已在运行时为 no-op。监听全部网卡，端口优先使用固定段
     * [PORT_MIN]..[PORT_MAX]（地址稳定便于手机保存/重访），全部被占时
     * 回落随机端口保证可用。
     *
     * @param context 用于读取控制台页模板资源
     */
    fun start(context: Context) {
        if (server != null) return
        val appContext = context.applicationContext
        assetProvider = { path ->
            runCatching {
                appContext.assets.open(path).use { it.readBytes() }
            }.getOrNull()
        }
        // 固定端口段优先：地址稳定便于手机保存/重访；全被占时回落随机端口保证可用
        var candidate: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
        var boundPort = 0
        for (port in PORT_MIN..PORT_MAX) {
            candidate =
                runCatching {
                    embeddedServer(CIO, port = port) {
                        routing {
                            consoleRoute()
                            actionRoute()
                        }
                    }.also { it.start(wait = false) }
                }.getOrNull()
            if (candidate != null) {
                boundPort = port
                break
            }
        }
        server =
            candidate
                ?: embeddedServer(CIO, port = 0) {
                    routing {
                        consoleRoute()
                        actionRoute()
                    }
                }.also { it.start(wait = false) }
        resolvedPort =
            runBlocking {
                server
                    ?.engine
                    ?.resolvedConnectors()
                    ?.firstOrNull()
                    ?.port
                    ?.takeIf { it != 0 }
            } ?: boundPort
        resolvedPort =
            runBlocking {
                server
                    ?.engine
                    ?.resolvedConnectors()
                    ?.firstOrNull()
                    ?.port
            }
        logger.info { "RemoteConsoleServer started on port $resolvedPort" }
    }

    /**
     * 停止服务器（仅测试使用；生产代码服务随进程常驻）。
     */
    fun stop() {
        features.clear()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 2000)
        server = null
        resolvedPort = null
        logger.info { "RemoteConsoleServer stopped" }
    }

    /**
     * 获取控制台页 URL（用于生成二维码）。
     *
     * @return 形如 `http://192.168.1.100:12345/` 的 URL，未运行或取不到 IP 返回 null。
     */
    fun getUrl(): String? {
        val port = resolvedPort ?: return null
        val host = getLocalIpAddress()
        if (host.isEmpty()) return null
        return "http://$host:$port/"
    }

    /** `GET /` — 控制台页（按注册功能拼装标签与面板）。 */
    private fun Route.consoleRoute() {
        get("/") {
            call.response.header("Cache-Control", "no-store")
            val template =
                assetProvider("remote_console_ui/index.html")?.decodeToString()
                    ?: return@get call.respondText(
                        "remote_console_ui/index.html not found",
                        status = HttpStatusCode.NotFound,
                    )
            call.respondText(assemblePage(template), ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }
    }

    /** `POST|GET /action?do=<功能id>:<动作>` — 动作分发，body 原样透传。 */
    private fun Route.actionRoute() {
        post("/action") { call.handleAction() }
        get("/action") { call.handleAction() }
    }

    private suspend fun ApplicationCall.handleAction() {
        val doParam = request.queryParameters["do"].orEmpty()
        val featureId = doParam.substringBefore(':')
        val actionName = doParam.substringAfter(':', missingDelimiterValue = "")
        val feature = features[featureId]
        val plain = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        if (feature == null) {
            respondText("功能未启用：$featureId", ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            return
        }
        if (actionName.isEmpty()) {
            respondText("缺少动作名", plain)
            return
        }
        val result =
            try {
                feature.dispatch(actionName, receiveText()) ?: "未知动作：$actionName"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            }
        respondText(result, plain)
    }

    /** 控制台页拼装：导航项与面板按注册顺序注入模板。 */
    private fun assemblePage(template: String): String {
        val registered = features.values.toList()
        val navItems =
            registered
                .joinToString("\n") { feature ->
                    val active = feature === registered.first()
                    """<div id="tab_${feature.id}" class="md-nav-item${if (active) " active" else ""}" onclick="showPanel('${feature.id}')">
  <div class="md-nav-indicator"><svg class="md-nav-icon" viewBox="0 0 24 24"><path d="${feature.iconPath}"/></svg></div>
  <span class="md-nav-label">${feature.label}</span>
</div>"""
                }
        val panels =
            registered
                .joinToString("\n") { feature ->
                    val active = feature === registered.first()
                    """<div id="panel_${feature.id}" class="tab-panel${if (active) " active" else ""}">
${feature.panelHtml}
</div>"""
                }
        return template
            .replace("<!--REMOTE_NAV_ITEMS-->", navItems)
            .replace("<!--REMOTE_PANELS-->", panels)
    }

    /**
     * 获取局域网访问 IP（参考 FongMi/TV `Util.getIp()` 的优先级策略）：
     * wlan 接口 → eth 接口 → 其余非回环 IPv4。
     * 多网卡设备（存在虚拟网卡时）优先取无线/有线的物理网段地址，
     * 避免返回手机无法访问的虚拟网卡 IP。失败返回空串。
     */
    private fun getLocalIpAddress(): String =
        runCatching {
            val nifs =
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { it.isUp && !it.isLoopback }

            fun ipv4Of(nif: NetworkInterface?): String? =
                nif?.inetAddresses
                    ?.toList()
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress

            // 多网卡设备（存在虚拟网卡时）优先取无线/有线物理网段地址，
            // 避免返回手机无法访问的虚拟网卡 IP
            ipv4Of(nifs.firstOrNull { it.name.startsWith("wlan") })
                ?: ipv4Of(nifs.firstOrNull { it.name.startsWith("eth") })
                ?: nifs.firstNotNullOfOrNull { ipv4Of(it) }
                ?: ""
        }.getOrDefault("")
}