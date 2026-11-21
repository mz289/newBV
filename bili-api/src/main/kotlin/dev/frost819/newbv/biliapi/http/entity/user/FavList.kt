package dev.frost819.newbv.biliapi.http.entity.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户公开收藏夹列表（Web，分页）
 *
 * 端点：GET /x/v3/fav/folder/created/list?up_mid={mid}&pn=&ps=
 * （匿名可访问，仅返回公开夹；分页版带封面，优于 list-all）
 */
@Serializable
data class WebUserFavFolders(
    val count: Int? = null,
    val list: List<FolderItem> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean? = null,
) {
    @Serializable
    data class FolderItem(
        /** 收藏夹 ID（即 favlist 页 fid / 内容页 media_id）。 */
        val id: Long,
        val title: String? = null,
        @SerialName("media_count")
        val mediaCount: Int? = null,
        /** 收藏夹封面（取自夹内视频封面）。 */
        val cover: String? = null,
        val attr: Int? = null,
    )
}

/**
 * 收藏夹内容列表（Web）
 *
 * 端点：GET /x/v3/fav/resource/list（匿名可访问；含已失效/非视频条目，需调用方过滤）
 */
@Serializable
data class WebFavResourceList(
    val info: Info? = null,
    val medias: List<MediaItem> = emptyList(),
    /** 是否还有下一页（total 字段不总返回，分页以本字段为准）。 */
    @SerialName("has_more")
    val hasMore: Boolean? = null,
) {
    @Serializable
    data class Info(
        val id: Long,
        val title: String? = null,
        @SerialName("media_count")
        val mediaCount: Int? = null,
        val cover: String? = null,
    )

    @Serializable
    data class MediaItem(
        /** 稿件 aid。 */
        val id: Long,
        /** 非视频/已失效条目可能为空。 */
        val bvid: String? = null,
        val title: String? = null,
        val cover: String? = null,
        /** 时长（秒）。 */
        val duration: Int? = null,
        /** 条目类型：2=视频。 */
        val type: Int? = null,
        val pubtime: Long? = null,
        @SerialName("cnt_info")
        val cntInfo: CntInfo? = null,
    ) {
        @Serializable
        data class CntInfo(
            val play: Long? = null,
            val danmaku: Int? = null,
        )
    }
}
