package dev.frost819.newbv.biliapi.http.entity.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PGC（番剧/影视）Web 播放地址（/pgc/player/web/v2/playurl）。
 *
 * PGC 内容必须走该专用接口：UGC 通道（/x/player/playurl）对会员专享剧集
 * 不返回试看流，仅报 -404“啥都木有”；本接口对无权限场景返回试看流
 * （video_info.is_preview = 1 + durl 试看分段）或明确的权限错误。
 *
 * 实测响应为 { code, message, result: { play_view_business_info, video_info, view_info } }，
 * 经 [dev.frost819.newbv.biliapi.http.entity.BiliResponse] 的 result 分支承接。
 *
 * @param business 播放业务信息（剧集/季/用户状态，试看标记实际位于 video_info 内）
 * @param videoInfo 视频流信息，结构与旧版 playurl 的 data 一致
 */
@Serializable
data class PgcPlayUrlData(
    @SerialName("play_view_business_info")
    val business: Business? = null,
    @SerialName("video_info")
    val videoInfo: VideoInfo? = null,
) {
    @Serializable
    data class Business(
        @SerialName("is_preview")
        val isPreview: Boolean = false,
        @SerialName("vip_type")
        val vipType: Int = 0,
        @SerialName("vip_status")
        val vipStatus: Int = 0,
    )

    @Serializable
    data class VideoInfo(
        /** 内层业务码：0 成功；非 0 时 [message] 为具体错误（如“大会员专享限制”）。 */
        val code: Int = 0,
        val message: String = "",
        @SerialName("error_code")
        val errorCode: Int = 0,
        /** 是否预览（试看）内容：1 为试看。 */
        @SerialName("is_preview")
        val isPreview: Int = 0,
        @SerialName("has_paid")
        val hasPaid: Boolean = false,
        val status: Int = 0,
        val quality: Int = 0,
        val format: String = "",
        @SerialName("timelength")
        val timeLength: Int = 0,
        @SerialName("accept_quality")
        val acceptQuality: List<Int> = emptyList(),
        @SerialName("video_codecid")
        val videoCodecId: Int = 0,
        /** 试看分段流（flv/mp4），仅无 DASH 权限时存在。 */
        val durl: List<Durl> = emptyList(),
        val dash: Dash? = null,
        @SerialName("support_formats")
        val supportFormats: List<SupportFormat> = emptyList(),
    )
}
