package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import org.junit.jupiter.api.Test

/**
 * [DanmakuMerger] 重复弹幕合并测试。
 */
class DanmakuMergerTest {
    @Test
    fun `大量超窗历史簇定案后不影响后续合并`() {
        val history = (1..65).map { item("历史文本$it", 0) }
        val merged =
            DanmakuMerger.mergeSimilar(history + listOf(item("aaaaaaaaaa", 40_000), item("aaaaaaaabb", 41_000)))
        assertThat(merged.last().content).isEqualTo("aaaaaaaaaa ×2")
    }

    @Test
    fun `已并入变体的归一化 key 仍精确归入原簇`() {
        val variants = listOf(item("aaaaaaaaaa", 0), item("aaaaaaaabb", 1000))
        val unrelated = (1..65).map { item("其他文本$it", 2000) }
        val merged = DanmakuMerger.mergeSimilar(variants + unrelated + item("aaaaaaaabb", 3000))
        assertThat(merged.first().content).isEqualTo("aaaaaaaaaa ×3")
    }

    @Test
    fun `纯标点不归并为空文本`() {
        val merged = DanmakuMerger.mergeSimilar(listOf(item("!!!", 0), item("???", 1000)))
        assertThat(merged).hasSize(2)
    }

    @Test
    fun `同音词经拼音距离合并且保持三十秒窗口`() {
        // 再见/在建：字形完全不同但读音相同（zài jiàn），拼音距离命中
        assertThat(DanmakuMerger.mergeSimilar(listOf(item("再见", 0), item("在建", 1000))).single().content)
            .isEqualTo("再见 ×2")
        assertThat(DanmakuMerger.mergeSimilar(listOf(item("同文", 0), item("同文", 25_000))).single().content)
            .isEqualTo("同文 ×2")
    }

    @Test
    fun `距离预算按文本长度缩放`() {
        // 短文本：预算 = maxDistance × lenSum ÷ minDanmakuSize
        assertThat(DanmakuMerger.isSimilar("abcd", "abcde")).isTrue()
        assertThat(DanmakuMerger.isSimilar("abc", "wxy")).isFalse()
        // 长文本：预算封顶 maxDistance，且低重叠文本余弦也救不回来
        assertThat(DanmakuMerger.isSimilar("aaaaaaaaaa", "aaaaaaaa")).isTrue()
        assertThat(DanmakuMerger.isSimilar("aaaaaaaaaa", "aaaabbbbbb")).isFalse()
        // 长度差是距离下界，超过 maxDistance 直接淘汰
        assertThat(DanmakuMerger.isSimilar("ab", "aaaaaaaaaa")).isFalse()
    }

    private fun item(
        content: String,
        positionMs: Long,
        mode: Int = DanmakuItemData.DANMAKU_MODE_ROLLING,
    ): DanmakuItemData =
        DanmakuItemData(
            danmakuId = positionMs,
            position = positionMs,
            content = content,
            mode = mode,
            textSize = 25,
            textColor = 0,
        )

    @Test
    fun `窗口内相同文本合并为一条并带 ×N 后缀`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("前方高能", 1_000),
                    item("前方高能", 5_000),
                    item("前方高能", 8_000),
                ),
            )
        assertThat(merged).hasSize(1)
        assertThat(merged.single().content).isEqualTo("前方高能 ×3")
        assertThat(merged.single().position).isEqualTo(1_000)
    }

    @Test
    fun `不同文本互不合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("aaaa", 1_000),
                    item("bbbb", 2_000),
                ),
            )
        assertThat(merged).hasSize(2)
        assertThat(merged.map { it.content }).containsExactly("aaaa", "bbbb")
    }

    @Test
    fun `超过窗口的重复文本分簇`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("spam", 1_000),
                    item("spam", 2_000),
                    // 距簇首超过 30s 窗口，开新簇
                    item("spam", 32_000),
                ),
            )
        assertThat(merged).hasSize(2)
        assertThat(merged[0].content).isEqualTo("spam ×2")
        assertThat(merged[1].content).isEqualTo("spam")
    }

    @Test
    fun `窗口按簇首起算而非相邻间隔链式延伸`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("x", 0),
                    item("x", 20_000),
                    // 距上一条 20s（链式可并入）但距簇首 40s（超窗）→ 应开新簇
                    item("x", 40_000),
                ),
            )
        assertThat(merged).hasSize(2)
        assertThat(merged[0].content).isEqualTo("x ×2")
        assertThat(merged[1].content).isEqualTo("x")
    }

    @Test
    fun `无重复时原样返回`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(item("a", 1_000), item("b", 2_000)),
            )
        assertThat(merged).hasSize(2)
        assertThat(merged[0].content).isEqualTo("a")
        assertThat(merged[0].mergedType).isEqualTo(DanmakuItemData.MERGED_TYPE_NORMAL)
    }

    @Test
    fun `乱序输入按时间排序后合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("dup", 9_000),
                    item("dup", 1_000),
                    item("dup", 5_000),
                ),
            )
        assertThat(merged).hasSize(1)
        assertThat(merged.single().content).isEqualTo("dup ×3")
        assertThat(merged.single().position).isEqualTo(1_000)
    }

    @Test
    fun `合并项保留簇首属性`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    DanmakuItemData(
                        danmakuId = 1,
                        position = 1_000,
                        content = "text",
                        mode = DanmakuItemData.DANMAKU_MODE_CENTER_TOP,
                        textSize = 18,
                        textColor = 0xFF0000,
                        userId = 0xABCDEF01L,
                    ),
                    item("text", 2_000),
                ),
                crossMode = true,
            )
        val mergedItem = merged.single()
        assertThat(mergedItem.mode).isEqualTo(DanmakuItemData.DANMAKU_MODE_CENTER_TOP)
        assertThat(mergedItem.textSize).isEqualTo(18)
        assertThat(mergedItem.textColor).isEqualTo(0xFF0000)
        assertThat(mergedItem.userId).isEqualTo(0xABCDEF01L)
        assertThat(mergedItem.mergedType).isEqualTo(DanmakuItemData.MERGED_TYPE_MERGED)
    }

    @Test
    fun `空列表与单条直接返回`() {
        assertThat(DanmakuMerger.mergeSimilar(emptyList())).isEmpty()
        val single = listOf(item("only", 1_000))
        assertThat(DanmakuMerger.mergeSimilar(single)).hasSize(1)
    }

    @Test
    fun `窗口为 0 时禁用合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(item("dup", 1_000), item("dup", 1_100)),
                windowMs = 0,
            )
        assertThat(merged).hasSize(2)
    }

    // ── 相似合并 ──────────────────────────────────────────────────

    @Test
    fun `全角半角差异经归一化后合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("前方高能！", 1_000),
                    item("前方高能!", 2_000),
                ),
            )
        assertThat(merged).hasSize(1)
        assertThat(merged.single().content).isEqualTo("前方高能！ ×2")
    }

    @Test
    fun `多重集距离相近的文本合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("aaaaaaaaaa", 1_000),
                    item("aaaaaaaabb", 2_000), // 10 字符 2 替换，多重集距离 4 <= 5
                ),
            )
        assertThat(merged).hasSize(1)
    }

    @Test
    fun `差异过大的文本不合并`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("aaaaaaaaaa", 1_000),
                    item("zzzzzzzzzz", 2_000),
                ),
            )
        assertThat(merged).hasSize(2)
    }

    @Test
    fun `相似合并同样受时间窗口约束`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("aaaaaaaaaa", 1_000),
                    item("aaaaaaaaab", 2_000),
                    // 距簇首超过 30s，开新簇
                    item("aaaaaaaabb", 40_000),
                ),
            )
        assertThat(merged).hasSize(2)
    }

    @Test
    fun `合并项显示簇首原文`() {
        val merged =
            DanmakuMerger.mergeSimilar(
                listOf(
                    item("这波操作可以", 1_000),
                    item("这波操作司以", 2_000), // 6 字符差 1
                ),
            )
        assertThat(merged.single().content).isEqualTo("这波操作可以 ×2")
    }

    @Test
    fun `normalize 全角转半角并去尾部标点`() {
        assertThat(DanmakuMerger.normalize("ＡＢＣ１２３！")).isEqualTo("ABC123")
        assertThat(DanmakuMerger.normalize("好  看 。")).isEqualTo("好看")
        assertThat(DanmakuMerger.normalize("ｗｗｗ")).isEqualTo("www")
    }

    @Test
    fun `maxAllowedDistance 与 withinDistance 判定严格一致`() {
        for (maxDistance in 0..8) {
            for (lenSum in 0..40) {
                val budget = DanmakuMerger.maxAllowedDistance(lenSum, maxDistance)
                // budget 恰好可通过，budget+1 必不可通过
                assertThat(DanmakuMerger.withinDistance(budget, lenSum, maxDistance)).isTrue()
                assertThat(DanmakuMerger.withinDistance(budget + 1, lenSum, maxDistance)).isFalse()
            }
        }
    }

    @Test
    fun `isSimilar 替换计二且短文本缩放保护`() {        assertThat(DanmakuMerger.isSimilar("abcd", "abcd")).isTrue()
        // 1 次替换 = 多重集距离 2 <= 5
        assertThat(DanmakuMerger.isSimilar("abcdef", "abcdeg")).isTrue()
        // 同字异序的多重集相同，距离为 0（bag 距离固有特性，pakku 同）
        assertThat(DanmakuMerger.isSimilar("abcd", "dcba")).isTrue()
        // 4 次替换 = 距离 8，lenSum 8 < 10 按比例缩放后 8×10 不小于 5×8
        assertThat(DanmakuMerger.isSimilar("aaaa", "bbbb")).isFalse()
        // 两字弹幕替换一字的距离为 2，2×10 不小于预算 5×4，不合并
        assertThat(DanmakuMerger.isSimilar("你好", "你哈")).isFalse()
    }

    // ── 拼音 / 余弦 / 跨类型 ──────────────────────────────────────

    @Test
    fun `拼音距离合并同音字`() {
        // 在/再 同音（zài）：字符路径被短文本缩放预算挡住，拼音路径命中
        val pair = listOf(item("在吗在吗", 1_000), item("再吗再吗", 2_000))
        assertThat(DanmakuMerger.mergeSimilar(pair)).hasSize(1)
        // 关闭拼音后各路径都不命中，不再合并
        assertThat(DanmakuMerger.mergeSimilar(pair, usePinyin = false)).hasSize(2)
    }

    @Test
    fun `余弦相似度合并部分重叠文本`() {
        // 刷屏加长版：字符距离 8 超预算、拼音长度差 8 超预筛，但 2-gram 完全重叠
        val pair = listOf(item("哈哈哈哈", 1_000), item("哈哈哈哈哈哈哈哈哈哈哈哈", 2_000))
        assertThat(DanmakuMerger.mergeSimilar(pair)).hasSize(1)
        // 阈值 >100 视为关闭余弦，不再合并
        assertThat(DanmakuMerger.mergeSimilar(pair, maxCosine = 101)).hasSize(2)
    }

    @Test
    fun `同文不同类型默认合并且可显式关闭`() {
        val rolling = item("前方高能", 1_000)
        val top = item("前方高能", 2_000, mode = DanmakuItemData.DANMAKU_MODE_CENTER_TOP)
        // 默认跨类型合并：同文归入簇首（滚动）一簇
        val merged = DanmakuMerger.mergeSimilar(listOf(rolling, top))
        assertThat(merged).hasSize(1)
        assertThat(merged.single().mode).isEqualTo(DanmakuItemData.DANMAKU_MODE_ROLLING)
        // 算法保留关闭能力（crossMode = false 时同文不同类型各自成簇）
        assertThat(DanmakuMerger.mergeSimilar(listOf(rolling, top), crossMode = false)).hasSize(2)
    }
}
