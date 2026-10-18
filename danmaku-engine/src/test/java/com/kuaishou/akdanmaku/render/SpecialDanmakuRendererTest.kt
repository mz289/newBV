package com.kuaishou.akdanmaku.render

import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.DisplayScaleFactor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * [SpecialDanmakuRenderer] 测量与描边语义（官方光栅化器规则：
 * 盒宽 = 最长行 advance + 描边宽、盒高 = 行数 × 行距 + 描边宽、行距 = 字体度量高、
 * 描边宽度 2 × factor、描边色按填充通道 > 0.3 取黑）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialDanmakuRendererTest {
    private val renderer = SpecialDanmakuRenderer()
    private val displayer =
        object : DanmakuDisplayer {
            override var width = 1216
            override var height = 912
            override val margin = 4
            override val allMarginTop = 0f
            override val density = 2f
            override val scaleDensity = 2f
            override val densityDpi = 320
        }

    private fun item(
        text: String,
        size: Int = 18,
        stroked: Boolean = true,
        color: Int = Color.WHITE,
        fontFamily: String = "SimHei",
    ): DanmakuItem {
        val spec =
            SpecialDanmakuParser.parse(
                """[0,0,"1-1",10,"$text",0,0,0,0,0,0,$stroked,"$fontFamily",false]""",
            )!!
        return DanmakuItem(DanmakuItemData(1, 0, spec.text, 7, size, color, special = spec))
    }

    /** 官方字号 = 载荷字号 × 可绘制区缩放因子 × 用户缩放。 */
    private fun factorFor(d: DanmakuDisplayer): Float =
        DisplayScaleFactor.suggestedFactor(
            d.width.toFloat(),
            d.height.toFloat(),
            d.density,
            DisplayScaleFactor.isTablet(d.width.toFloat(), d.height.toFloat(), d.density),
        )

    /** 官方行距 = 字体度量高（descent − ascent），描边占位只在盒边缘加一次。 */
    private fun lineAdvanceOf(fontSize: Int): Float {
        val factor = factorFor(displayer)
        val paint =
            TextPaint().apply {
                textSize = fontSize * factor
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    /** 官方固定系统粗体（fakeBold 不影响 advance 测量）。 */
    private fun measurePaint(fontSize: Int): TextPaint =
        TextPaint().apply {
            textSize = fontSize * factorFor(displayer)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

    @Test
    fun `width measures text and height uses font metrics per line`() {
        for (fontSize in listOf(5, 18, 36)) {
            val paint = measurePaint(fontSize)
            val size = renderer.measure(item("MMMM", fontSize), displayer, DanmakuConfig())
            val stroke = 2f * factorFor(displayer)
            assertThat(size.height).isEqualTo(ceil(lineAdvanceOf(fontSize) + stroke).toInt())
            assertThat(size.width).isEqualTo(ceil(paint.measureText("MMMM") + stroke).toInt())
        }
    }

    @Test
    fun `font size follows display area factor`() {
        // 官方缩放因子随可绘制区尺寸变化：大屏相对更小、小屏相对更大
        val largeFactor = factorFor(displayer)
        val small =
            object : DanmakuDisplayer {
                override var width = 960
                override var height = 540
                override val margin = 4
                override val allMarginTop = 0f
                override val density = 2f
                override val scaleDensity = 2f
                override val densityDpi = 320
            }
        // 1216×912 对角线/2 ≈ 760 → [700,1000) → 0.8×2；960×540 → 550 → [520,700) → 0.72×2
        assertThat(largeFactor).isWithin(1e-4f).of(0.8f * 2f)
        assertThat(factorFor(small)).isWithin(1e-4f).of(0.72f * 2f)
        assertThat(factorFor(small)).isLessThan(largeFactor)
    }

    @Test
    fun `multiline advances by font metrics per line`() {
        val paint = measurePaint(18)
        val size = renderer.measure(item("旋\n律\nは"), displayer, DanmakuConfig())
        val stroke = 2f * factorFor(displayer)
        assertThat(size.height).isEqualTo(ceil(3 * lineAdvanceOf(18) + stroke).toInt())
        assertThat(size.width).isEqualTo(ceil(maxOf(paint.measureText("旋"), paint.measureText("律")) + stroke).toInt())
    }

    @Test
    fun `stroked flag from payload controls stroke pass`() {
        assertThat(item("MMMM", stroked = false).data.special?.stroked).isFalse()
        assertThat(item("MMMM", stroked = true).data.special?.stroked).isTrue()
    }

    @Test
    fun `stroke draws around the glyph in the official color`() {
        // 白字 → 黑边：出现近黑像素；深灰字（各通道 ≤ 0.3）→ 白边
        val white = renderInk(item("M", stroked = true), isProbe = { r, g, b -> r < 64 && g < 64 && b < 64 })
        val dark = renderInk(item("M", stroked = true, color = Color.rgb(0x40, 0x40, 0x40)), isProbe = { r, g, b -> r > 200 && g > 200 && b > 200 })
        assertThat(white).isGreaterThan(0)
        assertThat(dark).isGreaterThan(0)
    }

    @Test
    fun `stroke pass adds ink compared to plain fill`() {
        val plain = renderInk(item("M", stroked = false), isProbe = { _, _, _ -> true })
        val stroked = renderInk(item("M", stroked = true), isProbe = { _, _, _ -> true })
        assertThat(stroked).isGreaterThan(plain)
    }

    /** 在 96×96 画布上绘制单字，返回满足探针的像素数。 */
    private fun renderInk(
        item: DanmakuItem,
        isProbe: (Int, Int, Int) -> Boolean,
    ): Int {
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        renderer.draw(item, Canvas(bitmap), displayer, DanmakuConfig().apply { bold = false })
        val pixels = IntArray(96 * 96)
        bitmap.getPixels(pixels, 0, 96, 0, 0, 96, 96)
        bitmap.recycle()
        return pixels.count { Color.alpha(it) > 0 && isProbe(Color.red(it), Color.green(it), Color.blue(it)) }
    }

    companion object {
        /** 官方基准描边宽度（2）。 */
        private const val STROKE_WIDTH = 2f
    }
}
