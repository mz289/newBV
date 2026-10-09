package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [ParseAccountRepository] 单元测试。
 *
 * 验证解析账号凭证的更新/清空规则与播放 Cookie 生成条件。
 */
class ParseAccountRepositoryTest {
    @Test
    fun `update stores credentials`() {
        val repository = ParseAccountRepository()

        repository.update(222L, "sess-b", "jct-b")

        assertThat(repository.uid).isEqualTo(222L)
        assertThat(repository.sessData).isEqualTo("sess-b")
        assertThat(repository.biliJct).isEqualTo("jct-b")
    }

    @Test
    fun `update with zero uid clears`() {
        val repository = ParseAccountRepository()
        repository.update(222L, "sess-b", "jct-b")

        repository.update(0L, "sess-b", "jct-b")

        assertThat(repository.uid).isEqualTo(0L)
        assertThat(repository.sessData).isEmpty()
    }

    @Test
    fun `update with blank sessData clears`() {
        val repository = ParseAccountRepository()

        repository.update(222L, "  ", "jct-b")

        assertThat(repository.uid).isEqualTo(0L)
        assertThat(repository.sessData).isEmpty()
    }

    @Test
    fun `playCookie null when inactive`() {
        val repository = ParseAccountRepository()

        assertThat(repository.playCookie(currentMid = 111L)).isNull()
    }

    @Test
    fun `playCookie null when same as current account`() {
        val repository = ParseAccountRepository()
        repository.update(222L, "sess-b", "jct-b")

        assertThat(repository.playCookie(currentMid = 222L)).isNull()
    }

    @Test
    fun `playCookie contains sessData and DedeUserID when active and different`() {
        val repository = ParseAccountRepository()
        repository.update(222L, "sess-b", "jct-b")

        val cookie = repository.playCookie(currentMid = 111L)

        // 与 injectCookies 的 playurl 策略一致：SESSDATA + DedeUserID，不带设备 cookie
        assertThat(cookie).isEqualTo("SESSDATA=sess-b; DedeUserID=222")
    }

    @Test
    fun `playCookie works when not logged in`() {
        val repository = ParseAccountRepository()
        repository.update(222L, "sess-b", "jct-b")

        assertThat(repository.playCookie(currentMid = null)).isEqualTo("SESSDATA=sess-b; DedeUserID=222")
    }

    @Test
    fun `clear resets state`() {
        val repository = ParseAccountRepository()
        repository.update(222L, "sess-b", "jct-b")

        repository.clear()

        assertThat(repository.uid).isEqualTo(0L)
        assertThat(repository.sessData).isEmpty()
        assertThat(repository.biliJct).isEmpty()
        assertThat(repository.playCookie(currentMid = null)).isNull()
    }
}
