package dev.frost819.newbv.player

import java.net.URI

/**
 * CDN 候选地址层级，未实测时按 [rank] 从小到大偏好。
 *
 * 实测吞吐一旦存在即覆盖层级偏好；层级只回答"没有数据时先信谁"。
 */
enum class CdnTier(
    val rank: Int,
) {
    /** B 站官方视频节点（upos mirror、bcache 自有 PoP、Akamai mirror）。 */
    OFFICIAL(0),

    /** tf 代理节点（upos-tf-* / proxy-tf-*，代回源 PCDN 地址）。 */
    PROXY(1),

    /** 无法识别的主机。 */
    UNKNOWN(2),

    /** P2P / PCDN 节点（mcdn、szbdyd、纯 IP、302 系 PCDN）。 */
    P2P(3),
}

/**
 * CDN 地址分类（参考 SukkaW/Make-Bilibili-Great-Than-Ever-Before PR #31 的
 * `cdn-classify.ts` / `candidates.ts` 候选分层，按本项目的需要精简）。
 */
object CdnClassify {
    /**
     * upos mirror / tf 代理的 upgcxcode 地址。
     * `upos-\w+-(?!302)\w+` 排除 `upos-*-302.*` 这类 HTTP 302 P2P CDN。
     */
    private val MIRROR_URL_REGEX =
        Regex(
            "^https?://(?:upos-\\w+-(?!302)\\w+|(?:upos|proxy)-tf-[^/]+)\\.(?:bilivideo|akamaized)\\.(?:com|net)/upgcxcode",
        )

    /** tf 代理主机名前缀。 */
    private val PROXY_HOST_REGEX = Regex("^(?:upos|proxy)-tf-")

    /** 纯 IPv4 主机名（mcdn 直连 IP 形态）。 */
    private val IP_HOST_REGEX = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

    /** 已知 P2P / PCDN 域名特征。 */
    private val P2P_KEYWORDS =
        listOf(
            "302ppio",
            "302kodo",
            ".mcdn.bilivideo",
            "szbdyd.com",
            ".nexusedgeio.com",
            ".ahdohpiechei.com", // 七牛云 PCDN
            "upos-sz-mirror14b.bilivideo.com", // mirror 形态，但证书是 PCDN 的（*.bilivideo.cn），无有效 SSL
        )

    /** 从 URL 提取小写主机名；无法解析时返回 `null`。 */
    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    /** 是否为 P2P / PCDN 节点。 */
    fun isP2pUrl(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (IP_HOST_REGEX.matches(host)) return true
        if (P2P_KEYWORDS.any { url.contains(it, ignoreCase = true) }) return true
        // 形如 *-*302*.* 的子域是 302 系 P2P CDN（如 upos-sz-302ppio.bilivideo.com）
        return host.substringBefore('.').contains("302")
    }

    /** 对候选地址归入 [CdnTier]。 */
    fun tierOf(url: String): CdnTier {
        val host = hostOf(url) ?: return CdnTier.UNKNOWN
        if (isP2pUrl(url)) return CdnTier.P2P
        if (PROXY_HOST_REGEX.containsMatchIn(host)) return CdnTier.PROXY
        if (MIRROR_URL_REGEX.containsMatchIn(url)) return CdnTier.OFFICIAL
        if (host.endsWith(".bilivideo.com") ||
            host.endsWith(".bilivideo.cn") ||
            host.endsWith(".akamaized.net")
        ) {
            return CdnTier.OFFICIAL
        }
        return CdnTier.UNKNOWN
    }
}
