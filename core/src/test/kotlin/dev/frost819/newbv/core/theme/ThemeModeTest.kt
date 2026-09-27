package dev.frost819.newbv.core.theme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [ThemeMode] 的单元测试。
 *
 * 验证显示名称与深浅色判定（data 层直接持久化本枚举，行为必须稳定）。
 */
class ThemeModeTest {

    @Test
    fun `displayNames are stable`() {
        assertThat(ThemeMode.FollowSystem.displayName).isEqualTo("跟随系统")
        assertThat(ThemeMode.Dark.displayName).isEqualTo("深色")
        assertThat(ThemeMode.Light.displayName).isEqualTo("浅色")
    }

    @Test
    fun `isDark resolves by mode`() {
        assertThat(ThemeMode.Dark.isDark(systemIsDark = false)).isTrue()
        assertThat(ThemeMode.Light.isDark(systemIsDark = true)).isFalse()
        assertThat(ThemeMode.FollowSystem.isDark(systemIsDark = true)).isTrue()
        assertThat(ThemeMode.FollowSystem.isDark(systemIsDark = false)).isFalse()
    }

    @Test
    fun `fromOrdinal falls back to FollowSystem for invalid ordinal`() {
        assertThat(ThemeMode.fromOrdinal(0)).isEqualTo(ThemeMode.FollowSystem)
        assertThat(ThemeMode.fromOrdinal(1)).isEqualTo(ThemeMode.Dark)
        assertThat(ThemeMode.fromOrdinal(99999)).isEqualTo(ThemeMode.FollowSystem)
    }
}
