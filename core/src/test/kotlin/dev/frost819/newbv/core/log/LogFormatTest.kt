package dev.frost819.newbv.core.log

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [LogFormat] 的单元测试。
 *
 * 验证崩溃日志头部格式。
 */
class LogFormatTest {
    @Test
    fun `crashHeader describes included logcat output`() {
        val header =
            LogFormat.crashHeader(
                appVersion = "1.0.0",
                appVersionCode = 100,
                androidVersion = "14",
                androidSdk = 34,
                device = "Pixel",
                model = "Pixel 8",
                manufacturer = "Google",
            )
        assertThat(header).contains("new BV Crash")
        assertThat(header).contains("Recent application logs are included below from Logcat:")
    }
}
