package dev.frost819.newbv.player.racing

import dev.frost819.newbv.player.CdnClassify
import dev.frost819.newbv.player.CdnOutcome
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 一次 Range 请求的响应（阻塞式流；由 [Transport.open] 的调用线程消费）。 */
interface TransportResponse : Closeable {
    val code: Int

    /** 原始 `Content-Range` 头（`bytes 0-262143/1234567`）；无则 null。 */
    val contentRange: String?

    /** 读一批字节；返回长度，流结束返回 -1。 */
    fun read(sink: ByteArray, offset: Int, length: Int): Int
}

/** 竞速层对 HTTP 的最小抽象；缺省 OkHttp 实现，测试注入假实现。 */
interface Transport {
    @Throws(IOException::class)
    fun open(
        url: String,
        rangeStart: Long,
        /** null 表示开放式 Range（`bytes=start-`）。 */
        rangeEndInclusive: Long?,
    ): TransportResponse
}

/** OkHttp 实现：Range + UA/Referer，读超时按字节间隔计。 */
class OkHttpTransport(
    client: OkHttpClient,
    private val userAgent: String?,
    private val defaultHeaders: Map<String, String>,
) : Transport {
    private val callClient =
        client
            .newBuilder()
            .connectTimeout(2_500, TimeUnit.MILLISECONDS)
            .readTimeout(4_000, TimeUnit.MILLISECONDS)
            .build()

    override fun open(
        url: String,
        rangeStart: Long,
        rangeEndInclusive: Long?,
    ): TransportResponse {
        val range =
            if (rangeEndInclusive != null) "bytes=$rangeStart-$rangeEndInclusive" else "bytes=$rangeStart-"
        val builder =
            Request
                .Builder()
                .url(url)
                .header("Range", range)
        userAgent?.let { builder.header("User-Agent", it) }
        for ((name, value) in defaultHeaders) builder.header(name, value)
        val response = callClient.newCall(builder.build()).execute()
        return OkHttpTransportResponse(response.code, response.header("Content-Range"), response)
    }

    private class OkHttpTransportResponse(
        override val code: Int,
        override val contentRange: String?,
        private val response: okhttp3.Response,
    ) : TransportResponse {
        private val source = response.body?.source()

        override fun read(sink: ByteArray, offset: Int, length: Int): Int {
            val s = source ?: throw IOException("Empty body")
            return s.read(sink, offset, length)
        }

        override fun close() {
            response.close()
        }
    }
}

/**
 * 多主机竞速下载任务（PR #31 thread-ripper 的 job 模型；media3 无关，可直接单测）。
 *
 * 对一条媒体地址的 [position, position+length) 区间：
 * - 提取签名地址（path?query，不含主机），在已知主机池之间**换宿**构造候选；
 * - 首片以 [FIRST_PIECE_WIDTH] 个主机并发竞速（PR `RACE_WIDTH[CRITICAL]=3`），共享一个按序
 *   队列，**先送出合法 206 字节的主机胜出**成为该片 leader，其余降为字节比对者（至多再读
 *   [LAGGARD_BYTES] 供交叉校验）；
 * - 起播窗口 [commitTimeoutMs] 内没有任何字节 → 记 job 失败并进入原生回退窗口（重试方据此
 *   走非竞速单流，PR `NATIVE_RETRY_MS`：「goes to the browser」）；原生路径失败时按候选序
 *   换宿重试——原生窗口只放弃竞速调度，不放弃换宿自救（钉死原始主机会把不可达指派拖成
 *   20~70s 加载黑窗，2026-11-22 E2E 实测）；
 * - leader 停滞超过 [stallMs] → 在**另一台主机**上为该片剩余区间增援，先送字节者接管
 *   （「a racer for a stuck request never goes to the same host」）；一片至多 [MAX_TRIES] 次尝试；
 * - 两个主机送出的同段字节内容不一致 → 比对者按 UNUSABLE 排除出本视频、leader 记失败学习，
 *   job 继续交付（PR byte-level cross-check）。不再全局停用竞速：比对错位造成的假分歧在
 *   起播第一片就会触发，全局熔断会让优选在起播瞬间自废。
 *
 * 起播路径上**没有任何前置探测**：主机的 TTFB/吞吐在真实下载里测得（样本按视频 cid 隔离、
 * 可靠性由 [dev.frost819.newbv.player.CdnHostModel] 维护）。
 *
 * 非竞速模式（[racing] = false）：单流直连原始 URL，失败按候选序换宿重试（原生回退窗口）。
 */
class RacingJob(
    private val originalUrl: String,
    private val position: Long,
    /** 区间长度；null 表示未知（读到 EOF）。 */
    private val length: Long?,
    private val registry: CdnRacingRegistry,
    private val executor: Executor,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val transport: Transport,
    private val commitTimeoutMs: Long = COMMIT_TIMEOUT_MS,
    private val jobStallMs: Long = JOB_STALL_MS,
    private val stallMs: Long = STALL_MS,
    /** 是否竞速；false = 单流直连原始 URL（透明旁路）。由调用方（DataSource）判定。 */
    private val racing: Boolean,
) {
    private val addressKey = CdnRacingRegistry.addressKeyOf(originalUrl)
    private val videoKey = CdnClassify.videoKeyOf(originalUrl)
    private val openedAt = nowMs()
    private var closed = false
    private var firstByteAt = 0L
    private var currentLeaderUrl: String = originalUrl

    // 原生模式
    private var nativeResponse: TransportResponse? = null

    // 竞速模式
    private var pieces = ArrayDeque<Piece>()

    // readLength 小于帧时的余量帧（下次 read 先消费，保证字节序）
    private var pending: QFrame? = null
    private var pendingOffset = 0

    /** 开始下载；返回区间长度（未知时为 null）。 */
    fun open(): Long? {
        if (!racing) {
            val end = length?.let { position + it - 1 }
            // 原生单流：先直连原始 URL，失败按候选序换宿（原生回退窗口 ≠ 钉死原始主机）
            val hosts = listOfNotNull(CdnClassify.hostOf(originalUrl)) +
                registry.candidateHosts(originalUrl, videoKey, addressKey)
            var lastError: IOException? = null
            for (host in hosts.distinct().take(NATIVE_HOST_WALK)) {
                val url = swappedUrl(host)
                try {
                    val response = transport.open(url, position, end)
                    if (response.code == 200 || response.code == 206) {
                        currentLeaderUrl = url
                        nativeResponse = response
                        return length
                    }
                    val outcome = classifyHttpCode(response.code)
                    registry.noteOutcome(host, outcome, videoKey, addressKey)
                    response.close()
                    lastError = IOException("HTTP ${response.code} for $url")
                } catch (e: IOException) {
                    registry.noteOutcome(host, CdnOutcome.CONNECT_FAIL, videoKey, addressKey)
                    lastError = e
                }
            }
            throw lastError ?: IOException("Native open failed for $originalUrl")
        }

        registry.learnMediaUrl(originalUrl)
        var start = position
        if (length != null) {
            val end = position + length - 1
            while (start <= end) {
                val pieceEnd = minOf(start + PIECE_SIZE - 1, end)
                pieces.addLast(Piece(start, pieceEnd))
                start = pieceEnd + 1
            }
        } else {
            // 未知总长：首片先建，后续片在消费中按需追加（EOF 收尾）
            pieces.addLast(Piece(start, start + PIECE_SIZE - 1))
        }
        ensureAttempts(pieces.first(), FIRST_PIECE_WIDTH)
        return length
    }

    fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
        if (racing) readRaced(buffer, offset, readLength) else readNative(buffer, offset, readLength)

    fun close() {
        if (closed) return
        closed = true
        nativeResponse?.close()
        nativeResponse = null
        for (piece in pieces) cancelPiece(piece)
    }

    fun currentUri(): String = currentLeaderUrl

    // ── 内部结构 ────────────────────────────────────────────────────────

    /** 片队列里的一帧：某次尝试的一段字节 / 流结束 / 失败。 */
    private class QFrame(
        val attempt: Attempt,
        val bytes: ByteArray?,
        val length: Int,
        val eof: Boolean = false,
        val error: CdnOutcome? = null,
    )

    /** 一片区间上的单次尝试（一个主机一条连接）。 */
    private inner class Attempt(
        val host: String,
        val url: String,
        val piece: Piece,
        /** 本尝试覆盖的区间起点 = 片起点（对齐）或片内 frontier（中断续传）。 */
        val start: Long,
        val endInclusive: Long,
    ) {
        val canceled = AtomicBoolean(false)

        /** true = 已被降级为字节比对者：帧作废、只读影子字节。 */
        @Volatile var isLaggard = false

        @Volatile var ttfbMs: Double? = null
        @Volatile var delivered = 0L
        val startedAt = nowMs()

        /** 影子字节（leader 在有比对者时捕获；比对者最多 [LAGGARD_BYTES]）。 */
        val shadow = ByteArrayOutputStream(LAGGARD_BYTES)

        /** 影子首字节在文件中的绝对偏移（对齐两条影子后才能比对）。 */
        var shadowStartAbs: Long = -1L
    }

    /** 一个片：job 区间上的一段连续区间。所有尝试共享一个按序队列（先到字节的胜出）。 */
    private inner class Piece(
        val start: Long,
        /** 片尾（含）。 */
        var endInclusive: Long,
    ) {
        val queue = ArrayBlockingQueue<QFrame>(QUEUE_FRAMES)
        val attempts = ArrayList<Attempt>()
        var leader: Attempt? = null
        var tries = 0
        val failedHosts = mutableSetOf<String>()
        var lastByteAt = 0L
        var deliveredToPlayer = 0L

        val pieceLength: Long
            get() = endInclusive - start + 1
    }

    // ── 原生单流 ────────────────────────────────────────────────────────

    private fun readNative(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val response = nativeResponse ?: throw IOException("Read before open")
        if (closed) return 0
        return try {
            val n = response.read(buffer, offset, readLength)
            if (n == -1) 0 else n
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            throw IOException("Native read failed: ${e.message}", e)
        }
    }

    // ── 竞速 ────────────────────────────────────────────────────────────

    private fun readRaced(buffer: ByteArray, offset: Int, readLength: Int): Int {
        while (!closed) {
            // 先消费上次读小的余量帧
            val pendingFrame = pending
            if (pendingFrame != null) {
                val n = copyFrame(pendingFrame, buffer, offset, readLength)
                if (n > 0) return n
            }
            val piece = pieces.firstOrNull() ?: return 0
            if (piece.attempts.isEmpty()) {
                // 上一片完成后的新片：立即补尝试
                ensureAttempts(piece, width = 1)
                continue
            }
            val frame =
                try {
                    piece.queue.poll(POLL_MS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException(e.message)
                }
            if (frame == null) {
                onSilence(piece)
                continue
            }
            when {
                frame.attempt.canceled.get() -> continue

                frame.error != null -> onAttemptError(piece, frame)

                frame.eof -> onEof(piece, frame)

                else -> onBytes(piece, frame, buffer, offset, readLength)?.let { return it }
            }
        }
        return 0
    }

    /** 字节帧：晋升 leader / 接管 / 丢弃失配帧；返回拷贝的字节数（>0 则结束本次 read）。 */
    private fun onBytes(
        piece: Piece,
        frame: QFrame,
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int? {
        val att = frame.attempt
        if (firstByteAt == 0L) firstByteAt = nowMs()
        piece.lastByteAt = nowMs()

        val leader = piece.leader
        if (att.isLaggard) return null // 降级后的迟到帧：丢弃
        if (leader == null) {
            // 该片第一份字节：胜出
            promote(piece, att)
        } else if (att !== leader) {
            // 并发对手先出字节：leader 已在但还没出字节 → 接管；否则帧失配丢弃
            if (leader.delivered == 0L && att.start == piece.start + piece.deliveredToPlayer) {
                demoteToLaggard(piece, leader)
                promote(piece, att)
            } else {
                return null
            }
        }
        pending = frame
        pendingOffset = 0
        return copyFrame(frame, buffer, offset, readLength).takeIf { it > 0 }
    }

    private fun copyFrame(
        frame: QFrame,
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        val att = frame.attempt
        val piece = att.piece
        val available = frame.length - pendingOffset
        val copy = minOf(available.toLong(), readLength.toLong()).toInt()
        if (copy <= 0) {
            pending = null
            pendingOffset = 0
            return 0
        }
        System.arraycopy(frame.bytes!!, pendingOffset, buffer, offset, copy)
        pendingOffset += copy
        if (pendingOffset >= frame.length) {
            pending = null
            pendingOffset = 0
        }
        currentLeaderUrl = att.url
        piece.deliveredToPlayer += copy
        registry.noteDelivery(att.host, att.ttfbMs, null, videoKey)
        if (piece.deliveredToPlayer >= piece.pieceLength) {
            completePiece(piece)
            // 未知总长的流：消费中追加下一片
            if (length == null && pieces.isEmpty()) {
                pieces.addLast(Piece(piece.start + PIECE_SIZE, piece.start + 2 * PIECE_SIZE - 1))
            }
        }
        return copy
    }

    private fun promote(
        piece: Piece,
        att: Attempt,
    ) {
        piece.leader = att
        currentLeaderUrl = att.url
        // 其余在场尝试降级为字节比对者：迟到的帧作废、转为影子捕获（供完整性比对）
        for (other in piece.attempts) {
            if (other !== att && !other.canceled.get()) other.isLaggard = true
        }
    }

    private fun demoteToLaggard(
        piece: Piece,
        att: Attempt,
    ) {
        att.isLaggard = true
    }

    /** EOF 帧：length 未知时即流尾；length 已知且片未满 = 截断。 */
    private fun onEof(
        piece: Piece,
        frame: QFrame,
    ) {
        val att = frame.attempt
        if (att.isLaggard) return
        if (piece.leader == null) promote(piece, att)
        if (att !== piece.leader) return
        if (length == null && piece.deliveredToPlayer < piece.pieceLength) {
            // 未知总长的流：EOF 就是文件尾（最后一片通常短于片大小）→ 正常收尾
            completePiece(piece)
            pieces.clear()
            return
        }
        if (piece.deliveredToPlayer >= piece.pieceLength) {
            completePiece(piece)
            if (length != null && pieces.isEmpty()) return // 整个 job 完成
        } else {
            // 已知长度却提前 EOF：截断
            onAttemptError(piece, QFrame(att, null, 0, error = CdnOutcome.TRUNCATED))
        }
    }

    private fun onAttemptError(
        piece: Piece,
        frame: QFrame,
    ) {
        val att = frame.attempt
        registry.noteOutcome(att.host, frame.error!!, videoKey, addressKey)
        if (frame.error in CONNECTION_LEVEL_FAILURES) {
            // page 级学习：失败主机跨片/跨 job 一段时间内不再优先（PR 的 host learning）
            registry.noteAttemptFailure(att.host, videoKey)
        }
        piece.attempts.remove(att)
        piece.failedHosts.add(att.host)
        if (att === piece.leader) {
            piece.leader = null
            // 失败前已交付部分字节：下一次尝试从 frontier 续传
        }
    }

    /** 队列静默：提交预算 → job 停滞 → 片停滞增援 → 片失败。 */
    private fun onSilence(piece: Piece) {
        val now = nowMs()
        if (firstByteAt == 0L && now - openedAt > commitTimeoutMs) {
            failJob("commit timeout")
            return
        }
        if (piece.lastByteAt > 0 && now - piece.lastByteAt > jobStallMs) {
            failJob("job stall")
            return
        }
        val sinceLast = if (piece.lastByteAt > 0) now - piece.lastByteAt else now - openedAt
        if (sinceLast > stallMs) {
            val leader = piece.leader
            if (leader != null && leader.delivered == 0L && now - leader.startedAt > stallMs) {
                // 无首字节：判失败换宿
                putError(leader, CdnOutcome.CONNECT_FAIL)
            } else {
                hedge(piece)
            }
        }
    }

    /** 在另一台主机上为片的剩余区间增援（「never goes to the same host」）。 */
    private fun hedge(piece: Piece) {
        val leader = piece.leader ?: return
        val frontier = piece.start + piece.deliveredToPlayer
        if (piece.attempts.any { it !== leader && it.start == frontier && !it.canceled.get() }) return // 已有增援
        spawn(piece, width = 1, frontier = frontier, excludeHosts = setOf(leader.host))
    }

    /** 为片补充尝试（首片竞速宽度 [FIRST_PIECE_WIDTH]，其余 1 + 停滞增援）。 */
    private fun ensureAttempts(
        piece: Piece,
        width: Int,
    ) {
        if (piece.attempts.isEmpty()) {
            spawn(piece, width, frontier = piece.start, excludeHosts = emptySet())
        }
    }

    private fun spawn(
        piece: Piece,
        width: Int,
        frontier: Long,
        excludeHosts: Set<String>,
    ): Boolean {
        val budget = MAX_TRIES - piece.tries
        if (budget <= 0) {
            failJob("piece ${piece.start} exhausted $MAX_TRIES tries")
            return false
        }
        val exclude = piece.failedHosts + excludeHosts
        val hosts =
            registry
                .candidateHosts(originalUrl, videoKey, addressKey)
                .filter { it !in exclude }
                .take(minOf(width, budget))
        if (hosts.isEmpty()) {
            if (piece.attempts.isNotEmpty()) return true // 还有在场的尝试，静默等待即可
            // 没有可用主机：放开排除项再试一次（冷却中的主机也比卡死强）
            val fallback =
                registry
                    .candidateHosts(originalUrl, videoKey, addressKey)
                    .take(minOf(width, budget))
            if (fallback.isEmpty()) {
                failJob("no candidate host")
                return false
            }
            fallback.forEach { spawnAttempt(piece, it, frontier) }
            return true
        }
        hosts.forEach { spawnAttempt(piece, it, frontier) }
        return true
    }

    private fun spawnAttempt(
        piece: Piece,
        host: String,
        frontier: Long,
    ) {
        piece.tries++
        val attempt = Attempt(host, swappedUrl(host), piece, frontier, piece.endInclusive)
        piece.attempts.add(attempt)
        executor.execute { runAttempt(attempt) }
    }

    /** 尝试线程主体：开请求 → 校验 206/Content-Range → 泵字节进片队列。 */
    private fun runAttempt(attempt: Attempt) {
        val piece = attempt.piece
        try {
            val response = transport.open(attempt.url, attempt.start, attempt.endInclusive)
            if (response.code != 206) {
                response.close()
                putError(attempt, classifyHttpCode(response.code))
                return
            }
            val rangeStart = parseContentRangeStart(response.contentRange)
            if (rangeStart != null && rangeStart != attempt.start) {
                // 对象版本不一致 / 服务器不支持该 Range：本主机对本视频不可用
                response.close()
                putError(attempt, CdnOutcome.UNUSABLE)
                return
            }
            val scratch = ByteArray(16 * 1024)
            var streamPos = attempt.start
            var firstAt = 0L
            while (!attempt.canceled.get() && !closed) {
                val n =
                    try {
                        response.read(scratch, 0, scratch.size)
                    } catch (e: IOException) {
                        putError(attempt, if (firstAt == 0L) CdnOutcome.CONNECT_FAIL else CdnOutcome.RESET)
                        return
                    }
                if (n == -1) break
                if (firstAt == 0L) {
                    firstAt = nowMs()
                    attempt.ttfbMs = (firstAt - attempt.startedAt).toDouble()
                }
                attempt.delivered += n
                // 影子起始必须用泵线程本地的流位置：attempt.delivered 会被消费端 copyFrame
                // 二次累加，拿它算绝对偏移会错位出假分歧（起播必触发完整性熔断的根因）
                val chunkStart = streamPos
                streamPos += n
                if (attempt.isLaggard) {
                    // 比对者：影子字节封顶后停止读取，等待取消
                    if (attempt.shadow.size() < LAGGARD_BYTES) {
                        if (attempt.shadowStartAbs < 0) attempt.shadowStartAbs = chunkStart
                        val cap = minOf(n, LAGGARD_BYTES - attempt.shadow.size())
                        attempt.shadow.write(scratch, 0, cap)
                    }
                    if (attempt.shadow.size() >= LAGGARD_BYTES) {
                        idleUntilCanceled(attempt)
                        return
                    }
                    continue
                }
                // leader 在场且有比对者：同步捕获自己的影子字节（记录绝对起点供对齐比对）
                if (piece.attempts.any { it !== attempt && it.isLaggard && !it.canceled.get() }) {
                    if (attempt.shadow.size() < LAGGARD_BYTES) {
                        if (attempt.shadowStartAbs < 0) attempt.shadowStartAbs = chunkStart
                        val cap = minOf(n, LAGGARD_BYTES - attempt.shadow.size())
                        attempt.shadow.write(scratch, 0, cap)
                    }
                }
                try {
                    piece.queue.put(QFrame(attempt, scratch.copyOf(n), n))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
            if (attempt.isLaggard || attempt.canceled.get()) return
            // 流结束：EOF 帧由消费端判定是正常收尾还是截断
            try {
                piece.queue.put(QFrame(attempt, null, 0, eof = true))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        } catch (e: IOException) {
            putError(attempt, CdnOutcome.CONNECT_FAIL)
        } catch (e: Exception) {
            putError(attempt, CdnOutcome.NETWORK)
        }
    }

    private fun idleUntilCanceled(attempt: Attempt) {
        while (!attempt.canceled.get() && !closed) {
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun putError(
        attempt: Attempt,
        outcome: CdnOutcome,
    ) {
        try {
            attempt.piece.queue.put(QFrame(attempt, null, 0, error = outcome))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 片完成：OK 记账、取消全部尝试、影子比对，进入下一片。 */
    private fun completePiece(piece: Piece) {
        piece.leader?.let { leader ->
            registry.noteOutcome(leader.host, CdnOutcome.OK, videoKey, addressKey)
            val totalMs = (nowMs() - leader.startedAt).toDouble()
            val bodyMs = totalMs - (leader.ttfbMs ?: 0.0)
            val rate = if (bodyMs > 0 && leader.delivered > 0) leader.delivered / (bodyMs / 1000.0) else null
            registry.noteDelivery(leader.host, leader.ttfbMs, rate, videoKey)
        }
        compareBytes(piece)
        cancelPiece(piece)
        pieces.removeFirst()
    }

    /**
     * 影子前缀比对（按绝对偏移对齐重叠区）。不一致 = 比对者对这条视频提供的对象版本与
     * leader 不同（边缘缓存未失效 / 版本漂移）：
     * 把比对者按 UNUSABLE 排除出本视频、leader 记 60s 失败学习（若旧版本节点恰是 leader，
     * 后续片自然避开），本片已按 leader 字节交付完成，job 继续——不全局停用竞速。
     */
    private fun compareBytes(piece: Piece) {
        val leader = piece.leader ?: return
        if (piece.attempts.none { it !== leader && it.shadow.size() > 0 }) return
        if (leader.shadowStartAbs < 0) return
        val leaderShadow = leader.shadow.toByteArray()
        for (other in piece.attempts) {
            if (other === leader) continue
            val otherShadow = other.shadow.toByteArray()
            if (otherShadow.isEmpty() || other.shadowStartAbs < 0) continue
            // 两条影子的绝对覆盖区间 [start, start+len)，取交集后逐字节比对
            val overlapStart = maxOf(leader.shadowStartAbs, other.shadowStartAbs)
            val overlapEnd =
                minOf(
                    leader.shadowStartAbs + leaderShadow.size,
                    other.shadowStartAbs + otherShadow.size,
                ).toLong()
            if (overlapEnd <= overlapStart) continue
            for (pos in overlapStart until overlapEnd) {
                val a = leaderShadow[(pos - leader.shadowStartAbs).toInt()]
                val b = otherShadow[(pos - other.shadowStartAbs).toInt()]
                if (a != b) {
                    android.util.Log.w(
                        "CdnRacing",
                        "integrity mismatch at $pos, excluding ${other.host} for video $videoKey",
                    )
                    registry.noteOutcome(other.host, CdnOutcome.UNUSABLE, videoKey, addressKey)
                    registry.noteAttemptFailure(leader.host, videoKey)
                    return
                }
            }
        }
    }

    private fun cancelPiece(piece: Piece) {
        for (att in piece.attempts) att.canceled.set(true)
        piece.attempts.clear()
        piece.queue.clear()
        pending = null
        pendingOffset = 0
    }

    /** 片重试额度耗尽 / 提交超时 / 停滞超限：记 job 失败并抛错（播放器会重试）。 */
    private fun failJob(reason: String) {
        registry.noteJobFailure(addressKey)
        close()
        throw IOException("RacingJob: $reason ($originalUrl)")
    }

    private fun swappedUrl(host: String): String {
        val key = CdnClassify.mediaPathKey(originalUrl) ?: originalUrl
        val path = if (key.startsWith("/")) key else "/$key"
        return "https://$host$path"
    }

    companion object {
        /** 首片竞速宽度（PR `RACE_WIDTH[CRITICAL]`）。 */
        const val FIRST_PIECE_WIDTH = 3

        /** 片大小：512KiB（PR 按 ~1s 传输量切片，这里取固定折中值）。 */
        const val PIECE_SIZE = 512L * 1024

        /** 提交预算：起播窗口内没有任何字节即放弃（PR `COMMIT_TIMEOUT_MS`；取 8s 兼容冷 TLS+回源慢边缘）。 */
        const val COMMIT_TIMEOUT_MS = 8_000L

        /** job 级停滞预算（PR `JOB_STALL_MS`）。 */
        const val JOB_STALL_MS = 8_000L

        /** 片级停滞预算：超过即在另一台主机增援。 */
        const val STALL_MS = 2_000L

        /** 一片的最大尝试次数（PR `MAX_TRIES`）。 */
        const val MAX_TRIES = 6

        /** 原生回退路径的换宿上限（原始主机 + 候选序前 N-1 台；全部不可达才交给播放器重试）。 */
        const val NATIVE_HOST_WALK = 5

        /** 字节比对者缓存的字节数上限（PR `LAGGARD_BYTES`）。 */
        const val LAGGARD_BYTES = 64 * 1024

        /** 轮询片队列的间隔。 */
        const val POLL_MS = 50L

        /** 片队列帧数上限（背压：播放器消费多快，下载多快）。 */
        const val QUEUE_FRAMES = 8

        private fun classifyHttpCode(code: Int): CdnOutcome =
            when (code) {
                404, 410 -> CdnOutcome.MISSING
                412, 429, 503 -> CdnOutcome.THROTTLED
                401, 403 -> CdnOutcome.REFUSED
                in 500..599 -> CdnOutcome.SERVER_ERROR
                else -> CdnOutcome.REFUSED
            }

        /** 连接层失败类别（这些失败计入 page 级失败主机学习）。 */
        private val CONNECTION_LEVEL_FAILURES =
            setOf(
                CdnOutcome.CONNECT_FAIL,
                CdnOutcome.NETWORK,
                CdnOutcome.RESET,
                CdnOutcome.STALL,
                CdnOutcome.TRUNCATED,
                CdnOutcome.SERVER_ERROR,
                CdnOutcome.THROTTLED,
            )

        private fun parseContentRangeStart(contentRange: String?): Long? =
            contentRange?.let { Regex("bytes\\s+(\\d+)-").find(it)?.groupValues?.get(1) }?.toLongOrNull()
    }
}

/** 缺省竞速执行器：daemon 线程池。 */
fun defaultRacingExecutor(): Executor =
    Executors.newCachedThreadPool { r ->
        Thread(r, "cdn-racing").apply { isDaemon = true }
    }
