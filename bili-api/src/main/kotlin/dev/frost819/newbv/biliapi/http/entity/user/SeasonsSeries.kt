package dev.frost819.newbv.biliapi.http.entity.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UP 主空间合集/系列列表（Web polymer 接口）
 *
 * 端点：GET /x/polymer/web-space/seasons_series_list（无需 WBI 签名）
 *
 * @param itemsLists 内容列表（套了个娃）
 */
@Serializable
data class WebSeasonsSeriesData(
    @SerialName("items_lists")
    val itemsLists: ItemsLists? = null,
) {
    @Serializable
    data class ItemsLists(
        val page: Page? = null,
        @SerialName("seasons_list")
        val seasonsList: List<Item> = emptyList(),
        @SerialName("series_list")
        val seriesList: List<Item> = emptyList(),
    ) {
        /**
         * @param pageNumber 当前页码
         * @param pageSize 每页项数
         * @param total 合集与系列总数
         */
        @Serializable
        data class Page(
            @SerialName("page_num")
            val pageNumber: Int? = null,
            @SerialName("page_size")
            val pageSize: Int? = null,
            val total: Int? = null,
        )
    }

    /**
     * @param meta 合集/系列元数据
     * @param archives 系列内视频（合集条目可能为空）
     * @param recentAids 最近更新的视频 aid 列表
     */
    @Serializable
    data class Item(
        val meta: WebCollectionMeta? = null,
        val archives: List<WebCollectionArchiveItem> = emptyList(),
        @SerialName("recent_aids")
        val recentAids: List<Long> = emptyList(),
    )
}

/**
 * 合集/系列元数据（合集与系列响应共用，字段按来源可缺省）。
 *
 * 合集（season）：season_id 非空，title 为去掉"合集·"前缀的标题，name 为完整展示名；
 * 系列（series）：series_id 非空，name 为标题。
 */
@Serializable
data class WebCollectionMeta(
    @SerialName("season_id")
    val seasonId: Long? = null,
    @SerialName("series_id")
    val seriesId: Long? = null,
    val name: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val total: Int? = null,
    val description: String? = null,
)

/**
 * 合集/系列内视频稿件（polymer seasons_archives_list 与 x/series/archives 共用）。
 */
@Serializable
data class WebCollectionArchiveItem(
    val aid: Long,
    val bvid: String,
    val title: String,
    val pic: String,
    /** 时长（秒）。 */
    val duration: Int,
    val pubdate: Long? = null,
    val ctime: Long? = null,
    val stat: Stat? = null,
    /** 播放进度百分比（0-100），0 表示未观看。 */
    @SerialName("playback_position")
    val playbackPosition: Int = 0,
) {
    @Serializable
    data class Stat(
        /** 播放数（个别稿件可能超过 Int 上限）。 */
        val view: Long? = null,
        val danmaku: Int? = null,
    )
}

/**
 * 合集内视频列表（Web polymer 接口）
 *
 * 端点：GET /x/polymer/web-space/seasons_archives_list
 */
@Serializable
data class WebSeasonArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<WebCollectionArchiveItem> = emptyList(),
    val meta: WebCollectionMeta? = null,
    val page: Page? = null,
) {
    @Serializable
    data class Page(
        @SerialName("page_num")
        val pageNumber: Int? = null,
        @SerialName("page_size")
        val pageSize: Int? = null,
        val total: Int? = null,
    )
}

/**
 * 系列内视频列表（Web 接口）
 *
 * 端点：GET /x/series/archives
 */
@Serializable
data class WebSeriesArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<WebCollectionArchiveItem> = emptyList(),
    val page: Page? = null,
) {
    @Serializable
    data class Page(
        val num: Int? = null,
        val size: Int? = null,
        val total: Int? = null,
    )
}
