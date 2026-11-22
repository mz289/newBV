package dev.frost819.newbv.player.racing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.frost819.newbv.player.CdnHostModel
import dev.frost819.newbv.player.CdnOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [RacingJob] 的单元测试（假传输层，不依赖网络与 media3）。
 *
 * 覆盖 PR #31 thread-ripper 的核心行为：
 * - 正常下载（单主机、未知长度流 EOF 收尾）
 * - 首选主机连接失败 → 换宿继续（「a racer never goes to the same host」）
 * - 提交超时 → job 失败 + 原生回退窗口（重试走非竞速单流，单流失败按候选序换宿）
 * - 注册表：P2P / `os=mcdn` 主机不进换宿池；内容分歧按视频排除比对者、不停竞速
 * - 真实下载的吞吐进入主机模型（measured）
 */
class RacingDataSourceTest {
    private val content = ByteArray(4096) { (it % 251).toByte() }

    private fun mediaUrl(host: String) = "https://$host/upgcxcode/00/66/43750526600/43750526600-1-30080.m4s?upsig=x&nettype=0"

    /** 假传输：按主机名分发行为（返回切片 / 抛连接失败 / 挂起直到放行）。 */
    private class FakeTransport : Transport {
        inner class FakeHost {
            var failConnect = false
            var content: ByteArray? = null
            var openLatch: CountDownLatch? = null
        }

        val handlers = HashMap<String, FakeHost>()

        fun host(name: String): FakeHost = handlers.getOrPut(name) { FakeHost() }

        override fun open(
            url: String,
            rangeStart: Long,
            rangeEndInclusive: Long?,
        ): TransportResponse {
            val hostName = Regex("https://([^/]+)/").find(url)!!.groupValues[1]
            val host = handlers[hostName] ?: throw IOException("no handler for $hostName")
            host.openLatch?.await(5, TimeUnit.SECONDS)
            if (host.failConnect) throw IOException("connect fail")
            val content = host.content ?: throw IOException("no content")
            val from = rangeStart.toInt().coerceIn(0, content.size)
            val to =
                if (rangeEndInclusive == null) {
                    content.size
                } else {
                    minOf(rangeEndInclusive.toInt() + 1, content.size)
                }
            val slice = content.copyOfRange(from, to)
            return object : TransportResponse {
                override val code = 206
                override val contentRange = "bytes $rangeStart-${to - 1}/${content.size}"
                private var pos = 0

                override fun read(sink: ByteArray, offset: Int, length: Int): Int {
                    if (pos >= slice.size) return -1
                    val n = minOf(length, slice.size - pos)
                    System.arraycopy(slice, pos, sink, offset, n)
                    pos += n
                    return n
                }

                override fun close() {}
            }
        }
    }

    private fun readAll(job: RacingJob): ByteArray {
        val out = ArrayList<Byte>()
        val buf = ByteArray(4096)
        while (true) {
            val n = job.read(buf, 0, buf.size)
            if (n <= 0) break // EOS(-1) / 关闭
            out.addAll(buf.take(n).toList())
        }
        return out.toByteArray()
    }

    /** 可编排分块/门控的假传输：驱动影子比对的真实时序（leader 末块等待、比对者受控放行）。 */
    private class GatedTransport : Transport {
        class Conf(
            var chunks: List<ByteArray> = emptyList(),
            /** 交付第 N 块（0 起）之前等待 [gate]。 */
            var gateBeforeChunk: Int = -1,
            var gate: CountDownLatch? = null,
            /** 交付完第 N 块之后对 [countdown] 放行。 */
            var afterChunk: Int = -1,
            var countdown: CountDownLatch? = null,
        )

        val hosts = LinkedHashMap<String, Conf>()

        fun host(
            name: String,
            conf: Conf,
        ) {
            hosts[name] = conf
        }

        override fun open(
            url: String,
            rangeStart: Long,
            rangeEndInclusive: Long?,
        ): TransportResponse {
            val hostName = Regex("https://([^/]+)/").find(url)!!.groupValues[1]
            val conf = hosts.getValue(hostName)
            var idx = 0
            return object : TransportResponse {
                override val code = 206
                override val contentRange = "bytes $rangeStart-${rangeEndInclusive ?: 4095}/4096"

                override fun read(
                    sink: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (idx >= conf.chunks.size) return -1
                    if (idx == conf.gateBeforeChunk) conf.gate?.await(5, TimeUnit.SECONDS)
                    val chunk = conf.chunks[idx]
                    val n = minOf(chunk.size, length)
                    System.arraycopy(chunk, 0, sink, offset, n)
                    idx++
                    if (idx - 1 == conf.afterChunk) conf.countdown?.countDown()
                    return n
                }

                override fun close() {}
            }
        }
    }

    private fun directExecutor(): Executor = Executor { it.run() }

    @Test
    fun `downloads from a single host with unknown length`() {
        val transport = FakeTransport()
        transport.host("upos-a.bilivideo.com").content = content
        val registry = CdnRacingRegistry(CdnHostModel())
        val job =
            RacingJob(
                originalUrl = mediaUrl("upos-a.bilivideo.com"),
                position = 0,
                length = null,
                registry = registry,
                executor = directExecutor(),
                transport = transport,
                racing = true,
            )
        job.open()
        val data = readAll(job)
        assertThat(data).isEqualTo(content)
        assertThat(job.currentUri()).contains("upos-a.bilivideo.com")
        // EOS 哨兵：流结束后必须是 -1（media3 DataReader 契约禁止 0——返回 0 会让
        // extractor 无限重读，播放到 EOF 后永远缓冲中）
        assertThat(job.read(ByteArray(16), 0, 16)).isEqualTo(RacingJob.EOS)
        job.close()
    }

    @Test
    fun `connect failure on the assigned host falls over to another host`() {
        val transport = FakeTransport()
        transport.host("bad.bilivideo.com").failConnect = true
        transport.host("upos-good.bilivideo.com").content = content
        val registry = CdnRacingRegistry(CdnHostModel())
        registry.learnMediaUrl(mediaUrl("upos-good.bilivideo.com"))
        val job =
            RacingJob(
                originalUrl = mediaUrl("bad.bilivideo.com"),
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = directExecutor(),
                transport = transport,
                racing = true,
            )
        job.open()
        val data = readAll(job)
        assertThat(data).isEqualTo(content)
        assertThat(job.currentUri()).contains("upos-good.bilivideo.com")
        job.close()
    }

    @Test
    fun `commit timeout fails the job and opens the native retry window`() {
        val transport = FakeTransport()
        val latch = CountDownLatch(1)
        transport.host("stuck.bilivideo.com").openLatch = latch
        transport.host("stuck.bilivideo.com").content = content
        val registry = CdnRacingRegistry(CdnHostModel())
        val pool = Executors.newCachedThreadPool()
        val url = mediaUrl("stuck.bilivideo.com")
        val job =
            RacingJob(
                originalUrl = url,
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = pool,
                transport = transport,
                commitTimeoutMs = 200,
                stallMs = 100,
                racing = true,
            )
        job.open()
        assertThrows<IOException> { readAll(job) }
        // 原生回退窗口生效
        assertThat(registry.isNativeRetry(CdnRacingRegistry.addressKeyOf(url))).isTrue()
        // 放行后重开：走原生路径（非竞速），直接消费原始 URL 的数据
        latch.countDown()
        val nativeJob =
            RacingJob(
                originalUrl = url,
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = pool,
                transport = transport,
                racing = false,
            )
        nativeJob.open()
        val data = readAll(nativeJob)
        assertThat(data).isEqualTo(content)
        pool.shutdownNow()
    }

    @Test
    fun `registry excludes p2p and os=mcdn hosts from the swap pool`() {
        val registry = CdnRacingRegistry(CdnHostModel())
        registry.learnMediaUrl(mediaUrl("upos-good.bilivideo.com"))
        // mcdn / szbdyd / os=mcdn 自有域名永不进池
        registry.learnMediaUrl("https://xy.mcdn.bilivideo.com:4483/upgcxcode/0/1/x/v.m4s?os=mcdn")
        registry.learnMediaUrl("https://x.szbdyd.com/upgcxcode/0/1/x/v.m4s")
        registry.learnMediaUrl("https://hze5261c.edge.mountaintoys.cn:4483/upgcxcode/0/1/x/v.m4s?os=mcdn")
        val url = mediaUrl("upos-good.bilivideo.com")
        val hosts =
            registry.candidateHosts(url, "43750526600", CdnRacingRegistry.addressKeyOf(url))
        assertThat(hosts).containsExactly("upos-good.bilivideo.com")
    }

    @Test
    fun `integrity mismatch excludes the divergent host and keeps the job alive`() {
        // 真实时序驱动：leader（a）分块交付并在末块前等待；比对者（b）在 leader 首帧被
        // 晋升（isLaggard=true）后才开始交付 → 全量进影子。分歧点落在两者影子重叠区内。
        // 期望：分歧被检出 → b 按 UNUSABLE 排除出本视频；job 不失败、竞速不停用
        // （全局停用会让优选在起播瞬间自废，2026-11-22 E2E 实测）。
        val divergent = content.copyOf().also { it[3500] = (it[3500] + 1).toByte() }
        val bGate = CountDownLatch(1)
        val aLastGate = CountDownLatch(1)
        val transport = GatedTransport()
        transport.host(
            "upos-a.bilivideo.com",
            GatedTransport.Conf(
                chunks = content.toList().chunked(1024).map { it.toByteArray() },
                gateBeforeChunk = 3,
                gate = aLastGate,
            ),
        )
        transport.host(
            "upos-b.bilivideo.com",
            GatedTransport.Conf(
                chunks = listOf(divergent),
                gateBeforeChunk = 0,
                gate = bGate,
                afterChunk = 0,
                countdown = aLastGate,
            ),
        )
        val model = CdnHostModel()
        val registry = CdnRacingRegistry(model)
        registry.learnMediaUrl(mediaUrl("upos-b.bilivideo.com"))
        val pool = Executors.newCachedThreadPool()
        val job =
            RacingJob(
                originalUrl = mediaUrl("upos-a.bilivideo.com"),
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = pool,
                transport = transport,
                racing = true,
            )
        job.open()
        val out = java.io.ByteArrayOutputStream()
        val reader =
            Thread {
                val buf = ByteArray(4096)
                while (true) {
                    val n = job.read(buf, 0, buf.size)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
        reader.start()
        // leader 首帧已交付（= 比对者已降级为影子捕获）再放行比对者
        while (out.size() == 0) Thread.sleep(5)
        bGate.countDown()
        reader.join(10_000)
        val got = out.toByteArray()
        val diffAt = content.indices.firstOrNull { got.getOrNull(it) != content[it] } ?: -1
        assertWithMessage("byte diff at $diffAt, got ${got.size} bytes (expect ${content.size})")
            .that(got.toList()).isEqualTo(content.toList())
        assertThat(registry.racingAllowed()).isTrue()
        assertThat(model.isExcluded("upos-b.bilivideo.com", "43750526600", System.currentTimeMillis())).isTrue()
        job.close()
        pool.shutdownNow()
    }

    @Test
    fun `native fallback walks to another host when the assigned one is unreachable`() {
        // 原生回退窗口 ≠ 钉死原始主机：直连失败按候选序换宿（不可达指派不再拖成加载黑窗）
        val transport = FakeTransport()
        transport.host("bad.bilivideo.com").failConnect = true
        transport.host("upos-good.bilivideo.com").content = content
        val registry = CdnRacingRegistry(CdnHostModel())
        registry.learnMediaUrl(mediaUrl("upos-good.bilivideo.com"))
        val job =
            RacingJob(
                originalUrl = mediaUrl("bad.bilivideo.com"),
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = directExecutor(),
                transport = transport,
                racing = false,
            )
        job.open()
        val data = readAll(job)
        assertThat(data).isEqualTo(content)
        assertThat(job.currentUri()).contains("upos-good.bilivideo.com")
        job.close()
    }

    @Test
    fun `outcome recording flows through the registry into the host model`() {
        val model = CdnHostModel()
        val registry = CdnRacingRegistry(model)
        val transport = FakeTransport()
        transport.host("upos-a.bilivideo.com").content = content
        // 推进时钟：保证 TTFB 与传输耗时不是同一毫秒（rate 才有值）
        var clock = 0L
        val job =
            RacingJob(
                originalUrl = mediaUrl("upos-a.bilivideo.com"),
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = directExecutor(),
                nowMs = { clock += 13; clock },
                transport = transport,
                racing = true,
            )
        job.open()
        readAll(job)
        job.close()
        val estimate = model.estimate("upos-a.bilivideo.com", "43750526600", bytes = 0)
        // 真实下载中测得的吞吐已进入模型（measured）
        assertThat(estimate.measured).isTrue()
    }

    @Test
    fun `successful host wins the first-piece race and laggards are compared`() {
        // 直接执行器下两个主机先后完成：后到的成为比对者，迟到帧被丢弃，不破坏字节序
        val transport = FakeTransport()
        transport.host("upos-a.bilivideo.com").content = content
        transport.host("upos-b.bilivideo.com").content = content
        val registry = CdnRacingRegistry(CdnHostModel())
        registry.learnMediaUrl(mediaUrl("upos-b.bilivideo.com"))
        val job =
            RacingJob(
                originalUrl = mediaUrl("upos-a.bilivideo.com"),
                position = 0,
                length = content.size.toLong(),
                registry = registry,
                executor = directExecutor(),
                transport = transport,
                racing = true,
            )
        job.open()
        val data = readAll(job)
        assertThat(data).isEqualTo(content)
        assertThat(registry.racingAllowed()).isTrue()
        job.close()
    }
}
