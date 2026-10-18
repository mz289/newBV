/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Kwai, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom they are furnished to do so,
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

package com.kuaishou.akdanmaku.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.DisplayScaleFactor
import com.kuaishou.akdanmaku.utils.Size
import kotlin.math.ceil

/**
 * mode 7 高级弹幕渲染器。
 *
 * 对齐官方客户端弹幕引擎的光栅化语义：
 * - 字号：`fontSize_px = 载荷字号 × 可绘制区缩放因子 × 用户缩放`（[DisplayScaleFactor]）；
 *   不做 12-25 截断（ASCII 艺术弹幕常用 5-10 的小字号）。
 * - 字体：固定系统粗体并叠加仿粗（fakeBold），官方弹幕画布恒为系统 Bold 加仿粗，
 *   与用户「粗体」开关无关；载荷第 13 项字体名官方不使用。
 * - 精灵盒（官方光栅化器的测量规则）：宽 = 最长行 advance + 描边宽，
 *   高 = 行数 × 行距 + 描边宽；行距 = 字体度量高（`descent − ascent`，官方由原生画布
 *   按字体度量逐行布局，与 DFM 的多行约定一致）；基线 = 盒顶 + `sw/2 − ascent`，
 *   文本左缘 = `sw/2`（描边居中于字形轮廓，盒四边各留 `sw/2` 正好容纳描边）。
 * - 描边：宽度 = `2 × factor × 用户缩放`（官方基准描边宽度 2）；
 *   `stroked`（载荷第 12 项，宽松布尔判定）为真时按官方描边色规则：
 *   填充任一通道 > 0.3 用黑边，否则白边；为假时不描边，
 *   但盒尺寸仍保留描边占位。绘制顺序 stroke → fill（两遍，同官方）。
 */
open class SpecialDanmakuRenderer : DanmakuRenderer {
    private class ThreadPaints {
        val textPaint =
            TextPaint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        val strokePaint =
            TextPaint().apply {
                textSize = textPaint.textSize
                color = Color.BLACK
                strokeWidth = STROKE_WIDTH
                // 官方先描边（仅轮廓）再填充，两遍绘制
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

        /** 当前生效的描边宽度（随可绘制区缩放因子缩放），用于盒尺寸与基线偏移。 */
        var strokeWidth: Float = STROKE_WIDTH
    }

    // 缓存线程与主线程会并发驱动同一渲染器（DFM 以 PAINT/PAINT_DUPLICATE 双份规避竞争）
    private val threadPaints =
        object : ThreadLocal<ThreadPaints>() {
            override fun initialValue(): ThreadPaints = ThreadPaints()
        }

    private fun refreshPaints(
        item: DanmakuItem,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ): ThreadPaints {
        val paints = threadPaints.get() ?: ThreadPaints().also { threadPaints.set(it) }
        val data = item.data
        // 官方字号 = 载荷字号 × 可绘制区缩放因子 × 用户缩放；
        // 描边宽度同倍率缩放
        val factor =
            DisplayScaleFactor.suggestedFactor(
                displayer.width.toFloat(),
                displayer.height.toFloat(),
                displayer.density,
                DisplayScaleFactor.isTablet(displayer.width.toFloat(), displayer.height.toFloat(), displayer.density),
            )
        val textSize = (data.textSize * factor * config.textSizeScale).coerceAtLeast(1f)
        val strokeWidth = STROKE_WIDTH * factor * config.textSizeScale
        // 官方弹幕字体恒为系统粗体并叠加仿粗，载荷第 13 项字体名不使用，
        // 因此这里固定粗体 + fakeBold
        paints.textPaint.textSize = textSize
        paints.textPaint.color = data.textColor or Color.argb(255, 0, 0, 0)
        paints.textPaint.typeface = OFFICIAL_TYPEFACE
        paints.textPaint.isFakeBoldText = true
        paints.strokePaint.textSize = textSize
        paints.strokePaint.typeface = OFFICIAL_TYPEFACE
        paints.strokePaint.isFakeBoldText = true
        paints.strokePaint.strokeWidth = strokeWidth
        paints.strokePaint.color = strokeColorFor(paints.textPaint.color)
        paints.strokeWidth = strokeWidth
        return paints
    }

    /** 官方描边色规则：填充色任一通道 > 0.3 → 黑边，否则白边。 */
    private fun strokeColorFor(fillColor: Int): Int {
        val red = Color.red(fillColor) / 255f
        val green = Color.green(fillColor) / 255f
        val blue = Color.blue(fillColor) / 255f
        return if (red > STROKE_CHANNEL_THRESHOLD || green > STROKE_CHANNEL_THRESHOLD || blue > STROKE_CHANNEL_THRESHOLD) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    final override fun updatePaint(
        item: DanmakuItem,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ) {
        refreshPaints(item, displayer, config)
    }

    override fun measure(
        item: DanmakuItem,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ): Size {
        val paints = refreshPaints(item, displayer, config)
        val lines = item.data.content.split('\n')
        var maxWidth = 0f
        lines.forEach { line -> maxWidth = maxOf(maxWidth, paints.textPaint.measureText(line)) }
        // 官方盒尺寸：宽 = 最长行 advance + strokeWidth，高 = 行数 × 行距 + strokeWidth
        //（盒四边各留 strokeWidth/2 容纳居中描边）
        return Size(
            ceil(maxWidth + paints.strokeWidth).toInt(),
            ceil(lineAdvance(paints.textPaint) * lines.size + paints.strokeWidth).toInt(),
        )
    }

    override fun draw(
        item: DanmakuItem,
        canvas: Canvas,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ) {
        val paints = refreshPaints(item, displayer, config)
        val advance = lineAdvance(paints.textPaint)
        // 官方绘制：笔位 x = strokeWidth/2，首行基线 = -ascent + strokeWidth/2，
        // 之后每行推进一个行距（descent − ascent）
        val baseline = -paints.textPaint.fontMetrics.ascent + paints.strokeWidth / 2f
        val x = paints.strokeWidth / 2f
        val stroked = item.data.special?.stroked != false
        for ((index, line) in item.data.content.split('\n').withIndex()) {
            val y = baseline + advance * index
            if (stroked) {
                canvas.drawText(line, x, y, paints.strokePaint)
            }
            canvas.drawText(line, x, y, paints.textPaint)
        }
    }

    /**
     * 行距 = 字体度量高（`descent − ascent`）。
     *
     * 官方多行文本由 cron 原生画布按字体度量逐行布局（`measureText().height` 即度量高，
     * 盒高 = 行数 × 度量高 + 描边宽只加一次），与 DFM `SimpleTextCacheStuffer` 的
     * `lines.length × (descent − ascent + leading)` 同款约定。
     */
    private fun lineAdvance(textPaint: TextPaint): Float {
        val metrics = textPaint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    companion object {
        /** 官方基准描边宽度（2），实际值再乘缩放因子。 */
        private const val STROKE_WIDTH = 2f

        /** 官方描边色阈值：填充通道归一化值大于 0.3 用黑边。 */
        private const val STROKE_CHANNEL_THRESHOLD = 0.3f

        /** 官方弹幕字体：默认字族的 Bold 样式。 */
        private val OFFICIAL_TYPEFACE: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}
