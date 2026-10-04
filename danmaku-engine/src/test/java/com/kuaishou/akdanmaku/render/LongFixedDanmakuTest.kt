package com.kuaishou.akdanmaku.render

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ecs.component.mode.rolling.RollingComponent
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LongFixedDanmakuTest {
    private val display =
        object : DanmakuDisplayer {
            override var width = 1920
            override var height = 1080
            override val margin = 4
            override val allMarginTop = 0f
            override val density = 2f
            override val scaleDensity = 2f
            override val densityDpi = 320
        }

    private fun item(mode: Int) = DanmakuItem(DanmakuItemData(1, 1000, "长弹幕".repeat(40), mode, 25, 0))

    @Test
    fun `actual width selects rolling for top and bottom while disabled preserves type`() {
        val renderer = SimpleRenderer()
        for (mode in listOf(4, 5)) {
            val item = item(mode)
            assertThat(renderer.layoutMode(item, display, DanmakuConfig(scrollThreshold = 1))).isEqualTo(1)
            assertThat(renderer.layoutMode(item, display, DanmakuConfig())).isEqualTo(mode)
            assertThat(renderer.layoutMode(item, display, DanmakuConfig(scrollThreshold = 9999))).isEqualTo(mode)
            assertThat(item.data.mode).isEqualTo(mode)
        }
        assertThat(renderer.layoutMode(item(1), display, DanmakuConfig(scrollThreshold = 1))).isEqualTo(1)
    }

    @Test
    fun `width decision follows rendered text scale`() {
        val renderer = SimpleRenderer()
        val item = item(5)
        val width = renderer.measure(item, display, DanmakuConfig()).width - 6
        val threshold = (width * 1.5f).toInt()
        assertThat(renderer.layoutMode(item, display, DanmakuConfig(scrollThreshold = threshold))).isEqualTo(5)
        assertThat(
            renderer.layoutMode(item, display, DanmakuConfig(scrollThreshold = threshold, textSizeScale = 2f)),
        ).isEqualTo(1)
    }

    @Test
    fun `engine creates rolling component for converted fixed item and restores after reload`() {
        val player = DanmakuPlayer(SimpleRenderer())
        try {
            player.updateConfig(DanmakuConfig(scrollThreshold = 1))
            player.updateData(listOf(item(5).data))
            player.engine.update(0f)
            player.engine.preAct()
            assertThat(
                player.engine.entities
                    .first()
                    .getComponent(RollingComponent::class.java),
            ).isNotNull()
            player.clearData()
            player.updateConfig(DanmakuConfig())
            player.updateData(listOf(item(5).data))
            player.engine.update(0f)
            player.engine.preAct()
            assertThat(
                player.engine.entities
                    .first()
                    .getComponent(RollingComponent::class.java),
            ).isNull()
        } finally {
            player.release()
        }
    }
}
