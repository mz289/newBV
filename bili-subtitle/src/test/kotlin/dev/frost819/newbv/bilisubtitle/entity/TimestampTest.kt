package dev.frost819.newbv.bilisubtitle.entity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [Timestamp] 的单元测试。
 *
 * 验证 SRT/BCC 格式解析、时间转换、totalMills 计算及往返一致性。
 */
class TimestampTest {
    // ---- fromSrtString ----

    @Test
    fun `fromBccString parses whole seconds`() {
        val ts = Timestamp.fromBccString(120.0f)

        assertThat(ts.hours).isEqualTo(0)
        assertThat(ts.minutes).isEqualTo(2)
        assertThat(ts.seconds).isEqualTo(0)
        assertThat(ts.milliSeconds).isEqualTo(0)
    }

    @Test
    fun `fromBccString parses fractional seconds`() {
        val ts = Timestamp.fromBccString(7530.2f)

        assertThat(ts.hours).isEqualTo(2)
        assertThat(ts.minutes).isEqualTo(5)
        assertThat(ts.seconds).isEqualTo(30)
        assertThat(ts.milliSeconds).isEqualTo(200)
    }

    @Test
    fun `fromBccString parses zero`() {
        val ts = Timestamp.fromBccString(0.0f)

        assertThat(ts.hours).isEqualTo(0)
        assertThat(ts.minutes).isEqualTo(0)
        assertThat(ts.seconds).isEqualTo(0)
        assertThat(ts.milliSeconds).isEqualTo(0)
    }

    @Test
    fun `fromBccString parses value exceeding one hour`() {
        val ts = Timestamp.fromBccString(3661.5f)

        assertThat(ts.hours).isEqualTo(1)
        assertThat(ts.minutes).isEqualTo(1)
        assertThat(ts.seconds).isEqualTo(1)
        assertThat(ts.milliSeconds).isEqualTo(500)
    }

    // ---- totalMills (init block) ----

    @Test
    fun `totalMills calculates correctly for zero`() {
        val ts = Timestamp(0, 0, 0, 0)
        assertThat(ts.totalMills).isEqualTo(0L)
    }

    @Test
    fun `totalMills calculates correctly for hours only`() {
        val ts = Timestamp(1, 0, 0, 0)
        assertThat(ts.totalMills).isEqualTo(3_600_000L)
    }

    @Test
    fun `totalMills calculates correctly for all components`() {
        val ts = Timestamp(1, 2, 3, 4)
        assertThat(ts.totalMills).isEqualTo(1 * 3_600_000L + 2 * 60_000L + 3 * 1_000L + 4)
    }

    @Test
    fun `totalMills calculates correctly from fromBccString`() {
        val ts = Timestamp.fromBccString(7530.2f)
        assertThat(ts.totalMills).isEqualTo(2 * 3_600_000L + 5 * 60_000L + 30 * 1_000L + 200)
    }

    // ---- getBccTime ----

    @Test
    fun `getBccTime returns correct float for whole seconds`() {
        val ts = Timestamp(0, 2, 0, 0)
        assertThat(ts.getBccTime()).isEqualTo(120.0f)
    }

    @Test
    fun `getBccTime returns correct float for fractional`() {
        val ts = Timestamp(0, 0, 1, 500)
        assertThat(ts.getBccTime()).isEqualTo(1.5f)
    }

    @Test
    fun `getBccTime returns correct float for hours`() {
        val ts = Timestamp(1, 0, 0, 0)
        assertThat(ts.getBccTime()).isEqualTo(3600.0f)
    }

    @Test
    fun `getBccTime returns zero for zero timestamp`() {
        val ts = Timestamp(0, 0, 0, 0)
        assertThat(ts.getBccTime()).isEqualTo(0.0f)
    }

    @Test
    fun `fromBccString to getBccTime roundtrip preserves value`() {
        val original = 7530.2f
        val ts = Timestamp.fromBccString(original)
        assertThat(ts.getBccTime()).isEqualTo(original)
    }

    // ---- getSrtTime ----

    @Test
    fun `two timestamps with same values are equal`() {
        val a = Timestamp(1, 2, 3, 4)
        val b = Timestamp(1, 2, 3, 4)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `two timestamps with different values are not equal`() {
        val a = Timestamp(1, 2, 3, 4)
        val b = Timestamp(1, 2, 3, 5)
        assertThat(a).isNotEqualTo(b)
    }
}
