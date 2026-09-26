package dev.frost819.newbv.app.network

/**
 * 更新渠道。
 *
 * 定义各渠道 APK 附件的匹配关键字；[GithubApi] 用关键字从 Release 附件中
 * 选取与当前构建 variant 匹配的 APK（附件名约定
 * `newBV_<versionCode>_<versionName>_<variant>.apk`）。
 *
 * @property assetKeywords 匹配 APK 附件名的关键字。
 */
enum class UpdateChannel(
    val assetKeywords: List<String>,
) {
    /**
     * 正式版渠道：匹配 release/alpha 附件（正式构建与 alpha 构建）。
     */
    RELEASE(listOf("release", "alpha")),

    /**
     * Debug 渠道：匹配 debug 附件（mods 分支 CI 自动构建）。
     */
    DEBUG(listOf("debug")),
}
