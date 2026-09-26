package dev.frost819.newbv.app.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 数据模型。
 *
 * 对应 `GET /repos/{owner}/{repo}/releases/latest` 响应。
 *
 * @property assets 附件列表（APK 等）。
 * @property body Release Notes 正文。
 * @property name Release 标题。
 * @property tagName Git 标签名。
 * @property prerelease 是否为预发布。
 */
@Serializable
data class GithubRelease(
    val assets: List<Asset>,
    @SerialName("assets_url")
    val assetsUrl: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String,
    val draft: Boolean,
    @SerialName("html_url")
    val htmlUrl: String,
    val id: Int,
    val name: String,
    val prerelease: Boolean,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("tarball_url")
    val tarballUrl: String,
    @SerialName("target_commitish")
    val targetCommitish: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    val url: String,
    @SerialName("zipball_url")
    val zipballUrl: String,
) {
    val isPreRelease: Boolean get() = prerelease

    /**
     * Release 附件（APK 文件等）。
     *
     * @property browserDownloadUrl 直链下载地址。
     * @property name 文件名。
     * @property size 文件大小（字节）。
     * @property contentType MIME 类型。
     */
    @Serializable
    data class Asset(
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
        @SerialName("content_type")
        val contentType: String,
        @SerialName("download_count")
        val downloadCount: Int,
        val id: Int,
        val name: String,
        val size: Int,
        val state: String,
        val url: String,
    )
}

/**
 * 从 APK 附件名解析 versionCode。
 *
 * 附件名约定为 `newBV_<versionCode>_<versionName>_<variant>.apk`
 * （由 app 模块构建脚本的 outputFileName 保证）。
 *
 * @return 附件名中的 versionCode；命名不符合约定时返回 null。
 */
fun GithubRelease.Asset.parseVersionCode(): Int? = name.split("_").getOrNull(1)?.toIntOrNull()

/**
 * 按更新渠道关键字选取更新用 APK 附件。
 *
 * @param assetChannels 匹配附件名的渠道关键字（如 `release`/`alpha`/`debug`），
 *   需与渠道对应的 APK variant 名一致。
 * @return 首个名称以 `newBV` 开头且命中任一关键字的附件；无匹配时返回 null。
 */
fun GithubRelease.findApkAsset(assetChannels: List<String>): GithubRelease.Asset? =
    assets.firstOrNull { asset ->
        asset.name.startsWith("newBV") && assetChannels.any { it in asset.name }
    }
