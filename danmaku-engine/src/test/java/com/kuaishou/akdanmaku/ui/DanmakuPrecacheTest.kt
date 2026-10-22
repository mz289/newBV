package com.kuaishou.akdanmaku.ui

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.cache.DrawingCache
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.ItemState
import com.kuaishou.akdanmaku.data.SpecialDanmakuSpec
import com.kuaishou.akdanmaku.ext.dataComponent
import com.kuaishou.akdanmaku.render.ModeAwareRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DanmakuPrecacheTest {
    @Test
    fun `future ordinary items only prewarm when overlapping tracks are allowed`() {
        val player = DanmakuPlayer(ModeAwareRenderer())
        val engine = player.engine
        try {
            engine.context.displayer =
                DanmakuView.ViewDisplayer().apply {
                    width = 1920
                    height = 1080
                }
            engine.timer.start()
            player.updateData(
                listOf(
                    DanmakuItemData(1, 3000, "普通弹幕", 1, 18, Color.WHITE),
                    DanmakuItemData(
                        2,
                        3000,
                        "高级弹幕",
                        7,
                        18,
                        Color.WHITE,
                        special =
                            SpecialDanmakuSpec(
                                "高级弹幕",
                                listOf(floatArrayOf(0.5f, 0.5f)),
                                1f,
                                1f,
                                5000L,
                                5000L,
                                0L,
                                0f,
                                0f,
                                true,
                                false,
                            ),
                    ),
                ),
            )
            val deadline = System.nanoTime() + 3_000_000_000L
            do {
                engine.preAct()
                engine.act()
                val special =
                    engine.entities.firstOrNull {
                        it.dataComponent
                            ?.item
                            ?.data
                            ?.mode == 7
                    }
                if (special?.dataComponent?.item?.state == ItemState.Rendered) break
                check(System.nanoTime() < deadline) { "Future special item did not prewarm" }
                Thread.sleep(5)
            } while (true)
            val ordinary =
                engine.entities
                    .first {
                        it.dataComponent!!
                            .item.data.mode == 1
                    }.dataComponent!!
                    .item
            assertThat(ordinary.state).isEqualTo(ItemState.Measured)
            assertThat(ordinary.drawState.drawingCache).isSameInstanceAs(DrawingCache.EMPTY_DRAWING_CACHE)
            engine.context.config.allowOverlap = true
            while (ordinary.state != ItemState.Rendered) {
                engine.act()
                check(System.nanoTime() < deadline) { "Future ordinary item did not prewarm" }
                Thread.sleep(5)
            }
            assertThat(ordinary.drawState.visibility).isFalse()
        } finally {
            player.release()
        }
    }
}
