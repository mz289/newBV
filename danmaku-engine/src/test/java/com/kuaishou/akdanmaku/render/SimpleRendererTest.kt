package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.DisplayScaleFactor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimpleRendererTest {
    @Test
    fun `cached metrics preserve normal text pixels across display and style changes`() {
        val renderer = SimpleRenderer()
        val displayer =
            object : DanmakuDisplayer {
                override var width = 1920
                override var height = 1080
                override var density = 2f
                override val densityDpi = 320
                override val scaleDensity = 2f
                override val margin = 0
                override val allMarginTop = 0f
            }
        for (width in listOf(1920, 960, 1920)) {
            displayer.width = width
            for (bold in listOf(true, false, true)) {
                for (fontSize in listOf(8, 18, 40)) {
                    for (selfSend in listOf(false, true)) {
                        val config = DanmakuConfig(bold = bold, textSizeScale = 0.8f)
                        val item =
                            DanmakuItem(
                                DanmakuItemData(
                                    1,
                                    0,
                                    "普通弹幕 Abg",
                                    1,
                                    fontSize,
                                    0x222222,
                                    danmakuStyle = if (selfSend) DanmakuItemData.DANMAKU_STYLE_SELF_SEND else 0,
                                ),
                            )
                        val factor =
                            DisplayScaleFactor.suggestedFactor(
                                width.toFloat(),
                                displayer.height.toFloat(),
                                displayer.density,
                                DisplayScaleFactor.isTablet(
                                    width.toFloat(),
                                    displayer.height.toFloat(),
                                    displayer.density,
                                ),
                            )
                        val fill =
                            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = fontSize.toFloat().coerceIn(12f, 25f) * factor * config.textSizeScale
                                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                                color = 0xff222222.toInt()
                            }
                        val stroke =
                            TextPaint(fill).apply {
                                style = Paint.Style.FILL_AND_STROKE
                                strokeWidth = 2f * factor * config.textSizeScale
                                color = Color.WHITE
                            }
                        val metrics = fill.fontMetrics
                        val extra = if (selfSend) 4f * factor * config.textSizeScale + stroke.strokeWidth / 2 else 0f
                        val widthPx = fill.measureText(item.data.content).roundToInt() + 6 + (2 * extra).roundToInt()
                        val heightPx =
                            (metrics.descent - metrics.ascent + metrics.leading).roundToInt() +
                                6 + (2 * extra).roundToInt()
                        val measured = renderer.measure(item, displayer, config)
                        assertThat(measured.width).isEqualTo(widthPx)
                        assertThat(measured.height).isEqualTo(heightPx)
                        val expected = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(expected)
                        if (selfSend) {
                            canvas.drawColor(Color.argb((0.3f * 255).toInt(), 0, 0, 0))
                            val half = stroke.strokeWidth / 2
                            canvas.drawRect(
                                half,
                                half,
                                widthPx - half,
                                heightPx - half,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeWidth = stroke.strokeWidth
                                    color = Color.WHITE
                                },
                            )
                        }
                        canvas.drawText(item.data.content, 3f + extra, 3f - metrics.ascent + extra, stroke)
                        canvas.drawText(item.data.content, 3f + extra, 3f - metrics.ascent + extra, fill)
                        val actual = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        renderer.draw(item, Canvas(actual), displayer, config)
                        assertThat(actual.sameAs(expected)).isTrue()
                        actual.recycle()
                        expected.recycle()
                    }
                }
            }
        }
    }
}
