package dev.frost819.newbv.biliapi.http.entity.user.favorite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 当前用户订阅（收藏）的收藏夹/合集列表
 *
 * @param count 订阅总数
 * @param list 订阅列表
 * @param hasMore 是否还有更多数据
 */
@Serializable
data class CollectedFavoriteFoldersData(
    val count: Int = 0,
    val list: List<CollectedFavoriteFolder> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

/**
 * 订阅的收藏夹/合集条目
 *
 * @param id 收藏夹/合集id 收藏夹内容用其查 resource/list，合集内容用其查 space/fav/season/list
 * @param fid 收藏夹原始id
 * @param mid 创建者mid
 * @param title 标题
 * @param cover 封面url
 * @param intro 简介
 * @param upper 创建者信息
 * @param mediaCount 内容数量
 * @param type 类型 11：收藏夹 21：视频合集
 * @param ctime 创建时间 时间戳
 * @param mtime 收藏（订阅）时间 时间戳
 */
@Serializable
data class CollectedFavoriteFolder(
    val id: Long,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val upper: Upper = Upper(mid = 0, name = "", face = ""),
    @SerialName("media_count")
    val mediaCount: Int = 0,
    val type: Int = 11,
    val ctime: Long = 0,
    val mtime: Long = 0,
)
