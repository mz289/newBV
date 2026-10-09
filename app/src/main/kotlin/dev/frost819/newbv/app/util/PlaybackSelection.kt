package dev.frost819.newbv.app.util

import dev.frost819.newbv.biliapi.entity.DashVideo
import dev.frost819.newbv.biliapi.entity.PlayData
import dev.frost819.newbv.data.datastore.VideoCodec

/** 可选的「画质 + 编码」组合。 */
data class PlaybackCandidate(
    val quality: Int,
    val codec: VideoCodec,
)

/** 菜单只显示有实际视频流的编码，按 ordinal 排序。 */
fun collectCodecs(
    data: PlayData,
    qualityId: Int,
): List<VideoCodec> =
    data.dashVideos
        .filter { it.quality == qualityId }
        .mapNotNull { it.videoCodec() }
        .distinct()
        .sortedBy { it.ordinal }

/** 使用同一编码解析规则生成菜单、选择流和检查解码能力。 */
private fun DashVideo.videoCodec(): VideoCodec? {
    val codecString = codecs
    return if (codecString.isNullOrBlank()) VideoCodec.fromCodecId(codecId) else VideoCodec.fromCodecString(codecString)
}

/**
 * 画质遍历顺序。
 *
 * - 存在不高于目标的画质时：只按**从高到低**遍历这些低档位。更高画质需求更大，
 *   低档位都解不了时更高档位更不可能成功，故不纳入。
 * - 可用画质**全部高于目标**时：按**从低到高**遍历，最低者最接近目标且解码风险最小。
 */
fun orderQualities(
    available: List<Int>,
    requested: Int,
): List<Int> {
    val sorted = available.filter { it > 0 }.distinct().sorted()
    val atOrBelow = sorted.filter { it <= requested }.sortedDescending()
    return atOrBelow.ifEmpty { sorted }
}

/** 判断某条 DASH 流是否属于指定编码（优先用 codecs 串，其次 codecId）。 */
fun trackMatchesCodec(
    track: DashVideo,
    codec: VideoCodec,
): Boolean = track.videoCodec() == codec

/** 查找指定画质 + 编码的 DASH 视频流。 */
fun findTrack(
    data: PlayData,
    quality: Int,
    codec: VideoCodec,
): DashVideo? = data.dashVideos.firstOrNull { it.quality == quality && trackMatchesCodec(it, codec) }

/**
 * 按「目标画质优先、逐档降级 + 编码偏好」挑选首个本机可解码的组合。
 *
 * @param data 播放数据。
 * @param requestedQualityId 用户期望画质（qn）。
 * @param preferredCodec 用户偏好的编码，排在最前。
 * @param capabilityProvider 设备解码能力查询器。
 * @return 可解码组合；全部不可解码时返回 null（交由运行时回退兜底）。
 */
fun pickDecodableProfile(
    data: PlayData,
    requestedQualityId: Int,
    preferredCodec: VideoCodec,
    capabilityProvider: VideoCapabilityProvider,
): PlaybackCandidate? {
    val qualities = orderQualities(data.dashVideos.map { it.quality }, requestedQualityId)
    val codecOrder =
        listOf(
            preferredCodec,
            VideoCodec.HEVC,
            VideoCodec.AV1,
            VideoCodec.AVC,
            VideoCodec.DVH1,
        ).distinct()
    for (quality in qualities) {
        for (codec in codecOrder) {
            val track = findTrack(data, quality, codec) ?: continue
            val profile =
                VideoDecodeProfile(
                    codec = codec,
                    width = track.width,
                    height = track.height,
                    frameRate = track.frameRate.toFloatOrNull(),
                    codecs = track.codecs,
                )
            if (capabilityProvider.isDecodable(profile)) {
                return PlaybackCandidate(quality, codec)
            }
        }
    }
    return null
}
