package dev.frost819.newbv.app.cast.server

import dev.frost819.newbv.app.cast.CastPlaybackSnapshot
import dev.frost819.newbv.app.cast.CastTransportState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 把 [CastPlaybackSnapshot] 格式化为 B 站官方客户端 Nirvana `GetPlayInfo`
 * 期望的 JSON（手机端凭此渲染投屏进度条/画质菜单/弹幕开关）。
 */
internal object CastNirvanaPlayInfoFormatter {
    fun format(snapshot: CastPlaybackSnapshot): String {
        val playerStatus = snapshot.state.toNirvanaPlayerState()
        val durationMs = snapshot.durationMs.toPhoneDurationMillis(snapshot.state)
        val positionSeconds = snapshot.positionMs.toPhonePositionSeconds(durationMs)
        val speedInfo =
            buildJsonObject {
                putJsonArray("supportSpeedList") {
                    listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { add(it) }
                }
                put(
                    "currSpeed",
                    if (snapshot.speed % 1f ==
                        0f
                    ) {
                        JsonPrimitive(snapshot.speed.toInt())
                    } else {
                        JsonPrimitive(snapshot.speed)
                    },
                )
            }
        return buildJsonObject {
            put("aid", snapshot.aid.takeIf { it > 0L }?.toString() ?: "")
            put("cid", snapshot.cid.takeIf { it > 0L }?.toString() ?: "")
            put("epId", snapshot.epid?.toString() ?: "")
            put("seasonId", snapshot.seasonId.takeIf { it > 0 }?.toString() ?: "")
            put("roomId", snapshot.roomId.takeIf { it > 0L }?.toString() ?: "")
            if (snapshot.qualityId > 0) {
                putJsonObject("qn") {
                    putJsonArray("supportQnList") {
                        snapshot.availableQuality.forEach { (qualityId, description) ->
                            add(
                                buildJsonObject {
                                    put("quality", qualityId)
                                    put("description", description)
                                    put("displayDesc", description)
                                    put("superscript", "")
                                    put("needLogin", false)
                                    put("needVip", false)
                                    put("need_login", false)
                                    put("need_vip", false)
                                },
                            )
                        }
                    }
                    putJsonObject("currentQn") { put("quality", snapshot.qualityId) }
                    put("curQn", snapshot.qualityId)
                    put("userDesireQn", snapshot.qualityId)
                }
            } else {
                put("qn", JsonNull)
            }
            put("duration", durationMs)
            put("position", positionSeconds)
            put("playerStatus", playerStatus)
            put("danmakuStatus", if (snapshot.danmakuEnabled) 1 else 0)
            put("supportVideoDanmaku", true)
            put("supportLiveDanmaku", true)
            put("supportMultiSpeed", true)
            put("isLastEp", false)
            put("playState", playerStatus)
            put("danmakuState", snapshot.danmakuEnabled)
            putJsonObject("playItem") {
                put("aid", snapshot.aid.coerceAtLeast(0L))
                put("cid", snapshot.cid.coerceAtLeast(0L))
                put("epId", snapshot.epid ?: 0)
                put("seasonId", snapshot.seasonId.coerceAtLeast(0))
                put("roomId", snapshot.roomId.coerceAtLeast(0L))
                put("contentType", 0)
            }
            put("listInfo", JsonNull)
            put("title", snapshot.title)
            put("volume", 100)
            put("speedInfo", speedInfo)
            put("speed", speedInfo)
        }.toString()
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
        (
            if (durationMs > 0L) {
                coerceIn(0L, durationMs) / 1000L
            } else {
                0L
            }
        ).coerceIn(0L, Int.MAX_VALUE.toLong())

    private fun CastTransportState.toNirvanaPlayerState(): Int =
        when (this) {
            CastTransportState.PLAYING -> 4
            CastTransportState.PAUSED_PLAYBACK -> 5
            CastTransportState.TRANSITIONING -> 2
            CastTransportState.STOPPED -> 7
        }

    private const val LOADING_DURATION_MS = 1_000L
}
