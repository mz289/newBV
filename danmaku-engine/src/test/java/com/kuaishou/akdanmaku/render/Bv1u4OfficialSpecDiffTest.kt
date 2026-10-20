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
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.DisplayScaleFactor
import com.kuaishou.akdanmaku.utils.Size
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.ceil

/**
 * BV1u4HVzZEno（【弹幕还原】緋色月下、狂咲ノ絶-1st Anniversary Remix，cid 32812567027）
 * 全部 mode 7 载荷的**官方光栅化规格逐条像素比对**（App 接口全量抓取）。
 *
 * 参考实现（本文件内的 [Bv1u4SpecRasterizer]）按官方客户端弹幕引擎的光栅化规则独立实现：
 * - 测量：盒宽 = 最长行 advance + strokeWidth；盒高 = 行数 × 行距 + strokeWidth；
 *   基线 = `-ascent + strokeWidth/2`；
 * - 画布属性：`fontSize/strokeWidth × factor × scale`、仿粗（fakeBold）、系统 Bold 字体；
 * - 绘制：笔位 `x = strokeWidth/2`，先描边后填充；
 * - 描边色：stroked 时填充任一通道 > 0.3 → 黑边否则白边，
 *   非 stroked 不描边（盒尺寸仍保留描边占位）。
 *
 * 生产侧 [SpecialDanmakuRenderer] 必须与参考实现**逐像素一致**（同字体、同 Skia，
 * 语义一致即像素一致），这是「观感和官方一致」的机器判定。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Bv1u4OfficialSpecDiffTest {
    private val displayWidth = 2560
    private val displayHeight = 1440
    private val deviceDensity = 2f

    private data class Entry(
        val timeMs: Long,
        val size: Int,
        val color: Int,
        val payload: String,
    )

    private fun fakeDisplayer(): DanmakuDisplayer =
        object : DanmakuDisplayer {
            override var width: Int = displayWidth
            override var height: Int = displayHeight
            override val margin: Int = 4
            override val allMarginTop: Float = 0f
            override val density: Float = deviceDensity
            override val scaleDensity: Float = deviceDensity
            override val densityDpi: Int = 320
        }

    @Test
    fun `every mode7 payload rasterizes identically to the official spec`() {
        val json =
            javaClass.classLoader!!
                .getResourceAsStream("bv1u4_m7.json")!!
                .readBytes()
                .decodeToString()
        val rows = JSONObject(json).getJSONArray("entries")
        val entries =
            (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .filter { it.getInt("mode") == 7 }
                .map {
                    Entry(
                        timeMs = (it.getDouble("t") * 1000).toLong(),
                        size = it.getInt("size"),
                        color = it.getInt("color"),
                        payload = it.getString("payload"),
                    )
                }
        println("[Spec] mode7 payloads: ${entries.size}")

        // 解析层：与官方 ≥14 门槛完全一致——官方丢弃的我们也丢弃，多丢/少丢都算回归
        val parsed =
            entries.map { it to SpecialDanmakuParser.parse(it.payload) }
        val officialGated =
            entries.count { entry ->
                runCatching {
                    val arr = org.json.JSONArray(entry.payload)
                    arr.length() >= 14
                }.getOrDefault(false)
            }
        val dropped = parsed.count { it.second == null }
        println("[Spec] official-gated=$officialGated parser-dropped=$dropped")
        assertThat(dropped).isEqualTo(entries.size - officialGated)

        val displayer = fakeDisplayer()
        val config = DanmakuConfig()
        val renderer = SpecialDanmakuRenderer()
        val spec = Bv1u4SpecRasterizer(displayWidth, displayHeight, deviceDensity)

        var sizeMismatch = 0
        var pixelMismatch = 0
        var worstDelta = 0
        var compared = 0
        var skippedOversize = 0
        val offenders = mutableListOf<String>()

        for ((entry, specData) in parsed) {
            val data = specData ?: continue
            val item =
                DanmakuItem(
                    DanmakuItemData(
                        danmakuId = entry.payload.hashCode().toLong() and 0x7FFFFFFF,
                        position = entry.timeMs,
                        content = data.text,
                        mode = DanmakuItemData.DANMAKU_MODE_SPECIAL,
                        textSize = entry.size,
                        // App 层规则：color 0 视为未设置 → 白色（官方 p.color ? parse : WHITE）
                        textColor = officialColor(entry.color),
                        lifeTime = data.visibleDurationMs,
                        special = data,
                    ),
                )

            val fillColor = officialColor(entry.color)
            val produced = renderer.measure(item, displayer, config)
            val expected = spec.measure(entry.size, data.text, data.stroked, fillColor)
            if (produced.width != expected.width || produced.height != expected.height) {
                sizeMismatch++
                if (offenders.size < 12) {
                    offenders.add(
                        "t=%.2f size=%d box prod=%dx%d spec=%dx%d text=%s"
                            .format(Locale.US, entry.timeMs / 1000.0, entry.size, produced.width, produced.height, expected.width, expected.height, data.text.take(24)),
                    )
                }
                continue
            }
            if (expected.width.toLong() * expected.height.toLong() > MAX_PIXELS) {
                skippedOversize++
                continue
            }

            val prodBitmap = Bitmap.createBitmap(expected.width, expected.height, Bitmap.Config.ARGB_8888)
            renderer.draw(item, Canvas(prodBitmap), displayer, config)
            val specBitmap = spec.rasterize(entry.size, data.text, data.stroked, expected, fillColor)

            val delta = diffPixels(prodBitmap, specBitmap)
            if (delta.first > 0) {
                pixelMismatch++
                worstDelta = maxOf(worstDelta, delta.second)
                if (offenders.size < 12) {
                    offenders.add(
                        "t=%.2f size=%d pixels=%d worst=%d text=%s"
                            .format(Locale.US, entry.timeMs / 1000.0, entry.size, delta.first, delta.second, data.text.take(24)),
                    )
                }
            }
            compared++
            prodBitmap.recycle()
            specBitmap.recycle()
        }

        println(
            "[Spec] compared=$compared sizeMismatch=$sizeMismatch pixelMismatch=$pixelMismatch " +
                "worstChannelDelta=$worstDelta oversizeSkipped=$skippedOversize",
        )
        offenders.forEach { println("  [Offender] $it") }

        assertThat(sizeMismatch).isEqualTo(0)
        assertThat(pixelMismatch).isEqualTo(0)
    }

    /** 官方取色规则：`p.color ? parse : WHITE`（color 0 → 白色），并补不透明通道。 */
    private fun officialColor(color: Int): Int = (if (color == 0) 0xFFFFFF else color) or -0x1000000

    private fun diffPixels(
        a: Bitmap,
        b: Bitmap,
    ): Pair<Int, Int> {
        assertThat(a.width).isEqualTo(b.width)
        assertThat(a.height).isEqualTo(b.height)
        val pa = IntArray(a.width * a.height)
        val pb = IntArray(b.width * b.height)
        a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        var count = 0
        var worst = 0
        for (i in pa.indices) {
            if (pa[i] == pb[i]) continue
            val d =
                maxOf(
                    Math.abs(Color.red(pa[i]) - Color.red(pb[i])),
                    Math.abs(Color.green(pa[i]) - Color.green(pb[i])),
                    Math.abs(Color.blue(pa[i]) - Color.blue(pb[i])),
                    Math.abs(Color.alpha(pa[i]) - Color.alpha(pb[i])),
                )
            if (d > 0) {
                count++
                worst = maxOf(worst, d)
            }
        }
        return count to worst
    }

    private companion object {
        /** 超大精灵（ASCII 画整幅可达数千像素宽）在缓存路径由引擎直接绘制，跳过位图比对。 */
        private const val MAX_PIXELS = 40_000_000L
    }
}

/**
 * 官方规格参考光栅化器：与生产代码完全独立，按官方引擎的测量 / 绘制规则实现。
 */
private class Bv1u4SpecRasterizer(
    displayWidth: Int,
    displayHeight: Int,
    density: Float,
) {
    private val factor =
        DisplayScaleFactor.suggestedFactor(
            displayWidth.toFloat(),
            displayHeight.toFloat(),
            density,
            DisplayScaleFactor.isTablet(displayWidth.toFloat(), displayHeight.toFloat(), density),
        )

    private val fillPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
            style = Paint.Style.FILL
        }

    private val strokePaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
            style = Paint.Style.STROKE
        }

    private val strokeWidth: Float = STROKE_WIDTH * factor

    init {
        println("[Spec] factor=$factor strokeWidth=$strokeWidth")
    }

    fun measure(
        payloadSize: Int,
        text: String,
        stroked: Boolean,
        fillColor: Int,
    ): Size {
        configure(payloadSize, stroked, fillColor)
        val lines = text.split('\n')
        var maxAdvance = 0f
        lines.forEach { line -> maxAdvance = maxOf(maxAdvance, fillPaint.measureText(line)) }
        val metrics = fillPaint.fontMetrics
        val lineAdvance = metrics.descent - metrics.ascent
        return Size(
            ceil(maxAdvance + strokeWidth).toInt(),
            ceil(lineAdvance * lines.size + strokeWidth).toInt(),
        )
    }

    fun rasterize(
        payloadSize: Int,
        text: String,
        stroked: Boolean,
        size: Size,
        fillColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        configure(payloadSize, stroked, fillColor)
        val metrics = fillPaint.fontMetrics
        val lineAdvance = metrics.descent - metrics.ascent
        val baseline = -metrics.ascent + strokeWidth / 2f
        val x = strokeWidth / 2f
        // 官方绘制：先描边后填充；非 stroked 时不画描边（跳过）
        for ((index, line) in text.split('\n').withIndex()) {
            val y = baseline + lineAdvance * index
            if (stroked) canvas.drawText(line, x, y, strokePaint)
            canvas.drawText(line, x, y, fillPaint)
        }
        return bitmap
    }

    /** `configureCanvas`：fontSize = 载荷字号 × factor（scale 取 1）。 */
    private fun configure(
        payloadSize: Int,
        stroked: Boolean,
        fillColor: Int,
    ) {
        val textSize = (payloadSize * factor).coerceAtLeast(1f)
        fillPaint.textSize = textSize
        fillPaint.color = fillColor
        strokePaint.textSize = textSize
        strokePaint.strokeWidth = strokeWidth
        // 官方描边色规则：填充任一通道 > 0.3 → BLACK，否则 WHITE
        strokePaint.color = fillColor.let { fill ->
            val bright =
                Color.red(fill) / 255f > 0.3f ||
                    Color.green(fill) / 255f > 0.3f ||
                    Color.blue(fill) / 255f > 0.3f
            if (bright) Color.BLACK else Color.WHITE
        }
    }

    private companion object {
        /** 官方基准描边宽度（2，× factor × scale）。 */
        private const val STROKE_WIDTH = 2f
    }
}
