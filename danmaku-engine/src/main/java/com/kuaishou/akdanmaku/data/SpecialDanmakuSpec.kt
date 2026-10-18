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

package com.kuaishou.akdanmaku.data

import kotlin.math.max

/**
 * mode 7 高级弹幕（特殊弹幕）的规格。
 *
 * 语义基准：B 站官方客户端的弹幕引擎（移动端现代版本的高级弹幕只有 mode 7 一种），
 * 开源弹幕库 DanmakuFlameMaster 与官方网页播放器引擎作为历史交叉参考。
 *
 * 坐标已按官方归一化规则处理：
 * - 载荷值 ≤ 1（含 0 / 1）视为比例；> 1 视为 861×572 基准画布像素，除以基准；
 * - 负值截断到 0；
 * - x 为舞台宽度比例（自左缘），y 为**文本顶边**自舞台上缘的高度比例
 *   （官方舞台坐标系原点在左下、锚点为精灵左上角，换算后与 DFM / 网页引擎一致）。
 * - 有路径（载荷第 15 项）时路径点覆盖起终点，且每个点同样按上面规则归一化。
 *
 * @property text 弹幕文本（载荷第 5 项，`\n` 为换行）
 * @property keyPoints 关键帧折线，元素为 `[x 比例, 文本顶边 y 比例]`；
 *   无路径时为 `[起点, 终点]` 两个点，有路径时为路径点序列（≥ 1 个点）
 * @property beginAlpha endAlpha 起止不透明度
 * @property displayDurationMs 载荷第 4 项（秒）算出的显示时长
 * @property animDurationMs 载荷第 10 项（毫秒）运动时长
 * @property animDelayMs 载荷第 11 项（毫秒）运动延迟
 * @property rotateZ 载荷第 6 项 Z 轴角度（度，顺时针为正）
 * @property rotateY 载荷第 7 项 Y 轴角度（度）；官方引擎只解析不使用，本实现同样忽略
 * @property stroked 载荷第 12 项判定是否描边（布尔原样 / 数字 > 0 / 字符串仅 "true"）
 * @property easeInOut 载荷第 14 项判定；true = 平滑起停缓动，false = 匀速
 */
class SpecialDanmakuSpec(
    val text: String,
    val keyPoints: List<FloatArray>,
    val beginAlpha: Float,
    val endAlpha: Float,
    val displayDurationMs: Long,
    val animDurationMs: Long,
    val animDelayMs: Long,
    val rotateZ: Float,
    val rotateY: Float,
    val stroked: Boolean,
    val easeInOut: Boolean,
) {
    /**
     * 弹幕在屏总时长：官方动作序列总长 = `max(displayDuration, animDelay + animDuration)`
     * （运动未走完时动作不会结束，视图要到运动结束才移除）。
     */
    val visibleDurationMs: Long
        get() = max(displayDurationMs, animDelayMs + animDurationMs)

    companion object {
        /**
         * 官方高级弹幕舞台基准（861 × 572）。
         * 载荷中 > 1 的像素坐标按此基准换算为比例，从而与显示尺寸无关。
         */
        const val STAGE_WIDTH = 861f
        const val STAGE_HEIGHT = 572f
    }
}
