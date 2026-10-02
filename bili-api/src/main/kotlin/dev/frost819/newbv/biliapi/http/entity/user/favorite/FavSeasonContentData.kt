package dev.frost819.newbv.biliapi.http.entity.user.favorite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 订阅的合集内容
 *
 * @param info 合集信息
 * @param medias 合集内容视频列表
 * @param hasMore 是否还有更多数据
 */
@Serializable
data class FavSeasonContentData(
    val info: CollectedFavoriteFolder? = null,
    val medias: List<FavSeasonMedia> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

/**
 * 合集内容视频
 *
 * @param id 视频稿件avid
 * @param type 内容类型 2：视频稿件
 * @param title 标题
 * @param cover 封面url
 * @param intro 简介
 * @param page 视频分P数
 * @param duration 视频时长
 * @param upper UP主信息
 * @param link 跳转uri
 * @param pubtime 发布时间 时间戳
 * @param bvid 视频稿件bvid
 */
@Serializable
data class FavSeasonMedia(
    val id: Long,
    val type: Int = 2,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val page: Int = 1,
    val duration: Int = 0,
    val upper: Upper = Upper(mid = 0, name = "", face = ""),
    val link: String = "",
    val pubtime: Long = 0,
    val bvid: String = "",
)
