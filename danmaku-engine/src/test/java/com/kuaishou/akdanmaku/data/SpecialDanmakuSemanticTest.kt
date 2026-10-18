package com.kuaishou.akdanmaku.data

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.component.action.SpecialDanmakuAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 官方引擎语义回归（黄金数值来自独立参考实现的逐帧状态输出，
 * 其坐标系为 1920×1080、原点左上）。
 *
 * 关键口径：
 * - 坐标：`≤ 1 视为比例，> 1 除以 861 / 572`，负值截 0；
 * - y 为**文本顶边**距画面顶部的高度比例（官方舞台原点在左下、锚点 `[0,1]` 为精灵左上，
 *   与 DFM / 网页引擎一致）——所以 y=572 是屏幕底缘、y=0 是顶缘；
 * - 缓动：第 14 项为真用平滑起停 `t²(3-2t)`，为假匀速；
 * - 透明度：延迟内恒定，之后在运动时长内线性过渡。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialDanmakuSemanticTest {
    private val displayWidth = 1920f
    private val displayHeight = 1080f

    private fun action(payload: String): ActionComponent {
        val spec = SpecialDanmakuParser.parse(payload) ?: error("payload rejected: $payload")
        val data =
            DanmakuItemData(
                danmakuId = 1,
                position = 0,
                content = spec.text,
                mode = DanmakuItemData.DANMAKU_MODE_SPECIAL,
                textSize = 18,
                textColor = 0xFFFFFF,
                lifeTime = spec.visibleDurationMs,
                special = spec,
            )
        val item = DanmakuItem(data)
        val component = ActionComponent()
        component.item = item
        component.addAction(SpecialDanmakuAction(spec, displayWidth, displayHeight))
        return component
    }

    private fun assertAt(
        component: ActionComponent,
        timeMs: Long,
        x: Float,
        y: Float,
        alpha: Float? = null,
    ) {
        component.act(timeMs)
        assertThat(component.position.x).isWithin(0.02f).of(x)
        assertThat(component.position.y).isWithin(0.02f).of(y)
        alpha?.let { assertThat(component.alpha).isWithin(1e-4f).of(it) }
    }

    /* BV1qx411c772 P2 文字墙真实载荷（388.9s）：
       [10,528,"1-1",3.3,"「心已被弄壞了嗎？」",0,0,10,528,900,0,0,"SimSun",true]
       官方参考：x=22.300(=10/861*1920)，y=996.923(=528/572*1080，贴屏幕底缘) */
    @Test
    fun `p2 lyric wall sits at screen bottom`() {
        val c = action("""[10,528,"1-1",3.3,"「心已被弄壞了嗎？」",0,0,10,528,900,0,0,"SimSun",true]""")
        for (time in listOf(0L, 100L, 1000L, 2000L, 3300L)) {
            assertAt(c, time, x = 22.3001f, y = 996.9231f, alpha = 1f)
        }
    }

    /* 绯色月下真实竖排歌词（205.9s）：终点 0.99 是比例，官方横穿顶部
       [0,0,"1-1",6,"其/nノ",0,0,0.99,0,10000,0,false,"楷体",1] */
    @Test
    fun `vertical lyric slides across the top with ease in out`() {
        val c = action("""[0,0,"1-1",6,"其/nノ",0,0,0.99,0,10000,0,false,"楷体",1]""")
        // 官方参考：t=1s→x=53.222，t=2s→197.683，t=4s→669.082（平滑起停曲线）
        assertAt(c, 1000, x = 53.222f, y = 0f, alpha = 1f)
        assertAt(c, 2000, x = 197.683f, y = 0f)
        assertAt(c, 4000, x = 669.082f, y = 0f)
        assertAt(c, 5000, x = 950.400f, y = 0f)
    }

    /* 绯色月下真实 ASCII 画（231s）：起点比例 (0.4,0.2)，终点 endX=0 也是比例（逐值判定）
       [0.4,0.2,"1-1",10,"MMM",0,0,0,0.2,6000,1500,false,"黑体",1]
       官方参考：延迟 1.5s 后自 x=768 左移到 0，y 恒为 216（=0.2*1080，文本顶边） */
    @Test
    fun `ascii art slides horizontally at fixed height`() {
        val c = action("""[0.4,0.2,"1-1",10,"MMM",0,0,0,0.2,6000,1500,false,"黑体",1]""")
        assertAt(c, 0, x = 768f, y = 216f)
        assertAt(c, 1500, x = 768f, y = 216f)
        assertAt(c, 2000, x = 752.889f, y = 216f)
        assertAt(c, 4000, x = 479.111f, y = 216f)
        assertAt(c, 6000, x = 120f, y = 216f)
        assertAt(c, 8000, x = 0f, y = 216f)
    }

    /* 美丽之物真实载荷：负坐标截 0，y=275 → 275/572 顶边比例
       ["-5","275","1-1","5.3","█",...] 官方参考：x=0，y=519.231 */
    @Test
    fun `negative coordinates clamp to the top left edge`() {
        val c =
            action(
                """["-5","275","1-1","5.3","█","0","0","-5","275","500","0","false","MS UI Gothic","0"]""",
            )
        assertAt(c, 0, x = 0f, y = 519.2308f)
        assertAt(c, 5300, x = 0f, y = 519.2308f)
    }

    /* 绯色月下真实路径弹幕：路径点归一化，官方参考 x=1360.279 y=796.783，透明度 1→0 用 0.5s */
    @Test
    fun `path payload lands at normalized point with fast fade`() {
        val c = action("""[0,0,"1-0",4.5,"●",0,0,0,0,500,0,0,"SimHei",true,"M610,422L610,422"]""")
        assertAt(c, 0, x = 1360.2799f, y = 796.7832f, alpha = 1f)
        assertAt(c, 250, x = 1360.2799f, y = 796.7832f, alpha = 0.5f)
        assertAt(c, 500, x = 1360.2799f, y = 796.7832f, alpha = 0f)
        assertAt(c, 4000, x = 1360.2799f, y = 796.7832f, alpha = 0f)
    }

    /* 多段路径：三段时间等分（官方 segDur = animDuration/(n-1)），段内按缓动插值 */
    @Test
    fun `multi segment path splits motion duration equally`() {
        val c = action("""[0,0,"1-1",4,"x",0,0,0,0,900,0,false,"f",0,"M0,0L300,0L300,300"]""")
        val stepX = 300f / 861f * displayWidth
        val stepY = 300f / 572f * displayHeight
        assertAt(c, 0, x = 0f, y = 0f)
        // t=450ms 恰好是第一段结束（中间关键帧）
        assertAt(c, 450, x = stepX, y = 0f)
        // t=675ms 在第二段中点（匀速：y 为一半）
        assertAt(c, 675, x = stepX, y = stepY / 2f)
        assertAt(c, 900, x = stepX, y = stepY)
    }

    /* 速度曲线：缓动为真时 t=25% 处应落在平滑曲线的 15.625% 而不是 25% */
    @Test
    fun `ease in out is smoothstep while default is linear`() {
        val eased = action("""[0,0,"1-1",4,"x",0,0,100,0,1000,0,false,"f",true]""")
        eased.act(250)
        assertThat(eased.position.x).isWithin(0.02f).of(100f / 861f * displayWidth * 0.15625f)
        val linear = action("""[0,0,"1-1",4,"x",0,0,100,0,1000,0,false,"f",0]""")
        linear.act(250)
        assertThat(linear.position.x).isWithin(0.02f).of(100f / 861f * displayWidth * 0.25f)
    }

    /* 透明度时间轴：延迟内恒为起始值，之后在运动时长内线性过渡（而非整个生命周期） */
    @Test
    fun `alpha fades over motion duration after delay`() {
        val c = action("""[0,0,"1-0",4,"x",0,0,0,0,1000,200,false,"f",0]""")
        c.act(0)
        assertThat(c.alpha).isWithin(1e-4f).of(1f)
        c.act(200)
        assertThat(c.alpha).isWithin(1e-4f).of(1f)
        c.act(700)
        assertThat(c.alpha).isWithin(1e-4f).of(0.5f)
        c.act(1200)
        assertThat(c.alpha).isWithin(1e-4f).of(0f)
    }

    /* 动画时长为 0：立即跳到终点并按终止透明度显示 */
    @Test
    fun `zero motion duration jumps instantly`() {
        val c = action("""[0,0,"0-1",4,"x",0,0,50,0,0,300,false,"f",0]""")
        // 延迟 300ms 内停在起点，透明度恒为起始值 0（不可见）
        c.act(299)
        assertThat(c.position.x).isWithin(0.02f).of(0f)
        assertThat(c.alpha).isWithin(1e-4f).of(0f)
        // 越过延迟后立即跳到终点，透明度取终止值 1
        c.act(301)
        assertThat(c.position.x).isWithin(0.02f).of(50f / 861f * displayWidth)
        assertThat(c.alpha).isWithin(1e-4f).of(1f)
        // 无运动时长：终点保持
        c.act(4000)
        assertThat(c.position.x).isWithin(0.02f).of(50f / 861f * displayWidth)
        assertThat(c.alpha).isWithin(1e-4f).of(1f)
    }

    /* 旋转：度数直接作用于画面（顺时针为正），与官方 rotateToAngle(-deg) 等价 */
    @Test
    fun `rotation z applies clockwise degrees`() {
        val c = action("""[100,80,"1-1",4,"翻转",30,60,100,80,500,0,false,"f",0]""")
        c.act(0)
        assertThat(c.rotation).isEqualTo(30f)
    }

    /* 固定点弹幕（起点=终点）全程停在同一位置 */
    @Test
    fun `fixed position payload stays put`() {
        val c = action("""[70,350,"1-1",2.6,"伸展五指弹奏的旋律",0,0,70,350,500,0,1,"SimHei",true]""")
        assertAt(c, 0, x = 70f / 861f * displayWidth, y = 350f / 572f * displayHeight)
        assertAt(c, 2600, x = 70f / 861f * displayWidth, y = 350f / 572f * displayHeight)
    }
}
