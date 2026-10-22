package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import com.badlogic.ashley.core.Entity
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.cache.DrawingCache
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.ecs.DanmakuEngine
import com.kuaishou.akdanmaku.ecs.component.FilterResultComponent
import com.kuaishou.akdanmaku.ecs.component.ItemDataComponent
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/** 2,000 simultaneously visible, translucent, two-line sprites at 1080p; none may be culled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialDanmaku2000RenderPerfTest {
    @Test
    fun `2000 visible cached sprites sustain dense playback`() = renderFrames(cached = true)

    @Test
    fun `2000 visible sprites render during a cache backlog`() = renderFrames(cached = false)

    private fun renderFrames(cached: Boolean) {
        var fallbackDraws = 0
        val renderer =
            object : SpecialDanmakuRenderer() {
                override fun draw(
                    item: DanmakuItem,
                    canvas: Canvas,
                    displayer: DanmakuDisplayer,
                    config: DanmakuConfig,
                ) {
                    fallbackDraws++
                    super.draw(item, canvas, displayer, config)
                }
            }
        val engine = DanmakuEngine(renderer)
        val context = engine.context
        val system = engine.getSystem(RenderSystem::class.java)
        engine.systems.forEach { it.setProcessing(it === system) }
        context.displayer =
            object : DanmakuDisplayer {
                override var width = WIDTH
                override var height = HEIGHT
                override val density = 2f
                override val scaleDensity = 2f
                override val densityDpi = 320
                override val margin = 0
                override val allMarginTop = 0f
            }
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val caches = ArrayList<DrawingCache>(COUNT)
        val actions = ArrayList<ActionComponent>(COUNT)
        try {
            context.timer.start()
            repeat(COUNT) { index ->
                val item =
                    DanmakuItem(
                        DanmakuItemData(index.toLong(), index.toLong(), "Dense 弹幕\n$index", 7, 18, Color.WHITE),
                    )
                val size = renderer.measure(item, context.displayer, context.config)
                item.state = if (cached) ItemState.Rendered else ItemState.Rendering
                item.drawState.apply {
                    width = size.width.toFloat()
                    height = size.height.toFloat()
                    visibility = true
                    measureGeneration = context.config.measureGeneration
                    layoutGeneration = context.config.layoutGeneration
                    cacheGeneration = context.config.cacheGeneration
                    if (cached) {
                        drawingCache = DrawingCache().build(size.width, size.height, 0, true)
                        drawingCache.increaseReference()
                        caches.add(drawingCache)
                        renderer.draw(item, drawingCache.get()!!.canvas, context.displayer, context.config)
                    }
                }
                val action =
                    ActionComponent().apply {
                        this.item = item
                        alpha = 0.5f
                        rotation = if (index % 2 == 0) 15f else 0f
                        position.set(100f + (index % 50) * 29f, 60f + (index / 50) * 22f)
                    }
                // Check the complete transformed sprite is onscreen, including rotation.
                val bounds = RectF(0f, 0f, size.width.toFloat(), size.height.toFloat())
                val matrix = Matrix()
                action.toTransformMatrix(matrix)
                matrix.mapRect(bounds)
                assertThat(bounds.left).isAtLeast(0f)
                assertThat(bounds.top).isAtLeast(0f)
                assertThat(bounds.right).isAtMost(WIDTH.toFloat())
                assertThat(bounds.bottom).isAtMost(HEIGHT.toFloat())
                actions.add(action)
                engine.addEntity(
                    Entity()
                        .add(ItemDataComponent().apply { this.item = item })
                        .add(FilterResultComponent())
                        .add(action),
                )
            }
            fallbackDraws = 0
            val renderResultField =
                RenderSystem::class.java.getDeclaredField("renderResult").apply { isAccessible = true }
            val renderObjects = Collections.newSetFromMap(IdentityHashMap<RenderObject, Boolean>())
            val frameCosts = LongArray(SAMPLES)
            val updateCosts = LongArray(SAMPLES)
            repeat(WARMUP + SAMPLES) { frame ->
                bitmap.eraseColor(Color.TRANSPARENT)
                val start = System.nanoTime()
                // Move every sprite without taking any of the 2,000 outside the viewport.
                actions.forEach { it.position.x += if (frame % 2 == 0) 1f else -1f }
                engine.update(1f / 60)
                val updated = System.nanoTime()
                system.draw(canvas) {}
                val drawn = System.nanoTime()
                if (frame >= WARMUP) {
                    frameCosts[frame - WARMUP] = drawn - start
                    updateCosts[frame - WARMUP] = updated - start
                }
                assertThat(system.cacheHit.den).isEqualTo(COUNT)
                assertThat(system.cacheHit.num).isEqualTo(if (cached) COUNT else 0)
                if (frame in WARMUP until WARMUP + 3) {
                    val result = renderResultField.get(system)
                    val objectsField = result.javaClass.getDeclaredField("renderObjects").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    renderObjects.addAll(objectsField.get(result) as List<RenderObject>)
                }
            }
            frameCosts.sort()
            updateCosts.sort()
            println(
                String.format(
                    Locale.US,
                    "[Perf2000] cached=%s visible=%d frame median=%.2f ms p95=%.2f ms max=%.2f ms " +
                        "update p95=%.2f ms spriteMemory=%.2f MiB fallbackDraws=%d renderObjects=%d",
                    cached,
                    COUNT,
                    frameCosts[SAMPLES / 2] / 1_000_000.0,
                    frameCosts[(SAMPLES * 0.95).toInt()] / 1_000_000.0,
                    frameCosts.last() / 1_000_000.0,
                    updateCosts[(SAMPLES * 0.95).toInt()] / 1_000_000.0,
                    caches.sumOf { it.size.toLong() } / (1024.0 * 1024),
                    fallbackDraws,
                    renderObjects.size,
                ),
            )
            assertThat(engine.entities.size()).isEqualTo(COUNT)
            // There are two live frame snapshots. A third snapshot must reuse the first one's objects.
            assertThat(renderObjects).hasSize(2 * COUNT)
            assertThat(fallbackDraws).isEqualTo(if (cached) 0 else COUNT * (WARMUP + SAMPLES))
            val pixels = IntArray(WIDTH * HEIGHT)
            bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            assertThat(pixels.any { Color.alpha(it) > 0 }).isTrue()
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(system)
            context.cacheManager.release()
            caches.forEach { it.get()?.bitmap?.recycle() }
            bitmap.recycle()
        }
    }

    companion object {
        private const val COUNT = 2_000
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        private const val WARMUP = 20
        private const val SAMPLES = 120
    }
}
