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
    private val logger = Loggers.get("RemoteConsoleServer")

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var resolvedPort: Int? = null

    @Volatile
    private var refCount = 0

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
     * 启动服务器（随机端口，监听全部网卡），引用计数管理生命周期：
     * 首个调用者真正启动，最后一个释放者才真正停止。
     *
     * @param context 用于读取控制台页模板资源
     */
    fun start(context: Context) {
        refCount++
        if (server != null) return
        val appContext = context.applicationContext
        assetProvider = { path ->
            runCatching {
                appContext.assets.open(path).use { it.readBytes() }
            }.getOrNull()
        }
        server =
            embeddedServer(CIO, port = 0) {
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
            }
        logger.info { "RemoteConsoleServer started on port $resolvedPort" }
    }

    /** 停止服务器（引用计数归零时真正停止），全部功能注销。 */
    fun stop() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount > 0) return
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

    /** 扫描非回环 IPv4 地址（无线/有线均可），失败返回空串。 */
    private fun getLocalIpAddress(): String =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress ?: ""
        }.getOrDefault("")
}
