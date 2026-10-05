package dev.frost819.newbv.biliapi.entity.pgc

import dev.frost819.newbv.biliapi.http.SeasonIndexType

data class PgcItem(
    var cover: String,
    var title: String,
    var subTitle: String,
    var seasonId: Int,
    var episodeId: Int,
    var seasonType: SeasonIndexType,
    var rating: String,
    /** 最新一话时长（秒），仅番剧/国创 feed 提供。 */
    var duration: Int? = null,
) {
    companion object {
        fun fromIndexResultItem(
            indexResultItem: dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem,
        ): PgcItem =
            PgcItem(
                cover = indexResultItem.cover,
                title = indexResultItem.title,
                subTitle = indexResultItem.subTitle,
                seasonId = indexResultItem.seasonId,
                episodeId = indexResultItem.firstEp.epId,
                seasonType = SeasonIndexType.fromId(indexResultItem.seasonType),
                rating = indexResultItem.score,
            )
    }
}
