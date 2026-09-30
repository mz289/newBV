package dev.frost819.newbv.biliapi.entity.pgc

import dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebRankData

/** 番剧热播榜（/pgc/web/rank/list）。 */
data class PgcRankData(
    val items: List<Item>,
) {
    data class Item(
        /** 排名，从 1 开始。 */
        val rank: Int,
        val seasonId: Int,
        val title: String,
        val cover: String,
        /** 评分（如 9.8），无评分时为空。 */
        val rating: String?,
        /** 最新一话展示文案（更新至第X话）。 */
        val newEpIndexShow: String?,
        /** 角标文案（大会员等）。 */
        val badge: String,
    ) {
        companion object {
            fun fromRankItem(item: PgcWebRankData.RankItem): Item =
                Item(
                    rank = item.rank,
                    seasonId = item.seasonId,
                    title = item.title,
                    cover = item.cover,
                    rating = item.rating?.removeSuffix("分")?.takeIf { it.isNotEmpty() },
                    newEpIndexShow = item.newEp?.indexShow,
                    badge = item.badge,
                )
        }
    }

    companion object {
        fun fromPgcWebRankData(data: PgcWebRankData): PgcRankData =
            PgcRankData(items = data.list.map { Item.fromRankItem(it) })
    }
}
