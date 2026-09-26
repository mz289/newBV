package dev.frost819.newbv.app.network

import dev.frost819.newbv.app.network.entity.GithubRelease
import dev.frost819.newbv.app.network.entity.findApkAsset
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * GitHub Releases API 封装。
 *
 * 用于检查更新和下载 APK。更新统一走本仓库（fork）`mz289/newBV` 的 Releases：
 * - 稳定版：`releases/latest`（GitHub 天然排除预发布与草稿）。
 * - 预发布：Releases 列表（含 mods 分支 CI 自动构建的 `debug` 预发布），
 *   由"接收预发布版本"开关控制是否纳入。
 * 下载通过 `ghfast.top` 代理加速国内访问。
 */
object GithubApi {
    private const val OWNER = "mz289"
    private const val REPO = "newBV"

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val client: HttpClient =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(json)
            }
            install(ContentEncoding) {
                deflate(1.0f)
                gzip(0.9f)
            }
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.github.com"
                }
            }
        }

    /**
     * 获取最新可更新的 Release。
     *
     * @param includePrerelease 是否包含预发布版本。为 false 时仅查
     *   `releases/latest`（GitHub 排除预发布与草稿）；为 true 时按创建时间倒序
     *   扫描 Releases 列表（含 CI 自动构建的 `debug` 预发布）。
     * @param assetChannels 更新 APK 附件匹配关键字，需与当前构建 variant 一致
     *   （如 debug 构建传 `UpdateChannel.DEBUG.assetKeywords`）。
     * @return 最新的、包含匹配 APK 附件的非草稿 Release；
     *   仓库无 Release 或无匹配附件时返回 null。
     */
    suspend fun getLatestBuild(
        includePrerelease: Boolean,
        assetChannels: List<String>,
    ): GithubRelease? {
        val candidates: List<GithubRelease> =
            if (includePrerelease) {
                getReleases(pageSize = 30)
            } else {
                val response = client.get("repos/$OWNER/$REPO/releases/latest")
                if (response.status.value == 404) return null
                val body = response.bodyAsText()
                checkErrorMessage(body)
                listOf(json.decodeFromString(body))
            }
        return candidates.firstOrNull { !it.draft && it.findApkAsset(assetChannels) != null }
    }

    /** 获取所有 Releases。 */
    suspend fun getReleases(
        pageSize: Int = 30,
        page: Int = 1,
    ): List<GithubRelease> {
        val response =
            client
                .get("repos/$OWNER/$REPO/releases") {
                    parameter("per_page", pageSize)
                    parameter("page", page)
                }.bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /**
     * 下载 Release 中的更新 APK 附件。
     *
     * @param release Release 信息。
     * @param file 目标文件路径。
     * @param channel 更新渠道，决定匹配哪个 variant 的 APK 附件。
     * @param onProgress 下载进度回调（已下载字节, 总字节）。
     */
    suspend fun downloadUpdate(
        release: GithubRelease,
        file: File,
        channel: UpdateChannel = UpdateChannel.RELEASE,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val downloadUrl =
            release.findApkAsset(channel.assetKeywords)?.browserDownloadUrl
                ?: throw IllegalStateException("Didn't find download url for channel $channel")

        client
            .prepareRequest {
                url(toGhProxyUrl(downloadUrl))
                onDownload { downloaded, total ->
                    onProgress(downloaded, total ?: 0)
                }
            }.execute { response ->
                response.bodyAsChannel().copyTo(file.outputStream())
            }
    }

    private fun checkErrorMessage(data: String) {
        val responseElement = json.parseToJsonElement(data)
        if (responseElement !is JsonObject) return
        val responseObject = responseElement.jsonObject
        if (responseObject["message"] != null) {
            error(responseObject["message"]!!.jsonPrimitive.content)
        }
    }

    private fun toGhProxyUrl(originalUrl: String): String = "https://ghfast.top/$originalUrl"
}
