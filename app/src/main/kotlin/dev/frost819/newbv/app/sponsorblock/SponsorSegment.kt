package dev.frost819.newbv.app.sponsorblock

/**
 * SponsorBlock 社区片段。
 *
 * @property id 片段 UUID（服务端去重标识）
 * @property category 分类名（见 [dev.frost819.newbv.data.datastore.SponsorBlockDefaults.supportedCategories]）
 * @property startMs 片段开始时间（毫秒）
 * @property endMs 片段结束时间（毫秒）
 */
data class SponsorSegment(
    val id: String,
    val category: String,
    val startMs: Long,
    val endMs: Long,
) {
    /** 播放位置是否落在片段内（含边界）。 */
    fun contains(positionMs: Long): Boolean = positionMs in startMs..endMs
}

/**
 * 等待用户确认的提示跳过片段。
 *
 * 播放位置进入策略为"手动确认"的片段时挂起，按确认键跳过、
 * 返回键忽略；播放位置离开片段窗口后自动清除。
 */
data class PendingSponsorSkip(
    val segmentId: String,
    val startPositionMs: Long,
    val targetPositionMs: Long,
    val category: String,
)
