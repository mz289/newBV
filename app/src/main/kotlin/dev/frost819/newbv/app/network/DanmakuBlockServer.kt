package dev.frost819.newbv.app.network

import android.content.Context
import dev.frost819.newbv.app.data.DanmakuBlockHitStats
import dev.frost819.newbv.app.data.DanmakuBlockRuleStore
import dev.frost819.newbv.app.data.exportBlockRulesXml
import dev.frost819.newbv.app.data.midHashOfUid
import dev.frost819.newbv.app.data.parseBlockRules
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 弹幕屏蔽规则远程管理服务器。
 *
 * 使用 Ktor CIO 引擎在随机端口启动，提供手机网页管理端与 REST API，
 * 与 TV 端通过 [DanmakuBlockRuleStore]（进程内 StateFlow）实时同步：
 * 手机端的任何修改即时生效到正在播放的弹幕过滤，TV 端的修改也会
 * 反映到手机页面（页面轮询拉取）。
 *
 * 服务器仅在 TV 端「远程管理」二维码弹窗打开期间运行（对话框负责
 * [start]/[stop] 生命周期），且仅监听局域网，无跨网段暴露面。
 *
 * 路由：
 * - `GET  /` — 管理页首页（assets/danmaku_block_ui/index.html）
 * - `GET  /danmaku_block_ui/{path...}` — 静态资源
 * - `GET  /api/block/state` — 屏蔽开关 + 规则列表 JSON
 * - `POST /api/block/enabled` — 修改总开关（body: `{"enabled":true}`）
 * - `POST /api/block/rule` — 添加规则（body: `{"type":"Keyword","value":".."}`）
 * - `POST /api/block/rule/toggle` — 切换规则启用状态
 * - `POST /api/block/rule/delete` — 删除规则
 * - `POST /api/block/import` — 导入屏蔽串（body 为原始文本，自动嗅探 XML/纯文本）
 * - `GET  /api/block/export` — 导出 B 站 XML 屏蔽串（附件下载）
 */
object DanmakuBlockServer {
    private val logger = Loggers.get("DanmakuBlockServer")

    /** encodeDefaults 必须为 true：否则 enabled=true（默认值）被省略，网页端会把启用规则误读为停用。 */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var resolvedPort: Int? = null

    /** 激活中的展示组件数（添加规则对话框 / 手机管理弹窗可能同时持有二维码）。 */
    @Volatile
    private var refCount = 0

    /** 由 [start] 注入的 assets 读取函数。 */
    @Volatile
    private var assetProvider: (String) -> ByteArray? = { null }

    /**
     * 启动服务器（随机端口），按展示组件引用计数管理生命周期：
     * 首个调用者真正启动，后续调用仅计数；与 [stop] 配对使用，
     * 最后一个调用者释放时才真正停止。
     *
     * @param context 用于读取 assets 管理页资源
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
                configureRoutes()
            }.also { it.start(wait = false) }
        resolvedPort =
            runBlocking {
                server
                    ?.engine
                    ?.resolvedConnectors()
                    ?.firstOrNull()
                    ?.port
            }
        logger.info { "DanmakuBlockServer started on port $resolvedPort" }
    }

    /** 停止服务器（引用计数归零时真正停止）。 */
    fun stop() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount > 0) return
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        server = null
        resolvedPort = null
        logger.info { "DanmakuBlockServer stopped" }
    }

    /**
     * 获取实际监听端口。
     *
     * @return 端口号，未运行时返回 null。
     */
    fun getPort(): Int? = resolvedPort

    /**
     * 获取管理页 URL（用于生成二维码）。
     *
     * @return 形如 `http://192.168.1.100:12345/` 的 URL，未运行或取不到 IP 返回 null。
     */
    fun getUrl(): String? {
        val port = resolvedPort ?: return null
        val host = getLocalIpAddress()
        if (host.isEmpty()) return null
        return "http://$host:$port/"
    }

    // ── 路由配置 ──────────────────────────────────────────────────────

    private fun Application.configureRoutes() {
        routing {
            homeRoute()
            staticAssetsRoute()
            blockApiRoute()
        }
    }

    /** `GET /` — 管理页首页。 */
    private fun Route.homeRoute() {
        get("/") {
            val bytes = assetProvider("danmaku_block_ui/index.html")
            if (bytes != null) {
                call.respondBytes(bytes, contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
            } else {
                call.respondText("danmaku_block_ui/index.html not found", status = HttpStatusCode.NotFound)
            }
        }
    }

    /** `GET /danmaku_block_ui/{path...}` — 静态资源（含路径穿越防护）。 */
    private fun Route.staticAssetsRoute() {
        get("/danmaku_block_ui/{path...}") {
            val segments = call.parameters.getAll("path").orEmpty()
            val relPath = segments.joinToString("/").ifBlank { "index.html" }
            if (relPath.contains("..") || relPath.contains("\\")) {
                return@get call.respondText("forbidden", status = HttpStatusCode.Forbidden)
            }
            val bytes = assetProvider("danmaku_block_ui/$relPath")
            if (bytes != null) {
                call.respondBytes(bytes, contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
            } else {
                call.respondText("not found", status = HttpStatusCode.NotFound)
            }
        }
    }

    /** 屏蔽规则 REST API。 */
    @Suppress("LongMethod")
    private fun Route.blockApiRoute() {
        get("/api/block/state") {
            call.respondText(
                text = json.encodeToString(call.stateDto()),
                contentType = ContentType.Application.Json,
            )
        }

        post("/api/block/enabled") {
            val body = call.receiveBody<EnabledDto>()
            DanmakuBlockRuleStore.setEnabled(body.enabled)
            call.respondState()
        }

        post("/api/block/rule") {
            val body = call.receiveBody<RuleDto>()
            val added =
                body.toRule()?.let { DanmakuBlockRuleStore.addRule(it) } ?: false
            if (added) {
                call.respondState()
            } else {
                call.respondText(
                    text = """{"ok":false,"message":"规则已存在或无效"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Conflict,
                )
            }
        }

        post("/api/block/rule/toggle") {
            val body = call.receiveBody<RuleDto>()
            if (DanmakuBlockRuleStore.toggleRule(body.typeEnum(), body.value)) {
                call.respondState()
            } else {
                call.respondText(
                    text = """{"ok":false,"message":"规则不存在"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.NotFound,
                )
            }
        }

        post("/api/block/rule/delete") {
            val body = call.receiveBody<RuleDto>()
            if (DanmakuBlockRuleStore.removeRule(body.typeEnum(), body.value)) {
                call.respondState()
            } else {
                call.respondText(
                    text = """{"ok":false,"message":"规则不存在"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.NotFound,
                )
            }
        }

        post("/api/block/import") {
            val text = call.receiveText()
            val result = parseBlockRules(text)
            if (result.rules.isEmpty()) {
                call.respondText(
                    text = """{"ok":false,"imported":0,"skipped":${result.skipped},"message":"未解析到有效规则"}""",
                    contentType = ContentType.Application.Json,
                )
                return@post
            }
            val imported = DanmakuBlockRuleStore.addRules(result.rules)
            val duplicates = result.rules.size - imported
            call.respondText(
                text = """{"ok":true,"imported":$imported,"duplicates":$duplicates,"skipped":${result.skipped}}""",
                contentType = ContentType.Application.Json,
            )
        }

        get("/api/block/export") {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, "tv.bilibili.player.xml")
                    .toString(),
            )
            call.respondText(
                text = exportBlockRulesXml(DanmakuBlockRuleStore.state.value),
                contentType = ContentType.Text.Xml.withCharset(Charsets.UTF_8),
            )
        }
    }

    /** 当前状态序列化为响应 DTO（含每规则会话命中数）。 */
    private fun io.ktor.server.application.ApplicationCall.stateDto(): BlockStateDto {
        val hitCounts = DanmakuBlockHitStats.snapshot()
        return BlockStateDto(
            enabled = DanmakuBlockRuleStore.state.value.enabled,
            totalHits = DanmakuBlockHitStats.totalHits.value,
            rules =
                DanmakuBlockRuleStore.state.value.rules.map { rule ->
                    val hits = hitCounts[DanmakuBlockFilter.ruleKey(rule.type, rule.value)] ?: 0
                    RuleDto
                        .from(rule)
                        .copy(hitCount = hits)
                },
        )
    }

    private suspend inline fun <reified T : Any> io.ktor.server.application.ApplicationCall.receiveBody(): T =
        json.decodeFromString(receiveText())

    private suspend fun io.ktor.server.application.ApplicationCall.respondState() {
        respondText(
            text = json.encodeToString(stateDto()),
            contentType = ContentType.Application.Json,
        )
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

    // ── DTO ──────────────────────────────────────────────────────────

    @Serializable
    private data class BlockStateDto(
        val enabled: Boolean,
        val rules: List<RuleDto>,
        /** 本会话总命中数（切换视频时清零）。 */
        val totalHits: Int = 0,
    )

    /** 规则 DTO：type 用 [DanmakuBlockRuleType] 名称（Keyword/Regex/User/Color）。 */
    @Serializable
    private data class RuleDto(
        val type: String,
        val value: String,
        val enabled: Boolean = true,
        /** 本会话该规则命中数。 */
        val hitCount: Int = 0,
    ) {
        fun toRule(): DanmakuBlockRule? =
            runCatching { DanmakuBlockRule(type = typeEnum(), value = normalizeValue(), enabled = enabled) }
                .getOrNull()
                ?.takeIf { it.value.isNotEmpty() && isValueValid(it.type, it.value) }

        fun typeEnum(): DanmakuBlockRuleType = DanmakuBlockRuleType.valueOf(type)

        /**
         * 规范化规则值，与屏蔽串导入语义一致：
         * 用户规则纯数字按 B 站 UID 转 midHash（否则会被过滤器按十六进制误读），
         * 颜色统一大写并剥可选 # 前缀。
         */
        private fun normalizeValue(): String {
            val trimmed = value.trim()
            return when (typeEnum()) {
                DanmakuBlockRuleType.User ->
                    if (trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }) {
                        midHashOfUid(trimmed.toLong())
                    } else {
                        trimmed
                    }
                DanmakuBlockRuleType.Color -> trimmed.removePrefix("#").uppercase()
                else -> trimmed
            }
        }

        /**
         * 服务端合法性校验（非法规则在过滤器编译时会被静默丢弃，必须在入口拒绝）：
         * 正则须可编译；用户须为 8 位十六进制 midHash；颜色须为 6 位十六进制 RGB。
         */
        private fun isValueValid(
            type: DanmakuBlockRuleType,
            value: String,
        ): Boolean =
            when (type) {
                DanmakuBlockRuleType.Regex -> DanmakuBlockFilter.parseBlockRegex(value) != null
                DanmakuBlockRuleType.User ->
                    value.length == 8 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                DanmakuBlockRuleType.Color ->
                    value.length == 6 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                DanmakuBlockRuleType.Keyword -> true
            }

        companion object {
            fun from(rule: DanmakuBlockRule): RuleDto =
                RuleDto(
                    type = rule.type.name,
                    value = rule.value,
                    enabled = rule.enabled,
                )
        }
    }

    @Serializable
    private data class EnabledDto(
        val enabled: Boolean,
    )
}
