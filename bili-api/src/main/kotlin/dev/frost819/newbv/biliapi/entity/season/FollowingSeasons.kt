package dev.frost819.newbv.biliapi.entity.season

data class FollowingSeasonData(
    val list: List<FollowingSeason>,
    val total: Int,
)

data class FollowingSeason(
    val seasonId: Int,
    val title: String,
    val cover: String,
    /** 观看进度文案（看到第X话 95%），尚未观看时可能为空串。 */
    val progress: String? = null,
    /** 最新一话展示文案（更新至第X话）。 */
    val newEpIndexShow: String? = null,
    /** 全部话数（App 通道缺失）。 */
    val totalCount: Int? = null,
    /** 角标文案（大会员/限时免费等）。 */
    val badge: String? = null,
) {
    companion object {
        fun fromFollowingSeason(season: dev.frost819.newbv.biliapi.http.entity.season.WebFollowingSeason) =
            FollowingSeason(
                seasonId = season.seasonId,
                title = season.title,
                cover = season.cover,
                progress = season.progress.takeIf { it.isNotEmpty() },
                newEpIndexShow = season.newEp.indexShow,
                totalCount = season.totalCount,
                badge = season.badge,
            )

        fun fromFollowingSeason(season: dev.frost819.newbv.biliapi.http.entity.season.AppFollowingSeason) =
            FollowingSeason(
                seasonId = season.seasonId,
                title = season.title,
                cover = season.cover,
                progress = season.progress?.indexShow,
                newEpIndexShow = season.newEp.indexShow,
                badge = season.badge,
            )
    }
}

enum class FollowingSeasonType(
    val id: Int,
    val paramName: String,
) {
    Bangumi(id = 1, paramName = "bangumi"),
    Cinema(id = 2, paramName = "cinema"),
}

enum class FollowingSeasonStatus(
    val id: Int,
) {
    All(id = 0),
    Want(id = 1),
    Watching(id = 2),
    Watched(id = 3),
}
