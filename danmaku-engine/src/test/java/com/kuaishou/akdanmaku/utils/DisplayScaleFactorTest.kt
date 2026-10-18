package com.kuaishou.akdanmaku.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [DisplayScaleFactor] 对照官方缩放因子的档位表。
 *
 * 期望值直接取自官方公式（档位表见 [DisplayScaleFactor] 文档）：
 * 逻辑对角线 r 落档 → `factor = 档位系数 × density`。
 */
class DisplayScaleFactorTest {
    private fun factor(
        w: Float,
        h: Float,
        density: Float,
        isTablet: Boolean = false,
    ) = DisplayScaleFactor.suggestedFactor(w, h, density, isTablet)

    @Test
    fun `thresholds match the official table`() {
        // 横屏 16:9，density=2：r = hypot(w, h)/2
        // 960×540 → r=550.4 → [520,700) → 0.72
        assertThat(factor(960f, 540f, 2f)).isWithin(1e-4f).of(1.44f)
        // 1216×912 → r=760 → [700,1000) → 0.8
        assertThat(factor(1216f, 912f, 2f)).isWithin(1e-4f).of(1.6f)
        // 1600×720 → r=878 → [700,1000) → 0.8
        assertThat(factor(1600f, 720f, 2f)).isWithin(1e-4f).of(1.6f)
        // 2340×1080, density=3 → r=859 → 0.8 × 3
        assertThat(factor(2340f, 1080f, 3f)).isWithin(1e-4f).of(2.4f)
        // 2560×1440, density=2 → r=1468 → [1000,1500) → 0.92
        assertThat(factor(2560f, 1440f, 2f)).isWithin(1e-4f).of(1.84f)
        // 3840×2160, density=1.5 → r=2944 → ≥1500 且非车载 → 1.04
        assertThat(factor(3840f, 2160f, 1.5f)).isWithin(1e-4f).of(1.56f)
    }

    @Test
    fun `small display uses the smallest brackets`() {
        // 480×270, density=2 → r=275 → [<300) → 0.32
        assertThat(factor(480f, 270f, 2f)).isWithin(1e-4f).of(0.64f)
        // 640×360, density=2 → r=367 → [300,400) → 0.48
        assertThat(factor(640f, 360f, 2f)).isWithin(1e-4f).of(0.96f)
    }

    @Test
    fun `portrait non tablet normalizes by sixteen by nine`() {
        // 1600×2560（竖屏手机）, density=2：
        // 原始对角线 r=1512 → 走竖屏分支：hypot(1600, 900)/2 = 918 → [700,1000) → 0.8×2
        assertThat(factor(1600f, 2560f, 2f, isTablet = false)).isWithin(1e-4f).of(1.6f)
        // 同一尺寸若是平板：用原始对角线 r=1512 → ≥1500 → 1.04×2
        assertThat(factor(1600f, 2560f, 2f, isTablet = true)).isWithin(1e-4f).of(2.08f)
    }

    @Test
    fun `degenerate size falls back to one`() {
        assertThat(factor(0f, 540f, 2f)).isEqualTo(1f)
        assertThat(factor(960f, 0f, 2f)).isEqualTo(1f)
    }

    @Test
    fun `tablet detection uses shortest side in dp`() {
        // 2560×1440 @2.0 → 最短边 720dp → 平板
        assertThat(DisplayScaleFactor.isTablet(2560f, 1440f, 2f)).isTrue()
        // 2340×1080 @3.0 → 最短边 360dp → 手机
        assertThat(DisplayScaleFactor.isTablet(2340f, 1080f, 3f)).isFalse()
    }
}
