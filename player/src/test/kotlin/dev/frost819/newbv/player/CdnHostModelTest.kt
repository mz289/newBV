package dev.frost819.newbv.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [CdnHostModel] 的单元测试（纯状态模型，不依赖网络）。
 *
 * 覆盖 PR #31 host-model 的核心语义：先验/典型值回退、稳健中位数、冷连接惩罚、
 * 指数退避冷却与成功重置、失联保护、按视频隔离与 LRU 淘汰、排除与封禁。
 */
class CdnHostModelTest {
    private var now = 0L
    private val model = CdnHostModel { now }

    private val mib = 1024.0 * 1024

    @Test
    fun `unmeasured host falls back to priors`() {
        model.recordOutcome("host", CdnOutcome.OK, videoKey = "video", now = 0)
        val estimate = model.estimate("host", "video", bytes = (1 * mib).toLong())
        assertThat(estimate.measured).isFalse()
        assertThat(estimate.ttfbMs).isEqualTo(350.0)
        assertThat(estimate.rateBytesPerSec).isEqualTo(768.0 * 1024)
        // eta = ttfb + bytes / rate
        assertThat(estimate.etaMs).isWithin(1e-6).of(350.0 + 1 * mib * 1000 / (768.0 * 1024))
    }

    @Test
    fun `unmeasured host uses median of measured hosts on the same video`() {
        model.recordSpeed("a", "video", ttfbMs = 100.0, rateBytesPerSec = 2 * mib)
        model.recordSpeed("b", "video", ttfbMs = 300.0, rateBytesPerSec = 6 * mib)
        model.recordOutcome("c", CdnOutcome.OK, videoKey = "video", now = 0)

        val estimate = model.estimate("c", "video", bytes = 0)
        assertThat(estimate.measured).isFalse()
        assertThat(estimate.ttfbMs).isEqualTo(200.0)
        assertThat(estimate.rateBytesPerSec).isEqualTo(4 * mib)
    }

    @Test
    fun `own samples override typical and mark measured`() {
        model.recordSpeed("a", "video", ttfbMs = 100.0, rateBytesPerSec = 2 * mib)
        model.recordSpeed("b", "video", ttfbMs = 300.0, rateBytesPerSec = 6 * mib)
        model.recordSpeed("c", "video", ttfbMs = 1000.0, rateBytesPerSec = 0.5 * mib)
        model.recordOutcome("c", CdnOutcome.OK, videoKey = "video", now = 0)

        val estimate = model.estimate("c", "video", bytes = 0)
        assertThat(estimate.measured).isTrue()
        assertThat(estimate.ttfbMs).isEqualTo(1000.0)
        assertThat(estimate.rateBytesPerSec).isEqualTo(0.5 * mib)
    }

    @Test
    fun `robust median reacts to sudden ttfb worsening`() {
        repeat(3) { model.recordSpeed("a", "video", ttfbMs = 100.0, rateBytesPerSec = null) }
        model.recordSpeed("a", "video", ttfbMs = 400.0, rateBytesPerSec = null)
        model.recordSpeed("a", "video", ttfbMs = 400.0, rateBytesPerSec = null)
        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video", now = 0)

        // 最近两个样本都超过中位数 2 倍：取两者较小值，立刻反映劣化
        assertThat(model.estimate("a", "video", bytes = 0).ttfbMs).isEqualTo(400.0)
    }

    @Test
    fun `robust median reacts to sudden rate drop`() {
        repeat(3) { model.recordSpeed("a", "video", ttfbMs = null, rateBytesPerSec = mib) }
        model.recordSpeed("a", "video", ttfbMs = null, rateBytesPerSec = 0.1 * mib)
        model.recordSpeed("a", "video", ttfbMs = null, rateBytesPerSec = 0.1 * mib)
        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video", now = 0)

        // 最近两个样本都不足中位数一半：取两者较大值
        assertThat(model.estimate("a", "video", bytes = 0).rateBytesPerSec).isEqualTo(0.1 * mib)
    }

    @Test
    fun `only recent samples are kept`() {
        repeat(12) { model.recordSpeed("a", "video", ttfbMs = it.toDouble(), rateBytesPerSec = null) }
        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video", now = 0)
        val estimate = model.estimate("a", "video", bytes = 0)
        // 只保留最近 8 个样本（4..11），中位数 = (7+8)/2
        assertThat(estimate.ttfbMs).isEqualTo(7.5)
    }

    @Test
    fun `idle connection doubles ttfb`() {
        model.recordSpeed("a", "video", ttfbMs = 100.0, rateBytesPerSec = mib)
        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video", now = 0)

        now = 1000
        assertThat(model.estimate("a", "video", bytes = 0).ttfbMs).isEqualTo(100.0)

        now = 31_000
        assertThat(model.estimate("a", "video", bytes = 0).ttfbMs).isEqualTo(200.0)
    }

    @Test
    fun `connect failures back off exponentially and success resets streak`() {
        // 先让字节流动过，否则失联保护会吞掉连接层失败
        model.recordOutcome("peer", CdnOutcome.OK, videoKey = "video", now = 0)

        model.recordOutcome("a", CdnOutcome.CONNECT_FAIL, videoKey = "video", now = 0)
        assertThat(model.isCoolingDown("a", now = 1000)).isTrue()
        model.recordOutcome("a", CdnOutcome.CONNECT_FAIL, videoKey = "video", now = 1000)
        // 第 2 次失败：60s * 2^1 = 120s，冷却截止 1000 + 120000
        assertThat(model.isCoolingDown("a", now = 121_000)).isFalse()

        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video", now = 121_000)
        model.recordOutcome("a", CdnOutcome.CONNECT_FAIL, videoKey = "video", now = 121_100)
        // 成功重置后回到 60s 基线
        assertThat(model.isCoolingDown("a", now = 181_100)).isFalse()
    }

    @Test
    fun `connection failures are ignored when the whole network looks down`() {
        // 从未有字节到达：连接层失败视为用户自身网络问题，不冷却任何主机
        model.recordOutcome("a", CdnOutcome.CONNECT_FAIL, videoKey = "video", now = 0)
        model.recordOutcome("a", CdnOutcome.TRUNCATED, videoKey = "video", now = 0)
        assertThat(model.isCoolingDown("a", now = 0)).isFalse()
        // 但非连接层失败（如 404 缺失）仍然生效
        model.recordOutcome("b", CdnOutcome.MISSING, videoKey = "video", now = 0)
        assertThat(model.isExcluded("b", "video", now = 0)).isTrue()
    }

    @Test
    fun `refused excludes the host for the video for a minute`() {
        model.recordOutcome("peer", CdnOutcome.OK, videoKey = "video", now = 0)
        model.recordOutcome("a", CdnOutcome.REFUSED, videoKey = "video", now = 0)
        assertThat(model.isExcluded("a", "video", now = 59_999)).isTrue()
        assertThat(model.isExcluded("a", "video", now = 60_001)).isFalse()
    }

    @Test
    fun `expired signature bans the address`() {
        model.recordOutcome("a", CdnOutcome.EXPIRED, addressKey = "/v.m4s?deadline=1", now = 0)
        assertThat(model.isAddressBanned("/v.m4s?deadline=1")).isTrue()
        assertThat(model.isAddressBanned("/v.m4s?deadline=2")).isFalse()
    }

    @Test
    fun `speed samples are isolated per video`() {
        model.recordSpeed("a", "video-1", ttfbMs = 50.0, rateBytesPerSec = 8 * mib)
        model.recordOutcome("a", CdnOutcome.OK, videoKey = "video-1", now = 0)

        // 新视频不沿用旧样本：重新需要探测，估计回到先验值
        assertThat(model.needsProbe("a", "/v.m4s", "video-2")).isTrue()
        val estimate = model.estimate("a", "video-2", bytes = 0)
        assertThat(estimate.measured).isFalse()
        assertThat(estimate.ttfbMs).isEqualTo(350.0)
    }

    @Test
    fun `needsProbe skips cooled down excluded and banned hosts`() {
        // 先让字节流动过，否则失联保护会吞掉 a 的连接层失败
        model.recordOutcome("peer", CdnOutcome.OK, videoKey = "video", now = 0)
        model.recordOutcome("a", CdnOutcome.CONNECT_FAIL, videoKey = "video", now = 0)
        assertThat(model.needsProbe("a", "/a.m4s", "video")).isFalse()

        model.recordOutcome("b", CdnOutcome.MISSING, videoKey = "video", now = 0)
        assertThat(model.needsProbe("b", "/b.m4s", "video")).isFalse()

        model.recordOutcome("c", CdnOutcome.EXPIRED, addressKey = "/c.m4s", now = 0)
        assertThat(model.needsProbe("c", "/c.m4s", "video")).isFalse()
    }

    @Test
    fun `speed samples evict least recently used videos beyond the cap`() {
        repeat(33) { v -> model.recordSpeed("host-$v", "video-$v", ttfbMs = 100.0, rateBytesPerSec = mib) }
        // video-0 最久未用，已被淘汰，重新需要探测
        assertThat(model.needsProbe("host-0", "/0.m4s", "video-0")).isTrue()
        // 最近写入的 video-32 样本仍在
        assertThat(model.needsProbe("host-32", "/32.m4s", "video-32")).isFalse()
    }
}
