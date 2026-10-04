package dev.frost819.newbv.biliapi.http.entity.video

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.PlayData
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * PGC Web 播放地址（/pgc/player/web/v2/playurl）的结构与解析测试。
 *
 * 样本取自 2026-10 真实接口响应（未登录 try_look=1）：
 * - 免费集（ep 374416《正常人》）：video_info 携带完整 DASH 正片；
 * - 会员集（ep 374417，badge=“会员”）：is_preview=1，无 DASH，仅 durl 试看分段；
 * - 内层错误：外层 code=0 时错误封装在 video_info.code/message 内。
 *
 * 固化两处易回归的结构：响应位于 result 而非 data；试看标记是
 * video_info.is_preview（Int）而非 play_view_business_info.is_preview。
 */
class PgcPlayUrlDataTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    /** 免费集样本（裁剪自真实响应，保留解析所需字段）。 */
    private val freeEpisodeResponse =
        """
        {
          "code": 0,
          "message": "success",
          "ttl": 1,
          "result": {
            "play_view_business_info": {"user_status": {}},
            "view_info": {"play_detail": "PLAY_FULL"},
            "video_info": {
              "code": 0,
              "message": "Success",
              "error_code": 0,
              "is_preview": 0,
              "has_paid": false,
              "status": 2,
              "quality": 120,
              "format": "hdflv2",
              "timelength": 5000,
              "accept_quality": [120, 80],
              "video_codecid": 12,
              "from": "local",
              "result": "suee",
              "type": "DASH",
              "seek_param": "start",
              "seek_type": "offset",
              "bp": 0,
              "is_drm": false,
              "no_rexcode": 1,
              "durl": [],
              "dash": {
                "duration": 5000,
                "minBufferTime": 1.5,
                "video": [
                  {
                    "id": 120,
                    "base_url": "https://example.com/v120.m4s",
                    "backup_url": [],
                    "bandwidth": 2000000,
                    "mime_type": "video/mp4",
                    "codecs": "avc1.640033",
                    "width": 3840,
                    "height": 2160,
                    "frame_rate": "16000/672",
                    "sar": "1:1",
                    "start_with_sap": 1,
                    "segment_base": {"initialization": "0-963", "index_range": "964-1200"},
                    "codecid": 12,
                    "md5": "",
                    "size": 1000
                  }
                ],
                "audio": [
                  {
                    "id": 30280,
                    "base_url": "https://example.com/a80.m4s",
                    "backup_url": [],
                    "bandwidth": 320000,
                    "mime_type": "audio/mp4",
                    "codecs": "mp4a.40.2",
                    "width": 0,
                    "height": 0,
                    "frame_rate": "",
                    "sar": "",
                    "start_with_sap": 1,
                    "segment_base": {"initialization": "0-800", "index_range": "801-1000"},
                    "codecid": 0,
                    "md5": "",
                    "size": 500
                  }
                ],
                "dolby": {"audio": [], "type": 2},
                "flac": {"display": false, "audio": null}
              },
              "support_formats": [
                {
                  "quality": 120,
                  "format": "hdflv2",
                  "new_description": "4K 超高清",
                  "description": "4K 超高清",
                  "display_desc": "4K",
                  "superscript": "",
                  "codecs": ["avc1.640033"],
                  "need_login": false,
                  "need_vip": true
                },
                {
                  "quality": 80,
                  "format": "flv",
                  "new_description": "1080P 高清",
                  "description": "1080P 高清",
                  "display_desc": "1080P",
                  "superscript": "",
                  "codecs": ["avc1.640032"],
                  "need_login": false,
                  "need_vip": null
                }
              ]
            }
          }
        }
        """.trimIndent()

    /** 会员集试看样本（未登录 try_look，无 DASH、仅 durl 试看分段）。 */
    private val vipEpisodePreviewResponse =
        """
        {
          "code": 0,
          "message": "success",
          "ttl": 1,
          "result": {
            "play_check": {"play_detail": "PLAY_PREVIEW"},
            "video_info": {
              "code": 0,
              "message": "Success",
              "error_code": -10403,
              "is_preview": 1,
              "has_paid": false,
              "status": 13,
              "quality": 32,
              "format": "mp4",
              "timelength": 360082,
              "accept_quality": [32],
              "video_codecid": 7,
              "from": "local",
              "result": "suee",
              "type": "MP4",
              "seek_param": "start",
              "seek_type": "second",
              "durl": [
                {
                  "order": 1,
                  "length": 360082,
                  "size": 29223571,
                  "ahead": "",
                  "vhead": "",
                  "url": "https://example.com/preview.mp4",
                  "backup_url": []
                }
              ],
              "support_formats": [
                {
                  "quality": 112,
                  "format": "hdflv2",
                  "new_description": "1080P 高码率",
                  "description": "1080P 高码率",
                  "display_desc": "1080P 高码率",
                  "superscript": "",
                  "need_login": false,
                  "need_vip": true
                },
                {
                  "quality": 32,
                  "format": "mp4",
                  "new_description": "480P 标清",
                  "description": "480P 标清",
                  "display_desc": "480P",
                  "superscript": "",
                  "need_login": false,
                  "need_vip": false
                }
              ]
            }
          }
        }
        """.trimIndent()

    @Test
    fun `parses free episode response from result field with full dash`() {
        val response = json.decodeFromString<BiliResponse<PgcPlayUrlData>>(freeEpisodeResponse)
        val playData = PlayData.fromPgcWebPlayUrlData(response.getResponseData())

        assertThat(playData.needPay).isFalse()
        assertThat(playData.dashVideos).hasSize(1)
        assertThat(playData.dashVideos.first().quality).isEqualTo(120)
        assertThat(playData.dashAudios).hasSize(1)
        assertThat(playData.codec[120]).containsExactly("avc1.640033")
    }

    @Test
    fun `parses vip episode preview as needPay with durl stream`() {
        val response = json.decodeFromString<BiliResponse<PgcPlayUrlData>>(vipEpisodePreviewResponse)
        val playData = PlayData.fromPgcWebPlayUrlData(response.getResponseData())

        assertThat(playData.needPay).isTrue()
        assertThat(playData.dashVideos).hasSize(1)
        assertThat(playData.dashVideos.first().baseUrl).isEqualTo("https://example.com/preview.mp4")
        assertThat(playData.dashAudios).isEmpty()
    }

    @Test
    fun `throws inner business error message for permission denial`() {
        // 外层 code=0、错误封装在 video_info 内的场景（无试看权限的会员内容）
        val raw =
            """
            {
              "code": 0,
              "message": "success",
              "result": {
                "video_info": {
                  "code": -403,
                  "message": "大会员专享限制",
                  "error_code": -10403
                }
              }
            }
            """.trimIndent()
        val response = json.decodeFromString<BiliResponse<PgcPlayUrlData>>(raw)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                PlayData.fromPgcWebPlayUrlData(response.getResponseData())
            }
        assertThat(exception.message).isEqualTo("大会员专享限制")
    }
}
