package dev.frost819.newbv.biliapi.entity.video.season

import dev.frost819.newbv.biliapi.entity.video.Dimension

/**
 * 剧集视频
 *
 * @param id 剧集id
 * @param aid av号
 * @param bvid bv号
 * @param cid cid
 * @param epid epid
 * @param title 标题 在投稿视频中显示为分 p 标题；
 * 在剧集中，如果存在完整标题，则该标题内容为纯数字，用于显示“第 x 话”，若完整标题内容为空时，显示该分集标题，例如“正片”“中文”
 * @param longTitle 完整标题，仅在剧集中存在，如果不存在完整标题，则该标题为空
 * @param cover 封面
 * @param duration 时长
 * @param dimension 分辨率
 * @param badge 官方角标（如“会员”“限免”），无角标为 null
 */
data class Episode(
    val id: Int,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val epid: Int? = null,
    val title: String,
    val longTitle: String,
    val cover: String,
    /** 时长（秒），用于与观看历史计算进度。 */
    val duration: Int,
    val dimension: Dimension?,
    val badge: EpisodeBadge? = null,
) {
    companion object {
        fun fromEpisode(episode: bilibili.app.view.v1.Episode) =
            Episode(
                id = episode.id.toInt(),
                aid = episode.aid,
                bvid = episode.bvid,
                cid = episode.cid,
                title = episode.title,
                longTitle = episode.title,
                cover = episode.cover,
                duration = episode.page.duration.toInt(),
                dimension = Dimension.fromDimension(episode.page.dimension),
            )

        fun fromEpisode(episode: dev.frost819.newbv.biliapi.http.entity.video.UgcSeason.Section.Episode) =
            Episode(
                id = episode.id,
                aid = episode.aid,
                bvid = episode.bvid,
                cid = episode.cid,
                title = episode.title,
                longTitle = episode.title,
                cover = episode.arc.pic,
                duration = episode.arc.duration,
                dimension = Dimension.fromDimension(episode.page.dimension),
            )

        /** 将 Web/App PGC 的毫秒时长转换为播放器和观看历史使用的秒数。 */
        fun fromEpisode(episode: dev.frost819.newbv.biliapi.http.entity.season.Episode) =
            Episode(
                id = episode.id,
                aid = episode.aid,
                cid = episode.cid,
                bvid = episode.bvid,
                cover = episode.cover,
                title = episode.title,
                longTitle = episode.longTitle,
                epid = episode.epId,
                duration = (episode.duration / 1000).coerceAtLeast(0),
                dimension = episode.dimension?.let { Dimension.fromDimension(it) },
                badge =
                    EpisodeBadge.fromText(
                        text = episode.badge,
                        info = episode.badgeInfo,
                    ),
            )
    }
}

/**
 * 剧集官方角标（如“会员”“限免”“预告”）。
 *
 * 文案与配色均来自接口（badge / badge_info），bgColor 与 bgColorNight
 * 为 B 站官方十六进制色值（如 #FB7299），可能为空串，由 UI 层兜底。
 *
 * @param text 角标文字
 * @param bgColor 白天背景色（十六进制，如 "#FB7299"）
 * @param bgColorNight 夜间背景色
 */
data class EpisodeBadge(
    val text: String,
    val bgColor: String,
    val bgColorNight: String,
) {
    companion object {
        fun fromText(
            text: String,
            info: dev.frost819.newbv.biliapi.http.entity.season.Episode.BadgeInfo?,
        ): EpisodeBadge? {
            val displayText =
                info?.text?.takeIf { it.isNotBlank() }
                    ?: text.takeIf { it.isNotBlank() }
                    ?: return null
            return EpisodeBadge(
                text = displayText,
                bgColor = info?.bgColor ?: "",
                bgColorNight = info?.bgColorNight ?: "",
            )
        }
    }
}
