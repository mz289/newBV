package dev.frost819.newbv.app.cast.server

import dev.frost819.newbv.app.cast.CastPlaybackSession
import dev.frost819.newbv.app.cast.CastPlaybackSessionRegistry
import dev.frost819.newbv.app.cast.CastPlaybackSnapshot
import dev.frost819.newbv.app.cast.CastPlaybackLauncher
import dev.frost819.newbv.app.cast.CastTransportState
import dev.frost819.newbv.app.cast.protocol.CastContent
import dev.frost819.newbv.app.cast.protocol.CastContentParser
import dev.frost819.newbv.core.log.Loggers
import io.ktor.http.Parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 投屏接收端网络服务（原生 TCP，端口 9958），承载两类通道：
 *
 * 1. **HTTP/SOAP**：UPnP 设备描述、SCPD、标准 DLNA 控制端点与 B 站私有
 *    NirvanaControl SOAP 端点（通用 DLNA 控制点与官方客户端 SOAP 流程）；
 * 2. **catch-all**：任意未匹配路径都尝试解析投屏内容并触发播放，兼容
 *    各类非标准投递姿势。
 */
class CastHttpServer(
    private val uuid: String,
    private val requestLogger: CastRequestLogger,
    private val playbackLauncher: CastPlaybackLauncher,
    private val scope: CoroutineScope,
) {
    private val logger = Loggers.get("CastHttpServer")
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    private val connections = CopyOnWriteArraySet<Socket>()

    @Volatile
    private var currentUri: String = ""

    @Volatile
    private var currentMetaData: String = ""

    fun start() {
        if (serverSocket != null) return
        val server =
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(CastReceiverConfig.HTTP_PORT))
            }
        try {
            serverSocket = server
            running = true
            scope.launch(Dispatchers.IO) { acceptLoop(server) }
            requestLogger.log("Cast HTTP server started on ${CastReceiverConfig.HTTP_PORT}")
        } catch (t: Throwable) {
            runCatching { server.close() }
            serverSocket = null
            logger.warn(t) { "Start cast server failed" }
            throw t
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.toList().forEach { runCatching { it.close() } }
        connections.clear()
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (running) {
            val client =
                try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
            connections.add(client)
            scope.launch(Dispatchers.IO) {
                try {
                    handleConnection(client)
                } catch (e: IOException) {
                    requestLogger.log("connection closed: ${e.message}")
                } catch (e: Exception) {
                    logger.warn(e) { "Handle cast connection failed" }
                } finally {
                    connections.remove(client)
                    runCatching { client.close() }
                }
            }
        }
    }

    // ── 连接处理 ──────────────────────────────────────────────

    private fun handleConnection(socket: Socket) {
        socket.tcpNoDelay = true
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        while (running) {
            val request = readHttpRequest(input) ?: return
            requestLogger.logRequest(
                method = request.method,
                path = request.target,
                remoteHost = socket.inetAddress?.hostAddress,
                headers = request.headers.mapValues { listOf(it.value) },
                body = request.body,
            )
            val keepAlive = respond(socket, request)
            if (!keepAlive) return
        }
    }

    private fun readHttpRequest(input: DataInputStream): HttpRequest? {
        val head = ByteArrayOutputStream()
        // matched = 已连续匹配 "\r\n\r\n" 前缀的字节数（0..3），-1 表示完整收到请求头
        var matched = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            matched =
                when {
                    b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                    b == '\n'.code && matched == 1 -> 2
                    b == '\n'.code && matched == 3 -> -1
                    else -> 0
                }
            if (matched == -1) break
            if (head.size() > MAX_HEAD_BYTES) return null
        }
        val headText = head.toString("UTF-8")
        val lines = headText.split("\r\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val parts = lines[0].split(" ")
        if (parts.size < 2) return null
        val headers = LinkedHashMap<String, String>()
        lines.drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase(Locale.ROOT)] =
                    line.substring(idx + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body =
            if (contentLength > 0) {
                if (contentLength > MAX_BODY_BYTES) return null
                val bodyBytes = ByteArray(contentLength)
                input.readFully(bodyBytes)
                String(bodyBytes, Charsets.UTF_8)
            } else {
                ""
            }
        val target = parts[1]
        return HttpRequest(
            method = parts[0].uppercase(Locale.ROOT),
            path = target.substringBefore("?"),
            target = target,
            version = parts.getOrElse(2) { "HTTP/1.1" },
            headers = headers,
            body = body,
        )
    }

    private fun respond(
        socket: Socket,
        request: HttpRequest,
    ): Boolean {
        val out = BufferedOutputStream(socket.getOutputStream())
        val keepAlive = request.isKeepAlive
        val result = route(socket, request)
        writeResponse(out, result.status, result.contentType, result.body, result.extraHeaders, keepAlive)
        return keepAlive
    }

    private fun route(
        socket: Socket,
        request: HttpRequest,
    ): RouteResult {
        val path = request.path.lowercase(Locale.ROOT).trimEnd('/')
        val method = request.method

        // GENA 订阅：投屏端只要求订阅成功（SID + TIMEOUT），不推送事件也能工作
        if (method == "SUBSCRIBE" || method == "UNSUBSCRIBE" || method == "PUT" && path.endsWith("/event")) {
            return RouteResult(
                status = "200 OK",
                contentType = "text/xml; charset=\"utf-8\"",
                extraHeaders =
                    if (method == "UNSUBSCRIBE") {
                        emptyList()
                    } else {
                        listOf("SID" to "uuid:$uuid", "TIMEOUT" to "Second-1800")
                    },
                body = "",
            )
        }

        if (method == "GET" || method == "HEAD") {
            return when {
                path == "/description.xml" -> xmlResult(CastXmlDocuments.deviceDescription(localDescriptionHost(socket), uuid))
                path == "/dlna/avtransport.xml" || path == "/avtransport.xml" ->
                    xmlResult(CastXmlDocuments.avTransportScpd())
                path == "/dlna/nirvanacontrol.xml" || path == "/nirvanacontrol.xml" ->
                    xmlResult(CastXmlDocuments.nirvanaControlScpd())
                path == "/renderingcontrol.xml" -> xmlResult(CastXmlDocuments.renderingControlScpd())
                path == "/connectionmanager.xml" -> xmlResult(CastXmlDocuments.connectionManagerScpd())
                path == "" || path == "/" ->
                    RouteResult("200 OK", "text/plain; charset=\"utf-8\"", emptyList(), "newBV cast receiver")
                path.endsWith("/event") ->
                    RouteResult(
                        "200 OK",
                        "text/xml; charset=\"utf-8\"",
                        listOf("SID" to "uuid:$uuid", "TIMEOUT" to "Second-1800"),
                        "",
                    )
                else -> {
                    val content = parseContent(request, null)
                    val launched = content?.let { maybeLaunch(it) } ?: false
                    RouteResult(
                        "200 OK",
                        "application/json; charset=\"utf-8\"",
                        emptyList(),
                        """{"code":0,"message":"ok","launched":$launched}""",
                    )
                }
            }
        }

        // POST/PUT/DELETE
        val soapAction = request.headers["soapaction"].orEmpty()
        val action = soapActionName(soapAction, request.body)
        val isControlPath =
            path.contains("/control") || path.endsWith("/action") || path.contains("nirvanacontrol")
        return if (action.isNotBlank() && isControlPath) {
            if (action == "SetAVTransportURI") updateCurrentMedia(request.body)
            parseContent(request, request.body)?.let { maybeLaunch(it) }
            val serviceType = soapServiceType(path, soapAction)
            val response =
                when (serviceType) {
                    CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE -> handleAvTransportAction(action, request.body)
                    CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE -> handleRenderingControlAction(action)
                    CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE -> handleConnectionManagerAction(action)
                    CastReceiverConfig.NIRVANA_SERVICE_TYPE -> handleNirvanaAction(action, request.body)
                    else -> soapResponse(serviceType, action)
                }
            RouteResult("200 OK", "text/xml; charset=\"utf-8\"", emptyList(), response)
        } else {
            val content = parseContent(request, request.body)
            val launched = content?.let { maybeLaunch(it) } ?: false
            RouteResult(
                "200 OK",
                "application/json; charset=\"utf-8\"",
                emptyList(),
                """{"code":0,"message":"ok","launched":$launched}""",
            )
        }
    }

    private fun parseContent(
        request: HttpRequest,
        body: String?,
    ): CastContent? =
        CastContentParser.parse(
            path = request.target,
            queryParameters = Parameters.Empty,
            body = body?.takeIf { it.isNotBlank() },
            headers = request.headers,
        )


    private fun localDescriptionHost(socket: Socket): String {
        val remote = socket.inetAddress ?: return CastNetworkUtil.localIpv4Address()
        return CastNetworkUtil.localIpv4AddressFor(remote)
    }

    private fun writeResponse(
        out: BufferedOutputStream,
        status: String,
        contentType: String,
        body: String,
        extraHeaders: List<Pair<String, String>>,
        keepAlive: Boolean,
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val head =
            buildString {
                append("HTTP/1.1 ").append(status).append("\r\n")
                append("DATE: ").append(DateHeader.now()).append("\r\n")
                append("SERVER: ").append(CastReceiverConfig.SERVER_TOKEN).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                extraHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                append("Connection: ").append(if (keepAlive) "keep-alive" else "close").append("\r\n")
                append("\r\n")
            }
        out.write(head.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
        out.flush()
    }

    private fun xmlResult(xml: String): RouteResult =
        RouteResult("200 OK", "text/xml; charset=\"utf-8\"", emptyList(), xml)

    // ── 投屏内容解析与播放启动 ────────────────────────────────

    private fun maybeLaunch(content: CastContent): Boolean {
        requestLogger.log("parsed cast content=$content")
        scope.launch {
            runCatching { playbackLauncher.launch(content) }
                .onFailure { logger.warn(it) { "Launch cast content failed: $content" } }
        }
        return true
    }

    // ── SOAP 控制处理（标准 DLNA + NirvanaControl） ──────────

    private fun updateCurrentMedia(body: String) {
        currentUri = extractXmlText(body, "CurrentURI")?.decodeXmlEntities().orEmpty()
        currentMetaData = extractXmlText(body, "CurrentURIMetaData")?.decodeXmlEntities().orEmpty()
    }

    private suspend fun withPlaybackSession(
        action: String,
        block: CastPlaybackSession.() -> Unit,
    ) {
        val session = CastPlaybackSessionRegistry.current()
        if (session == null) {
            requestLogger.log("no active cast playback session for action=$action")
            return
        }
        withContext(Dispatchers.Main.immediate) {
            requestLogger.log("execute playback action=$action")
            runCatching { session.block() }
                .onSuccess { requestLogger.log("completed playback action=$action") }
                .onFailure { logger.warn(it) { "Cast playback command failed: $action" } }
        }
    }

    private suspend fun currentSnapshot(): CastPlaybackSnapshot =
        withContext(Dispatchers.Main.immediate) {
            CastPlaybackSessionRegistry.current()?.snapshot()
        } ?: CastPlaybackSnapshot()

    private fun handleAvTransportAction(
        action: String,
        body: String,
    ): String {
        when (action) {
            "Play" -> withPlaybackSessionBlocking { play() }
            "Pause" -> withPlaybackSessionBlocking { pause() }
            "Stop" -> withPlaybackSessionBlocking { stop() }
            "Seek" -> {
                val unit = extractXmlText(body, "Unit")?.decodeXmlEntities().orEmpty()
                val target = extractXmlText(body, "Target")?.decodeXmlEntities().orEmpty()
                val positionMs = parseDlnaTimeMillis(target)
                requestLogger.log("AVTransport Seek unit=$unit target=$target parsedMs=$positionMs")
                if (positionMs != null && (unit.isBlank() || unit == "REL_TIME" || unit == "ABS_TIME")) {
                    withPlaybackSessionBlocking { seekTo(positionMs) }
                } else {
                    requestLogger.log("ignore unsupported Seek unit=$unit target=$target")
                }
            }
        }

        return when (action) {
            "GetTransportInfo" -> {
                val snapshot = currentSnapshotBlocking()
                soapResponse(
                    serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                    action = action,
                    values = mapOf(
                        "CurrentTransportState" to snapshot.state.dlnaName,
                        "CurrentTransportStatus" to "OK",
                        "CurrentSpeed" to snapshot.speed.cleanSpeed(),
                    ),
                )
            }

            "GetPositionInfo" -> {
                val snapshot = currentSnapshotBlocking()
                soapResponse(
                    serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                    action = action,
                    values = mapOf(
                        "Track" to "1",
                        "TrackDuration" to formatDlnaTime(snapshot.durationMs),
                        "TrackMetaData" to currentMetaData,
                        "TrackURI" to currentUri,
                        "RelTime" to formatDlnaTime(snapshot.positionMs),
                        "AbsTime" to formatDlnaTime(snapshot.positionMs),
                        "RelCount" to "0",
                        "AbsCount" to "0",
                    ),
                )
            }

            "GetMediaInfo" -> {
                val snapshot = currentSnapshotBlocking()
                soapResponse(
                    serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                    action = action,
                    values = mapOf(
                        "NrTracks" to "1",
                        "MediaDuration" to formatDlnaTime(snapshot.durationMs),
                        "CurrentURI" to currentUri,
                        "CurrentURIMetaData" to currentMetaData,
                        "NextURI" to "",
                        "NextURIMetaData" to "",
                        "PlayMedium" to "NETWORK",
                        "RecordMedium" to "NOT_IMPLEMENTED",
                        "WriteStatus" to "NOT_IMPLEMENTED",
                    ),
                )
            }

            "GetCurrentTransportActions" -> soapResponse(
                serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                action = action,
                values = mapOf("Actions" to "Play,Pause,Seek,Stop"),
            )

            "GetDeviceCapabilities" -> soapResponse(
                serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "PlayMedia" to "NETWORK",
                    "RecMedia" to "NOT_IMPLEMENTED",
                    "RecQualityModes" to "NOT_IMPLEMENTED",
                ),
            )

            "GetTransportSettings" -> soapResponse(
                serviceType = CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "PlayMode" to "NORMAL",
                    "RecQualityMode" to "NOT_IMPLEMENTED",
                ),
            )

            else -> soapResponse(CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE, action)
        }
    }

    private fun handleRenderingControlAction(action: String): String =
        when (action) {
            "GetVolume" -> soapResponse(
                serviceType = CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE,
                action = action,
                values = mapOf("CurrentVolume" to "100"),
            )

            "GetMute" -> soapResponse(
                serviceType = CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE,
                action = action,
                values = mapOf("CurrentMute" to "0"),
            )

            else -> soapResponse(CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE, action)
        }

    private fun handleConnectionManagerAction(action: String): String =
        when (action) {
            "GetProtocolInfo" -> soapResponse(
                serviceType = CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "Source" to "",
                    "Sink" to CastXmlDocuments.SINK_PROTOCOL_INFO,
                ),
            )

            "PrepareForConnection" -> soapResponse(
                serviceType = CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "ConnectionID" to "0",
                    "AVTransportID" to "0",
                    "RcsID" to "0",
                ),
            )

            "GetCurrentConnectionIDs" -> soapResponse(
                serviceType = CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE,
                action = action,
                values = mapOf("ConnectionIDs" to "0"),
            )

            "GetCurrentConnectionInfo" -> soapResponse(
                serviceType = CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "RcsID" to "0",
                    "AVTransportID" to "0",
                    "ProtocolInfo" to "",
                    "PeerConnectionManager" to "",
                    "PeerConnectionID" to "-1",
                    "Direction" to "Input",
                    "Status" to "OK",
                ),
            )

            else -> soapResponse(CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE, action)
        }

    private fun handleNirvanaAction(
        action: String,
        body: String,
    ): String {
        when (action) {
            "Play" -> withPlaybackSessionBlocking { play() }
            "Pause" -> withPlaybackSessionBlocking { pause() }
            "Stop" -> withPlaybackSessionBlocking { stop() }
            "Seek" -> {
                val target =
                    extractXmlText(body, "Target")?.decodeXmlEntities()
                        ?: extractXmlText(body, "Position")?.decodeXmlEntities().orEmpty()
                val positionMs = parseDlnaTimeMillis(target)
                requestLogger.log("Nirvana Seek target=$target parsedMs=$positionMs")
                positionMs?.let { withPlaybackSessionBlocking { seekTo(it) } }
            }

            "SetSpeed", "SwitchSpeed" -> (
                extractXmlText(body, "Speed")?.decodeXmlEntities()?.toFloatOrNull()
                    ?: extractXmlText(body, "PlaybackSpeed")?.decodeXmlEntities()?.toFloatOrNull()
                    ?: extractXmlText(body, "CurrSpeed")?.decodeXmlEntities()?.toFloatOrNull()
                    ?: extractXmlText(body, "Rate")?.decodeXmlEntities()?.toFloatOrNull()
                )
                ?.let { speed -> withPlaybackSessionBlocking { setSpeed(speed) } }

            "SwitchQuality", "SetQuality" -> {
                val qualityId =
                    extractXmlText(body, "Qn")?.decodeXmlEntities()?.toIntOrNull()
                        ?: extractXmlText(body, "Quality")?.decodeXmlEntities()?.toIntOrNull()
                qualityId?.let { withPlaybackSessionBlocking { setQuality(it) } }
            }

            "SetDanmakuSwitch", "SwitchDanmaku", "SetDanmaku" -> (
                parseSoapBoolean(extractXmlText(body, "DesiredSwitch")?.decodeXmlEntities())
                    ?: parseSoapBoolean(extractXmlText(body, "Open")?.decodeXmlEntities())
                    ?: parseSoapBoolean(extractXmlText(body, "DanmakuState")?.decodeXmlEntities())
                )
                ?.let { enabled -> withPlaybackSessionBlocking { setDanmakuEnabled(enabled) } }
        }

        return when (action) {
            "GetAppInfo" -> soapResponse(
                serviceType = CastReceiverConfig.NIRVANA_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "PackageName" to CastReceiverConfig.OFFICIAL_YST_PACKAGE_NAME,
                    "AppKey" to "",
                    "Signature" to "",
                    "CurrentSignedIn" to "0",
                ),
            )

            "GetAccountInfo" -> soapResponse(
                serviceType = CastReceiverConfig.NIRVANA_SERVICE_TYPE,
                action = action,
                values = mapOf("VipInfo" to "0"),
            )

            "PrepareForMirrorProjection" -> soapResponse(
                serviceType = CastReceiverConfig.NIRVANA_SERVICE_TYPE,
                action = action,
                values = mapOf(
                    "ScreenWidth" to "1920",
                    "ScreenHeight" to "1080",
                    "PushUrl" to "http://${CastNetworkUtil.localIpv4Address()}:5223",
                ),
            )

            "GetPlayInfo" -> {
                val snapshot = currentSnapshotBlocking()
                requestLogger.log(
                    "GetPlayInfo snapshot state=${snapshot.state} " +
                        "positionMs=${snapshot.positionMs} durationMs=${snapshot.durationMs} " +
                        "speed=${snapshot.speed} danmaku=${snapshot.danmakuEnabled} qn=${snapshot.qualityId} " +
                        "playerStatus=${snapshot.state.toNirvanaPlayerState()}"
                )
                soapResponse(
                    serviceType = CastReceiverConfig.NIRVANA_SERVICE_TYPE,
                    action = action,
                    values = mapOf("Content" to CastNirvanaPlayInfoFormatter.format(snapshot)),
                )
            }

            else -> soapResponse(CastReceiverConfig.NIRVANA_SERVICE_TYPE, action)
        }
    }

    /**
     * SOAP 处理运行在连接线程（阻塞模型），播放控制在主线程执行并
     * 等待完成后返回（大多数 SOAP 客户端期望应答即结果已生效）。
     */
    private fun withPlaybackSessionBlocking(block: CastPlaybackSession.() -> Unit) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                withPlaybackSession("SOAP", block)
            }
        }
    }

    private fun currentSnapshotBlocking(): CastPlaybackSnapshot =
        runCatching {
            kotlinx.coroutines.runBlocking { currentSnapshot() }
        }.getOrDefault(CastPlaybackSnapshot())

    private fun CastTransportState.toNirvanaPlayerState(): Int =
        when (this) {
            CastTransportState.PLAYING -> 4
            CastTransportState.PAUSED_PLAYBACK -> 5
            CastTransportState.TRANSITIONING -> 2
            CastTransportState.STOPPED -> 7
        }

    // ── SOAP/XML 工具 ─────────────────────────────────────────

    private fun soapResponse(
        serviceType: String,
        action: String,
        values: Map<String, String> = emptyMap(),
    ): String {
        val actionName = action.ifBlank { "Response" }
        val responseValues = values.entries.joinToString(separator = "\n") { (name, value) ->
            "        <$name>${value.escapeXml()}</$name>"
        }
        return buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append('\n')
            append(
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
                    """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">"""
            )
            append('\n')
            append("  <s:Body>\n")
            append("""    <u:${actionName}Response xmlns:u="$serviceType">""")
            append('\n')
            if (responseValues.isNotBlank()) {
                append(responseValues)
                append('\n')
            }
            append("""    </u:${actionName}Response>""")
            append('\n')
            append("  </s:Body>\n")
            append("</s:Envelope>")
        }
    }

    private fun soapActionName(
        soapAction: String,
        body: String,
    ): String {
        val headerAction = soapAction.trim().trim('"')
            .substringAfter("#", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
        if (headerAction != null) return headerAction

        return Regex("""<\s*(?:[A-Za-z0-9_.-]+:)?([A-Z][A-Za-z0-9_.-]+)\b""")
            .findAll(body)
            .map { it.groupValues[1] }
            .firstOrNull { it != "Envelope" && it != "Body" }
            .orEmpty()
    }

    private fun soapServiceType(
        path: String,
        soapAction: String,
    ): String {
        val headerService = soapAction.trim().trim('"')
            .substringBefore("#", missingDelimiterValue = "")
            .takeIf { it.startsWith("urn:") }
        if (headerService != null) return headerService

        return when {
            path.contains("RenderingControl", ignoreCase = true) ->
                CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE
            path.contains("ConnectionManager", ignoreCase = true) ->
                CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE
            path.contains("NirvanaControl", ignoreCase = true) ->
                CastReceiverConfig.NIRVANA_SERVICE_TYPE
            else -> CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE
        }
    }

    private fun extractXmlText(
        xml: String,
        localName: String,
    ): String? {
        val tagName = Regex.escape(localName)
        return Regex(
            pattern = """(?is)<(?:[A-Za-z0-9_.-]+:)?$tagName\b[^>]*>(.*?)</(?:[A-Za-z0-9_.-]+:)?$tagName>"""
        ).find(xml)?.groupValues?.getOrNull(1)
    }

    private fun parseDlnaTimeMillis(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!text.contains(":")) {
            return text.toDoubleOrNull()?.let { (it * 1000).toLong().coerceAtLeast(0L) }
        }

        val parts = text.split(":")
        if (parts.size !in 2..3) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        return (totalSeconds * 1000).toLong().coerceAtLeast(0L)
    }

    private fun parseSoapBoolean(value: String?): Boolean? =
        when (value?.trim()?.lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }

    private fun formatDlnaTime(timeMs: Long): String {
        val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun String.decodeXmlEntities(): String =
        replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun Float.cleanSpeed(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private class HttpRequest(
        val method: String,
        val path: String,
        val target: String,
        val version: String,
        val headers: Map<String, String>,
        val body: String,
    ) {
        val isKeepAlive: Boolean
            get() =
                if (version == "HTTP/1.0") {
                    headers["connection"]?.lowercase() == "keep-alive"
                } else {
                    headers["connection"]?.lowercase() != "close"
                }
    }

    private data class RouteResult(
        val status: String,
        val contentType: String,
        val extraHeaders: List<Pair<String, String>>,
        val body: String,
    )

    private companion object {
        const val MAX_HEAD_BYTES = 64 * 1024
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
    }
}
