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

package com.kuaishou.akdanmaku.ecs.component.action

import com.kuaishou.akdanmaku.data.SpecialDanmakuSpec

/**
 * mode 7 高级弹幕动画：按帧推进位置 / 旋转 / 透明度。
 *
 * 在弹幕入场（实体创建）时由规格烘培为绝对坐标，显示尺寸变化后由
 * DataSystem 重新烘培（见 [com.kuaishou.akdanmaku.data.DanmakuItem.rebakeSpecialActions]）。
 *
 * 语义与官方客户端弹幕引擎的高级弹幕动画对齐：
 * - 坐标：规格里已是「舞台比例」（x 为宽度比例，y 为文本顶边高度比例），
 *   直接乘显示宽高即得像素位置；锚点为文本左上角（与官方一致）；
 * - 运动：关键帧折线按**段数等分**总运动时长（`segDur = animDuration / (n - 1)`），
 *   段内插值；`easeInOut` 为真时用平滑起停曲线，否则匀速；
 * - 延迟：`animDelay` 内停在首帧；
 * - 透明度：延迟内恒为起始值，之后在 `animDuration` 内线性过渡到终止值；
 * - 旋转：`rotateZ`（度，顺时针为正）在入场瞬间生效，绕文本左上角（Action 本地原点）；
 *   官方解析但不使用 `rotateY`，此处同样不参与渲染。
 *
 * @param spec 弹幕规格（坐标已归一化）
 * @param displayWidth 显示宽度（像素）
 * @param displayHeight 显示高度（像素）
 */
class SpecialDanmakuAction(
    private val spec: SpecialDanmakuSpec,
    displayWidth: Float,
    displayHeight: Float,
) : Action() {
    /** 关键帧（像素）：`[x, 文本顶边 y]`。 */
    private val points: List<FloatArray> =
        spec.keyPoints.map { point ->
            floatArrayOf(point[0] * displayWidth, point[1] * displayHeight)
        }

    /** 每段时长（官方 `animDuration / (keyCoords.length - 1)`）。 */
    private val segmentDurationMs: Float =
        if (points.size > 1) {
            spec.animDurationMs.toFloat() / (points.size - 1)
        } else {
            0f
        }

    override fun act(timeMills: Long): Boolean {
        val target = target ?: return true
        val time = timeMills.coerceAtLeast(0L)

        target.alpha = alphaAt(time).coerceIn(0f, 1f)
        val position = positionAt(time)
        target.position.set(position[0], position[1])
        // 官方 rotateToAngle(-deg)：正向角度在画面上顺时针，等价于 Android 画布正角度旋转
        target.rotation = spec.rotateZ
        return time >= spec.animDelayMs + spec.animDurationMs
    }

    /**
     * 官方时间轴：`delay` 内停在 `fromAlpha`，之后在 `animDuration` 内线性过渡到 `toAlpha`
     * （运动时长为 0 时立即取终止值）。
     */
    private fun alphaAt(time: Long): Float {
        if (time <= spec.animDelayMs) return spec.beginAlpha
        if (spec.animDurationMs <= 0L) return spec.endAlpha
        val progress = ((time - spec.animDelayMs).toFloat() / spec.animDurationMs).coerceIn(0f, 1f)
        return spec.beginAlpha + (spec.endAlpha - spec.beginAlpha) * progress
    }

    /** 关键帧折线插值：等分时长、段内缓动、越界停在首末帧。 */
    private fun positionAt(time: Long): FloatArray {
        val first = points.first()
        if (time <= spec.animDelayMs) return first
        val last = points.last()
        if (points.size <= 1 || spec.animDurationMs <= 0L) return last
        val elapsed = time - spec.animDelayMs
        if (elapsed >= spec.animDurationMs) return last

        val index = (elapsed / segmentDurationMs).toInt().coerceIn(0, points.size - 2)
        val from = points[index]
        val to = points[index + 1]
        var progress = (elapsed - index * segmentDurationMs) / segmentDurationMs
        if (spec.easeInOut) progress = easeInOut(progress)
        return floatArrayOf(
            from[0] + (to[0] - from[0]) * progress,
            from[1] + (to[1] - from[1]) * progress,
        )
    }

    /**
     * 官方平滑起停缓动（EaseInOut）的近似：三次平滑曲线 `t²(3 - 2t)`，
     * 与本地参考实现的输出一致。
     */
    private fun easeInOut(t: Float): Float = t * t * (3f - 2f * t)
}
