package dev.frost819.newbv.player.racing

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dev.frost819.newbv.player.CdnClassify
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executor

/**
 * 多主机竞速数据源：[RacingJob]（PR #31 thread-ripper 的 job 模型）的 media3 适配层。
 *
 * 对 B 站 upgcxcode 媒体请求启用换宿竞速（首片 3 主机并发、停滞增援、字节比对、
 * 提交超时 + 原生回退窗口、连续失败熔断、完整性熔断），其余请求透明旁路单流。
 * 起播路径上没有任何前置探测——测量发生在真实下载里。
 *
 * 各行为常量与语义详见 [RacingJob] 的 KDoc。
 */
class RacingDataSource(
    client: OkHttpClient,
    userAgent: String?,
    defaultHeaders: Map<String, String>,
    private val registry: CdnRacingRegistry,
    private val executor: Executor = defaultRacingExecutor(),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val transport: Transport = OkHttpTransport(client, userAgent, defaultHeaders),
    private val commitTimeoutMs: Long = RacingJob.COMMIT_TIMEOUT_MS,
    private val jobStallMs: Long = RacingJob.JOB_STALL_MS,
    private val stallMs: Long = RacingJob.STALL_MS,
) : BaseDataSource(true) {
    private var job: RacingJob? = null
    private var pendingDataSpec: DataSpec? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        pendingDataSpec = dataSpec
        val url = dataSpec.uri.toString()
        val racing =
            isRaceEligible(url) &&
                registry.racingAllowed() &&
                !registry.isNativeRetry(CdnRacingRegistry.addressKeyOf(url))
        job =
            RacingJob(
                originalUrl = url,
                position = dataSpec.position,
                length = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() },
                registry = registry,
                executor = executor,
                nowMs = nowMs,
                transport = transport,
                commitTimeoutMs = commitTimeoutMs,
                jobStallMs = jobStallMs,
                stallMs = stallMs,
                racing = racing,
            )
        val opened = job!!.open()
        transferStarted(dataSpec)
        return opened ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val j = job ?: throw IOException("Read before open")
        val n = j.read(buffer, offset, readLength)
        if (n == RacingJob.EOS) return C.RESULT_END_OF_INPUT
        if (n > 0) bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = job?.currentUri()?.let(Uri::parse)

    override fun close() {
        job?.close()
        job = null
        pendingDataSpec = null
    }

    private fun isRaceEligible(url: String): Boolean =
        registry.isBilibiliMediaUrl(url) &&
            (CdnClassify.mediaPathKey(url)?.substringBefore('?')?.contains("/upgcxcode/") == true)

    /** Hilt 友好的工厂（media3 DataSource.Factory）。 */
    class Factory(
        private val client: OkHttpClient,
        private val userAgent: String?,
        private val defaultHeaders: Map<String, String>,
        private val registry: CdnRacingRegistry,
        private val executor: Executor = defaultRacingExecutor(),
        private val nowMs: () -> Long = System::currentTimeMillis,
    ) : DataSource.Factory {
        override fun createDataSource(): RacingDataSource =
            RacingDataSource(
                client = client,
                userAgent = userAgent,
                defaultHeaders = defaultHeaders,
                registry = registry,
                executor = executor,
                nowMs = nowMs,
            )
    }
}
