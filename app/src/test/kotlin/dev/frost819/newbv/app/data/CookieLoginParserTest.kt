package dev.frost819.newbv.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [CookieLoginParser] 单元测试。
 *
 * 覆盖浏览器 Cookie 串、newBV 凭证 JSON、NeoBV 凭证 JSON 三种输入格式，
 * 以及缺失必需字段的错误提示。
 */
class CookieLoginParserTest {
    @Test
    fun `parses browser cookie string with semicolons`() {
        val input =
            "DedeUserID=12345; SESSDATA=aaa%2Cbbb; bili_jct=csrf456; " +
                "DedeUserID__ckMd5=md5abc; sid=7abc; buvid3=device-buvid; foo=bar"

        val authData = CookieLoginParser.parse(input)

        assertThat(authData.uid).isEqualTo(12345L)
        assertThat(authData.sessData).isEqualTo("aaa%2Cbbb")
        assertThat(authData.biliJct).isEqualTo("csrf456")
        assertThat(authData.uidCkMd5).isEqualTo("md5abc")
        assertThat(authData.sid).isEqualTo("7abc")
        // 设备 cookie 等无关字段不进入凭证
        assertThat(authData.accessToken).isEmpty()
        assertThat(authData.refreshToken).isEmpty()
    }

    @Test
    fun `parses cookie string with newlines and extra spaces`() {
        val input =
            "SESSDATA=aaa%2Cbbb;\n  bili_jct=csrf456;\nDedeUserID=98765\n"

        val authData = CookieLoginParser.parse(input)

        assertThat(authData.uid).isEqualTo(98765L)
        assertThat(authData.sessData).isEqualTo("aaa%2Cbbb")
        assertThat(authData.biliJct).isEqualTo("csrf456")
        assertThat(authData.uidCkMd5).isEmpty()
        assertThat(authData.sid).isEmpty()
    }

    @Test
    fun `parses newBV AuthData JSON`() {
        val json =
            """{"uid":111,"uidCkMd5":"m","sid":"s","biliJct":"j","sessData":"ss",""" +
                """"tokenExpiredDate":0,"accessToken":"tk","refreshToken":"rt"}"""

        val authData = CookieLoginParser.parse(json)

        assertThat(authData.uid).isEqualTo(111L)
        assertThat(authData.sessData).isEqualTo("ss")
        assertThat(authData.biliJct).isEqualTo("j")
        assertThat(authData.uidCkMd5).isEqualTo("m")
        assertThat(authData.sid).isEqualTo("s")
    }

    @Test
    fun `parses NeoBV style cookie-name JSON`() {
        val json =
            """{"DedeUserID":222,"DedeUserID__ckMd5":"m2","sid":"s2",""" +
                """"bili_jct":"j2","SESSDATA":"ss2","expired_date":1700000000000,""" +
                """"access_token":"","refresh_token":""}"""

        val authData = CookieLoginParser.parse(json)

        assertThat(authData.uid).isEqualTo(222L)
        assertThat(authData.sessData).isEqualTo("ss2")
        assertThat(authData.biliJct).isEqualTo("j2")
        assertThat(authData.uidCkMd5).isEqualTo("m2")
        assertThat(authData.sid).isEqualTo("s2")
        // Cookie 登录不保留 App token（避免误用他人 token 调 App 接口）
        assertThat(authData.accessToken).isEmpty()
        assertThat(authData.refreshToken).isEmpty()
    }

    @Test
    fun `throws on empty input`() {
        assertThrows<IllegalArgumentException> { CookieLoginParser.parse("   ") }
    }

    @Test
    fun `throws when sessData missing`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CookieLoginParser.parse("DedeUserID=1; bili_jct=j")
            }
        assertThat(error.message).contains("SESSDATA")
    }

    @Test
    fun `throws when biliJct missing`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CookieLoginParser.parse("DedeUserID=1; SESSDATA=ss")
            }
        assertThat(error.message).contains("bili_jct")
    }

    @Test
    fun `throws when DedeUserID missing`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CookieLoginParser.parse("SESSDATA=ss; bili_jct=j")
            }
        assertThat(error.message).contains("DedeUserID")
    }

    @Test
    fun `throws on invalid JSON`() {
        assertThrows<IllegalArgumentException> { CookieLoginParser.parse("{\"broken\":") }
    }

    @Test
    fun `throws on JSON without required fields`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CookieLoginParser.parse("""{"foo":"bar"}""")
            }
        assertThat(error.message).contains("DedeUserID")
    }
}
