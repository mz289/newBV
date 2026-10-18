package com.kuaishou.akdanmaku.ui

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ext.dataComponent
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import com.kuaishou.akdanmaku.data.SpecialDanmakuSpec
import com.kuaishou.akdanmaku.layout.SpecialDanmakuLayouter
import com.kuaishou.akdanmaku.render.ModeAwareRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * mode 7 高级弹幕生命周期语义（官方 App 引擎）：
 * - 实体创建窗口：`当前时间 - 弹幕时间 > animDelay` 的弹幕不再创建视图
 *   （跳转 / 中途载入时动画已过延迟的弹幕不补显示）；
 * - 在屏时长 = `max(displayDuration, animDelay + animDuration)`；
 * - 载荷解析失败（special 缺失）不创建实体。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialDanmakuLifecycleTest {
    private fun fakeDisplayer(): DanmakuDisplayer =
        object : DanmakuDisplayer {
            override var width: Int = 1280
            override var height: Int = 720
            override val margin: Int = 4
            override val allMarginTop: Float = 0f
            override val density: Float = 2f
            override val scaleDensity: Float = 2f
            override val densityDpi: Int = 320
        }

    private fun data(
        position: Long,
        special: SpecialDanmakuSpec?,
        lifeTime: Long = special?.visibleDurationMs ?: 0L,
    ): DanmakuItemData =
        DanmakuItemData(
            danmakuId = position + (special?.text?.length ?: 0),
            position = position,
            content = special?.text ?: "m7",
            mode = DanmakuItemData.DANMAKU_MODE_SPECIAL,
            textSize = 25,
            textColor = Color.WHITE,
            lifeTime = lifeTime,
            special = special,
        )

    /** 驱动引擎前进 n 帧（30fps）。 */
    private fun DanmakuPlayer.runFrames(frames: Int) {
        engine.start()
        repeat(frames) {
            engine.step(0.033f)
            engine.preAct()
            engine.act()
        }
    }

    @Test
    fun `danmaku already past its delay window is never created`() {
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        try {
            // 时钟已走到 3s，弹幕时间为 0：延迟 0 → n=3s > 0 → 官方不再创建视图
            val dropped = SpecialDanmakuParser.parse("""[100,100,"1-1",5,"补显",0,0,100,100,500,0,false,"f",false]""")!!
            // 延迟 5s → n=3s ≤ 5s → 仍在创建窗口内
            val kept = SpecialDanmakuParser.parse("""[100,100,"1-1",5,"等待",0,0,100,100,500,5000,false,"f",false]""")!!
            player.updateData(listOf(data(0L, dropped), data(0L, kept)))
            player.engine.seekTo(3_000L)
            player.runFrames(3)
            assertThat(player.engine.entities.size()).isEqualTo(1)
            val item = player.engine.entities.first().dataComponent?.item
            assertThat(item?.data?.content).isEqualTo("等待")
        } finally {
            player.release()
        }
    }

    @Test
    fun `mode 7 without special spec creates no entity`() {
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        try {
            player.updateData(listOf(data(0L, null, 5_000L)))
            player.runFrames(2)
            assertThat(player.engine.entities.size()).isEqualTo(0)
        } finally {
            player.release()
        }
    }

    @Test
    fun `visible duration is max of display and motion timeline`() {
        // display=1s，delay=0.2s + anim=2s → 在屏 2.2s（官方运动未走完不结束）
        val spec = SpecialDanmakuParser.parse("""[100,100,"1-1",1,"运动",0,0,300,100,2000,200,false,"f",false]""")!!
        assertThat(spec.visibleDurationMs).isEqualTo(2_200L)
        val player = DanmakuPlayer(ModeAwareRenderer())
        player.engine.context.displayer = fakeDisplayer()
        try {
            player.updateData(listOf(data(0L, spec)))
            player.runFrames(20)
            // 第 20 帧（0.66s）仍在屏
            assertThat(player.engine.entities.size()).isEqualTo(1)
            // 走到 2.2s 之后被移除
            player.runFrames(80)
            assertThat(player.engine.getCurrentTimeMs()).isGreaterThan(2_200L)
            assertThat(player.engine.entities.size()).isEqualTo(0)
        } finally {
            player.release()
        }
    }

    @Test
    fun `preLayout visibility window is the open interval`() {
        val spec = SpecialDanmakuParser.parse("""[100,100,"1-1",4,"窗口",0,0,100,100,500,0,false,"f",false]""")!!
        val item = DanmakuItem(data(0L, spec))
        item.duration = spec.visibleDurationMs
        val layouter = SpecialDanmakuLayouter()
        val config = DanmakuConfig()
        val displayer = fakeDisplayer()
        assertThat(layouter.preLayout(item, -1, displayer, config)).isFalse()
        assertThat(layouter.preLayout(item, 0, displayer, config)).isFalse()
        assertThat(layouter.preLayout(item, 1, displayer, config)).isTrue()
        assertThat(layouter.preLayout(item, 3_999, displayer, config)).isTrue()
        assertThat(layouter.preLayout(item, 4_000, displayer, config)).isFalse()
    }
}
