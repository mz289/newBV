package dev.frost819.newbv.biliapi.http.entity.live

/**
 * 分区直播分页列表结果。
 *
 * 由 `GET /room/v1/Area/getRoomList` 返回的数组封装而成。
 * 该端点返回 `data` 直接为数组，不包含 `has_more` 字段，
 * 因此通过返回数量是否达到 `pageSize` 来判断是否还有更多。
 */
data class AreaLiveListResult(
    val list: List<LiveRoomItem>,
    val hasMore: Boolean,
)
