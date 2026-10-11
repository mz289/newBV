package dev.frost819.newbv.player

/**
 * 一次媒体请求在 CDN 主机视角下的结果（PR #31 `MediaOutcome` 的精简移植）。
 */
enum class CdnOutcome {
    /** 成功。 */
    OK,

    /** 响应提前结束。 */
    TRUNCATED,

    /** 响应中途连接断开。 */
    RESET,

    /** 传输停滞。 */
    STALL,

    /** 412 / 429 / 503 限流。 */
    THROTTLED,

    /** 5xx 服务器错误。 */
    SERVER_ERROR,

    /** 传输层网络错误。 */
    NETWORK,

    /** 建连失败（DNS / TLS / 连接拒绝），头部到达之前。 */
    CONNECT_FAIL,

    /** 404 / 410，主机没有这个文件。 */
    MISSING,

    /** 401 / 403，主机拒绝。 */
    REFUSED,

    /** 401 / 403 且签名已过期（地址本身失效，与主机无关）。 */
    EXPIRED,

    /** 其余协议层失败（Range 不支持、对象版本不一致等），该主机对本视频不可用。 */
    UNUSABLE,
}

/**
 * CDN 主机状态模型（参考 SukkaW/Make-Bilibili-Great-Than-Ever-Before PR #31 的 `host-model.ts`，
 * 按本项目的"起播前一次性探测 + 播放失败逐个回退"模式精简）。
 *
 * - **速度：按「视频 + 主机」维度记录最近若干次探测的 TTFB 与吞吐样本。** CDN 边缘节点对
 *   当前视频是否命中缓存直接决定其速度，因此样本不跨视频复用，新视频一律重新评估。
 *   未测速的主机使用同视频已测主机的中位数（"typical"）估计，再退到先验值。
 * - **可靠性：按主机维度做冷却退避。** 按失败类别施加指数退避冷却，成功重置；当所有主机
 *   同时失联（最近 3 秒没有任何字节到达）时视为用户自身网络问题，不惩罚任何主机。
 */
class CdnHostModel(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** 对某主机读取 `bytes` 字节的预计耗时。 */
    class Estimate(
        /** 首字节耗时（毫秒），已含冷连接惩罚。 */
        val ttfbMs: Double,
        /** 吞吐（字节/秒）。 */
        val rateBytesPerSec: Double,
        /** 读完 `bytes` 的预计耗时（毫秒）= ttfb + bytes / rate。 */
        val etaMs: Double,
        /** 该主机在本视频上的吞吐是否实测过。 */
        val measured: Boolean,
    )

    /** 一个主机在一个视频上的最近样本，新样本在后。 */
    private class Speed {
        val ttfb = ArrayList<Double>(SAMPLES)
        val rate = ArrayList<Double>(SAMPLES)
    }

    /** 一个主机的可靠性记录。 */
    private class HostRecord {
        /** 最近一次请求结束时间；空闲超过 [WARM_MS] 的连接视为已断开。 */
        var lastUsedAt = Long.MIN_VALUE / 2

        /** 冷却截止时间，之前不再请求该主机。 */
        var cooldownUntil = 0L

        /** 连续失败次数：放大退避时长，成功即清零。 */
        var failureStreak = 0
    }

    private val lock = Any()

    /** videoKey -> host -> 速度样本；容量上限 [MAX_VIDEOS]，超出淘汰最久未用的视频。 */
    private val speeds = lruMap<String, MutableMap<String, Speed>>(MAX_VIDEOS)

    /** videoKey -> host -> 排除截止时间（该主机对本视频不可用）。 */
    private val exclusions = lruMap<String, MutableMap<String, Long>>(MAX_VIDEOS)

    private val hosts = HashMap<String, HostRecord>()

    /** 签名地址（path?query）-> 已被判定过期封禁。 */
    private val bannedAddresses = lruMap<String, Boolean>(MAX_BANNED_ADDRESSES)

    /** 最近一次有字节从任何主机到达的时间（哨兵取 MIN/2，避免 `now - lastBytesAt` 下溢）。 */
    private var lastBytesAt = Long.MIN_VALUE / 2

    /**
     * 估计从 [hostname] 读取 `bytes` 字节的耗时。
     *
     * 有本视频样本时用稳健中位数；否则用同视频已测主机的中位数；再没有就用先验值。
     * 空闲超过 [WARM_MS] 的连接大概率已断开，TTFB 乘 2 计入重连成本。
     */
    fun estimate(
        hostname: String,
        videoKey: String,
        bytes: Long,
        now: Long = nowMs(),
    ): Estimate =
        synchronized(lock) {
            val own = speeds[videoKey]?.get(hostname)
            val typical = typicalSpeedLocked(videoKey)
            val cold = now - hostLocked(hostname).lastUsedAt > WARM_MS
            val ttfb =
                (robust(own?.ttfb ?: emptyList(), higherIsWorse = true) ?: typical.first) * (if (cold) 2.0 else 1.0)
            val rate = robust(own?.rate ?: emptyList(), higherIsWorse = false) ?: typical.second
            Estimate(
                ttfbMs = ttfb,
                rateBytesPerSec = rate,
                etaMs = ttfb + bytes * 1000.0 / rate,
                measured = own != null && robust(own.rate, higherIsWorse = false) != null,
            )
        }

    /**
     * 记录一次探测的速度样本。
     *
     * @param ttfbMs 首字节耗时（毫秒），`null` 表示未测得。
     * @param rateBytesPerSec 首字节之后的吞吐（字节/秒），`null` 表示未测得。
     * @param partial 传输是否被截断：截断的吞吐只在尚无任何样本时记录一次，
     *   避免被截断的短读拉低整体估计（完整样本到达后即覆盖）。
     */
    fun recordSpeed(
        hostname: String,
        videoKey: String,
        ttfbMs: Double?,
        rateBytesPerSec: Double?,
        partial: Boolean = false,
    ) {
        synchronized(lock) {
            val byHost = speeds.getOrPut(videoKey) { HashMap() }
            val speed = byHost.getOrPut(hostname) { Speed() }
            push(speed.ttfb, ttfbMs)
            if (rateBytesPerSec != null && rateBytesPerSec > 0 && (!partial || speed.rate.isEmpty())) {
                push(speed.rate, rateBytesPerSec)
            }
        }
    }

    /**
     * 记录一次请求结果，更新冷却/排除/封禁状态。
     *
     * @param videoKey 排除按视频维度生效。
     * @param addressKey 签名地址 key（path?query），过期封禁按地址生效。
     */
    fun recordOutcome(
        hostname: String,
        outcome: CdnOutcome,
        videoKey: String? = null,
        addressKey: String? = null,
        now: Long = nowMs(),
    ) {
        synchronized(lock) {
            val host = hostLocked(hostname)
            host.lastUsedAt = now
            // 连接层失败且最近没有任何主机送来字节：多半是用户自己的网络，不惩罚主机
            if (outcome in CONNECTION_FAILURES && now - lastBytesAt > NETWORK_DOWN_MS) return
            when (outcome) {
                CdnOutcome.OK -> {
                    lastBytesAt = now
                    host.failureStreak = 0
                }
                CdnOutcome.TRUNCATED, CdnOutcome.RESET, CdnOutcome.STALL -> cooldownLocked(host, now, 500)
                CdnOutcome.THROTTLED -> cooldownLocked(host, now, 2000L * (1L shl minOf(host.failureStreak, 3)))
                CdnOutcome.SERVER_ERROR, CdnOutcome.NETWORK ->
                    cooldownLocked(host, now, 1000L * (1L shl minOf(host.failureStreak, 5)))
                CdnOutcome.CONNECT_FAIL -> cooldownLocked(host, now, 60_000L * (1L shl minOf(host.failureStreak, 2)))
                CdnOutcome.MISSING, CdnOutcome.UNUSABLE -> excludeLocked(hostname, videoKey, Long.MAX_VALUE)
                CdnOutcome.EXPIRED -> addressKey?.let { bannedAddresses[it] = true }
                CdnOutcome.REFUSED -> excludeLocked(hostname, videoKey, now + REFUSED_EXCLUDE_MS)
            }
        }
    }

    /** 探测结果一站式记录：成功记速度样本，结果本身走 [recordOutcome]。 */
    fun recordProbe(
        hostname: String,
        addressKey: String,
        videoKey: String,
        outcome: CdnOutcome,
        ttfbMs: Double? = null,
        rateBytesPerSec: Double? = null,
        partial: Boolean = false,
        now: Long = nowMs(),
    ) {
        if (outcome == CdnOutcome.OK) recordSpeed(hostname, videoKey, ttfbMs, rateBytesPerSec, partial)
        recordOutcome(hostname, outcome, videoKey, addressKey, now)
    }

    /** 该主机是否在冷却期内。 */
    fun isCoolingDown(
        hostname: String,
        now: Long = nowMs(),
    ): Boolean = synchronized(lock) { hostLocked(hostname).cooldownUntil > now }

    /** 该主机是否被排除出本视频（排除未过期）。 */
    fun isExcluded(
        hostname: String,
        videoKey: String,
        now: Long = nowMs(),
    ): Boolean =
        synchronized(lock) {
            (exclusions[videoKey]?.get(hostname) ?: 0L) > now
        }

    /** 该签名地址是否已被封禁（签名过期）。 */
    fun isAddressBanned(addressKey: String): Boolean = synchronized(lock) { bannedAddresses.containsKey(addressKey) }

    /**
     * 是否需要对该主机做本视频的测速探测：
     * 本视频尚无样本，且主机未冷却、未被排除、地址未封禁。
     */
    fun needsProbe(
        hostname: String,
        addressKey: String,
        videoKey: String,
        now: Long = nowMs(),
    ): Boolean =
        synchronized(lock) {
            if (bannedAddresses.containsKey(addressKey)) return@synchronized false
            if ((exclusions[videoKey]?.get(hostname) ?: 0L) > now) return@synchronized false
            if (hostLocked(hostname).cooldownUntil > now) return@synchronized false
            speeds[videoKey]?.get(hostname) == null
        }

    /** 同视频已测主机的中位速度；没有任何样本时为先验值。 */
    private fun typicalSpeedLocked(videoKey: String): Pair<Double, Double> {
        val ttfbs = ArrayList<Double>()
        val rates = ArrayList<Double>()
        for (speed in speeds[videoKey]?.values ?: emptyList()) {
            robust(speed.ttfb, higherIsWorse = true)?.let(ttfbs::add)
            robust(speed.rate, higherIsWorse = false)?.let(rates::add)
        }
        val ttfb = if (ttfbs.isEmpty()) PRIOR_TTFB_MS else median(ttfbs)
        val rate = if (rates.isEmpty()) PRIOR_RATE_BPS else median(rates)
        return ttfb to rate
    }

    private fun hostLocked(hostname: String): HostRecord = hosts.getOrPut(hostname) { HostRecord() }

    private fun cooldownLocked(
        host: HostRecord,
        now: Long,
        ms: Long,
    ) {
        host.failureStreak++
        host.cooldownUntil = maxOf(host.cooldownUntil, now + ms)
    }

    private fun excludeLocked(
        hostname: String,
        videoKey: String?,
        until: Long,
    ) {
        if (videoKey == null) return
        exclusions.getOrPut(videoKey) { HashMap() }[hostname] = until
    }

    private companion object {
        /** 未测速时假设的吞吐：768 KiB/s。 */
        const val PRIOR_RATE_BPS = 768.0 * 1024

        /** 未测速时假设的 TTFB：350ms。 */
        const val PRIOR_TTFB_MS = 350.0

        /** 每主机每视频保留的样本数。 */
        const val SAMPLES = 8

        /** 空闲超过该时长的连接视为已断开，TTFB 加倍。 */
        const val WARM_MS = 30 * 1000L

        /** 超过该时长没有任何主机送来字节：失败是用户自身网络，不惩罚主机。 */
        const val NETWORK_DOWN_MS = 3000L

        /** REFUSED 时对该主机按视频排除的时长。 */
        const val REFUSED_EXCLUDE_MS = 60 * 1000L

        /** 速度样本按视频隔离，最多记住的视频数。 */
        const val MAX_VIDEOS = 32

        /** 封禁地址的容量上限。 */
        const val MAX_BANNED_ADDRESSES = 512

        /** 连接层失败类别：可能在用户网络整体失联时同时出现。 */
        val CONNECTION_FAILURES =
            setOf(CdnOutcome.CONNECT_FAIL, CdnOutcome.NETWORK, CdnOutcome.STALL, CdnOutcome.TRUNCATED, CdnOutcome.RESET)

        /**
         * 稳健中位数：取中位数，但突然变差/变好要立刻反映——
         * TTFB 最近两个样本都超过中位数 2 倍时取两者较小值；吞吐反之取较大值。
         */
        fun robust(
            samples: List<Double>,
            higherIsWorse: Boolean,
        ): Double? {
            if (samples.isEmpty()) return null
            val med = median(samples)
            if (samples.size >= 3) {
                val latest = samples[samples.size - 1]
                val previous = samples[samples.size - 2]
                if (higherIsWorse && latest > 2 * med && previous > 2 * med) return minOf(latest, previous)
                if (!higherIsWorse && latest < med / 2 && previous < med / 2) return maxOf(latest, previous)
            }
            return med
        }

        fun median(samples: List<Double>): Double {
            val sorted = samples.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }

        fun push(
            samples: ArrayList<Double>,
            value: Double?,
        ) {
            if (value == null) return
            samples.add(value)
            if (samples.size > SAMPLES) samples.subList(0, samples.size - SAMPLES).clear()
        }

        /** 访问序 LRU；始终在 [CdnHostModel] 的锁内使用。 */
        fun <K, V> lruMap(maxEntries: Int): LinkedHashMap<K, V> =
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxEntries
            }
    }
}
