package dev.frost819.newbv.app.sponsorblock

import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.SponsorBlockDefaults
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bilibili SponsorBlock 社区服务器（bsbsb.top）API。
 *
 * 协议与 sponsor.ajay.app 兼容：按 BVID 查询视频的社区提交片段，
 * 响应为片段数组（UUID / 分类 / 起止秒）。任何失败均返回空列表，
 * 绝不影响正常播放。
 *
 * 参考实现：hanydd/BilibiliSponsorBlock（浏览器扩展）、
 * b1ackmarket/NeoBV（BV 客户端插件）。
 */
@Singleton
class SponsorBlockApi
    @Inject
    constructor() {
        private val logger = Loggers.get("SponsorBlockApi")

        private val client =
            HttpClient(OkHttp) {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }

        /**
         * 获取视频的社区片段。
         *
         * @param bvid 视频 BV 号
         * @return 片段列表；网络失败、超时或视频无片段（404）时为空列表
         */
        suspend fun getSegments(bvid: String): List<SponsorSegment> =
            runCatching {
                client
                    .get(BASE_URL) {
                        parameter("videoID", bvid)
                        SponsorBlockDefaults.supportedCategories.forEach { parameter("category", it) }
                    }.body<List<SegmentResponse>>()
            }.onFailure { error ->
                logger.warn { "Failed to fetch segments for $bvid: ${error.message}" }
            }.getOrElse { emptyList() }
                .map { it.toDomain() }

        @Serializable
        private data class SegmentResponse(
            @SerialName("UUID") val uuid: String,
            val category: String,
            val segment: List<Double>,
        ) {
            fun toDomain(): SponsorSegment =
                SponsorSegment(
                    id = uuid,
                    category = category,
                    startMs = (segment.getOrElse(0) { 0.0 } * 1000).toLong(),
                    endMs = (segment.getOrElse(1) { 0.0 } * 1000).toLong(),
                )
        }

        private companion object {
            /** Bilibili SponsorBlock 社区服务器（协议兼容 sponsor.ajay.app）。 */
            const val BASE_URL = "https://bsbsb.top/api/skipSegments"

            /** 第三方服务器，短超时快速失败，避免拖慢起播。 */
            const val REQUEST_TIMEOUT_MS = 5_000L
        }
    }
