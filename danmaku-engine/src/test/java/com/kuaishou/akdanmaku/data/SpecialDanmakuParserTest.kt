package com.kuaishou.akdanmaku.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [SpecialDanmakuParser] 单元测试。
 *
 * 语义基准：B 站官方客户端弹幕引擎的 mode 7 载荷解析行为。
 * 样例取自真实弹幕数据（BV1qx411c772 绯色月下 P1/P2、BV15x411F7oZ 美丽之物）。
 */
class SpecialDanmakuParserTest {
    /**
     * 按官方 14 项载荷补齐缺省位（不同下标缺省值不同），只覆盖传入的下标。
     */
    private fun payload(vararg fields: String): String {
        val merged = DEFAULTS.toMutableList()
        fields.forEachIndexed { index, value -> merged[index] = value }
        return merged.joinToString(",", prefix = "[", postfix = "]")
    }

    @Test
    fun `parses real pixel payload and normalizes to stage fractions`() {
        // 美丽之物 108.3s：["548","258","1-1","4","きっと　　","0","0","248","258","1000","0","true","黑体","0"]
        val spec =
            SpecialDanmakuParser.parse(
                """["548","258","1-1","4","きっと　　　　","0","0","248","258","1000","0","true","黑体","0"]""",
            )!!
        assertThat(spec.text).isEqualTo("きっと　　　　")
        // 像素 > 1 → /861、/572（官方 relativeCoords），y 为文本顶边比例
        assertThat(spec.keyPoints[0][0]).isWithin(1e-6f).of(548f / 861f)
        assertThat(spec.keyPoints[0][1]).isWithin(1e-6f).of(258f / 572f)
        assertThat(spec.keyPoints[1][0]).isWithin(1e-6f).of(248f / 861f)
        assertThat(spec.keyPoints[1][1]).isWithin(1e-6f).of(258f / 572f)
        assertThat(spec.displayDurationMs).isEqualTo(4000L)
        assertThat(spec.animDurationMs).isEqualTo(1000L)
        assertThat(spec.animDelayMs).isEqualTo(0L)
        assertThat(spec.beginAlpha).isEqualTo(1f)
        assertThat(spec.endAlpha).isEqualTo(1f)
        // 第 12 项字符串 "true" → 宽松布尔判定为真 → 描边
        assertThat(spec.stroked).isTrue()
        // 第 14 项数值 0 → 非 "true" → 不缓动
        assertThat(spec.easeInOut).isFalse()
        assertThat(spec.rotateZ).isEqualTo(0f)
    }

    @Test
    fun `ratio values are kept per value including boundaries`() {
        // 绯色月下 231s 真实载荷：起点 (0.4, 0.2) 比例，终点 endX=0 与 endY=0.2 各自判定
        val spec =
            SpecialDanmakuParser.parse(
                """[0.4,0.2,"1-1",10,"MMMM",0,0,0,0.2,6000,1500,false,"黑体",1]""",
            )!!
        assertThat(spec.keyPoints[0][0]).isEqualTo(0.4f)
        assertThat(spec.keyPoints[0][1]).isEqualTo(0.2f)
        // 0 ≤ 1 → 仍是比例 0（官方逐值判定，不是「成对」）
        assertThat(spec.keyPoints[1][0]).isEqualTo(0f)
        assertThat(spec.keyPoints[1][1]).isEqualTo(0.2f)
        assertThat(spec.animDelayMs).isEqualTo(1500L)
        // 第 14 项数值 1 → 宽松布尔判定为真 → 缓动
        assertThat(spec.easeInOut).isTrue()
    }

    @Test
    fun `value exactly one is a ratio while just above one is a pixel`() {
        val spec = SpecialDanmakuParser.parse(payload("1", "1"))!!
        // ≤ 1 → 比例 1.0（右缘 / 底边）
        assertThat(spec.keyPoints[0][0]).isEqualTo(1f)
        assertThat(spec.keyPoints[0][1]).isEqualTo(1f)
        val above = SpecialDanmakuParser.parse(payload("1.0001", "1.0001"))!!
        assertThat(above.keyPoints[0][0]).isWithin(1e-6f).of(1.0001f / 861f)
        assertThat(above.keyPoints[0][1]).isWithin(1e-6f).of(1.0001f / 572f)
    }

    @Test
    fun `negative coordinates clamp to zero`() {
        // 美丽之物真实载荷：["-5","275",...] → 官方 Math.max(0, ·)
        val spec =
            SpecialDanmakuParser.parse(
                """["-5","275","1-1","5.3","█","0","0","-5","275","500","0","false","MS UI Gothic","0"]""",
            )!!
        assertThat(spec.keyPoints[0][0]).isEqualTo(0f)
        assertThat(spec.keyPoints[0][1]).isWithin(1e-6f).of(275f / 572f)
    }

    @Test
    fun `drops payloads with fewer than fourteen fields`() {
        // 官方 JSON.parse 后 u.length < 14 → null：远古 5 字段载荷在现代 App 上不显示
        assertThat(SpecialDanmakuParser.parse("""["20","355","1.00-1","3","肌膚污穢了嗎？"]""")).isNull()
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true]""")).isNotNull()
    }

    @Test
    fun `rejects payloads failing type gates`() {
        // u[4]、u[12] 必须是字符串
        assertThat(SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "123"))).isNull()
        assertThat(SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "0", "0", "0", "0", "500", "0", "false", "12"))).isNull()
        // 数值位不得为布尔
        assertThat(SpecialDanmakuParser.parse(payload("true"))).isNull()
        assertThat(SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "false"))).isNull()
        // u[11] / u[13] 允许布尔
        assertThat(SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "0", "0", "0", "0", "500", "0", "true", "\"f\"", "false"))).isNotNull()
        // u[6]（rotateY）官方不校验
        assertThat(SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "0", "\"x\""))).isNotNull()
    }

    @Test
    fun `rejects malformed json like the official JSON parse`() {
        assertThat(SpecialDanmakuParser.parse("""{"p":{}}""")).isNull()
        assertThat(SpecialDanmakuParser.parse("plain text")).isNull()
        // 尾逗号 / 空槽位在 JSON.parse 中非法
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true,]""")).isNull()
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,,0,0,500,0,false,"f",true]""")).isNull()
        // 右括号之后存在非空白字符同样非法
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true]] junk""")).isNull()
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true]   """)).isNotNull()
        // null 字面量合法，但落在数值位上会被类型校验丢弃
        assertThat(SpecialDanmakuParser.parse("""[null,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true]""")).isNull()
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,500,0,false,"f",true,null]""")).isNotNull()
    }

    @Test
    fun `alpha accepts numbers and one or two dash parts`() {
        val constant = SpecialDanmakuParser.parse(payload("0", "0", "1"))!!
        assertThat(constant.beginAlpha).isEqualTo(1f)
        assertThat(constant.endAlpha).isEqualTo(1f)
        val numeric = SpecialDanmakuParser.parse(payload("0", "0", "0.5"))!!
        assertThat(numeric.beginAlpha).isEqualTo(0.5f)
        assertThat(numeric.endAlpha).isEqualTo(0.5f)
        val single = SpecialDanmakuParser.parse(payload("0", "0", "\"0.25\""))!!
        assertThat(single.beginAlpha).isEqualTo(0.25f)
        assertThat(single.endAlpha).isEqualTo(0.25f)
        val range = SpecialDanmakuParser.parse(payload("0", "0", "\"0.7-0\""))!!
        assertThat(range.beginAlpha).isEqualTo(0.7f)
        assertThat(range.endAlpha).isEqualTo(0f)
        // 官方 ≥3 段时为初始值 0（整条透明）
        val many = SpecialDanmakuParser.parse(payload("0", "0", "\"1-0.5-0.2\""))!!
        assertThat(many.beginAlpha).isEqualTo(0f)
        assertThat(many.endAlpha).isEqualTo(0f)
    }

    @Test
    fun `boolean flags follow the official coercion`() {
        fun strokedOf(literal: String): Boolean =
            SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "0", "0", "0", "0", "500", "0", literal))
                ?.stroked ?: error("payload rejected: $literal")

        assertThat(strokedOf("true")).isTrue()
        assertThat(strokedOf("false")).isFalse()
        // 字符串只有 "true"（忽略大小写）为真；"1" / "0" 都是假
        assertThat(strokedOf("\"True\"")).isTrue()
        assertThat(strokedOf("\"1\"")).isFalse()
        assertThat(strokedOf("\"0\"")).isFalse()
        // 数字 > 0 为真
        assertThat(strokedOf("1")).isTrue()
        assertThat(strokedOf("0")).isFalse()

        fun easeOf(literal: String): Boolean =
            SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "4", "\"t\"", "0", "0", "0", "0", "500", "0", "false", "\"f\"", literal))
                ?.easeInOut ?: error("payload rejected: $literal")

        assertThat(easeOf("true")).isTrue()
        assertThat(easeOf("1")).isTrue()
        assertThat(easeOf("\"1\"")).isFalse()
        assertThat(easeOf("0")).isFalse()
    }

    @Test
    fun `durations clamp at zero and accept decimals and strings`() {
        val spec =
            SpecialDanmakuParser.parse(
                """[0,0,"1-1",3.3,"t",0,0,0,0,"900.5",0,false,"f",true]""",
            )!!
        assertThat(spec.displayDurationMs).isEqualTo(3300L)
        assertThat(spec.animDurationMs).isEqualTo(900L)
        val negative = SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "-4", "\"t\"", "0", "0", "0", "0", "-500", "-300"))!!
        assertThat(negative.displayDurationMs).isEqualTo(0L)
        assertThat(negative.animDurationMs).isEqualTo(0L)
        assertThat(negative.animDelayMs).isEqualTo(0L)
        // 空白字符串按 JS Number("") = 0
        val blank = SpecialDanmakuParser.parse("""[0,0,"1-1",4,"t",0,0,0,0,"","",false,"f",true]""")!!
        assertThat(blank.animDurationMs).isEqualTo(0L)
        assertThat(blank.animDelayMs).isEqualTo(0L)
    }

    @Test
    fun `visible duration is max of display and motion timeline`() {
        // 官方动作总长 = max(displayDuration, animDelay + animDuration)
        val lyric =
            SpecialDanmakuParser.parse(
                """[0,0,"1-1",6,"其/nノ",0,0,0.99,0,10000,0,false,"楷体",1]""",
            )!!
        assertThat(lyric.visibleDurationMs).isEqualTo(10_000L)
        val wall =
            SpecialDanmakuParser.parse(
                """[10,528,"1-1",3.3,"x",0,0,10,528,900,0,0,"SimSun",true]""",
            )!!
        assertThat(wall.visibleDurationMs).isEqualTo(3300L)
    }

    @Test
    fun `motion path normalizes every point and overrides start end`() {
        // 绯色月下真实路径弹幕：起点/终点字段被忽略
        val spec =
            SpecialDanmakuParser.parse(
                """[0,0,"1-0",4.5,"●",0,0,0,0,500,0,0,"SimHei",true,"M610,422L300,200"]""",
            )!!
        assertThat(spec.keyPoints).hasSize(2)
        assertThat(spec.keyPoints[0][0]).isWithin(1e-6f).of(610f / 861f)
        assertThat(spec.keyPoints[0][1]).isWithin(1e-6f).of(422f / 572f)
        assertThat(spec.keyPoints[1][0]).isWithin(1e-6f).of(300f / 861f)
        assertThat(spec.keyPoints[1][1]).isWithin(1e-6f).of(200f / 572f)
        // 首字符被 substr(1) 丢弃，M / L 均可作为分隔符；路径点同样适用比例规则
        val ratio =
            SpecialDanmakuParser.parse(
                """[0,0,"1-1",4,"p",0,0,0,0,500,0,false,"f",0,"L0.1,0.1L0.5,0.9M0.2,0.2"]""",
            )!!
        assertThat(ratio.keyPoints).hasSize(3)
        assertThat(ratio.keyPoints[0][0]).isEqualTo(0.1f)
        assertThat(ratio.keyPoints[1][0]).isEqualTo(0.5f)
        assertThat(ratio.keyPoints[1][1]).isEqualTo(0.9f)
        assertThat(ratio.keyPoints[2][1]).isEqualTo(0.2f)
    }

    @Test
    fun `empty path result drops the whole danmaku`() {
        // 官方：路径字符串存在但解析不出点 → keyCoords 为空 → 视图永不创建（不回退起终点）
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"p",0,0,0,0,500,0,false,"f",0,""]""")).isNull()
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"p",0,0,0,0,500,0,false,"f",0,"xyz"]""")).isNull()
        // 第 15 项非字符串 → 视为没有路径，回退起终点
        assertThat(SpecialDanmakuParser.parse("""[0,0,"1-1",4,"p",0,0,0,0,500,0,false,"f",0,123]""")).isNotNull()
    }

    @Test
    fun `text keeps escapes verbatim and has no slash-n convention`() {
        val spec =
            SpecialDanmakuParser.parse("""[0,0,"1-1",3,"他说：\"你好, 世界\"\n第二行",0,0,0,0,0,0,false,"f",true]""")!!
        assertThat(spec.text).isEqualTo("他说：\"你好, 世界\"\n第二行")
        // 官方对文本不做任何替换：老播放器的 "/n" 约定在现代 App 引擎上按字面渲染
        val legacy = SpecialDanmakuParser.parse(payload("\"110\"", "\"27\"", "\"0.7-0.7\"", "\"3\"", "\"そ/nの/n肄\""))!!
        assertThat(legacy.text).isEqualTo("そ/nの/n肄")
        // 空文本合法（官方只校验类型）
        val empty = SpecialDanmakuParser.parse(payload("0", "0", "\"1-1\"", "3", "\"\""))!!
        assertThat(empty.text).isEmpty()
    }

    @Test
    fun `defaults match payload field order`() {
        // 旋转角为度数；rotateY 解析但不参与渲染
        val spec = SpecialDanmakuParser.parse(payload("100", "80", "\"1-1\"", "4", "\"翻转\"", "30", "60"))!!
        assertThat(spec.rotateZ).isEqualTo(30f)
        assertThat(spec.rotateY).isEqualTo(60f)
        // 辅助载荷缺省描边为假、缺省不缓动
        assertThat(spec.stroked).isFalse()
        assertThat(spec.easeInOut).isFalse()
    }

    companion object {
        /** 14 项载荷的缺省值，下标与官方字段一一对应。 */
        private val DEFAULTS =
            listOf(
                "0",
                "0",
                "\"1-1\"",
                "4",
                "\"t\"",
                "0",
                "0",
                "0",
                "0",
                "500",
                "0",
                "false",
                "\"SimHei\"",
                "false",
            )
    }
}
