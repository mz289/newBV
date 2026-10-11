package dev.frost819.newbv.biliapi.http

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.web.NavResponseData
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [BiliHttpApi.updateWbi] 的单元测试。
 *
 * 验证 wbi 密钥获取：正常写入、nav 未返回数据时保持 null 并记日志
 * （密钥缺失会使所有 WBI 签名请求失败，失败必须可诊断）。
 */
class UpdateWbiTest {
    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        BiliHttpApi.wbiImgKey = null
        BiliHttpApi.wbiSubKey = null
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    private fun navData(
        imgKey: String,
        subKey: String,
    ): NavResponseData =
        NavResponseData(
            isLogin = false,
            wbiImg =
                NavResponseData.WbiImg(
                    "https://i.hdslb.com/$imgKey.png",
                    "https://i.hdslb.com/$subKey.png",
                ),
        )

    @Test
    fun `updateWbi stores keys from nav`() =
        runTest {
            coEvery { BiliHttpApi.getWebInterfaceNav(any()) } returns
                BiliResponse(code = -101, message = "账号未登录", data = navData("imgkey1", "subkey1"))

            BiliHttpApi.updateWbi()

            assertThat(BiliHttpApi.wbiImgKey).isEqualTo("imgkey1")
            assertThat(BiliHttpApi.wbiSubKey).isEqualTo("subkey1")
        }

    @Test
    fun `updateWbi keeps keys null when nav returns no data`() =
        runTest {
            // 风控等场景下 nav 可能不返回 data：失败被记录（日志），密钥保持 null，
            // 后续 WBI 请求抛明确异常而不是拿到错误密钥
            coEvery { BiliHttpApi.getWebInterfaceNav(any()) } returns
                BiliResponse<NavResponseData>(code = -352, message = "风控校验失败", data = null)

            BiliHttpApi.updateWbi()

            assertThat(BiliHttpApi.wbiImgKey).isNull()
            assertThat(BiliHttpApi.wbiSubKey).isNull()
        }

    @Test
    fun `updateWbi keeps keys null when nav request throws`() =
        runTest {
            coEvery { BiliHttpApi.getWebInterfaceNav(any()) } throws java.io.IOException("offline")

            BiliHttpApi.updateWbi()

            assertThat(BiliHttpApi.wbiImgKey).isNull()
            assertThat(BiliHttpApi.wbiSubKey).isNull()
        }
}
