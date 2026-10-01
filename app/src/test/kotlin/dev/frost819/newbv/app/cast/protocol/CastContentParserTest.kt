package dev.frost819.newbv.app.cast.protocol

import com.google.common.truth.Truth.assertThat
import io.ktor.http.Parameters
import org.junit.jupiter.api.Test

/**
 * [CastContentParser] 的单元测试。
 *
 * 覆盖各来源客户端的投屏姿势：Nirvana JSON 投递、查询串、直播表单、
 * B 站官方加密 `nva_ext` 元数据、标准 DLNA 直链与 DIDL 音频元数据。
 */
class CastContentParserTest {
    @Test
    fun `parses projection analytics style JSON body`() {
        val content = CastContentParser.parse(
            path = "/NirvanaControl/control",
            queryParameters = Parameters.Empty,
            body = """
                {
                  "aid": "116662971925516",
                  "cid": "38721687073",
                  "seekTs": "36",
                  "userDesireQn": "120",
                  "userDesireSpeed": "1.5",
                  "epId": "0",
                  "seasonId": "0"
                }
            """.trimIndent(),
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.aid).isEqualTo(116662971925516L)
        assertThat(content.cid).isEqualTo(38721687073L)
        assertThat(content.seekSeconds).isEqualTo(36)
        assertThat(content.quality).isEqualTo(120)
        assertThat(content.playSpeed).isEqualTo(1.5f)
    }

    @Test
    fun `prefers official desired quality and parses danmaku switch`() {
        val content = CastContentParser.parse(
            path = "/NirvanaControl/control",
            queryParameters = Parameters.Empty,
            body = """
                {
                  "aid": "116662971925516",
                  "cid": "38721687073",
                  "qn": "64",
                  "userDesireQn": "120",
                  "userDesireSpeed": "2.0",
                  "danmakuSwitchSave": "false"
                }
            """.trimIndent(),
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.quality).isEqualTo(120)
        assertThat(content.playSpeed).isEqualTo(2.0f)
        assertThat(content.danmakuEnabled).isFalse()
    }

    @Test
    fun `parses query parameters with bvid and cid`() {
        val content = CastContentParser.parse(
            path = "/cast/open",
            queryParameters = Parameters.build {
                append("bvid", "BV1xx411c7mD")
                append("cid", "1234")
                append("part_title", "%E6%AD%A3%E7%89%87")
            },
            body = null,
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.bvid).isEqualTo("BV1xx411c7mD")
        assertThat(content.cid).isEqualTo(1234L)
        assertThat(content.partTitle).isEqualTo("正片")
    }

    @Test
    fun `parses live room identity from form body`() {
        val content = CastContentParser.parse(
            path = "/bilibili/live",
            queryParameters = Parameters.Empty,
            body = "room_id=27183290&title=%E7%9B%B4%E6%92%AD&userDesireQn=10000&dm_switch=0",
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.roomId).isEqualTo(27183290)
        assertThat(content.title).isEqualTo("直播")
        assertThat(content.quality).isNull()
        assertThat(content.danmakuEnabled).isFalse()
    }

    @Test
    fun `parses live cast and ignores restricted mobile quality`() {
        val content = CastContentParser.parse(
            path = "/AVTransport/control",
            queryParameters = Parameters.Empty,
            body = """
                <CurrentURI>http://example.com/live.flv?qn=250&amp;bili_room_id=865961&amp;proj_source=bilibili</CurrentURI>
                <upnp:longDescription>{"content":{"contentType":3,"roomId":865961,"userDesireQn":116,"danmakuSwitchSave":true}}</upnp:longDescription>
            """.trimIndent(),
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.roomId).isEqualTo(865961)
        assertThat(content.quality).isNull()
        assertThat(content.danmakuEnabled).isTrue()
    }

    @Test
    fun `parses official bilibili projection DIDL with encrypted nva ext`() {
        val longDescription =
            "_wHTR8YnWOvCScdp30aTVk7OkqFHJ-ZH9fLX1HiLdvQ-Po5GMw9paJnjOOsb8TrFL1hYMekHKzVEdBjC8zmnlxZ1unMb2bZC0gbqmvLkqOyYz57iURnuDCFJIbMfZ7948GGdplio23x_ovwyhJcBO6uKtcOQVJD5MvhT0-nJsKusGgpRNCQVjJ0BmeEgmG73JSmxmzck811ZHA5i7dk7uCk72EeOvMpiyZIURjPUFhJgGStb2ymiulAd020jCnmptS6nMCroCoN2bQXzHIIfi9iIqqm_7Ux4zU1gt2ct_X-KsoUk6ZWjTb3_15qOFPcPcPfQTEhoeZIiGyMuzSW9aQOqgHeawEowV0SuaQHTCCfGZ5r-QLbxV2yyhXzE6SeJ5NUygZ-UuBwYw1krjkkxFvGulp_8lNv9ubhs5P_QjZaHGp1x7efwOvyTosuFmatBCAHLNEd3VV65cj-5dIQQo_SAAyePxOev_fnA9__guxZAM1-isDgzMXOEGLN87stWdNaR5TB9_S9XMlEDscVhsS_Cy0pKOpEhE9NE_A56dQ9--9D_kgqRLxeZkcLm8UMIBW2eg-BaWt2iHUBHZ0P3yIqaC_PtCFpN6tU9PKfKNGBCq9mv3_XYWmQDH1DcN1XNcUn5VbfJVpbqLVhwVRr-5w"
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>bilibili://projection?proj_source=bilibili&amp;_nva_ext_=</CurrentURI>
                  <CurrentURIMetaData>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="0" parentID="-1" restricted="1"&gt;&lt;dc:title&gt;如果罪恶都市变得非常阴险，大结局&lt;/dc:title&gt;&lt;upnp:longDescription&gt;$longDescription&lt;/upnp:longDescription&gt;&lt;res protocolInfo="http-get:*:video/x-flv:DLNA.ORG_OP=01;DLNA.ORG_CI=0"&gt;bilibili://projection?proj_source=bilibili&amp;amp;_nva_ext_=&lt;/res&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val content = CastContentParser.parse(
            path = "/AVTransport/control",
            queryParameters = Parameters.Empty,
            body = body,
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.aid).isEqualTo(116622152963643L)
        assertThat(content.cid).isEqualTo(38546702683L)
        assertThat(content.seekSeconds).isEqualTo(1)
        assertThat(content.quality).isEqualTo(16)
        assertThat(content.title).isEqualTo("如果罪恶都市变得非常阴险，大结局")
        assertThat(content.clientHint).isEqualTo(CastClientHint.OfficialBilibili)
    }

    @Test
    fun `parses standard DLNA direct media url from CurrentURI`() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>http://videoplay.115.com/m3u8/pickcode?filesha1=abc&amp;definition=5</CurrentURI>
                  <CurrentURIMetaData><![CDATA[
                    <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                      <item id="1" parentID="0" restricted="1">
                        <dc:title>115 video</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:video/mp4:*">http://videoplay.115.com/m3u8/pickcode?filesha1=abc&amp;definition=5</res>
                      </item>
                    </DIDL-Lite>
                  ]]></CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val content = CastContentParser.parse(
            path = "/AVTransport/control",
            queryParameters = Parameters.Empty,
            body = body,
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.aid).isNull()
        assertThat(content.title).isEqualTo("115 video")
        assertThat(content.directMediaType).isEqualTo(CastDirectMediaType.Hls)
        assertThat(content.directMediaUrl)
            .isEqualTo("http://videoplay.115.com/m3u8/pickcode?filesha1=abc&definition=5")
        assertThat(content.isBilibiliDirectMedia).isFalse()
    }

    @Test
    fun `parses standard DLNA audio metadata with album art`() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>https://music.example.com/song.mp3</CurrentURI>
                  <CurrentURIMetaData><![CDATA[
                    <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                      <item id="1" parentID="0" restricted="1">
                        <dc:title>Song title</dc:title>
                        <dc:creator>Song artist</dc:creator>
                        <upnp:artist>Upnp artist</upnp:artist>
                        <upnp:albumArtURI>https://music.example.com/cover.jpg</upnp:albumArtURI>
                        <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                        <res protocolInfo="http-get:*:audio/mpeg:*">https://music.example.com/song.mp3</res>
                      </item>
                    </DIDL-Lite>
                  ]]></CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val content = CastContentParser.parse(
            path = "/AVTransport/control",
            queryParameters = Parameters.Empty,
            body = body,
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.title).isEqualTo("Song title")
        assertThat(content.creator).isEqualTo("Upnp artist")
        assertThat(content.directMediaCover).isEqualTo("https://music.example.com/cover.jpg")
        assertThat(content.directMediaType).isEqualTo(CastDirectMediaType.Audio)
        assertThat(content.directMediaUrl).isEqualTo("https://music.example.com/song.mp3")
    }

    @Test
    fun `marks bilibili CDN direct media as bilibili`() {
        val content = CastContentParser.parse(
            path = "/AVTransport/control",
            queryParameters = Parameters.Empty,
            body = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <CurrentURI>https://upos-sz-mirror08c.bilivideo.com/30/m200x330kMa.mp4?deadline=1</CurrentURI>
                    </u:SetAVTransportURI>
                  </s:Body>
                </s:Envelope>
            """.trimIndent(),
        )

        assertThat(content).isNotNull()
        content!!
        assertThat(content.clientHint).isEqualTo(CastClientHint.GenericBilibili)
        assertThat(content.isBilibiliDirectMedia).isTrue()
    }

    @Test
    fun `returns null when no identity present`() {
        val content = CastContentParser.parse(
            path = "/unknown",
            queryParameters = Parameters.Empty,
            body = "foo=bar",
        )

        assertThat(content).isNull()
    }
}
