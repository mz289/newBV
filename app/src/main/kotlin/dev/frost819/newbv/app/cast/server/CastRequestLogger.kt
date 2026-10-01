package dev.frost819.newbv.app.cast.server

import android.content.Context
import dev.frost819.newbv.core.log.Loggers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 投屏请求文件日志（`filesDir/cast_receiver_requests.log`，双文件轮转）。
 *
 * 投屏协议来源多样且难以复现，完整请求（含头与体）落盘便于排查
 * 「手机投了但没反应」类问题；日志大小超过 [CastReceiverConfig.MAX_LOG_BYTES]
 * 时滚动到 `.old`。
 */
class CastRequestLogger(context: Context) {
    private val logger = Loggers.get("CastReceiver")
    private val logFile = File(context.filesDir, CastReceiverConfig.LOG_FILE_NAME)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    fun log(message: String) {
        logger.info { message }
        rotateIfNeeded()
        runCatching { logFile.appendText("[${dateFormat.format(Date())}] $message\n") }
    }

    @Synchronized
    fun logRequest(
        method: String,
        path: String,
        remoteHost: String?,
        headers: Map<String, List<String>>,
        body: String?,
    ) {
        val safeBody = body
            ?.take(4096)
            ?.replace('\r', ' ')
            ?.replace('\n', ' ')
            ?.takeIf { it.isNotBlank() }
        log(
            buildString {
                append("request method=").append(method)
                append(" path=").append(path)
                append(" remote=").append(remoteHost ?: "unknown")
                append(
                    " headers=" +
                        headers.entries.joinToString(";") { "${it.key}=${it.value.joinToString("|")}" }
                )
                if (safeBody != null) append(" body=").append(safeBody)
            }
        )
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > CastReceiverConfig.MAX_LOG_BYTES) {
            val rotated = File(logFile.parentFile, "${CastReceiverConfig.LOG_FILE_NAME}.old")
            if (rotated.exists()) rotated.delete()
            logFile.renameTo(rotated)
        }
    }
}
