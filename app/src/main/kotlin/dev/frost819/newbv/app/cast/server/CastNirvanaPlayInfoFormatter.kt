package dev.frost819.newbv.app.cast.server

import dev.frost819.newbv.app.cast.CastPlaybackSnapshot
import dev.frost819.newbv.app.cast.CastTransportState

/**
 * 把 [CastPlaybackSnapshot] 格式化为 B 站官方客户端 Nirvana `GetPlayInfo`
 * 期望的 JSON（手机端凭此渲染投屏进度条/画质菜单/弹幕开关）。
 */
internal object CastNirvanaPlayInfoFormatter {
    fun format(snapshot: CastPlaybackSnapshot): String {
        val qnItems =
            snapshot.availableQuality.entries.joinToString(separator = ",") { (qualityId, description) ->
                """
                {
                  "quality":$qualityId,
                  "description":"${description.escapeJson()}",
                  "displayDesc":"${description.escapeJson()}",
                  "superscript":"",
                  "needLogin":false,
                  "needVip":false,
                  "need_login":false,
                  "need_vip":false
                }
                """.trimIndent().lineSequence().joinToString("") { it.trim() }
            }
        val qn =
            if (snapshot.qualityId > 0) {
                """
                "qn":{
                  "supportQnList":[$qnItems],
                  "currentQn":{"quality":${snapshot.qualityId}},
                  "curQn":${snapshot.qualityId},
                  "userDesireQn":${snapshot.qualityId}
                },
                """.trimIndent().lineSequence().joinToString("") { it.trim() }
            } else {
                """"qn":null,"""
            }
        val playerStatus = snapshot.state.toNirvanaPlayerState()
        val durationMs = snapshot.durationMs.toPhoneDurationMillis(snapshot.state)
        val positionSeconds = snapshot.positionMs.toPhonePositionSeconds(durationMs)
        return """
            {
              "aid":"${snapshot.aid.takeIf { it > 0L } ?: ""}",
              "cid":"${snapshot.cid.takeIf { it > 0L } ?: ""}",
              "epId":"${snapshot.epid ?: ""}",
              "seasonId":"${snapshot.seasonId.takeIf { it > 0 } ?: ""}",
              "roomId":"${snapshot.roomId.takeIf { it > 0L } ?: ""}",
              $qn
              "duration":$durationMs,
              "position":$positionSeconds,
              "playerStatus":$playerStatus,
              "danmakuStatus":${if (snapshot.danmakuEnabled) 1 else 0},
              "supportVideoDanmaku":true,
              "supportLiveDanmaku":true,
              "supportMultiSpeed":true,
              "isLastEp":false,
              "playState":$playerStatus,
              "danmakuState":${snapshot.danmakuEnabled},
              "playItem":{
                "aid":${snapshot.aid.coerceAtLeast(0L)},
                "cid":${snapshot.cid.coerceAtLeast(0L)},
                "epId":${snapshot.epid ?: 0},
                "seasonId":${snapshot.seasonId.coerceAtLeast(0)},
                "roomId":${snapshot.roomId.coerceAtLeast(0L)},
                "contentType":0
              },
              "listInfo":null,
              "title":"${snapshot.title.escapeJson()}",
              "volume":100,
              "speedInfo":{
                "supportSpeedList":[0.5,0.75,1.0,1.25,1.5,2.0],
                "currSpeed":${snapshot.speed.cleanSpeed()}
              },
              "speed":{
                "supportSpeedList":[0.5,0.75,1.0,1.25,1.5,2.0],
                "currSpeed":${snapshot.speed.cleanSpeed()}
              }
            }
        """.trimIndent().lineSequence().joinToString("") { it.trim() }
    }

    private fun Long.toNirvanaMillis(): Long = coerceIn(0L, Int.MAX_VALUE.toLong())

    /** 未拿到真实时长时上报 1s 占位，避免官方客户端把进度条归零。 */
    private fun Long.toPhoneDurationMillis(state: CastTransportState): Long =
        when {
            this > 0L -> toNirvanaMillis()
            state == CastTransportState.STOPPED -> 0L
            else -> LOADING_DURATION_MS
        }

    private fun Long.toPhonePositionSeconds(durationMs: Long): Long =
        (if (durationMs > 0L) {
            coerceIn(0L, durationMs) / 1000L
        } else {
            0L
        }).coerceIn(0L, Int.MAX_VALUE.toLong())

    private fun CastTransportState.toNirvanaPlayerState(): Int =
        when (this) {
            CastTransportState.PLAYING -> 4
            CastTransportState.PAUSED_PLAYBACK -> 5
            CastTransportState.TRANSITIONING -> 2
            CastTransportState.STOPPED -> 7
        }

    private fun Float.cleanSpeed(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun String.escapeJson(): String =
        buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private const val LOADING_DURATION_MS = 1_000L
}
