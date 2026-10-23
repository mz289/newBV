package com.kuaishou.akdanmaku.ui

import android.os.Handler
import android.os.HandlerThread
import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SpecialDanmakuRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DanmakuPlayerReleaseTest {
    @Test
    fun `release wakes a blocked action thread and waits for it to exit`() {
        val player = DanmakuPlayer(SpecialDanmakuRenderer())
        val threadField = DanmakuPlayer::class.java.getDeclaredField("actionThread\$delegate")
        threadField.isAccessible = true
        val thread = (threadField.get(player) as Lazy<*>).value as HandlerThread
        val semaphoreField = DanmakuPlayer::class.java.getDeclaredField("drawSemaphore")
        semaphoreField.isAccessible = true
        val semaphore = semaphoreField.get(player) as Semaphore
        val blocked = CountDownLatch(1)
        val finished = CountDownLatch(1)
        Handler(thread.looper).post {
            blocked.countDown()
            semaphore.acquire()
            finished.countDown()
        }
        try {
            assertThat(blocked.await(3, TimeUnit.SECONDS)).isTrue()
            player.release()
            assertThat(finished.count).isEqualTo(0)
            assertThat(thread.isAlive).isFalse()
            assertThat(player.engine.systems.size()).isEqualTo(0)
        } finally {
            semaphore.release()
            thread.quitSafely()
            thread.join(3_000)
        }
    }

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
