package dev.frost819.newbv.app.cast.server

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.cast.CastPlaybackSnapshot
import dev.frost819.newbv.app.cast.CastTransportState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * [CastNirvanaPlayInfoFormatter] 的单元测试。
 *
 * 验证 GetPlayInfo 对手机投屏端的关键契约：毫秒时长/秒位置、
 * 起播占位时长、播放状态映射与倍速/弹幕能力声明。
 */
class CastNirvanaPlayInfoFormatterTest {
    @Test
    fun `preserves protocol field types quality order and aliases`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(
                        CastPlaybackSnapshot(
                            aid = 123L,
                            cid = 456L,
                            epid = 789,
                            seasonId = 10,
                            roomId = 11L,
                            qualityId = 80,
                            availableQuality = linkedMapOf(80 to "1080P", 64 to "720P"),
                            state = CastTransportState.PAUSED_PLAYBACK,
                            danmakuEnabled = true,
                            speed = 2f,
                        ),
                    ),
                ).jsonObject

        for (key in listOf("aid", "cid", "epId", "seasonId", "roomId")) {
            assertThat(playInfo.getValue(key).jsonPrimitive.isString).isTrue()
            assertThat(
                playInfo
                    .getValue("playItem")
                    .jsonObject
                    .getValue(key)
                    .jsonPrimitive.isString,
            ).isFalse()
        }
        val qn = playInfo.getValue("qn").jsonObject
        val qualities = qn.getValue("supportQnList").jsonArray
        assertThat(
            qualities.map {
                it.jsonObject
                    .getValue("quality")
                    .jsonPrimitive.content
            },
        ).containsExactly("80", "64")
            .inOrder()
        assertThat(qn.getValue("curQn")).isEqualTo(qn.getValue("userDesireQn"))
        assertThat(qn.getValue("currentQn").jsonObject.getValue("quality")).isEqualTo(qn.getValue("curQn"))
        val quality = qualities.first().jsonObject
        assertThat(quality.getValue("description")).isEqualTo(quality.getValue("displayDesc"))
        assertThat(quality.getValue("needLogin")).isEqualTo(quality.getValue("need_login"))
        assertThat(quality.getValue("needVip")).isEqualTo(quality.getValue("need_vip"))
        assertThat(playInfo.getValue("playerStatus").jsonPrimitive.content).isEqualTo("5")
        assertThat(playInfo.getValue("playerStatus")).isEqualTo(playInfo.getValue("playState"))
        assertThat(playInfo.getValue("danmakuStatus").jsonPrimitive.content).isEqualTo("1")
        assertThat(playInfo.getValue("danmakuState").jsonPrimitive.content).isEqualTo("true")
        assertThat(playInfo.getValue("speedInfo")).isEqualTo(playInfo.getValue("speed"))
        assertThat(
            playInfo
                .getValue("speedInfo")
                .jsonObject
                .getValue("currSpeed")
                .jsonPrimitive.content,
        ).isEqualTo("2")
    }

    @Test
    fun `escapes titles and quality descriptions including control characters`() {
        val text = "引号\"反斜杠\\\n\t\r\b\u000C\u0000\u0001\u001F emoji😀"
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(
                        CastPlaybackSnapshot(title = text, qualityId = 80, availableQuality = mapOf(80 to text)),
                    ),
                ).jsonObject
        assertThat(playInfo.getValue("title").jsonPrimitive.content).isEqualTo(text)
        val quality =
            playInfo
                .getValue("qn")
                .jsonObject
                .getValue("supportQnList")
                .jsonArray
                .single()
                .jsonObject
        assertThat(quality.getValue("description").jsonPrimitive.content).isEqualTo(text)
        assertThat(quality.getValue("displayDesc").jsonPrimitive.content).isEqualTo(text)
    }

    @Test
    fun `keeps absent identities empty and unsupported quality null`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(CastPlaybackSnapshot(aid = -1, cid = -2, positionMs = -1)),
                ).jsonObject
        assertThat(playInfo.getValue("qn")).isEqualTo(JsonNull)
        assertThat(playInfo.getValue("listInfo")).isEqualTo(JsonNull)
        for (key in listOf("aid", "cid", "epId", "seasonId", "roomId")) {
            assertThat(playInfo.getValue(key).jsonPrimitive.content).isEmpty()
            assertThat(
                playInfo
                    .getValue("playItem")
                    .jsonObject
                    .getValue(key)
                    .jsonPrimitive.content,
            ).isEqualTo("0")
        }
        assertThat(playInfo.getValue("duration").jsonPrimitive.content).isEqualTo("0")
        assertThat(playInfo.getValue("position").jsonPrimitive.content).isEqualTo("0")
        assertThat(playInfo.getValue("playerStatus").jsonPrimitive.content).isEqualTo("7")
    }

    @Test
    fun `formats duration in milliseconds and position in seconds`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(
                        CastPlaybackSnapshot(
                            state = CastTransportState.PLAYING,
                            positionMs = 4_103L,
                            durationMs = 365_640_000L,
                        ),
                    ),
                ).jsonObject

        assertThat(playInfo.getValue("position").jsonPrimitive.content).isEqualTo("4")
        assertThat(playInfo.getValue("duration").jsonPrimitive.content).isEqualTo("365640000")
        assertThat(playInfo.getValue("playerStatus").jsonPrimitive.content).isEqualTo("4")
    }

    @Test
    fun `keeps position within duration`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(
                        CastPlaybackSnapshot(
                            state = CastTransportState.PLAYING,
                            positionMs = 27_416L,
                            durationMs = 365_683_639L,
                        ),
                    ),
                ).jsonObject

        assertThat(playInfo.getValue("position").jsonPrimitive.content).isEqualTo("27")
        assertThat(playInfo.getValue("duration").jsonPrimitive.content).isEqualTo("365683639")
    }

    @Test
    fun `uses nonzero loading duration while transitioning`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(
                        CastPlaybackSnapshot(
                            state = CastTransportState.TRANSITIONING,
                            positionMs = 0L,
                            durationMs = 0L,
                        ),
                    ),
                ).jsonObject

        assertThat(playInfo.getValue("position").jsonPrimitive.content).isEqualTo("0")
        assertThat(playInfo.getValue("duration").jsonPrimitive.content).isEqualTo("1000")
        assertThat(playInfo.getValue("playerStatus").jsonPrimitive.content).isEqualTo("2")
    }

    @Test
    fun `advertises danmaku and speed capability`() {
        val playInfo =
            Json
                .parseToJsonElement(
                    CastNirvanaPlayInfoFormatter.format(CastPlaybackSnapshot(speed = 1.5f)),
                ).jsonObject

        assertThat(playInfo.getValue("supportMultiSpeed").jsonPrimitive.content).isEqualTo("true")
        assertThat(playInfo.getValue("supportVideoDanmaku").jsonPrimitive.content).isEqualTo("true")
        assertThat(playInfo.getValue("supportLiveDanmaku").jsonPrimitive.content).isEqualTo("true")
        assertThat(
            playInfo
                .getValue("speedInfo")
                .jsonObject
                .getValue("currSpeed")
                .jsonPrimitive.content,
        ).isEqualTo("1.5")
    }

    @Test
    fun `nirvana scpd advertises danmaku and speed actions`() {
        val scpd = CastXmlDocuments.nirvanaControlScpd()

        assertThat(scpd).contains("<name>SetSpeed</name>")
        assertThat(scpd).contains("<name>SwitchQuality</name>")
        assertThat(scpd).contains("<name>SetDanmakuSwitch</name>")
        assertThat(scpd).contains("<name>GetPlayInfo</name>")
        assertThat(scpd).contains("<name>GetAppInfo</name>")
    }

    @Test
    fun `device description mirrors official yst shape`() {
        val description = CastXmlDocuments.deviceDescription(host = "192.168.1.2", uuid = "test-uuid")

        assertThat(description).contains(CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE)
        assertThat(description).contains(CastReceiverConfig.NIRVANA_SERVICE_TYPE)
        assertThat(description).contains("<UDN>uuid:test-uuid</UDN>")
        assertThat(description).contains("<capability>255</capability>")
        assertThat(description).contains("<ottVersion>")
        assertThat(description).contains("DMR-1.50")
        assertThat(description).contains("/AVTransport/action")
        assertThat(description).contains("/NirvanaControl/action")
    }
}
