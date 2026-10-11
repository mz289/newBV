package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.util.BiliLogger

/**
 * bili-api 层鉴权状态。
 *
 * 由 app 层在启动与登录/登出/切换账号时同步写入，
 * 仓库层通过它读取当前凭证（bili-api 不依赖 Prefs/Room）。
 */
class AuthRepository {
    var sessionData: String? = null
    var biliJct: String? = null
    var accessToken: String? = null
    var mid: Long? = null
    var buvid3: String? = null
    var buvid: String? = null
    var deviceCookies: String? = null

    /**
     * App 通道（gRPC channel 与 access_key 接口）是否可用。
     *
     * App 通道依赖登录时颁发的 access_token；Cookie 登录的账号只有
     * Web 凭证（SESSDATA/bili_jct），没有 access_token，此状态下
     * App 通道整体不可用。
     */
    val appChannelAvailable: Boolean
        get() = !accessToken.isNullOrBlank()

    /**
     * 解析实际使用的接口类型。
     *
     * 请求 App 通道但当前无 access_token（如 Cookie 登录账号）时，App 通道
     * 整体不可用——这是持久状态而非瞬态故障，抛错只会让对应功能永久不可用，
     * 因此回退 Web 通道并记日志；有 token 但 channel 未就绪等瞬态仍由各仓库
     * 按原策略抛出并允许重试。
     *
     * @param preferApiType 用户偏好的接口类型。
     * @param feature 功能名（用于日志定位）。
     */
    fun effectiveApiType(
        preferApiType: ApiType,
        feature: String,
    ): ApiType =
        if (preferApiType == ApiType.App && !appChannelAvailable) {
            BiliLogger.warn {
                "App channel unavailable (no access_token), fallback to Web: [$feature]"
            }
            ApiType.Web
        } else {
            preferApiType
        }
}
