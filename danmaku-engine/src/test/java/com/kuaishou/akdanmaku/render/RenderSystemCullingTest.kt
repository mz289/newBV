package com.kuaishou.akdanmaku.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.badlogic.ashley.core.Entity
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.ecs.component.FilterResultComponent
import com.kuaishou.akdanmaku.ecs.component.ItemDataComponent
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.system.RenderSystem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import com.kuaishou.akdanmaku.utils.Size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderSystemCullingTest {
    private class Fixture : AutoCloseable {
        var draws = 0
        private val paint = Paint().apply { color = Color.WHITE }
        private val renderer =
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
                    canvas.drawRect(0f, 0f, 20f, 12f, paint)
                }
            }
        private val player = DanmakuPlayer(renderer)
        private val engine = player.engine
        private val context = engine.context
        private val system = engine.getSystem(RenderSystem::class.java)
        val bitmap: Bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val item = DanmakuItem(DanmakuItemData(1, 0, "test", 7, 25, Color.WHITE), player)
        val action = ActionComponent().apply { this.item = this@Fixture.item }

        init {
            engine.systems.forEach { it.setProcessing(it === system) }
            context.timer.start(1_000L)
            item.state = ItemState.Rendering
            item.drawState.apply {
                width = 20f
                height = 12f
                visibility = true
                measureGeneration = context.config.measureGeneration
                layoutGeneration = context.config.layoutGeneration
            }
            engine.addEntity(
                Entity()
                    .add(ItemDataComponent().apply { this.item = this@Fixture.item })
                    .add(FilterResultComponent())
                    .add(action),
            )
        }

        fun draw() {
            bitmap.eraseColor(Color.TRANSPARENT)
            engine.update(1f / 60)
            val saveCount = canvas.saveCount
            system.draw(canvas) {}
            assertThat(canvas.saveCount).isEqualTo(saveCount)
        }

        override fun close() {
            engine.removeAllEntities()
            engine.removeSystem(system)
            player.release()
            bitmap.recycle()
        }
    }

    @Test
    fun `transparent and offscreen items skip renderer and can become visible again`() {
        Fixture().use { fixture ->
            fixture.action.position.set(10f, 10f)
            fixture.action.alpha = 0f
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(0)
            fixture.action.alpha = 0.5f
            fixture.action.position.set(100f, 10f)
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(0)
            fixture.action.position.set(10f, 10f)
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(1)
            assertThat(Color.alpha(fixture.bitmap.getPixel(15, 15))).isWithin(1).of(127)
        }
    }

    @Test
    fun `rotated item with offscreen anchor still renders its visible portion`() {
        Fixture().use { fixture ->
            fixture.action.position.set(85f, 10f)
            fixture.action.rotation = 90f
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(1)
            assertThat(fixture.bitmap.getPixel(75, 15)).isEqualTo(Color.WHITE)
        }
    }

    @Test
    fun `culling respects the caller canvas transform and clip`() {
        Fixture().use { fixture ->
            fixture.action.position.set(85f, 10f)
            fixture.canvas.translate(-80f, 0f)
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(1)
            assertThat(fixture.bitmap.getPixel(10, 15)).isEqualTo(Color.WHITE)
            fixture.canvas.clipRect(40f, 40f, 60f, 60f)
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(1)
        }
    }

    @Test
    fun `translation combines action and draw state offsets without stale matrices`() {
        Fixture().use { fixture ->
            fixture.item.drawState.apply {
                positionX = 20f
                positionY = 10f
                translateX = 2f
                translateY = 3f
            }
            fixture.action.position.set(10f, 10f)
            repeat(4) {
                fixture.action.rotation = 90f
                fixture.draw()
                fixture.action.rotation = 0f
                fixture.draw()
                assertThat(fixture.bitmap.getPixel(37, 28)).isEqualTo(Color.WHITE)
                assertThat(fixture.bitmap.getPixel(15, 15)).isEqualTo(Color.TRANSPARENT)
            }
        }
    }

    @Test
    fun `held item remains opaque even when animation alpha is zero`() {
        Fixture().use { fixture ->
            fixture.action.alpha = 0f
            fixture.item.hold()
            fixture.draw()
            assertThat(fixture.draws).isEqualTo(1)
            assertThat(fixture.bitmap.getPixel(5, 5)).isEqualTo(Color.WHITE)
        }
    }
}
