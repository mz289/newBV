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
import com.kuaishou.akdanmaku.data.DanmakuItemData
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
                typeface = OFFICIAL_TYPEFACE
                isFakeBoldText = true
            }
        val strokePaint =
            TextPaint().apply {
                textSize = textPaint.textSize
                color = Color.BLACK
                strokeWidth = STROKE_WIDTH
                // 官方先描边（仅轮廓）再填充，两遍绘制
                style = Paint.Style.STROKE
                isAntiAlias = true
                typeface = OFFICIAL_TYPEFACE
                isFakeBoldText = true
            }

        /** 官方 self-send 底框：黑 30% 填充（DestinationOver 垫在文字下）。 */
        val selfSendFillPaint =
            Paint().apply {
                style = Paint.Style.FILL
                color = SELF_SEND_BOX_COLOR
            }

        /** 官方 self-send 底框的白色描边（宽度同描边）。 */
        val selfSendBorderPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                isAntiAlias = true
            }

        /** 当前生效的描边宽度（随可绘制区缩放因子缩放），用于盒尺寸与基线偏移。 */
        var strokeWidth: Float = STROKE_WIDTH

        /** 最近一次刷新算得的官方缩放因子（self-send 额外内边距用）。 */
        var suggestedFactor: Float = 1f

        var displayWidth = -1
        var displayHeight = -1
        var density = Float.NaN
        var textSize = Float.NaN
        val fontMetrics = Paint.FontMetrics()
        var lineAdvance = 0f
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
        if (paints.displayWidth != displayer.width ||
            paints.displayHeight != displayer.height ||
            paints.density != displayer.density
        ) {
            paints.displayWidth = displayer.width
            paints.displayHeight = displayer.height
            paints.density = displayer.density
            paints.suggestedFactor =
                DisplayScaleFactor.suggestedFactor(
                    displayer.width.toFloat(),
                    displayer.height.toFloat(),
                    displayer.density,
                    DisplayScaleFactor.isTablet(
                        displayer.width.toFloat(),
                        displayer.height.toFloat(),
                        displayer.density,
                    ),
                )
        }
        val factor = paints.suggestedFactor
        val textSize = (data.textSize * factor * config.textSizeScale).coerceAtLeast(1f)
        val strokeWidth = STROKE_WIDTH * factor * config.textSizeScale
        // 官方弹幕字体恒为系统粗体并叠加仿粗，载荷第 13 项字体名不使用，
        // 因此这里固定粗体 + fakeBold
        if (paints.textSize != textSize) {
            paints.textSize = textSize
            paints.textPaint.textSize = textSize
            paints.strokePaint.textSize = textSize
            paints.textPaint.getFontMetrics(paints.fontMetrics)
            paints.lineAdvance = paints.fontMetrics.descent - paints.fontMetrics.ascent
        }
        paints.textPaint.color = data.textColor or Color.argb(255, 0, 0, 0)
        paints.strokePaint.strokeWidth = strokeWidth
        paints.strokePaint.color = strokeColorFor(paints.textPaint.color)
        paints.selfSendBorderPaint.strokeWidth = strokeWidth
        paints.strokeWidth = strokeWidth
        return paints
    }

    /** 官方 self-send 弹幕的额外内边距：`SELF_SENT_PADDING(4) × factor × 缩放 + 0.5 × 描边宽`。 */
    private fun selfSendExtra(
        item: DanmakuItem,
        paints: ThreadPaints,
        scale: Float,
    ): Float {
        val isSelfSend = item.data.danmakuStyle and DanmakuItemData.DANMAKU_STYLE_SELF_SEND != 0
        if (!isSelfSend) return 0f
        return SELF_SEND_PADDING * paints.suggestedFactor * scale + 0.5f * paints.strokeWidth
    }

    /** 官方描边色规则：填充色任一通道 > 0.3 → 黑边，否则白边。 */
    private fun strokeColorFor(fillColor: Int): Int {
        val red = Color.red(fillColor) / 255f
        val green = Color.green(fillColor) / 255f
        val blue = Color.blue(fillColor) / 255f
        return if (red > STROKE_CHANNEL_THRESHOLD ||
            green > STROKE_CHANNEL_THRESHOLD ||
            blue > STROKE_CHANNEL_THRESHOLD
        ) {
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
        val content = item.data.content
        var maxWidth = 0f
        var lineCount = 0
        forEachLine(content) { start, end, index ->
            maxWidth = maxOf(maxWidth, paints.textPaint.measureText(content, start, end))
            lineCount = index + 1
        }
        val extra = selfSendExtra(item, paints, config.textSizeScale)
        // 官方盒尺寸：宽 = 最长行 advance + strokeWidth，高 = 行数 × 行距 + strokeWidth，
        // self-send 弹幕再各边加额外内边距（盒四边各留 strokeWidth/2 容纳居中描边）
        return Size(
            ceil(maxWidth + paints.strokeWidth + 2 * extra).toInt(),
            ceil(paints.lineAdvance * lineCount + paints.strokeWidth + 2 * extra).toInt(),
        )
    }

    override fun draw(
        item: DanmakuItem,
        canvas: Canvas,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ) {
        val paints = refreshPaints(item, displayer, config)
        val advance = paints.lineAdvance
        val extra = selfSendExtra(item, paints, config.textSizeScale)
        // 官方绘制：笔位 x = strokeWidth/2 + 额外内边距，首行基线 = -ascent + strokeWidth/2 + 额外内边距，
        // 之后每行推进一个行距（descent − ascent）
        val baseline = -paints.fontMetrics.ascent + paints.strokeWidth / 2f + extra
        val x = paints.strokeWidth / 2f + extra
        val stroked = item.data.special?.stroked != false
        val content = item.data.content
        var maxWidth = 0f
        var lineCount = 0
        forEachLine(content) { start, end, index ->
            val y = baseline + advance * index
            if (stroked) {
                canvas.drawText(content, start, end, x, y, paints.strokePaint)
            }
            canvas.drawText(content, start, end, x, y, paints.textPaint)
            // Only self-send items need box dimensions during drawing. Normal items were measured
            // by the cache worker already; reshaping every line here stalls dense fallback frames.
            if (extra > 0f) {
                maxWidth = maxOf(maxWidth, paints.textPaint.measureText(content, start, end))
                lineCount = index + 1
            }
        }
        // 官方 self-send 底框：整盒黑 30% 填充 + 内缩 strokeWidth/2 的白描边，垫在文字之下
        // （官方用 DestinationOver 垫底，这里先画框后画字，透明位图上等价）
        if (extra > 0f) {
            val boxWidth = ceil(maxWidth + paints.strokeWidth + 2 * extra)
            val boxHeight = ceil(advance * lineCount + paints.strokeWidth + 2 * extra)
            canvas.drawRect(0f, 0f, boxWidth, boxHeight, paints.selfSendFillPaint)
            val half = paints.strokeWidth / 2f
            canvas.drawRect(half, half, boxWidth - half, boxHeight - half, paints.selfSendBorderPaint)
        }
    }

    /** Iterate string ranges without allocating substrings, preserving empty and trailing lines. */
    private inline fun forEachLine(
        content: String,
        block: (start: Int, end: Int, index: Int) -> Unit,
    ) {
        var start = 0
        var index = 0
        while (true) {
            val newline = content.indexOf('\n', start)
            val end = if (newline < 0) content.length else newline
            block(start, end, index++)
            if (newline < 0) return
            start = newline + 1
        }
    }

    companion object {
        /** 官方基准描边宽度（2），实际值再乘缩放因子。 */
        private const val STROKE_WIDTH = 2f

        /** 官方 self-send 底框填充色：黑 30%（`Color(0, 0, 0, 0.3)`）。 */
        private val SELF_SEND_BOX_COLOR = Color.argb((0.3f * 255).toInt(), 0, 0, 0)

        /** 官方 self-send 底框的基准额外内边距（`SELF_SENT_PADDING = 4`）。 */
        private const val SELF_SEND_PADDING = 4f

        /** 官方描边色阈值：填充通道归一化值大于 0.3 用黑边。 */
        private const val STROKE_CHANNEL_THRESHOLD = 0.3f

        /** 官方弹幕字体：默认字族的 Bold 样式。 */
        private val OFFICIAL_TYPEFACE: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}
