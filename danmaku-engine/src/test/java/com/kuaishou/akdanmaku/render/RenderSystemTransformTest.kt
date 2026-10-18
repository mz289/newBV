package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.cache.DrawingCache
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.ecs.DanmakuContext
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.Size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderSystemTransformTest {
    @Test
    fun `cache hit and miss preserve rotation and composed opacity`() {
        // Two overlapping opaque shapes detect accidental per-paint alpha blending.
        val renderer =
            object : DanmakuRenderer {
                override fun updatePaint(
                    item: DanmakuItem,
                    displayer: DanmakuDisplayer,
                    config: DanmakuConfig,
                ) {}

                override fun measure(
                    item: DanmakuItem,
                    displayer: DanmakuDisplayer,
                    config: DanmakuConfig,
                ) = Size(20, 12)

                override fun draw(
                    item: DanmakuItem,
                    canvas: Canvas,
                    displayer: DanmakuDisplayer,
                    config: DanmakuConfig,
                ) {
                    canvas.drawRect(0f, 0f, 20f, 12f, Paint().apply { color = Color.RED })
                    canvas.drawRect(4f, 2f, 16f, 10f, Paint().apply { color = Color.WHITE })
                }
            }
        val context = DanmakuContext(renderer)
        try {
            val system = RenderSystem(context)
            val item = DanmakuItem(DanmakuItemData(1, 0, "test", 7, 25, Color.WHITE))
            item.state = ItemState.Rendered
            item.drawState.cacheGeneration = context.config.cacheGeneration
            val transform =
                Matrix().apply {
                    setRotate(90f)
                    postTranslate(50f, 20f)
                }
            val obj = RenderObject(item, DrawingCache.EMPTY_DRAWING_CACHE, PointF(50f, 20f), RectF(), transform)
            val paint =
                RenderSystem::class.java
                    .getDeclaredField(
                        "drawPaint",
                    ).apply { isAccessible = true }
                    .get(system) as Paint
            val draw =
                RenderSystem::class.java
                    .getDeclaredMethod(
                        "drawRenderObject",
                        Canvas::class.java,
                        RenderObject::class.java,
                        DanmakuDisplayer::class.java,
                        DanmakuConfig::class.java,
                    ).apply { isAccessible = true }
            val cache = DrawingCache().build(20, 12, 0, true)
            renderer.draw(item, cache.get()!!.canvas, context.displayer, context.config)
            for (alpha in listOf(0, 128, 255)) {
                paint.alpha = alpha

                fun render(cached: Boolean): Bitmap {
                    obj.drawingCache = if (cached) cache else DrawingCache.EMPTY_DRAWING_CACHE
                    val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val saveCount = canvas.saveCount
                    assertThat(draw.invoke(system, canvas, obj, context.displayer, context.config)).isEqualTo(cached)
                    assertThat(canvas.saveCount).isEqualTo(saveCount)
                    return bitmap
                }
                val hit = render(true)
                val miss = render(false)
                assertThat(Color.alpha(miss.getPixel(44, 30))).isEqualTo(alpha)
                assertThat(miss.getPixel(55, 25)).isEqualTo(Color.TRANSPARENT)
                val hitPixels = IntArray(80 * 80)
                val missPixels = IntArray(80 * 80)
                hit.getPixels(hitPixels, 0, 80, 0, 0, 80, 80)
                miss.getPixels(missPixels, 0, 80, 0, 0, 80, 80)
                assertThat(missPixels).isEqualTo(hitPixels)
                hit.recycle()
                miss.recycle()
            }
        } finally {
            context.cacheManager.release()
        }
    }
}
