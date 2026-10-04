package dev.frost819.newbv.app.data

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import org.junit.jupiter.api.Test

/**
 * 屏蔽串导入/导出解析测试。
 */
class DanmakuBlockRuleImportExportTest {
    @Test
    fun `解析新版 B 站 XML 屏蔽串`() {
        val xml =
            """
            <filters>
            <item enabled="true">t=广告</item>
            <item enabled="true">r=/^\d+秒/</item>
            <item enabled="true">u=12345678</item>
            <item enabled="false">t=已停用</item>
            </filters>
            """.trimIndent()
        val result = parseBlockRules(xml)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.rules).hasSize(4)
        assertThat(result.rules[0]).isEqualTo(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "广告"))
        assertThat(result.rules[1]).isEqualTo(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "/^\\d+秒/"))
        assertThat(result.rules[2].type).isEqualTo(DanmakuBlockRuleType.User)
        assertThat(result.rules[2].value).isEqualTo(midHashOfUid(12345678L))
        assertThat(result.rules[3].enabled).isFalse()
    }

    @Test
    fun `XML 值中的等号与 XML 实体正确保留`() {
        val xml = """<filters><item enabled="true">t=a=b&amp;c&lt;d</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules.single().value).isEqualTo("a=b&c<d")
    }

    @Test
    fun `无前缀的 item 按关键词导入`() {
        val xml = """<filters><item enabled="true">普通关键词</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules.single().type).isEqualTo(DanmakuBlockRuleType.Keyword)
        assertThat(result.rules.single().value).isEqualTo("普通关键词")
    }

    @Test
    fun `r 开头但无等号的文本不被误截断`() {
        val xml = """<filters><item enabled="true">red</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules.single().value).isEqualTo("red")
    }

    @Test
    fun `解析旧版 f 节点格式`() {
        val xml = """<filters><f t="0">旧关键词</f><f t="1">^test\d+</f><f t="2">87654321</f></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules).hasSize(3)
        assertThat(result.rules[0].type).isEqualTo(DanmakuBlockRuleType.Keyword)
        assertThat(result.rules[1].type).isEqualTo(DanmakuBlockRuleType.Regex)
        // 纯数字用户值按 UID 转换为 midHash
        assertThat(result.rules[2].value).isEqualTo(midHashOfUid(87654321L))
    }

    @Test
    fun `XML 注释节点被跳过`() {
        val xml =
            """<filters><!-- 这是注释 --><item enabled="true">t=词</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules).hasSize(1)
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    fun `颜色规则导入并统一大写`() {
        val xml = """<filters><item enabled="true">c=ff6699</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules.single().type).isEqualTo(DanmakuBlockRuleType.Color)
        assertThat(result.rules.single().value).isEqualTo("FF6699")
    }

    @Test
    fun `非法颜色规则计入 skipped`() {
        val xml = """<filters><item enabled="true">c=GGGGGG</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules).isEmpty()
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `非法正则计入 skipped`() {
        val xml = """<filters><item enabled="true">r=[invalid</item></filters>"""
        val result = parseBlockRules(xml)
        assertThat(result.rules).isEmpty()
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `纯文本行自动判型`() {
        val text =
            """
            前方高能
            /^(冷知识|热知识)/
            ^开头匹配\d+
            t=行内前缀
            u=11111111
            """.trimIndent()
        val result = parseBlockRules(text)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.rules.map { it.type })
            .containsExactly(
                DanmakuBlockRuleType.Keyword,
                DanmakuBlockRuleType.Regex,
                DanmakuBlockRuleType.Regex,
                DanmakuBlockRuleType.Keyword,
                DanmakuBlockRuleType.User,
            ).inOrder()
        assertThat(result.rules[3].value).isEqualTo("行内前缀")
        // 8 位纯数字按 UID 优先转换（B 站 u= 官方语义），而非原样当 midHash
        assertThat(result.rules[4].value).isEqualTo(midHashOfUid(11111111L))
    }

    @Test
    fun `纯数字 UID 行内前缀转换为 midHash`() {
        val result = parseBlockRules("u=2")
        assertThat(result.rules.single().value).isEqualTo(midHashOfUid(2L))
    }

    @Test
    fun `空行与空白行被跳过`() {
        val result = parseBlockRules("词一\n\n  \n词二\n")
        assertThat(result.rules.map { it.value }).containsExactly("词一", "词二").inOrder()
    }

    @Test
    fun `超长行被跳过`() {
        val long = "a".repeat(301)
        val result = parseBlockRules(long)
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `控制字符被剥离后正常解析`() {
        val result = parseBlockRules("<filters><item enabled=\"true\">\u0007t=词\u0000</item></filters>")
        assertThat(result.rules.single().value).isEqualTo("词")
    }

    @Test
    fun `midHashOfUid 输出 8 位十六进制`() {
        assertThat(midHashOfUid(0L)).matches("[0-9a-f]{8}")
        assertThat(midHashOfUid(10000L)).matches("[0-9a-f]{8}")
    }

    @Test
    fun `导出再导入往返保持语义`() {
        val state =
            DanmakuBlockRuleStore.State(
                enabled = true,
                rules =
                    listOf(
                        DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "广告&推广"),
                        DanmakuBlockRule(DanmakuBlockRuleType.Regex, "/^\\d+秒/", enabled = false),
                        DanmakuBlockRule(DanmakuBlockRuleType.User, "abc12345"),
                        DanmakuBlockRule(DanmakuBlockRuleType.Color, "FF6699"),
                    ),
            )
        val xml = exportBlockRulesXml(state)
        val parsed = parseBlockRules(xml)
        assertThat(parsed.skipped).isEqualTo(0)
        assertThat(parsed.rules).isEqualTo(state.rules)
    }
}
