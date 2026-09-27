package dev.frost819.newbv.danmaku.entity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DanmakuSpeedFactorTest {
    @Test
    fun `factor values are correct`() {
        assertThat(DanmakuSpeedFactor.S1.factor).isEqualTo(1.5f)
        assertThat(DanmakuSpeedFactor.S2.factor).isEqualTo(1.25f)
        assertThat(DanmakuSpeedFactor.S3.factor).isEqualTo(1.0f)
        assertThat(DanmakuSpeedFactor.S4.factor).isEqualTo(0.75f)
        assertThat(DanmakuSpeedFactor.S5.factor).isEqualTo(0.5f)
    }

    @Test
    fun `entries has correct count`() {
        assertThat(DanmakuSpeedFactor.entries).hasSize(5)
    }
}
