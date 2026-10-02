package dev.frost819.newbv.biliapi.http.entity.pgc

import dev.frost819.newbv.biliapi.http.entity.web.Hover
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * PGC 首页 ssr 数据
 */
@Serializable
data class PgcWebInitialStateData(
    val modules: Modules,
) {
    /**
     * @param banner 轮播图
     * @param index 索引快捷筛选分组
     * @param ext 板块列表（标题/样式/条目均由服务端下发，v2 与 v3 分区页同构）
     */
    @Suppress("KDocUnresolvedReference")
    @Serializable
    data class Modules(
        val banner: Banner,
        val index: Index? = null,
        val ext: List<ExtModule> = emptyList(),
    ) {
        /** 索引快捷筛选模块：每个条目是一组筛选维度（排序/风格/地区…）。 */
        @Serializable
        data class Index(
            val items: List<IndexGroup> = emptyList(),
        ) {
            @Serializable
            data class IndexGroup(
                val field: String? = null,
                val name: String? = null,
                val all: IndexValue? = null,
                val values: List<List<IndexValue>> = emptyList(),
            ) {
                @Serializable
                data class IndexValue(
                    val keyword: String? = null,
                    val name: String? = null,
                )
            }
        }

        @Serializable
        data class ExtModule(
            val title: String? = null,
            val style: String? = null,
            @SerialName("module_id")
            val moduleId: Int? = null,
            val items: List<ExtItem> = emptyList(),
        ) {
            @Serializable
            data class ExtItem(
                val title: String? = null,
                val cover: String? = null,
                val link: String? = null,
                @SerialName("season_id")
                val seasonId: Int? = null,
                @SerialName("episode_id")
                val episodeId: Long? = null,
                val rating: String? = null,
                @SerialName("sub_title")
                val subTitle: String? = null,
                val rank: Int? = null,
                val avid: Long? = null,
                @SerialName("sub_items")
                val subItems: List<ExtItem>? = null,
            )
        }

        @Serializable
        data class Banner(
            val title: String,
            val spmid: String,
            val size: Int,
            val style: String,
            val headers: JsonArray,
            val items: List<BannerItem>,
            val wids: JsonArray,
            @SerialName("module_id")
            val moduleId: Int,
        ) {
            @Serializable
            data class BannerItem(
                val rating: String? = null,
                val title: String,
                val cover: String,
                val link: String,
                val evaluate: String? = null,
                val report: JsonElement? = null,
                val hover: Hover? = null,
                val stat: Stat? = null,
                val values: JsonArray? = null,
                @SerialName("season_id")
                val seasonId: Int? = null,
                @SerialName("season_type")
                val seasonType: Int? = null,
                @SerialName("rating_count")
                val ratingCount: Int? = null,
                @SerialName("episode_id")
                val episodeId: Int? = null,
                @SerialName("big_cover")
                val bigCover: String? = null,
                @SerialName("play_btn")
                val playBtn: Int? = null,
                @SerialName("play_title")
                val playTitle: String? = null,
                @SerialName("rank_id")
                val rankId: Int,
                @SerialName("user_status")
                val userStatus: UserStatus? = null,
                @SerialName("date_ts")
                val dateTs: Int? = null,
                @SerialName("day_of_week")
                val dayOfWeek: Int? = null,
                @SerialName("is_today")
                val isToday: Int? = null,
                @SerialName("is_latest")
                val isLatest: Int? = null,
                val id: String,
                @SerialName("showReportData")
                val showReportData: ShowReportData,
                // 当前获取到的 json 中未包含 webpcover 和 webpbigcover
                // @SerialName("webpcover")
                // val webpCover: String,
                // @SerialName("webpbigcover")
                // val webpBigCover: String
            ) {
                @Serializable
                data class Stat(
                    val view: Long,
                )

                @Serializable
                data class UserStatus(
                    val follow: Int,
                )

                @Serializable
                data class ShowReportData(
                    @SerialName("module_type")
                    val moduleType: String,
                    @SerialName("module_id")
                    val moduleId: Int,
                    @SerialName("ep_id")
                    val epId: Int? = null,
                    @SerialName("season_id")
                    val seasonId: Int? = null,
                )
            }
        }
    }
}
