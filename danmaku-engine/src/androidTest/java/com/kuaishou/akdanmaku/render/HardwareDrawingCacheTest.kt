package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.cache.DrawingCache
import com.kuaishou.akdanmaku.cache.DrawingCachePool
import com.kuaishou.akdanmaku.cache.HardwareBitmapBatch
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.ecs.DanmakuContext
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.utils.Size
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class HardwareDrawingCacheTest {
    @Test
    fun atlasKeepsSpritesSeparateAndPoolReuseDoesNotEraseANeighbor() {
        val red = DrawingCache().build(20, 12, 320, true)
        val green = DrawingCache().build(24, 16, 320, true)
        val pool = DrawingCachePool(1_024 * 1_024)
        try {
            red.get()!!.canvas.drawColor(Color.RED)
            green.get()!!.canvas.drawColor(Color.GREEN)
            val before = red.get()!!.image
            val batch = HardwareBitmapBatch()
            batch.add(red)
            batch.add(green)
            batch.flush()
            val redImage = red.get()!!.image
            val greenImage = green.get()!!.image
            assertThat(redImage.bitmap).isSameInstanceAs(greenImage.bitmap)
            assertThat(redImage.source!!.width()).isEqualTo(20)
            assertThat(greenImage.source!!.width()).isEqualTo(24)
            assertThat(before.source).isNull()
            assertThat(before.bitmap.getPixel(5, 5)).isEqualTo(Color.RED)
            val pixels = redImage.bitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                assertThat(pixels.getPixel(redImage.source.left + 5, redImage.source.top + 5)).isEqualTo(Color.RED)
                assertThat(
                    pixels.getPixel(greenImage.source.left + 5, greenImage.source.top + 5),
                ).isEqualTo(Color.GREEN)
            } finally {
                pixels.recycle()
            }
            assertThat(pool.release(red)).isTrue()
            assertThat(pool.acquire(20, 12)).isSameInstanceAs(red)
            assertThat(red.get()!!.image.source).isNull()
            assertThat(red.get()!!.bitmap.density).isEqualTo(320)
            assertThat(red.get()!!.bitmap.getPixel(5, 5)).isEqualTo(Color.TRANSPARENT)
            assertThat(green.get()!!.image).isSameInstanceAs(greenImage)
            assertThat(greenImage.bitmap.isRecycled).isFalse()
        } finally {
            pool.clear()
            red.get()?.bitmap?.recycle()
            green.get()?.bitmap?.recycle()
        }
    }

    @Test
    fun uploadedCacheCanBeRecycledAndRenderedAgain() {
        val cache = DrawingCache().build(20, 12, 320, true)
        val pool = DrawingCachePool(1_024 * 1_024)
        try {
            val holder = cache.get()!!
            holder.canvas.drawColor(Color.RED)
            assertThat(holder.prepareForHardwareRendering()).isTrue()
            assertThat(holder.bitmap.config).isEqualTo(Bitmap.Config.HARDWARE)
            assertThat(holder.bitmap.isMutable).isFalse()
            assertThat(pool.release(cache)).isTrue()
            assertThat(pool.acquire(20, 12)).isSameInstanceAs(cache)
            assertThat(holder.bitmap.isMutable).isTrue()
            assertThat(holder.bitmap.density).isEqualTo(320)
            assertThat(holder.bitmap.getPixel(5, 5)).isEqualTo(Color.TRANSPARENT)
            holder.canvas.drawColor(Color.GREEN)
            assertThat(holder.bitmap.getPixel(5, 5)).isEqualTo(Color.GREEN)
            assertThat(holder.prepareForHardwareRendering()).isTrue()
            cache.build(24, 16, 320, true)
            assertThat(cache.get()!!.bitmap.isMutable).isTrue()
            assertThat(cache.width).isEqualTo(24)
            assertThat(cache.height).isEqualTo(16)
        } finally {
            pool.clear()
            cache.get()?.bitmap?.recycle()
        }
    }

    @Test
    fun softwareCanvasFallsBackWhenTheCacheIsOnTheGpu() {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                assertSoftwareFallback()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun assertSoftwareFallback() {
        var draws = 0
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
                    draws++
                    canvas.drawRect(0f, 0f, 20f, 12f, Paint().apply { color = Color.GREEN })
                }
            }
        val context = DanmakuContext(renderer)
        val system = RenderSystem(context)
        val cache = DrawingCache().build(20, 12, 0, true)
        val output = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        try {
            assertThat(cache.get()!!.prepareForHardwareRendering()).isTrue()
            val item = DanmakuItem(DanmakuItemData(1, 0, "test", 7, 18, Color.WHITE))
            item.state = ItemState.Rendered
            item.drawState.cacheGeneration = context.config.cacheGeneration
            val obj =
                RenderObject(item, cache, PointF(), RectF(), Matrix()).apply {
                    width = 20f
                    height = 12f
                }
            val draw =
                RenderSystem::class.java
                    .getDeclaredMethod(
                        "drawRenderObject",
                        Canvas::class.java,
                        RenderObject::class.java,
                        DanmakuDisplayer::class.java,
                        DanmakuConfig::class.java,
                    ).apply { isAccessible = true }
            assertThat(draw.invoke(system, Canvas(output), obj, context.displayer, context.config)).isEqualTo(false)
            assertThat(draws).isEqualTo(1)
            assertThat(output.getPixel(5, 5)).isEqualTo(Color.GREEN)
        } finally {
            cache.get()?.bitmap?.recycle()
            output.recycle()
            context.cacheManager.release()
        }
    }
}
