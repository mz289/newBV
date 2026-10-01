package dev.frost819.newbv.app.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 番剧页卡片缩放分档的单元测试。
 *
 * 屏宽按密度覆盖后的 dp 计：720p=640dp、1080p=960dp、2K=1280dp、4K=1920dp。
 * 每行可见卡数按内容区宽（屏宽 - 48dp 页边距）估算，行间距 16dp。
 */
class AnimeCardScaleTest {
    private fun cardsPerRow(
        screenWidthDp: Int,
        cardWidth: Dp,
    ): Int = ((screenWidthDp.dp - 48.dp).value / (cardWidth + 16.dp).value).toInt()

    @Test
    fun scale_buckets_byScreenWidth() {
        assertThat(animeCardScale(640.dp)).isEqualTo(0.85f)
        assertThat(animeCardScale(960.dp)).isEqualTo(1f)
        assertThat(animeCardScale(1280.dp)).isEqualTo(1.15f)
        assertThat(animeCardScale(1920.dp)).isEqualTo(1.3f)
    }

    @Test
    fun scale_monotonicWithScreenWidth() {
        assertThat(animeCardScale(640.dp)).isLessThan(animeCardScale(960.dp))
        assertThat(animeCardScale(960.dp)).isLessThan(animeCardScale(1280.dp))
        assertThat(animeCardScale(1280.dp)).isLessThan(animeCardScale(1920.dp))
    }

    @Test
    fun landscapeCard_visibleCount_perRow() {
        val width = { screenWidthDp: Int -> 200.dp * animeCardScale(screenWidthDp.dp) }
        assertThat(cardsPerRow(640, width(640))).isAtLeast(3)
        assertThat(cardsPerRow(960, width(960))).isAtLeast(4)
        assertThat(cardsPerRow(1280, width(1280))).isAtLeast(4)
        assertThat(cardsPerRow(1920, width(1920))).isAtLeast(6)
    }

    @Test
    fun portraitCard_visibleCount_perRow() {
        val width = { screenWidthDp: Int -> 150.dp * animeCardScale(screenWidthDp.dp) }
        assertThat(cardsPerRow(640, width(640))).isAtLeast(4)
        assertThat(cardsPerRow(960, width(960))).isAtLeast(5)
        assertThat(cardsPerRow(1280, width(1280))).isAtLeast(6)
        assertThat(cardsPerRow(1920, width(1920))).isAtLeast(8)
    }

    @Test
    fun timelineCard_visibleCount_perRow() {
        val width = { screenWidthDp: Int -> 260.dp * animeCardScale(screenWidthDp.dp) }
        assertThat(cardsPerRow(960, width(960))).isAtLeast(3)
        assertThat(cardsPerRow(1920, width(1920))).isAtLeast(5)
    }
}
