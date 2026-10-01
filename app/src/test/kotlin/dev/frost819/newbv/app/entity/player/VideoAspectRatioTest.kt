package dev.frost819.newbv.app.entity.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [VideoAspectRatio] 的单元测试。
 *
 * 验证枚举值、ratio 属性及 [VideoAspectRatio.fromOrdinal] 安全解析。
 */
class VideoAspectRatioTest {
    @Test
    fun `fromOrdinal returns correct value for valid ordinals`() {
        assertThat(VideoAspectRatio.fromOrdinal(0)).isEqualTo(VideoAspectRatio.Default)
        assertThat(VideoAspectRatio.fromOrdinal(1)).isEqualTo(VideoAspectRatio.FourToThree)
        assertThat(VideoAspectRatio.fromOrdinal(2)).isEqualTo(VideoAspectRatio.SixteenToNine)
    }

    @Test
    fun `fromOrdinal returns Default for negative ordinal`() {
        assertThat(VideoAspectRatio.fromOrdinal(-1)).isEqualTo(VideoAspectRatio.Default)
    }

    @Test
    fun `fromOrdinal returns Default for out-of-bounds ordinal`() {
        assertThat(VideoAspectRatio.fromOrdinal(99)).isEqualTo(VideoAspectRatio.Default)
    }
}
