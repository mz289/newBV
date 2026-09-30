package dev.frost819.newbv.biliapi.http.entity.pgc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/pgc/page/pc/bangumi/tab`（TV/PC 端番剧页模块化接口）响应的 data 根节点。
 *
 * 模块按 style 区分：`follow` 我的追番、`v_card` 番剧/国创推荐、
 * `double_feed` 猜你喜欢（用 [nextCursor] 翻页）、`rank` 排行等。
 */
@Serializable
data class PgcPageTabData(
    @SerialName("has_next")
    val hasNext: Int,
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    val modules: List<Module> = emptyList(),
) {
    @Serializable
    data class Module(
        @SerialName("module_id")
        val moduleId: Int,
        val style: String,
        val title: String,
        val items: List<Item> = emptyList(),
    ) {
        @Serializable
        data class Item(
            @SerialName("badge_info")
            val badgeInfo: BadgeInfo? = null,
            @SerialName("bottom_right_badge")
            val bottomRightBadge: BadgeInfo? = null,
            val cover: String,
            val desc: String? = null,
            @SerialName("episode_id")
            val episodeId: Long? = null,
            val link: String? = null,
            @SerialName("new_ep")
            val newEp: NewEp? = null,
            /** 观看进度文案（看到第X话 95%），follow 模块提供。 */
            val progress: String? = null,
            @SerialName("season_id")
            val seasonId: Int,
            @SerialName("season_type")
            val seasonType: Int? = null,
            @SerialName("sub_title")
            val subTitle: String? = null,
            val title: String,
        ) {
            @Serializable
            data class BadgeInfo(
                val text: String? = null,
            )

            @Serializable
            data class NewEp(
                @SerialName("index_show")
                val indexShow: String? = null,
            )
        }
    }
}
