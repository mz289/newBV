package dev.frost819.newbv.app.sponsorblock

import dev.frost819.newbv.app.entity.player.ProgressSegmentMark
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.data.datastore.SkipPolicy

/**
 * SponsorBlock 分类的展示样式（名称与进度条色块颜色）。
 *
 * 颜色沿用 SponsorBlock 社区标准配色（对齐 BilibiliSponsorBlock 扩展）。
 */
object SponsorBlockCategoryStyle {
    private val categoryColors: Map<String, Long> =
        linkedMapOf(
            "sponsor" to 0xFF43D676,
            "selfpromo" to 0xFFFFE24D,
            "exclusive_access" to 0xFF1FD2A4,
            "interaction" to 0xFFD94BFF,
            "poi_highlight" to 0xFFFF4AA5,
            "intro" to 0xFF00C8FF,
            "outro" to 0xFF0287E0,
            "preview" to 0xFF33A3FF,
            "filler" to 0xFF8F96A3,
            "music_offtopic" to 0xFFFFAB1F,
        )

    private val categoryNames: Map<String, String> =
        linkedMapOf(
            "sponsor" to "赞助/恰饭",
            "selfpromo" to "无偿/自我推广",
            "exclusive_access" to "独家/抢先体验",
            "interaction" to "三连/互动提醒",
            "poi_highlight" to "精彩时刻/重点",
            "intro" to "片头/过场",
            "outro" to "片尾/鸣谢",
            "preview" to "预览/剧透",
            "filler" to "填充/口水内容",
            "music_offtopic" to "音乐(非音乐部分)",
        )

    /** 分类色块颜色（ARGB），未知分类返回 null。 */
    fun colorArgb(category: String): Long? = categoryColors[category]

    /** 分类展示名，未知分类回退为"片段"。 */
    fun displayName(category: String): String = categoryNames[category] ?: "片段"
}

/**
 * 构建进度条色块：仅保留策略非"不跳过"且区间有效的片段。
 */
fun buildSponsorBlockProgressMarks(segments: List<SponsorSegment>): List<ProgressSegmentMark> =
    segments.mapNotNull { segment ->
        if (Prefs.sponsorBlockPolicy(segment.category) == SkipPolicy.Disabled) return@mapNotNull null
        val colorArgb = SponsorBlockCategoryStyle.colorArgb(segment.category) ?: return@mapNotNull null
        val startMs = segment.startMs.coerceAtLeast(0L)
        val endMs = segment.endMs.coerceAtLeast(0L)
        if (endMs <= startMs) return@mapNotNull null
        ProgressSegmentMark(
            startMs = startMs,
            endMs = endMs,
            colorArgb = colorArgb,
        )
    }
