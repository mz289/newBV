package dev.frost819.newbv.danmaku.filter

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import org.junit.jupiter.api.Test

/**
 * [DanmakuBlockFilter] 屏蔽规则编译与匹配语义测试。
 */
class DanmakuBlockFilterTest {
    private fun filter(
        vararg rules: DanmakuBlockRule,
        enable: Boolean = true,
    ): DanmakuBlockFilter =
        DanmakuBlockFilter().apply {
            this.enable = enable
            setRules(rules.toList())
        }

    // === 关键词 ===

    @Test
    fun `关键词命中为忽略大小写的子串匹配`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "abc"))
        assertThat(f.isBlocked("xxABCyy")).isTrue()
        assertThat(f.isBlocked("abc")).isTrue()
        assertThat(f.isBlocked("abd")).isFalse()
    }

    @Test
    fun `中文关键词按子串匹配`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "前方高能"))
        assertThat(f.isBlocked("请注意前方高能反应")).isTrue()
        assertThat(f.isBlocked("后方高能")).isFalse()
    }

    @Test
    fun `空白关键词不参与匹配`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "   "))
        assertThat(f.isBlocked("anything")).isFalse()
    }

    // === 正则 ===

    @Test
    fun `斜杠包裹的正则被剥壳执行`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "/^\\d+秒/"))
        assertThat(f.isBlocked("123秒 later")).isTrue()
        assertThat(f.isBlocked("第123秒")).isFalse()
    }

    @Test
    fun `裸正则默认忽略大小写`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "abc\\d+"))
        assertThat(f.isBlocked("xxABC123")).isTrue()
    }

    @Test
    fun `斜杠包裹带 flags 的正则生效`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "/ABC/i"))
        assertThat(f.isBlocked("xyzabc")).isTrue()
    }

    @Test
    fun `非法正则被静默忽略而不抛异常`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "[invalid"))
        assertThat(f.isBlocked("anything")).isFalse()
    }

    @Test
    fun `空白正则规则被忽略`() {
        assertThat(DanmakuBlockFilter.parseBlockRegex("   ")).isNull()
    }

    // === 用户 ===

    @Test
    fun `midHash 十六进制串精确匹配 userId`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.User, "0ab12cd3"))
        assertThat(f.isBlocked("hi", userId = 0x0AB12CD3L)).isTrue()
        assertThat(f.isBlocked("hi", userId = 0x0AB12CD4L)).isFalse()
    }

    @Test
    fun `非法 midHash 规则被忽略`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.User, "zzzz"))
        assertThat(f.isBlocked("hi", userId = 123L)).isFalse()
    }

    @Test
    fun `userId 为 null 时不命中用户规则`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.User, "abc123"))
        assertThat(f.isBlocked("hi", userId = null)).isFalse()
    }

    // === 颜色 ===

    @Test
    fun `颜色规则按 RGB 精确匹配`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Color, "FF6699"))
        assertThat(f.isBlocked("hi", color = 0xFFFF6699.toInt())).isTrue()
        assertThat(f.isBlocked("hi", color = 0x00FF6699)).isTrue() // alpha 不参与比较
        assertThat(f.isBlocked("hi", color = 0xFFFF6698.toInt())).isFalse()
    }

    @Test
    fun `非法颜色规则被忽略`() {
        val f1 = filter(DanmakuBlockRule(DanmakuBlockRuleType.Color, "GGGGGG"))
        assertThat(f1.isBlocked("hi", color = 0xFF6699)).isFalse()
        val f2 = filter(DanmakuBlockRule(DanmakuBlockRuleType.Color, "12345"))
        assertThat(f2.isBlocked("hi", color = 0x012345)).isFalse()
    }

    // === 命中回调 ===

    @Test
    fun `命中时返回对应规则 key（供 onHit 上报）`() {
        val f =
            filter(
                DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "广告"),
                DanmakuBlockRule(DanmakuBlockRuleType.User, "abc12345"),
            )
        assertThat(f.matchedRuleKey("卖广告的")).isEqualTo("Keyword:广告")
        assertThat(f.matchedRuleKey("正常", userId = 0xABC12345L)).isEqualTo("User:abc12345")
        assertThat(f.matchedRuleKey("正常")).isNull()
    }

    @Test
    fun `总开关关闭时命中不上报`() {
        val f =
            filter(
                DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "广告"),
                enable = false,
            )
        assertThat(f.matchedRuleKey("广告")).isNull()
    }

    @Test
    fun `ruleKey 按匹配语义生成稳定 key`() {
        assertThat(DanmakuBlockFilter.ruleKey(DanmakuBlockRuleType.Keyword, " AbC ")).isEqualTo("Keyword:abc")
        assertThat(DanmakuBlockFilter.ruleKey(DanmakuBlockRuleType.Color, "#ff6699")).isEqualTo("Color:FF6699")
        assertThat(DanmakuBlockFilter.ruleKey(DanmakuBlockRuleType.Regex, "/x/")).isEqualTo("Regex:/x/")
    }

    // === 开关与组合 ===

    @Test
    fun `停用的规则不参与匹配`() {
        val f =
            filter(
                DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "spam", enabled = false),
            )
        assertThat(f.isBlocked("spam message")).isFalse()
    }

    @Test
    fun `总开关关闭时全部不命中`() {
        val f =
            filter(
                DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "spam"),
                enable = false,
            )
        assertThat(f.isBlocked("spam")).isFalse()
    }

    @Test
    fun `任意一条规则命中即屏蔽`() {
        val f =
            filter(
                DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "广告"),
                DanmakuBlockRule(DanmakuBlockRuleType.Regex, "/^\\d+$/"),
            )
        assertThat(f.isBlocked("卖广告")).isTrue()
        assertThat(f.isBlocked("12345")).isTrue()
        assertThat(f.isBlocked("正常弹幕")).isFalse()
    }

    @Test
    fun `重复 setRules 覆盖旧规则`() {
        val f = filter(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "旧"))
        assertThat(f.isBlocked("旧弹幕")).isTrue()
        f.setRules(listOf(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "新")))
        assertThat(f.isBlocked("旧弹幕")).isFalse()
        assertThat(f.isBlocked("新弹幕")).isTrue()
    }
}
