package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.RenderNode
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.ashley.core.Entity
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.cache.DrawingCache
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.data.SpecialDanmakuSpec
import com.kuaishou.akdanmaku.ecs.component.FilterResultComponent
import com.kuaishou.akdanmaku.ecs.component.ItemDataComponent
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import com.kuaishou.akdanmaku.ext.action
import com.kuaishou.akdanmaku.ext.dataComponent
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import org.junit.Test
import org.junit.runner.RunWith
import java.util.IdentityHashMap
import java.util.Locale

/** Records every sprite again each frame and waits for GPU presentation, not just command submission. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DenseDanmaku2000HardwareTest {
    @Test
    fun cached2000Sprites() = onMain { renderFrames(cached = true) }

    @Test
    fun hardwareCached2000Sprites() = onMain { renderFrames(cached = true, hardwareBitmaps = true) }

    @Test
    fun fullPlayback2000SpritesMeets60FpsBudget() =
        onMain { renderFrames(cached = true, hardwareBitmaps = true, fullPlayback = true) }

    @Test
    fun ordinaryPlayback2000SpritesMeets60FpsBudget() =
        onMain { renderFrames(cached = true, hardwareBitmaps = true, fullPlayback = true, ordinary = true) }

    @Test
    fun mixedPlayback2000SpritesMeets60FpsBudget() =
        onMain { renderFrames(cached = true, hardwareBitmaps = true, fullPlayback = true, mixed = true) }

    @Test
    fun uncached2000Sprites() = onMain { renderFrames(cached = false) }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun renderFrames(
        cached: Boolean,
        hardwareBitmaps: Boolean = false,
        fullPlayback: Boolean = false,
        ordinary: Boolean = false,
        mixed: Boolean = false,
    ) {
        val renderer = ModeAwareRenderer()
        val player = DanmakuPlayer(renderer)
        val engine = player.engine
        val context = engine.context
        val system = engine.getSystem(RenderSystem::class.java)
        if (!fullPlayback) engine.systems.forEach { it.setProcessing(it === system) }
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
        val images = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 3)
        val root = RenderNode("2000 danmaku").apply { setPosition(0, 0, WIDTH, HEIGHT) }
        val hardware =
            HardwareRenderer().apply {
                setName("Danmaku2000Stress")
                setSurface(images.surface)
                setContentRoot(root)
                isOpaque = false
                start()
            }
        val caches = ArrayList<DrawingCache>(COUNT)
        val actions = ArrayList<ActionComponent>(COUNT)
        try {
            context.timer.start()
            val preloadStart = SystemClock.elapsedRealtime()
            if (fullPlayback) {
                context.cacheManager.useHardwareBitmaps = true
                if (ordinary || mixed) {
                    // Showing 2,000 ordinary items requires explicitly allowing overlapping tracks.
                    context.config.allowOverlap = true
                    context.config.alpha = 0.5f
                    context.config.durationMs = 60_000L
                    context.config.rollingDurationMs = 60_000L
                }
                player.updateData(
                    List(COUNT) { index ->
                        val rolling = ordinary || (mixed && index % 2 == 0)
                        val text = if (rolling) "Dense 弹幕 $index" else "Dense 弹幕\n$index"
                        val x = 100f + (index % 50) * 29f
                        val y = 60f + (index / 50) * 22f
                        val spec =
                            SpecialDanmakuSpec(
                                text,
                                listOf(floatArrayOf(x / WIDTH, y / HEIGHT), floatArrayOf((x + 20) / WIDTH, y / HEIGHT)),
                                0.5f,
                                0.5f,
                                60_000L,
                                60_000L,
                                0L,
                                if (index % 4 < 2) 15f else 0f,
                                0f,
                                true,
                                false,
                            )
                        DanmakuItemData(
                            index.toLong(),
                            3_000L,
                            text,
                            if (rolling) DanmakuItemData.DANMAKU_MODE_ROLLING else 7,
                            18,
                            Color.WHITE,
                            lifeTime = spec.visibleDurationMs,
                            special = if (rolling) null else spec,
                        )
                    },
                )
                val deadline = SystemClock.elapsedRealtime() + 20_000L
                do {
                    engine.preAct()
                    engine.act()
                    val items = engine.entities.mapNotNull { it.dataComponent?.item }
                    if (items.size == COUNT && items.all { it.state == ItemState.Rendered }) break
                    check(SystemClock.elapsedRealtime() < deadline) { "2,000 sprites did not preload in time" }
                    Thread.sleep(10)
                } while (true)
                // The last partial atlas is flushed after the worker's queued rasterization jobs.
                while (engine.entities.any {
                        it.dataComponent!!
                            .item.drawState.drawingCache
                            .get()!!
                            .bitmap.config != Bitmap.Config.HARDWARE
                    }
                ) {
                    check(SystemClock.elapsedRealtime() < deadline) { "GPU upload did not complete in time" }
                    Thread.sleep(10)
                }
                engine.entities.forEach {
                    caches.add(
                        it.dataComponent!!
                            .item.drawState.drawingCache,
                    )
                }
                assertThat(caches.all { it.get()!!.bitmap.config == Bitmap.Config.HARDWARE }).isTrue()
                assertThat(
                    engine.entities.all {
                        !it.dataComponent!!
                            .item.drawState.visibility
                    },
                ).isTrue()
                engine.step(if (ordinary || mixed) 18.001f else 3.001f)
            }
            repeat(if (fullPlayback) 0 else COUNT) { index ->
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
                        if (hardwareBitmaps) {
                            assertThat(drawingCache.get()!!.prepareForHardwareRendering()).isTrue()
                        }
                    }
                }
                val action =
                    ActionComponent().apply {
                        this.item = item
                        alpha = 0.5f
                        rotation = if (index % 2 == 0) 15f else 0f
                        position.set(100f + (index % 50) * 29f, 60f + (index / 50) * 22f)
                    }
                actions.add(action)
                engine.addEntity(
                    Entity()
                        .add(ItemDataComponent().apply { this.item = item })
                        .add(FilterResultComponent())
                        .add(action),
                )
            }
            val preloadMs = SystemClock.elapsedRealtime() - preloadStart
            if (fullPlayback) assertThat(preloadMs).isLessThan(3_000L)
            val sampleCount = if (fullPlayback) 600 else SAMPLES
            val frames = LongArray(sampleCount)
            val recordings = LongArray(sampleCount)
            val updates = LongArray(sampleCount)
            var outputFrames = 0
            repeat(WARMUP + sampleCount) { frame ->
                val start = System.nanoTime()
                actions.forEach { it.position.x += if (frame % 2 == 0) 1f else -1f }
                if (fullPlayback) {
                    engine.step(1f / 60)
                    engine.preAct()
                    engine.act()
                } else {
                    engine.update(1f / 60)
                }
                val updated = System.nanoTime()
                val canvas = root.beginRecording()
                try {
                    assertThat(canvas.isHardwareAccelerated).isTrue()
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    system.draw(canvas) {}
                } finally {
                    root.endRecording()
                }
                val recorded = System.nanoTime()
                val status =
                    hardware
                        .createRenderRequest()
                        .setVsyncTime(start)
                        .setWaitForPresent(true)
                        .syncAndDraw()
                assertThat(status and HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND).isEqualTo(0)
                assertThat(status and HardwareRenderer.SYNC_FRAME_DROPPED).isEqualTo(0)
                // Release every output buffer so the producer never silently stalls behind ImageReader.
                images.acquireLatestImage()?.use { outputFrames++ }
                val presented = System.nanoTime()
                if (frame >= WARMUP) {
                    frames[frame - WARMUP] = presented - start
                    recordings[frame - WARMUP] = recorded - start
                    updates[frame - WARMUP] = updated - start
                }
                assertThat(system.cacheHit.den).isEqualTo(COUNT)
                assertThat(system.cacheHit.num).isEqualTo(if (cached) COUNT else 0)
                if (frame == WARMUP || frame == WARMUP + sampleCount - 1) {
                    // Cache-hit counters also include rejected items. Check the actual transformed
                    // bounds outside the measured interval so the stress load cannot pass by culling.
                    val transform = Matrix()
                    val bounds = RectF()
                    engine.entities.forEach { entity ->
                        val state = entity.dataComponent!!.item.drawState
                        val action = entity.action
                        if (action == null) {
                            transform.set(state.transform)
                        } else {
                            action.toTransformMatrix(transform)
                            if (state.hasTransform) transform.postConcat(state.transform)
                            assertThat(action.alpha).isGreaterThan(0f)
                        }
                        bounds.set(0f, 0f, state.width, state.height)
                        transform.mapRect(bounds)
                        assertThat(bounds.left).isAtLeast(0f)
                        assertThat(bounds.top).isAtLeast(0f)
                        assertThat(bounds.right).isAtMost(WIDTH.toFloat())
                        assertThat(bounds.bottom).isAtMost(HEIGHT.toFloat())
                    }
                }
            }
            frames.sort()
            recordings.sort()
            updates.sort()
            val textures = IdentityHashMap<Bitmap, Boolean>()
            caches.forEach { cache -> textures[cache.get()!!.bitmap] = true }
            val textureBytes = textures.keys.sumOf { it.allocationByteCount.toLong() }
            val slowFrames = frames.count { it > FRAME_BUDGET_NS }
            val message =
                String.format(
                    Locale.US,
                    "[GPU2000] cached=%s hardwareBitmaps=%s fullPlayback=%s ordinary=%s mixed=%s " +
                        "visible=%d median=%.2f ms " +
                        "p95=%.2f ms p99=%.2f ms max=%.2f ms slowFrames=%d/%d " +
                        "recordP95=%.2f ms updateP95=%.2f ms textureMemory=%.2f MiB " +
                        "textures=%d outputs=%d preload=%d ms",
                    cached,
                    hardwareBitmaps,
                    fullPlayback,
                    ordinary,
                    mixed,
                    COUNT,
                    frames[sampleCount / 2] / 1_000_000.0,
                    frames[(sampleCount * 0.95).toInt()] / 1_000_000.0,
                    frames[(sampleCount * 0.99).toInt()] / 1_000_000.0,
                    frames.last() / 1_000_000.0,
                    slowFrames,
                    sampleCount,
                    recordings[(sampleCount * 0.95).toInt()] / 1_000_000.0,
                    updates[(sampleCount * 0.95).toInt()] / 1_000_000.0,
                    textureBytes / (1024.0 * 1024),
                    textures.size,
                    outputFrames,
                    preloadMs,
                )
            Log.i("Danmaku2000Stress", message)
            println(message)
            assertThat(engine.entities.size()).isEqualTo(COUNT)
            assertThat(outputFrames).isEqualTo(WARMUP + sampleCount)
            if (fullPlayback) {
                assertThat(frames[(sampleCount * 0.95).toInt()]).isLessThan(FRAME_BUDGET_NS)
                assertThat(slowFrames).isAtMost(sampleCount / 100)
            }
        } finally {
            hardware.stop()
            hardware.destroy()
            root.discardDisplayList()
            images.close()
            engine.removeAllEntities()
            engine.removeSystem(system)
            context.cacheManager.release()
            caches.forEach { it.get()?.bitmap?.recycle() }
            player.release()
        }
    }

    companion object {
        private const val COUNT = 2_000
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        private const val WARMUP = 30
        private const val SAMPLES = 120
        private const val FRAME_BUDGET_NS = 16_666_667L
    }
}
