package dev.frost819.newbv.app.cast.protocol

/**
 * 从投屏请求（DLNA SOAP / 查询串 / JSON / DIDL 元数据）中提取出的播放内容描述。
 *
 * 同一个对象可能携带多种身份：B 站视频身份（aid/bvid/cid/epid/seasonId）、
 * 直播间身份（roomId）或第三方直接媒体地址（directMediaUrl），
 * 由 [CastPlaybackLauncher] 按优先级选择播放方式。
 */
data class CastContent(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val epid: Int? = null,
    val seasonId: Int? = null,
    val roomId: Int? = null,
    val seekSeconds: Int? = null,
    val quality: Int? = null,
    val playSpeed: Float? = null,
    val danmakuEnabled: Boolean? = null,
    val title: String? = null,
    val partTitle: String? = null,
    val directMediaUrl: String? = null,
    val directMediaType: CastDirectMediaType = CastDirectMediaType.Unknown,
    val directMediaCover: String? = null,
    val creator: String? = null,
    val clientHint: CastClientHint = CastClientHint.Generic,
    val rawFields: Map<String, String> = emptyMap(),
) {
    val hasVideoIdentity: Boolean
        get() = aid != null || !bvid.isNullOrBlank() || epid != null || seasonId != null

    val hasLiveIdentity: Boolean
        get() = roomId != null && roomId > 0

    val hasDirectMedia: Boolean
        get() = !directMediaUrl.isNullOrBlank()

    /** 直接媒体地址是否指向 B 站 CDN（决定播放时是否附带 B 站 Referer/UA）。 */
    val isBilibiliDirectMedia: Boolean
        get() = clientHint.isBilibiliClient || directMediaUrl.orEmpty().isBilibiliMediaUrl()
}

/**
 * 投屏来源端提示。
 *
 * B 站官方客户端投屏时 CurrentURI 为 `bilibili://projection?...` 且携带
 * 加密的 `_nva_ext_` 元数据；普通 DLNA 客户端（Macast、PPTV 等）则直接投媒体直链。
 */
enum class CastClientHint {
    OfficialBilibili,
    GenericBilibili,
    Generic,
    ;

    val isBilibiliClient: Boolean
        get() = this != Generic
}

/** 直接媒体地址的流类型。 */
enum class CastDirectMediaType {
    Unknown,
    Progressive,
    Audio,
    Hls,
    Dash,
}

internal fun String.isBilibiliMediaUrl(): Boolean {
    val lower = lowercase()
    return lower.contains("bilivideo.com") ||
        lower.contains("bilibili.com") ||
        lower.contains("bilivideo.cn") ||
        lower.contains("biliapi.net")
}
