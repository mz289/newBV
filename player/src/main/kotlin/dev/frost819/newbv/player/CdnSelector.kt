package dev.frost819.newbv.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * CDN 地址选择器。
 *
 * 负责对候选播放地址做实测评估并给出推荐顺序，供播放器起播前选择最优节点。
 * 评估方式参考 SukkaW/Make-Bilibili-Great-Than-Ever-Before PR #31（thread-ripper host-model）：
 * 每个视频分别评估各主机的 TTFB 与吞吐，样本不跨视频复用；可靠性（冷却退避/排除/封禁）
 * 按主机维度学习，并接受播放侧的成败回报。
 */
interface CdnSelector {
    /**
     * 给出候选地址的推荐顺序（无需网络时也保证确定性：同估计值保持入参相对顺序）。
     *
     * 对本视频尚无样本的主机并发做小流量探测；探测失败且当时确有其他主机送来字节的主机
     * 进入冷却退避，排序时沉底；P2P / 无法识别的节点在没有实测数据支撑时排在官方节点之后。
     *
     * @param urls 候选播放地址（同一视频/音频轨道的 base + backup 列表）。
     * @param videoKey 同一视频各轨道共用的评估 key（如 `aid-cid-qn`），速度样本按它隔离、
     *   不跨视频复用；缺省时以首个候选地址的签名路径为 key。
     * @return 排序后的地址列表；入参为空时返回空列表。
     */
    suspend fun rank(
        urls: List<String>,
        videoKey: String? = null,
    ): List<String>

    /** 播放侧回报：某地址成功起播/播放（重置该主机的连续失败计数）。 */
    fun noteSuccess(
        url: String?,
        videoKey: String? = null,
    ) {
    }

    /**
     * 播放侧回报：某地址播放失败，按 [outcome] 对其主机冷却退避。
     * 播放器错误无法细分 HTTP 状态，调用方通常保持默认 [CdnOutcome.STALL] 即可，
     * 连续失败会经由退避逐步拉长冷却时间。
     */
    fun noteFailure(
        url: String?,
        videoKey: String? = null,
        outcome: CdnOutcome = CdnOutcome.STALL,
    ) {
    }
}

/** 单地址探测结果。 */
class CdnProbeResult(
    val outcome: CdnOutcome,
    /** 首字节耗时（毫秒），仅 [CdnOutcome.OK] 时有值。 */
    val ttfbMs: Double? = null,
    /** 首字节之后的吞吐（字节/秒），仅 [CdnOutcome.OK] 时有值。 */
    val rateBytesPerSec: Double? = null,
    /** 响应是否在不足样本量时提前结束。 */
    val partial: Boolean = false,
)

/** 对单个候选地址做小流量测速的探测器（阻塞式，调用方保证在工作线程上）。 */
fun interface CdnProber {
    fun probe(url: String): CdnProbeResult
}

/**
 * 基于 OkHttp Range 请求的 CDN 测速选择器。
 *
 * 探测对每个候选主机发起 `Range: bytes=0-<probeBytes-1>` 请求，分别记录首字节耗时（TTFB）
 * 与首字节之后的吞吐；排序按"读取 [startupBytes] 的预计耗时（ETA）"进行，实测值覆盖
 * 层级与先验估计。速度样本按视频 key 隔离，可靠性（冷却/排除/封禁）由 [model] 维护。
 *
 * @param client 复用的 OkHttpClient，内部会派生一个短超时的探测专用 client。
 * @param probeBytes 每次探测读取的样本字节数。
 * @param probeTimeoutMs 单地址探测超时。
 * @param startupBytes 排序时假想起播需要读取的字节数，用于把 TTFB + 传输时间合成 ETA。
 * @param nowMs 时钟，可注入以便测试。
 * @param prober 探测器，缺省为 [OkHttpCdnProber]；可注入假实现以便测试。
 * @param model 主机状态模型，可注入以便测试。
 */
class OkHttpCdnSelector(
    client: OkHttpClient,
    probeBytes: Long = DEFAULT_PROBE_BYTES,
    probeTimeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    private val startupBytes: Long = DEFAULT_STARTUP_BYTES,
    private val nowMs: () -> Long = System::currentTimeMillis,
    prober: CdnProber? = null,
    private val model: CdnHostModel = CdnHostModel(nowMs),
) : CdnSelector {
    private val prober: CdnProber = prober ?: OkHttpCdnProber(client, probeBytes, probeTimeoutMs)

    override suspend fun rank(
        urls: List<String>,
        videoKey: String?,
    ): List<String> {
        val candidates = urls.filter { it.isNotBlank() }.distinct()
        if (candidates.size <= 1) return candidates

        val key = videoKey ?: addressKeyOf(candidates.first())
        val now = nowMs()

        // 并发补测：本视频尚无样本的主机（同一主机多个地址只探一次）
        val toProbe =
            candidates
                .distinctBy { hostOf(it) }
                .filter { model.needsProbe(hostOf(it), addressKeyOf(it), key, now) }
        if (toProbe.isNotEmpty()) {
            coroutineScope {
                toProbe
                    .map { url ->
                        async(Dispatchers.IO) {
                            val result = prober.probe(url)
                            model.recordProbe(
                                hostname = hostOf(url),
                                addressKey = addressKeyOf(url),
                                videoKey = key,
                                outcome = result.outcome,
                                ttfbMs = result.ttfbMs,
                                rateBytesPerSec = result.rateBytesPerSec,
                                partial = result.partial,
                                now = nowMs(),
                            )
                        }
                    }.awaitAll()
            }
        }

        val rankedAt = nowMs()
        // sortedWith 为稳定排序：可用性分组、层级、ETA 全部相同时保持入参（B 站指派）顺序
        return candidates
            .withIndex()
            .sortedWith(
                compareBy(
                    { usabilityGroup(it.value, key, rankedAt) },
                    { CdnClassify.tierOf(it.value).rank },
                    { model.estimate(hostOf(it.value), key, startupBytes, rankedAt).etaMs },
                ),
            ).map { it.value }
    }

    override fun noteSuccess(
        url: String?,
        videoKey: String?,
    ) {
        if (url.isNullOrBlank()) return
        model.recordOutcome(hostOf(url), CdnOutcome.OK, videoKey ?: addressKeyOf(url), addressKeyOf(url), nowMs())
    }

    override fun noteFailure(
        url: String?,
        videoKey: String?,
        outcome: CdnOutcome,
    ) {
        if (url.isNullOrBlank()) return
        model.recordOutcome(hostOf(url), outcome, videoKey ?: addressKeyOf(url), addressKeyOf(url), nowMs())
    }

    /** 可用性分组：可用 0 < 冷却中 1 < 已排除/封禁 2。 */
    private fun usabilityGroup(
        url: String,
        videoKey: String,
        now: Long,
    ): Int =
        when {
            model.isAddressBanned(addressKeyOf(url)) -> 2
            model.isExcluded(hostOf(url), videoKey, now) -> 2
            model.isCoolingDown(hostOf(url), now) -> 1
            else -> 0
        }

    private fun hostOf(url: String): String = CdnClassify.hostOf(url) ?: url

    /** 签名地址 key（pathname + query），同一签名换主机仍在同一 key 下。 */
    private fun addressKeyOf(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val path = uri.rawPath ?: ""
        val query = uri.rawQuery
        return if (query != null) "$path?$query" else path
    }

    companion object {
        /** 默认样本字节数：256 KB。 */
        const val DEFAULT_PROBE_BYTES = 256L * 1024

        /** 默认单地址探测超时：2.5 秒。 */
        const val DEFAULT_PROBE_TIMEOUT_MS = 2_500L

        /** 默认起播假想读取量：1 MiB。 */
        const val DEFAULT_STARTUP_BYTES = 1024L * 1024
    }
}

/**
 * OkHttp 实现的探测器。
 *
 * HTTP 状态到 [CdnOutcome] 的映射与 PR #31 的 `MediaOutcome` 一致：
 * 404/410 视为文件缺失，412/429/503 视为限流，401/403 按签名是否已过 `deadline`
 * 区分地址过期与主机拒绝，其余 4xx 视为拒绝，5xx 视为服务器错误。
 */
class OkHttpCdnProber(
    client: OkHttpClient,
    private val probeBytes: Long = OkHttpCdnSelector.DEFAULT_PROBE_BYTES,
    timeoutMs: Long = OkHttpCdnSelector.DEFAULT_PROBE_TIMEOUT_MS,
) : CdnProber {
    private val probeClient =
        client
            .newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

    override fun probe(url: String): CdnProbeResult {
        val startedAtNs = System.nanoTime()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=0-${probeBytes - 1}")
                .header("User-Agent", PROBE_USER_AGENT)
                .header("Referer", "https://www.bilibili.com")
                .build()
        val response =
            try {
                probeClient.newCall(request).execute()
            } catch (e: Exception) {
                return CdnProbeResult(CdnOutcome.CONNECT_FAIL)
            }
        return response.use { readOutcome(it, url, startedAtNs) }
    }

    private fun readOutcome(
        response: Response,
        url: String,
        startedAtNs: Long,
    ): CdnProbeResult =
        when (response.code) {
            200, 206 -> readSample(response, startedAtNs)
            404, 410 -> CdnProbeResult(CdnOutcome.MISSING)
            412, 429, 503 -> CdnProbeResult(CdnOutcome.THROTTLED)
            401, 403 ->
                if (isSignatureExpired(
                        url,
                    )
                ) {
                    CdnProbeResult(CdnOutcome.EXPIRED)
                } else {
                    CdnProbeResult(CdnOutcome.REFUSED)
                }
            in 500..599 -> CdnProbeResult(CdnOutcome.SERVER_ERROR)
            else -> CdnProbeResult(CdnOutcome.REFUSED)
        }

    /** 读样本：首字节耗时即 TTFB，其余样本换算为首字节之后的吞吐。 */
    private fun readSample(
        response: Response,
        startedAtNs: Long,
    ): CdnProbeResult {
        val body = response.body ?: return CdnProbeResult(CdnOutcome.UNUSABLE)
        val buffer = Buffer()
        var ttfbMs: Double? = null
        var total = 0L
        try {
            val source = body.source()
            while (total < probeBytes) {
                val read = source.read(buffer, probeBytes - total)
                if (read == -1L) break
                if (ttfbMs == null) ttfbMs = (System.nanoTime() - startedAtNs) / 1_000_000.0
                total += read
                buffer.clear()
            }
        } catch (e: IOException) {
            if (ttfbMs == null) return CdnProbeResult(CdnOutcome.CONNECT_FAIL)
            // 首字节已到、传输中断：只保留 TTFB 样本，按部分样本记录
            return CdnProbeResult(CdnOutcome.OK, ttfbMs = ttfbMs, partial = true)
        }
        if (total <= 0 || ttfbMs == null) return CdnProbeResult(CdnOutcome.UNUSABLE)
        val bodyMs = (System.nanoTime() - startedAtNs) / 1_000_000.0 - ttfbMs
        val rate = if (bodyMs > 0) total / (bodyMs / 1000.0) else null
        return CdnProbeResult(CdnOutcome.OK, ttfbMs = ttfbMs, rateBytesPerSec = rate, partial = total < probeBytes)
    }

    /** B 站签名地址带 `deadline=<unix 秒>`；已过期的 403 是地址失效，而非主机拒绝。 */
    private fun isSignatureExpired(url: String): Boolean {
        val deadline =
            DEADLINE_REGEX
                .find(url)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull() ?: return false
        return deadline < System.currentTimeMillis() / 1000
    }

    private companion object {
        private val DEADLINE_REGEX = Regex("[?&]deadline=(\\d+)")

        private const val PROBE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
