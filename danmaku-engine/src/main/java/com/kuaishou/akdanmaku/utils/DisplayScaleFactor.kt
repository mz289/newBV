/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Kwai, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.kuaishou.akdanmaku.utils

import kotlin.math.hypot
import kotlin.math.min

/**
 * 官方弹幕字号/描边缩放因子。
 *
 * 官方客户端按**可绘制区尺寸**计算缩放因子并下发给光栅化器，字号与描边宽度都按
 * `载荷值 × factor × 用户缩放` 换算成设备像素：
 * `fontSize_px = 载荷字号 × factor × 用户缩放`、`strokeWidth_px = 2 × factor × 用户缩放`。
 *
 * 公式：取可绘制区对角线的逻辑像素长度（像素对角线 ÷ 设备像素比；非平板竖屏按 16:9
 * 归一高度后重算对角线），按分档取系数再乘设备像素比：
 *
 * | 逻辑对角线 (dp) | <300 | <400 | <520 | <700 | <1000 | <1500 | ≥1500 |
 * | --- | --- | --- | --- | --- | --- | --- | --- |
 * | 系数 | 0.32 | 0.48 | 0.64 | 0.72 | 0.80 | 0.92 | 1.04 |
 *
 * 效果：小屏设备字号相对更大、大屏相对更小，与官方客户端观感一致
 * （1080p 手机横屏 factor ≈ 2.4，平板 ≈ 1.84；旧实现 `density − 0.6` 在平板上偏小约 24%）。
 */
object DisplayScaleFactor {
    /**
     * @param width 可绘制区宽（设备像素）
     * @param height 可绘制区高（设备像素）
     * @param density 设备像素比
     * @param isTablet 平板（官方 `CF.isPad()`）；仅影响竖屏分支
     * @return 字号 / 描边缩放因子（已含 density）
     */
    fun suggestedFactor(
        width: Float,
        height: Float,
        density: Float,
        isTablet: Boolean,
    ): Float {
        if (width <= 0f || height <= 0f) return 1f
        val t = if (density > 0f) density else 1f
        var r = hypot(width, height) / t
        if (width < height && !isTablet) {
            // 竖屏：按 16:9 高度归一后的对角线
            val n = 9f * width / 16f
            r = hypot(width, n) / t
        }
        return when {
            r < 300f -> 0.32f * t
            r < 400f -> 0.48f * t
            r < 520f -> 0.64f * t
            r < 700f -> 0.72f * t
            r < 1000f -> 0.8f * t
            r < 1500f -> 0.92f * t
            // 车载 / WASM 另有 1.3~1.6 档；普通 Android 为 1.04
            else -> 1.04f * t
        }
    }

    /** 官方 `CF.isPad()` 的等价判据：最小边（dp）不小于 600。 */
    fun isTablet(
        width: Float,
        height: Float,
        density: Float,
    ): Boolean {
        if (density <= 0f) return false
        return min(width, height) / density >= TABLET_MIN_SHORTEST_DP
    }

    private const val TABLET_MIN_SHORTEST_DP = 600f
}
