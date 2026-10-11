package dev.frost819.newbv.biliapi.http.entity.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * nav 接口（/x/web-interface/nav）返回数据。
 *
 * 除 WBI 密钥外，还携带当前登录账号的 mid、昵称、头像与会员状态，
 * 用于 Cookie 登录时的服务端校验（未登录时 isLogin=false，账号字段为默认值）。
 */
@Serializable
data class NavResponseData(
    val isLogin: Boolean,
    @SerialName("wbi_img")
    val wbiImg: WbiImg,
    val mid: Long = 0L,
    val uname: String = "",
    val face: String = "",
    /** 会员状态：0 无、1 月度大会员、2 年度大会员。 */
    val vipStatus: Int = 0,
    /** 会员类型：0 无、1 月度、2 年度。 */
    val vipType: Int = 0,
) {
    @Serializable
    data class WbiImg(
        @SerialName("img_url")
        val imgUrl: String,
        @SerialName("sub_url")
        val subUrl: String,
    ) {
        fun getImgKey(): String =
            imgUrl
                .split("/")
                .last()
                .split(".")
                .first()

        fun getSubKey(): String =
            subUrl
                .split("/")
                .last()
                .split(".")
                .first()
    }
}
