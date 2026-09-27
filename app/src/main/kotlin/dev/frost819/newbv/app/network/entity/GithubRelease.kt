package dev.frost819.newbv.app.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 数据模型。
 *
 * 对应 `GET /repos/{owner}/{repo}/releases` 响应，仅保留更新流程实际使用的字段；
 * 其余响应字段由 `ignoreUnknownKeys` 忽略。
 *
 * @property assets 附件列表（APK 等）。
 * @property body Release Notes 正文。
 * @property draft 是否为草稿（草稿不可匿名下载，选取时直接跳过）。
 * @property name Release 标题（形如 `newBV <versionName>`）。
 */
@Serializable
data class GithubRelease(
    val assets: List<Asset>,
    val body: String,
    val draft: Boolean,
    val name: String,
) {
    /**
     * Release 附件（APK 文件）。
     *
     * @property browserDownloadUrl 直链下载地址。
     * @property name 文件名（形如 `newBV_<versionCode>_<versionName>_<variant>.apk`）。
     * @property size 文件大小（字节），用于下载进度显示。
     */
    @Serializable
    data class Asset(
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
        val name: String,
        val size: Int,
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
 * @param assetChannels 匹配附件名的渠道关键字（如 `release`/`debug`），
 *   需与渠道对应的 APK variant 名一致。
 * @return 首个名称以 `newBV` 开头且命中任一关键字的附件；无匹配时返回 null。
 */
fun GithubRelease.findApkAsset(assetChannels: List<String>): GithubRelease.Asset? =
    assets.firstOrNull { asset ->
        asset.name.startsWith("newBV") && assetChannels.any { it in asset.name }
    }
