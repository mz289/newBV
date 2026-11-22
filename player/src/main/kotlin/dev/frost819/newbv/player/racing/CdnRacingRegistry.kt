package dev.frost819.newbv.player.racing

import dev.frost819.newbv.player.CdnClassify
import dev.frost819.newbv.player.CdnHostModel
import dev.frost819.newbv.player.CdnOutcome
import java.net.URI

/**
 * CDN 换宿注册表（PR #31 thread-ripper 的 page 级状态移植）。
 *
 * 维护竞速下载所需的会话级状态：
 * - **已知 upgcxcode 主机池**：upos 系主机的签名不含主机，任意已知主机都可以服务
 *   任意 upgcxcode 签名地址，因此可以从 playinfo 与历史请求里学习主机池并在它们之间换宿
 *   （PR：「Every host a playinfo names becomes a candidate」）。
 * - **原生回退窗口**：一个 job 失败后 30s 内，同地址的直接请求不再竞速，走非竞速单流；
 *   单流失败时按候选序换宿重试（PR `NATIVE_RETRY_MS`：「After a failed job, the player's
 *   retry of the same range goes to the browser」）。
 * - **连续失败熔断**：一分钟内 [MAX_JOB_FAILURES] 个 job 失败 → 竞速整体停用
 *   [TURN_OFF_MS]，全部走原生请求（PR `FAILURES_TO_TURN_OFF`）。
 * - **内容分歧处理**：两个主机对同一段字节内容不一致 → 比对者按 UNUSABLE 排除出本视频
 *   （由 [CdnHostModel] 持久排除），job 继续交付；不做全局停用——比对错位造成的假分歧
 *   在起播第一片就会触发，全局熔断会让优选自废。
 *
 * 可靠性（冷却/排除/封禁）与速度样本仍由 [CdnHostModel] 维护，本类只做主机池与熔断。
 */
class CdnRacingRegistry(
    private val model: CdnHostModel,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** 已知可换宿的 upgcxcode 主机（非 P2P），访问序 LRU。 */
    private val knownHosts = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean = size > MAX_HOSTS
    }

    /** 地址 key（path?query）→ 原生回退截止时间。 */
    private val nativeRetryUntil = HashMap<String, Long>()

    /** 最近的 job 失败时间戳（用于一分钟窗口的熔断计数）。 */
    private val recentJobFailures = ArrayDeque<Long>()

    /** 熔断（连续失败）截止时间。 */
    private var turnedOffUntil = 0L

    private val lock = Any()

    /** videoKey -> (host -> 失败计入截止时间)：竞速尝试失败的主机在 TTL 内不再作为候选。 */
    private val recentFailedHosts = HashMap<String, MutableMap<String, Long>>()

    /**
     * 从一条媒体地址学习主机池。
     *
     * 只收录 upgcxcode 签名 + 非 P2P 主机（mirror / bcache）；
     * P2P / mcdn / szbdyd 主机永不进入换宿池。
     */
    fun learnMediaUrl(url: String) {
        val path = CdnClassify.mediaPathKey(url)?.substringBefore('?') ?: return
        if (!path.contains("/upgcxcode/")) return
        val host = CdnClassify.hostOf(url) ?: return
        if (CdnClassify.isP2pUrl(url)) return
        if (!isBilibiliMediaHost(host)) return
        synchronized(lock) {
            knownHosts[host] = Unit
        }
    }

    /** 是否为 B 站媒体域名（竞速只对站内媒体生效）。 */
    fun isBilibiliMediaUrl(url: String): Boolean = CdnClassify.hostOf(url)?.let(::isBilibiliMediaHost) == true

    /** 竞速当前是否可用（未熔断）。 */
    fun racingAllowed(): Boolean {
        val now = nowMs()
        return synchronized(lock) {
            now >= turnedOffUntil
        }
    }

    /** 该地址是否处于原生回退窗口内（上次 job 失败后 30s）。 */
    fun isNativeRetry(addressKey: String): Boolean =
        synchronized(lock) {
            nativeRetryUntil[addressKey]?.let { it > nowMs() } == true
        }

    /** 记录一次 job 失败：进入原生回退窗口，并按一分钟窗口计数决定是否整体熔断。 */
    fun noteJobFailure(addressKey: String) {
        val now = nowMs()
        synchronized(lock) {
            nativeRetryUntil[addressKey] = now + NATIVE_RETRY_MS
            recentJobFailures.addLast(now)
            while (recentJobFailures.isNotEmpty() && now - recentJobFailures.first() > FAILURE_WINDOW_MS) {
                recentJobFailures.removeFirst()
            }
            if (recentJobFailures.size >= MAX_JOB_FAILURES) {
                turnedOffUntil = now + TURN_OFF_MS
                recentJobFailures.clear()
            }
        }
    }

    /** 竞速尝试在该视频上失败：TTL 内不再作为候选（跨片/跨 job 学习，PR 的 page 级可靠性）。 */
    fun noteAttemptFailure(
        host: String,
        videoKey: String,
    ) {
        val now = nowMs()
        synchronized(lock) {
            recentFailedHosts
                .getOrPut(videoKey) { HashMap() }[host] = now + ATTEMPT_FAILURE_TTL_MS
        }
    }

    private fun isRecentlyFailed(
        host: String,
        videoKey: String,
        now: Long,
    ): Boolean =
        synchronized(lock) {
            recentFailedHosts[videoKey]?.get(host)?.let { it > now } == true
        }

    /**
     * 给出对该地址可用的换宿候选主机（按 [CdnHostModel] 的 ETA 升序，
     * 已按冷却/排除/封禁/P2P/近期尝试失败过滤），最多 [max] 个。
     *
     * 原始主机也参与排序——PR 原话：「Bilibili's own assigned host gets no special trust」。
     */
    fun candidateHosts(
        url: String,
        videoKey: String,
        addressKey: String,
        max: Int = MAX_CANDIDATE_HOSTS,
    ): List<String> {
        val now = nowMs()
        val original = CdnClassify.hostOf(url)
        val pool = synchronized(lock) { knownHosts.keys.toList() }
        val hosts = (listOfNotNull(original) + pool).distinct().filter { host ->
            !model.isCoolingDown(host, now) &&
                !model.isExcluded(host, videoKey, now) &&
                !model.isAddressBanned(addressKey) &&
                !isRecentlyFailed(host, videoKey, now)
        }
        val ranked =
            hosts
                .sortedWith(
                    compareBy(
                        { usabilityGroup(it, videoKey, now) },
                        // 层级先验：官方 mirror/bcache > tf 代理 > 未知 > P2P（PR candidates 分层——
                        // 实测数据缺失时「先信谁」由层级决定，指派主机不获特殊信任）
                        { CdnClassify.tierOf("https://$it/upgcxcode").rank },
                        { model.estimate(it, videoKey, 1L shl 20, now).etaMs },
                    ),
                )
        // 全部被近期失败过滤时，放开失败过滤兜底（冷却中的主机也比卡死强）
        val usable = ranked.ifEmpty {
            (listOfNotNull(original) + pool).distinct().filter { host ->
                !model.isCoolingDown(host, now) && !model.isAddressBanned(addressKey)
            }
        }
        return usable.take(max)
    }

    private fun usabilityGroup(
        host: String,
        videoKey: String,
        now: Long,
    ): Int =
        when {
            model.isExcluded(host, videoKey, now) -> 2
            model.isCoolingDown(host, now) -> 1
            else -> 0
        }

    /** 该主机的一条请求刚送来字节（速度采样 + alive 维护）。 */
    fun noteDelivery(host: String, ttfbMs: Double?, rateBytesPerSec: Double?, videoKey: String) {
        model.noteDelivery(host)
        if (ttfbMs != null || rateBytesPerSec != null) {
            model.recordSpeed(host, videoKey, ttfbMs, rateBytesPerSec)
        }
    }

    /** 该主机的一条请求结束，按结局记账（转发 [CdnHostModel.recordOutcome]）。 */
    fun noteOutcome(
        host: String,
        outcome: CdnOutcome,
        videoKey: String,
        addressKey: String,
    ) {
        model.recordOutcome(host, outcome, videoKey, addressKey)
    }

    /** 播放侧回报：当前候选地址起播/播放成功（重置该主机的连续失败计数）。 */
    fun notePlaybackSuccess(url: String) {
        recordPlaybackOutcome(url, CdnOutcome.OK)
    }

    /**
     * 播放侧回报：当前候选地址播放失败，按 [outcome] 对其主机冷却退避。
     * 播放器错误无法细分 HTTP 状态，调用方通常保持默认 [CdnOutcome.STALL] 即可，
     * 连续失败会经由退避逐步拉长冷却时间。
     */
    fun notePlaybackFailure(
        url: String,
        outcome: CdnOutcome = CdnOutcome.STALL,
    ) {
        recordPlaybackOutcome(url, outcome)
    }

    private fun recordPlaybackOutcome(
        url: String,
        outcome: CdnOutcome,
    ) {
        val host = CdnClassify.hostOf(url) ?: return
        model.recordOutcome(host, outcome, CdnClassify.videoKeyOf(url), addressKeyOf(url))
    }

    private fun isBilibiliMediaHost(host: String): Boolean =
        host.endsWith(".bilivideo.com") ||
            host.endsWith(".bilivideo.cn") ||
            host.endsWith(".akamaized.net")

    companion object {
        /** 主机池容量上限。 */
        const val MAX_HOSTS = 64

        /** 单个请求参与竞速的主机数上限。 */
        const val MAX_CANDIDATE_HOSTS = 6

        /** job 失败后该地址走原生请求的时长（PR `NATIVE_RETRY_MS`）。 */
        const val NATIVE_RETRY_MS = 30 * 1000L

        /** 熔断计数窗口。 */
        const val FAILURE_WINDOW_MS = 60 * 1000L

        /** 窗口内失败的 job 数达到该值即整体熔断（PR `FAILURES_TO_TURN_OFF`）。 */
        const val MAX_JOB_FAILURES = 3

        /** 竞速尝试失败的主机在该 TTL 内不再作为候选（page 级学习）。 */
        const val ATTEMPT_FAILURE_TTL_MS = 60 * 1000L

        /** 连续失败熔断时长。 */
        const val TURN_OFF_MS = 30 * 1000L

        /** 从 upgcxcode 地址提取路径 key 的便捷入口（供 VM 播种与测试）。 */
        fun addressKeyOf(url: String): String = CdnClassify.mediaPathKey(url) ?: url
    }
}
