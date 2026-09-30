package dev.frost819.newbv.biliapi.http.entity.pgc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/pgc/web/rank/list`（番剧热播榜）响应的 result 根节点。
 */
@Serializable
data class PgcWebRankData(
    val list: List<RankItem> = emptyList(),
) {
    @Serializable
    data class RankItem(
        val badge: String = "",
        val cover: String,
        @SerialName("icon_font")
        val iconFont: IconFont? = null,
        @SerialName("new_ep")
        val newEp: NewEp? = null,
        val rank: Int,
        val rating: String? = null,
        @SerialName("season_id")
        val seasonId: Int,
        val title: String,
    ) {
        @Serializable
        data class IconFont(
            val name: String = "",
            val text: String = "",
        )

        @Serializable
        data class NewEp(
            @SerialName("index_show")
            val indexShow: String? = null,
        )
    }
}
