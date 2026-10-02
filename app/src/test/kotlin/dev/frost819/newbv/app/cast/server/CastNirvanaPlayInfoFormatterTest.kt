package dev.frost819.newbv.app.cast.server

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.cast.CastPlaybackSnapshot
import dev.frost819.newbv.app.cast.CastTransportState
import kotlinx.serialization.json.Json
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
    fun `formats duration in milliseconds and position in seconds`() {
        val playInfo = Json.parseToJsonElement(
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
        val playInfo = Json.parseToJsonElement(
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
        val playInfo = Json.parseToJsonElement(
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
        val playInfo = Json.parseToJsonElement(
            CastNirvanaPlayInfoFormatter.format(CastPlaybackSnapshot(speed = 1.5f)),
        ).jsonObject

        assertThat(playInfo.getValue("supportMultiSpeed").jsonPrimitive.content).isEqualTo("true")
        assertThat(playInfo.getValue("supportVideoDanmaku").jsonPrimitive.content).isEqualTo("true")
        assertThat(playInfo.getValue("supportLiveDanmaku").jsonPrimitive.content).isEqualTo("true")
        assertThat(
            playInfo.getValue("speedInfo").jsonObject.getValue("currSpeed").jsonPrimitive.content,
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
