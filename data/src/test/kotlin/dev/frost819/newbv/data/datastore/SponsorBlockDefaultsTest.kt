package dev.frost819.newbv.data.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [SponsorBlockDefaults] 与 [SkipPolicy] 的单元测试。
 *
 * 覆盖策略编码/解析的往返一致性、非法输入兜底与默认策略。
 */
class SponsorBlockDefaultsTest {
    // ===== SkipPolicy =====

    @Test
    fun `SkipPolicy fromCode returns matching policy`() {
        assertThat(SkipPolicy.fromCode("A")).isEqualTo(SkipPolicy.Auto)
        assertThat(SkipPolicy.fromCode("P")).isEqualTo(SkipPolicy.Prompt)
        assertThat(SkipPolicy.fromCode("D")).isEqualTo(SkipPolicy.Disabled)
    }

    @Test
    fun `SkipPolicy fromCode falls back to Disabled for unknown code`() {
        assertThat(SkipPolicy.fromCode("X")).isEqualTo(SkipPolicy.Disabled)
        assertThat(SkipPolicy.fromCode("")).isEqualTo(SkipPolicy.Disabled)
    }

    // ===== SponsorBlockDefaults =====

    @Test
    fun `encode and parse round-trip preserves policies`() {
        val policies =
            SponsorBlockDefaults.policies +
                mapOf(
                    "intro" to SkipPolicy.Auto,
                    "outro" to SkipPolicy.Prompt,
                )

        assertThat(SponsorBlockDefaults.parse(SponsorBlockDefaults.encode(policies)))
            .isEqualTo(policies)
    }

    @Test
    fun `parse falls back to defaults for missing categories`() {
        val parsed = SponsorBlockDefaults.parse("intro:A")

        assertThat(parsed).isEqualTo(SponsorBlockDefaults.policies + ("intro" to SkipPolicy.Auto))
    }

    @Test
    fun `parse maps unknown codes to Disabled and ignores unknown categories`() {
        val parsed = SponsorBlockDefaults.parse("bogus:A,sponsor:X,selfpromo:P")

        assertThat(parsed)
            .isEqualTo(
                SponsorBlockDefaults.policies +
                    ("sponsor" to SkipPolicy.Disabled) +
                    ("selfpromo" to SkipPolicy.Prompt),
            )
    }

    @Test
    fun `parse of blank string returns defaults`() {
        assertThat(SponsorBlockDefaults.parse("")).isEqualTo(SponsorBlockDefaults.policies)
    }

    @Test
    fun `default policies auto-skip sponsor only`() {
        assertThat(SponsorBlockDefaults.policies["sponsor"]).isEqualTo(SkipPolicy.Auto)
        assertThat(SponsorBlockDefaults.policies.values.count { it == SkipPolicy.Auto }).isEqualTo(1)
        assertThat(SponsorBlockDefaults.policies.keys).containsExactlyElementsIn(
            SponsorBlockDefaults.supportedCategories,
        )
    }
}
