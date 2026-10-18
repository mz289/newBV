package com.kuaishou.akdanmaku.ecs.component.action

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.SpecialDanmakuParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SpecialDanmakuAction] 的动画数学测试。
 *
 * 语义：官方客户端弹幕引擎的高级弹幕动画时间轴
 * （等分时长关键帧折线、延迟内停首帧、EaseInOut 开关、透明度在运动时长内过渡）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialDanmakuActionTest {
    private val displayWidth = 1216f
    private val displayHeight = 912f

    private fun action(payload: String): Pair<SpecialDanmakuAction, ActionComponent> {
        val spec = SpecialDanmakuParser.parse(payload) ?: error("payload rejected: $payload")
        val data =
            DanmakuItemData(
                danmakuId = 1,
                position = 0,
                content = spec.text,
                mode = DanmakuItemData.DANMAKU_MODE_SPECIAL,
                textSize = 25,
                textColor = 0xFFFFFF,
                lifeTime = spec.visibleDurationMs,
                special = spec,
            )
        val item = DanmakuItem(data)
        val component = ActionComponent()
        component.item = item
        val action = SpecialDanmakuAction(spec, displayWidth, displayHeight)
        component.addAction(action)
        return action to component
    }

    @Test
    fun `position stays at start during delay`() {
        val (_, component) =
            action("""[0.4,0.2,"1-1",10,"MMMM",0,0,0,0.2,6000,1500,false,"黑体",1]""")
        component.act(0)
        val beginX = component.position.x
        component.act(1499)
        assertThat(component.position.x).isWithin(0.01f).of(beginX)
        component.act(1600)
        assertThat(component.position.x).isLessThan(beginX)
    }

    @Test
    fun `action finishes when motion timeline ends`() {
        val (action, _) = action("""[100,80,"1-1",4,"x",0,0,200,80,1000,500,false,"f",0]""")
        val target = ActionComponent()
        action.target = target
        assertThat(action.act(1499)).isFalse()
        assertThat(action.act(1500)).isTrue()
    }

    @Test
    fun `single point path pins position`() {
        // 路径退化为一个点：官方 steps 为空，视图停在 keyCoords[0]
        val (_, component) = action("""[0,0,"1-0",4.5,"●",0,0,0,0,500,0,0,"SimHei",true,"M610,422"]""")
        component.act(0)
        assertThat(component.position.x).isWithin(0.01f).of(610f / 861f * displayWidth)
        assertThat(component.position.y).isWithin(0.01f).of(422f / 572f * displayHeight)
        component.act(250)
        assertThat(component.position.x).isWithin(0.01f).of(610f / 861f * displayWidth)
    }

    @Test
    fun `zero size display falls back to stage basis`() {
        val spec = SpecialDanmakuParser.parse("""[861,572,"1-1",4,"x",0,0,861,572,1000,0,false,"f",true]""")!!
        val item = DanmakuItem(DanmakuItemData(1, 0, spec.text, 7, 25, 0xFFFFFF, special = spec))
        // 宽高为 0（View 未完成布局）时按官方舞台基准 861×572 烘培
        item.rebakeSpecialActions(0, 0, generation = 0)
        val action = item.actions.first() as SpecialDanmakuAction
        val component = ActionComponent()
        component.item = item
        action.target = component
        action.act(0)
        assertThat(component.position.x).isWithin(0.01f).of(861f)
        assertThat(component.position.y).isWithin(0.01f).of(572f)
    }

    @Test
    fun `reused component clears rotation`() {
        val (_, component) = action("""[100,80,"1-1",4,"rotate",30,60,100,80,500,0,false,"f",0]""")
        component.act(0)
        assertThat(component.rotation).isEqualTo(30f)
        component.actions.clear()
        component.act(0)
        val matrix = android.graphics.Matrix()
        component.toTransformMatrix(matrix)
        assertThat(matrix.isIdentity).isTrue()
    }

    @Test
    fun `rotation keeps top left anchor fixed`() {
        // 起点 (430.5, 286) 恰为舞台中心，即旋转锚点本身
        val (_, component) = action("""[430.5,286,"1-1",4,"旋转",90,0,430.5,286,500,0,false,"f",0]""")
        component.act(0)
        val matrix = android.graphics.Matrix()
        component.toTransformMatrix(matrix)
        val anchor = floatArrayOf(0f, 0f)
        matrix.mapPoints(anchor)
        // 锚点为文本左上角（同官方），旋转后仍在目标位置
        assertThat(anchor[0]).isWithin(0.01f).of(0.5f * displayWidth)
        assertThat(anchor[1]).isWithin(0.01f).of(0.5f * displayHeight)
        // 顺时针 90°：本地 +x 方向映射到屏幕 +y 方向
        val probe = floatArrayOf(100f, 0f)
        matrix.mapPoints(probe)
        assertThat(probe[0]).isWithin(0.01f).of(0.5f * displayWidth)
        assertThat(probe[1]).isWithin(0.01f).of(0.5f * displayHeight + 100f)
    }
}
