package dev.frost819.newbv.danmaku.entity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [DanmakuType] 的单元测试。
 *
 * modeValue 是传给 akdanmaku 的显示区域过滤值，必须与
 * [com.kuaishou.akdanmaku.data.DanmakuItemData] 的 mode 常量保持一致。
 */
class DanmakuTypeTest {
    @Test
    fun `modeValue Rolling returns correct value`() {
        assertThat(DanmakuType.Rolling.modeValue).isEqualTo(1)
    }

    @Test
    fun `modeValue Top returns correct value`() {
        assertThat(DanmakuType.Top.modeValue).isEqualTo(5)
    }

    @Test
    fun `modeValue Bottom returns correct value`() {
        assertThat(DanmakuType.Bottom.modeValue).isEqualTo(4)
    }

    @Test
    fun `modeValue All returns minus one`() {
        assertThat(DanmakuType.All.modeValue).isEqualTo(-1)
    }

    @Test
    fun `modeValue constants are correct`() {
        assertThat(DanmakuType.All.modeValue).isEqualTo(-1)
        assertThat(DanmakuType.Rolling.modeValue).isEqualTo(1)
        assertThat(DanmakuType.Top.modeValue).isEqualTo(5)
        assertThat(DanmakuType.Bottom.modeValue).isEqualTo(4)
    }
}
