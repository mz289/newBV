package dev.frost819.newbv.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

/**
 * [OkHttpCdnSelector] 的单元测试。
 *
 * 测速依赖真实网络，这里只覆盖不依赖可达性的语义：
 * - 空输入、单元素短路、空白过滤；
 * - 全部不可达时保持入参顺序（全网失联保护：不惩罚任何主机，全部未测速 → 先验估计相同）；
 * - 注入 [CdnProber] 假实现后的排序语义：实测 ETA 覆盖入参顺序、失败主机冷却沉底、
 *   P2P 未测速时沉底、速度样本不跨视频复用（PR #31 host-model 语义）。
 */
class CdnSelectorTest {
    private val selector = OkHttpCdnSelector(OkHttpClient())

    @Test
    fun `rank empty returns empty`() =
        runTest {
            assertThat(selector.rank(emptyList())).isEmpty()
        }

    @Test
    fun `rank single url short-circuits`() =
        runTest {
            val url = "https://upos-sz-mirrorcos.bilivideo.com/v.m4s"
            assertThat(selector.rank(listOf(url))).containsExactly(url)
        }

    @Test
    fun `rank filters blank and duplicate urls`() =
        runTest {
            val url = "https://upos-sz-mirrorcos.bilivideo.com/v.m4s"
            assertThat(selector.rank(listOf("", "  ", url, url))).containsExactly(url)
        }

    @Test
    fun `rank preserves input order when all hosts are unreachable`() =
        runTest {
            // 端口 9 无服务，连接立即被拒；连接层失败且没有任何字节到达过（用户网络失联特征），
            // 不惩罚任何主机 → 全部未测速 → 先验估计相同 → 稳定排序保持原顺序
            val first = "https://127.0.0.1:9/first.m4s"
            val second = "https://127.0.0.1:9/second.m4s"
            assertThat(selector.rank(listOf(first, second))).containsExactly(first, second).inOrder()
        }

    @Test
    fun `rank orders by measured eta not input order`() =
        runTest {
            var now = 0L
            val fast = "https://upos-a.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val slow = "https://upos-b.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val prober =
                ScriptedProber { url ->
                    if (url == fast) {
                        CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 8.0 * 1024 * 1024)
                    } else {
                        CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 1.0 * 1024 * 1024)
                    }
                }
            val ranked =
                OkHttpCdnSelector(OkHttpClient(), nowMs = { now }, prober = prober)
                    .rank(listOf(slow, fast), "video-1")
            assertThat(ranked).containsExactly(fast, slow).inOrder()
        }

    @Test
    fun `rank sinks host whose probe failed while bytes have arrived elsewhere`() =
        runTest {
            var now = 0L
            val good = "https://upos-good.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val bad = "https://upos-bad.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val model = CdnHostModel { now }
            val selector =
                OkHttpCdnSelector(OkHttpClient(), nowMs = {
                    now
                }, prober = ScriptedProber { CdnProbeResult(CdnOutcome.CONNECT_FAIL) }, model = model)
            // 预置：good 本视频已有速度样本（无需再探）且刚送来过字节（网络可达）
            model.recordSpeed(CdnClassify.hostOf(good)!!, "video-1", ttfbMs = 50.0, rateBytesPerSec = 4.0 * 1024 * 1024)
            model.recordOutcome(CdnClassify.hostOf(good)!!, CdnOutcome.OK, videoKey = "video-1", now = 0)
            now = 10
            // bad 建连失败：有字节到达过 → 判定为主机自身问题 → 冷却沉底
            assertThat(selector.rank(listOf(bad, good), "video-1")).containsExactly(good, bad).inOrder()
        }

    @Test
    fun `unmeasured p2p candidate sinks below official ones`() =
        runTest {
            var now = 0L
            val officialA = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/x/v.m4s"
            val officialB = "https://upos-sz-mirrorhw.bilivideo.com/upgcxcode/x/v.m4s"
            val p2p = "https://upos-sz-302ppio.bilivideo.com/upgcxcode/x/v.m4s"
            // 全部探测失败且无字节到达（失联保护）→ 未测速 → 按层级排序，P2P 沉底；
            // 同层级保持入参相对顺序
            val ranked =
                OkHttpCdnSelector(
                    OkHttpClient(),
                    nowMs = { now },
                    prober = ScriptedProber { CdnProbeResult(CdnOutcome.CONNECT_FAIL) },
                ).rank(listOf(p2p, officialA, officialB), "video-1")
            assertThat(ranked).containsExactly(officialA, officialB, p2p).inOrder()
        }

    @Test
    fun `speed samples do not leak across videos`() =
        runTest {
            var now = 0L
            val a = "https://upos-a.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val b = "https://upos-b.bilivideo.com/upgcxcode/v.m4s?deadline=9999999999"
            val prober = ScriptedProber { CdnProbeResult(CdnOutcome.CONNECT_FAIL) }
            val selector = OkHttpCdnSelector(OkHttpClient(), nowMs = { now }, prober = prober)

            prober.script = { url ->
                if (url == a) {
                    CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 8.0 * 1024 * 1024)
                } else {
                    CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 1.0 * 1024 * 1024)
                }
            }
            assertThat(selector.rank(listOf(b, a), "video-1")).containsExactly(a, b).inOrder()

            // 新视频重新评估：同一组主机在视频 2 上互换快慢，排序应随之翻转而非沿用视频 1 的结论
            prober.script = { url ->
                if (url == a) {
                    CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 1.0 * 1024 * 1024)
                } else {
                    CdnProbeResult(CdnOutcome.OK, ttfbMs = 50.0, rateBytesPerSec = 8.0 * 1024 * 1024)
                }
            }
            assertThat(selector.rank(listOf(a, b), "video-2")).containsExactly(b, a).inOrder()
        }

    /** 可替换脚本的假探测器：按 URL 返回预置结果，测试中可随时换脚本。 */
    private class ScriptedProber(
        var script: (String) -> CdnProbeResult,
    ) : CdnProber {
        override fun probe(url: String): CdnProbeResult = script(url)
    }
}
