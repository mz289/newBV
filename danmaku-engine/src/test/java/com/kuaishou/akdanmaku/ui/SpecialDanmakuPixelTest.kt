package com.kuaishou.akdanmaku.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.ext.dataComponent
import com.kuaishou.akdanmaku.render.ModeAwareRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 端到端像素验证：高级弹幕经引擎（解析 → 入库 → 实体 → 测量缓存 → 绘制）真正落到画布上。
 *
 * 同时覆盖官方创建窗口：`n = 当前时间 - 弹幕时间 > animDelay` 的弹幕不再创建视图
 * （seek 后仍保留的条目需满足该条件，否则整条不显示）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialDanmakuPixelTest {
    @Test
    fun `advanced danmaku produces visible pixels through the engine`() {
        // 弹幕时间 = 当前时间（n=0 ≤ delay=0），入场即可见
        assertVisiblePixels(displayMs = 1_000L, position = 350_517L, seekTo = 350_517L, delay = 0L)
    }

    @Test
    fun `seek restores advanced danmaku inside the creation window`() {
        // 弹幕时间 0s、delay=60s：seek 到 30s 时 n=30s ≤ 60s 仍在创建窗口内
        assertVisiblePixels(displayMs = 60_000L, position = 0L, seekTo = 30_000L, delay = 60_000L)
    }

    @Test
    fun `seek drops advanced danmaku past its delay window`() {
        // 弹幕时间 0s、delay=0：seek 到 30s 时 n=30s > 0 → 官方不再创建视图
        assertNoEntity(displayMs = 60_000L, position = 0L, seekTo = 30_000L, delay = 0L)
    }

    private fun assertVisiblePixels(
        displayMs: Long,
        position: Long,
        seekTo: Long,
        delay: Long,
    ) {
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        val spec =
            SpecialDanmakuParser.parse(
                """[255,252,"1-0",${displayMs / 1000},"Test",0,0,255,252,500,${delay},0,"SimSun",false]""",
            )!!
        val bitmap = Bitmap.createBitmap(682, 438, Bitmap.Config.ARGB_8888)
        try {
            player.updateData(
                listOf(
                    DanmakuItemData(
                        1, position, spec.text, 7, 25, Color.WHITE,
                        lifeTime = spec.visibleDurationMs,
                        special = spec,
                    ),
                    DanmakuItemData(2, seekTo + 60_000L, "future", 1, 25, Color.WHITE),
                ),
            )
            player.engine.seekTo(seekTo)
            player.engine.preAct()
            player.engine.act()
            repeat(10) {
                player.engine.step(0.033f)
                player.engine.preAct()
                player.engine.act()
                // Wait for the real background measure/cache worker.
                Thread.sleep(20)
            }
            player.engine.draw(Canvas(bitmap)) {}
            val pixels = IntArray(682 * 438)
            bitmap.getPixels(pixels, 0, 682, 0, 0, 682, 438)
            assertThat(player.engine.entities.size()).isEqualTo(1)
            assertThat(player.engine.entities.first().dataComponent!!.item.drawState.visibility).isTrue()
            assertThat(pixels.count { Color.alpha(it) > 0 }).isGreaterThan(0)
        } finally {
            player.release()
            bitmap.recycle()
        }
    }

    private fun assertNoEntity(
        displayMs: Long,
        position: Long,
        seekTo: Long,
        delay: Long,
    ) {
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        val spec =
            SpecialDanmakuParser.parse(
                """[255,252,"1-0",${displayMs / 1000},"Test",0,0,255,252,500,${delay},0,"SimSun",false]""",
            )!!
        try {
            player.updateData(
                listOf(
                    DanmakuItemData(
                        1, position, spec.text, 7, 25, Color.WHITE,
                        lifeTime = spec.visibleDurationMs,
                        special = spec,
                    ),
                ),
            )
            player.engine.seekTo(seekTo)
            repeat(5) {
                player.engine.step(0.033f)
                player.engine.preAct()
                player.engine.act()
            }
            assertThat(player.engine.entities.size()).isEqualTo(0)
        } finally {
            player.release()
        }
    }

    private fun fakeDisplayer(): DanmakuDisplayer =
        object : DanmakuDisplayer {
            override var width = 682
            override var height = 438
            override val margin = 4
            override val allMarginTop = 0f
            override val density = 2f
            override val scaleDensity = 2f
            override val densityDpi = 320
        }
}
