package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.badlogic.ashley.core.Entity
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.ecs.DanmakuEngine
import com.kuaishou.akdanmaku.ecs.component.FilterResultComponent
import com.kuaishou.akdanmaku.ecs.component.ItemDataComponent
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Deterministic cache backlog: all items are measured, but no bitmap cache is ready yet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialDanmakuDenseRenderPerfTest {
    @Test
    fun `dense translucent fallback frames render visible text`() {
        renderDenseFrames(mixedVisibility = false)
    }

    @Test
    fun `dense fallback frames include transparent and offscreen text`() {
        renderDenseFrames(mixedVisibility = true)
    }

    private fun renderDenseFrames(mixedVisibility: Boolean) {
        val renderer = SpecialDanmakuRenderer()
        val engine = DanmakuEngine(renderer)
        val context = engine.context
        val system = engine.getSystem(RenderSystem::class.java)
        engine.systems.forEach { it.setProcessing(it === system) }
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            context.timer.paused = false
            repeat(240) { index ->
                val item =
                    DanmakuItem(
                        DanmakuItemData(index.toLong(), 0, "Dense 弹幕\n${index % 10}", 7, 18, Color.WHITE),
                    )
                val size = renderer.measure(item, context.displayer, context.config)
                item.state = ItemState.Rendering
                item.drawState.apply {
                    width = size.width.toFloat()
                    height = size.height.toFloat()
                    visibility = true
                    measureGeneration = context.config.measureGeneration
                    layoutGeneration = context.config.layoutGeneration
                }
                val action =
                    ActionComponent().apply {
                        this.item = item
                        alpha = if (mixedVisibility && index % 3 == 0) 0f else 0.5f
                        rotation = if (index % 2 == 0) 15f else 0f
                        position.set(
                            if (mixedVisibility && index % 3 == 1) 1600f else (index % 12) * 95f,
                            (index / 12) * 30f,
                        )
                    }
                engine.addEntity(
                    Entity()
                        .add(ItemDataComponent().apply { this.item = item })
                        .add(FilterResultComponent())
                        .add(action),
                )
            }
            repeat(5) {
                engine.update(1f / 60)
                system.draw(canvas) {}
            }
            val costs = LongArray(30)
            for (frame in costs.indices) {
                bitmap.eraseColor(Color.TRANSPARENT)
                val start = System.nanoTime()
                engine.update(1f / 60)
                system.draw(canvas) {}
                costs[frame] = System.nanoTime() - start
            }
            costs.sort()
            println(
                String.format(
                    Locale.US,
                    "[Perf] dense fallback (mixedVisibility=%s): 240 entities, median=%.2f ms, p95=%.2f ms",
                    mixedVisibility,
                    costs[costs.size / 2] / 1_000_000.0,
                    costs[(costs.size * 0.95).toInt()] / 1_000_000.0,
                ),
            )
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertThat(engine.entities.size()).isEqualTo(240)
            assertThat(pixels.any { Color.alpha(it) > 0 }).isTrue()
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(system)
            context.cacheManager.release()
            bitmap.recycle()
        }
    }
}
