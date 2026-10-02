package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.app.entity.player.ChapterMark
import dev.frost819.newbv.app.entity.player.ProgressSegmentMark
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 视频进度条。
 *
 * 使用 Canvas 绘制多层进度线：背景轨道、缓冲进度、播放进度、片段色块。
 * 三段轨道用「色相 + 明度」双重区分，避免混在一起：
 * 已播放用品牌色（primary），已缓冲用较亮的中性灰，未缓冲用很暗的中性灰。
 * 播放器固定运行在深色主题下，均能保证黑底上的可见性。
 * 片段色块（如 SponsorBlock 片段）绘制在播放进度之上，用分类原色标注可跳过区间。
 * 章节刻度（view_points 看点起点）绘制在最上层，用白色细竖线标注章节边界。
 * 支持两种显示模式：
 * - **常显模式**（[isPersistentSeek] = true）：2dp 细线，不显示缓冲进度，
 *   用于播放器底部始终可见的进度条。
 * - **交互模式**（[isPersistentSeek] = false）：8dp 粗线，显示缓冲进度，
 *   用于控制器信息栏中的可交互 seek bar。
 *
 * @param modifier 修饰符
 * @param duration 视频总时长（毫秒）
 * @param position 当前播放位置（毫秒）
 * @param bufferedPercentage 缓冲百分比（0-100）
 * @param isPersistentSeek 是否为常显模式
 * @param segmentMarks 片段色块（起止毫秒 + ARGB 颜色），默认无
 * @param chapterMarks 章节标记（仅使用起点画刻度），默认无
 */
@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean,
    segmentMarks: List<ProgressSegmentMark> = emptyList(),
    chapterMarks: List<ChapterMark> = emptyList(),
) {
    val trackWidthDp = if (isPersistentSeek) 2.dp else 8.dp
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    // 换算为比例区间，duration 就绪前或区间无效时过滤掉
    val segmentRanges =
        remember(duration, segmentMarks) {
            if (duration <= 0L) {
                emptyList()
            } else {
                segmentMarks.mapNotNull { mark ->
                    val start = mark.startMs.coerceIn(0L, duration) / duration.toFloat()
                    val end = mark.endMs.coerceIn(0L, duration) / duration.toFloat()
                    if (end <= start) null else SegmentRange(start, end, mark.colorArgb)
                }
            }
        }

    // 章节起点换算为比例位置，duration 就绪前过滤掉
    val chapterStartFractions =
        remember(duration, chapterMarks) {
            if (duration <= 0L) {
                emptyList()
            } else {
                chapterMarks.map { it.startMs.coerceIn(0L, duration) / duration.toFloat() }
            }
        }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(trackWidthDp)
                .clip(RoundedCornerShape(50)),
    ) {
        val trackWidthPx = trackWidthDp.toPx()

        // 背景轨道
        drawLine(
            color = inactiveTrackColor,
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round,
        )

        // 缓冲进度（仅交互模式显示）
        if (!isPersistentSeek && bufferedPercentage > 0) {
            drawLine(
                color = bufferedTrackColor,
                start = Offset(trackWidthPx / 2, center.y),
                end = Offset(size.width * bufferedPercentage / 100, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round,
            )
        }

        // 播放进度
        if (duration > 0) {
            val progressRatio = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            drawLine(
                color = activeTrackColor,
                start = Offset(trackWidthPx / 2, center.y),
                end = Offset(size.width * progressRatio, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round,
            )
        }

        // 片段色块（绘制在播放进度之上；平头端点避免色块区间外溢）
        segmentRanges.forEach { range ->
            drawLine(
                color = Color(range.colorArgb),
                start = Offset(size.width * range.startFraction, center.y),
                end = Offset(size.width * range.endFraction, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Butt,
            )
        }

        // 章节刻度（绘制在最上层；白色细竖线标注章节起点）
        chapterStartFractions.forEach { fraction ->
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Butt,
            )
        }
    }
}

/** 色块的比例区间（换算结果，避免每帧重复计算）。 */
private data class SegmentRange(
    val startFraction: Float,
    val endFraction: Float,
    val colorArgb: Long,
)

// region Previews

@Preview(showBackground = true)
@Composable
private fun VideoProgressSeekPersistentPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoProgressSeekNormalPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = false,
            chapterMarks =
                listOf(
                    ChapterMark(startMs = 0L, endMs = 30_000L, title = "引言"),
                    ChapterMark(startMs = 30_000L, endMs = 70_000L, title = "第一章"),
                    ChapterMark(startMs = 70_000L, endMs = 100_000L, title = "第二章"),
                ),
        )
    }
}

// endregion
