package com.kuaishou.akdanmaku.layout.retainer

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AkTopRetainerTest {
    private val displayer =
        object : DanmakuDisplayer {
            override var height = 40
            override var width = 1000
            override val margin = 0
            override val allMarginTop = 0f
            override val density = 1f
            override val scaleDensity = 1f
            override val densityDpi = 160
        }

    private fun item(
        id: Long,
        mode: Int,
        score: Int,
        position: Long = 0,
    ) = DanmakuItem(DanmakuItemData(id, position, "弹幕$id", mode, 25, 0, score = score)).apply {
        duration = 5000
        drawState.width = 200f
        drawState.height = 20f
    }

    @Test
    fun `禁止重叠时满轨道拒绝所有权重的滚动和顶部弹幕`() {
        for (mode in listOf(DanmakuItemData.DANMAKU_MODE_ROLLING, DanmakuItemData.DANMAKU_MODE_CENTER_TOP)) {
            for (score in listOf(0, 9, 10, 11)) {
                val retainer = AkTopRetainer().apply { update(0, 40) }
                val config = DanmakuConfig(allowOverlap = false)
                val items = (1L..100L).map { item(it, mode, score) }
                val positions = items.map { retainer.layout(it, 100, displayer, config) }
                assertThat(positions.take(2)).containsExactly(0f, 20f).inOrder()
                assertThat(positions.drop(2)).containsExactlyElementsIn(List(98) { -1f })
                assertThat(items.count { it.drawState.visibility }).isEqualTo(2)
            }
        }
    }

    @Test
    fun `轨道释放后正权重弹幕仍能正常显示`() {
        val retainer = AkTopRetainer().apply { update(0, 20) }
        val config = DanmakuConfig(allowOverlap = false)
        val first = item(1, DanmakuItemData.DANMAKU_MODE_ROLLING, 9)
        assertThat(retainer.layout(first, 100, displayer, config)).isEqualTo(0f)
        assertThat(retainer.layout(first, 5001, displayer, config)).isEqualTo(-1f)
        val next = item(2, DanmakuItemData.DANMAKU_MODE_ROLLING, 11, position = 5001)
        assertThat(retainer.layout(next, 5001, displayer, config)).isEqualTo(0f)
        assertThat(next.drawState.visibility).isTrue()
    }

    @Test
    fun `显式允许重叠时仍能复用满轨道`() {
        val retainer = AkTopRetainer().apply { update(0, 20) }
        val config = DanmakuConfig(allowOverlap = true)
        val first = item(1, DanmakuItemData.DANMAKU_MODE_ROLLING, 0)
        val next = item(2, DanmakuItemData.DANMAKU_MODE_ROLLING, 0)
        assertThat(retainer.layout(first, 100, displayer, config)).isEqualTo(0f)
        assertThat(retainer.layout(next, 100, displayer, config)).isEqualTo(0f)
        assertThat(next.drawState.visibility).isTrue()
    }
}
