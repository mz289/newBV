package com.kuaishou.akdanmaku.ui

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SpecialDanmakuRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DanmakuPlayerReleaseTest {
    @Test
    fun `release removes all systems and stops the cache worker`() {
        val player = DanmakuPlayer(SpecialDanmakuRenderer())
        val engine = player.engine
        val cacheManager = engine.context.cacheManager
        cacheManager.requestMeasure(
            DanmakuItem(DanmakuItemData(1, 0, "release", 7, 18, -1)),
            engine.context.displayer,
            engine.context.config,
        )
        player.release()
        assertThat(engine.systems.size()).isEqualTo(0)
        assertThat(engine.entities.size()).isEqualTo(0)
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!cacheManager.isReleased && System.nanoTime() < deadline) Thread.sleep(5)
        assertThat(cacheManager.isReleased).isTrue()
        player.release()
    }

    @Test
    fun `release without cache work does not initialize a worker`() {
        val player = DanmakuPlayer(SpecialDanmakuRenderer())
        val cacheManager = player.engine.context.cacheManager
        player.release()
        assertThat(cacheManager.isReleased).isTrue()
    }
}
