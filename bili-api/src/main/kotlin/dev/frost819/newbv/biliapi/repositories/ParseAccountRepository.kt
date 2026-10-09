package dev.frost819.newbv.biliapi.repositories

/**
 * 解析账号（双账号场景）。
 *
 * 参考油猴脚本「哔哩哔哩双账号助手-A身份-B大会员权益」的思路：
 * 当前账号（A）保持完整身份用于浏览、互动、历史记录；
 * 指定的解析账号（B，通常为大会员）仅用于播放地址解析，
 * 以解锁会员画质（4K/HDR/杜比）与会员专享内容。
 *
 * 凭证由 app 层从多账号库同步进来（见 [ParseAccountRepository.update]），
 * bili-api 层不持有 Prefs/Room 依赖。
 */
class ParseAccountRepository {
    /** 解析账号 UID（0 表示未启用）。 */
    var uid: Long = 0L
        private set

    /** 解析账号 SESSDATA Cookie。 */
    var sessData: String = ""
        private set

    /** 解析账号 bili_jct（保留备用，播放地址请求为 GET 不需要 csrf）。 */
    var biliJct: String = ""
        private set

    /**
     * 更新解析账号凭证。
     *
     * @param uid 解析账号 UID（0 等价于 [clear]）。
     * @param sessData SESSDATA Cookie，为空时视为凭证无效并清空。
     * @param biliJct bili_jct Cookie。
     */
    fun update(
        uid: Long,
        sessData: String,
        biliJct: String,
    ) {
        if (uid == 0L || sessData.isBlank()) {
            clear()
            return
        }
        this.uid = uid
        this.sessData = sessData
        this.biliJct = biliJct
    }

    /** 清空解析账号。 */
    fun clear() {
        uid = 0L
        sessData = ""
        biliJct = ""
    }

    /**
     * 当解析账号可用且不是当前登录账号时，返回播放地址请求用的 Cookie 头。
     *
     * Cookie 只含 SESSDATA + DedeUserID，与全局 [dev.frost819.newbv.biliapi.http.util.injectCookies]
     * 对 playurl 的策略一致（不带 buvid3 等设备 cookie 以降低风控风险）。
     *
     * @param currentMid 当前登录账号 UID（未登录为 null）。
     * @return Cookie 头字符串；无需替换解析身份时为 null。
     */
    fun playCookie(currentMid: Long?): String? {
        if (uid == 0L || sessData.isBlank()) return null
        if (currentMid == uid) return null
        return "SESSDATA=$sessData; DedeUserID=$uid"
    }
}
