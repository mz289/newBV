package dev.frost819.newbv.app.entity.player

/**
 * 进度条片段色块（如 SponsorBlock 片段标记）。
 *
 * @property startMs 开始时间（毫秒）
 * @property endMs 结束时间（毫秒）
 * @property colorArgb 色块颜色（ARGB Long）
 */
data class ProgressSegmentMark(
    val startMs: Long,
    val endMs: Long,
    val colorArgb: Long,
)
