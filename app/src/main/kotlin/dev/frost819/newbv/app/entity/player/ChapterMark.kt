package dev.frost819.newbv.app.entity.player

/**
 * 章节标记（播放器内使用的 view_points 看点，时间基为毫秒）。
 *
 * @property startMs 章节开始时间（毫秒）
 * @property endMs 章节结束时间（毫秒）
 * @property title 章节标题
 */
data class ChapterMark(
    val startMs: Long,
    val endMs: Long,
    val title: String,
) {
    /** 播放位置是否落在本章节内（左闭右开）。 */
    operator fun contains(positionMs: Long): Boolean = positionMs in startMs..<endMs
}
