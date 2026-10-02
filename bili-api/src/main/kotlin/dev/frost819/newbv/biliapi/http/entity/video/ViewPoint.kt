package dev.frost819.newbv.biliapi.http.entity.video

import kotlinx.serialization.Serializable

/**
 * 视频章节（看点）。
 *
 * 对应 playerinfo 接口（GET /x/player/wbi/v2）响应中的 view_points 数组项，
 * 由 UP 主在创作中心手动添加，按分 P（cid）独立。
 *
 * @param type 章节类型（实测均为 2，无需区分）
 * @param from 章节开始时间，单位秒（可能为小数）
 * @param to 章节结束时间，单位秒（可能为小数）
 * @param content 章节标题
 * @param imgUrl 章节封面 URL（http 协议，展示前需替换为 https）
 */
@Serializable
data class ViewPoint(
    val type: Int = 0,
    val from: Double = 0.0,
    val to: Double = 0.0,
    val content: String = "",
    val imgUrl: String = "",
)
