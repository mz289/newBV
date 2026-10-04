package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import org.junit.jupiter.api.Test

class DanmakuSimilarityTest {
    private val disabled = DanmakuMergeConfig(editDistanceThreshold = 0, cosineThreshold = 101, recognizePinyin = false)

    private fun matches(
        a: String,
        b: String,
        config: DanmakuMergeConfig,
    ): Boolean =
        DanmakuSimilarity.matches(DanmakuSimilarity.prepare(a, config), DanmakuSimilarity.prepare(b, config), config)

    @Test
    fun `编辑距离阈值计字符频次差且可禁用`() {
        val config = disabled.copy(editDistanceThreshold = 5)
        assertThat(matches("你指尖跃动的电光", "你之间跃动的电光", config)).isTrue()
        assertThat(matches("你指尖跃动的电光", "你之间跃动的电光", config.copy(editDistanceThreshold = 1))).isFalse()
        assertThat(matches("abcdef", "abcdeg", disabled)).isFalse()
        assertThat(matches("abcdef", "abcdef", disabled)).isTrue()
    }

    @Test
    fun `短弹幕自动收紧距离阈值防止误合并`() {
        assertThat(matches("好的", "好吧", disabled.copy(editDistanceThreshold = 8))).isFalse()
        assertThat(matches("好", "坏", disabled.copy(editDistanceThreshold = 8))).isFalse()
        assertThat(matches("abcdefgh", "abcdefgi", disabled.copy(editDistanceThreshold = 2))).isTrue()
    }

    @Test
    fun `词频向量识别重复片段`() {
        val config = disabled.copy(cosineThreshold = 45)
        assertThat(matches("yeah!~", "yeah!~yeah!~yeah!~", config)).isTrue()
        assertThat(matches("yeah!~", "yeah!~yeah!~yeah!~", disabled)).isFalse()
        assertThat(matches("aaaa", "zzzz", config)).isFalse()
        assertThat(matches("", "abc", config)).isFalse()
    }

    @Test
    fun `词频阈值越高越严格且覆盖百分比边界`() {
        assertThat(matches("abc", "abcabc", disabled.copy(cosineThreshold = 100))).isTrue()
        assertThat(matches("abcd", "abce", disabled.copy(cosineThreshold = 25))).isTrue()
        assertThat(matches("abcd", "abce", disabled.copy(cosineThreshold = 26))).isFalse()
    }

    @Test
    fun `拼音识别同音字且开关真正生效`() {
        val config = disabled.copy(recognizePinyin = true)
        assertThat(matches("这波操作可以", "这波操做可已", config)).isTrue()
        assertThat(matches("这波操作可以", "这波操做可已", disabled)).isFalse()
        assertThat(matches("甲", "乙", config)).isFalse()
        assertThat(matches("操做A😀", "操作a😀", config)).isTrue()
        assertThat(matches("操做😀", "操作😎", config)).isFalse()
    }

    @Test
    fun `阈值和拼音识别实际接入合并输出并保留原文`() {
        fun item(
            text: String,
            time: Long = 0,
            mode: Int = 1,
        ) = DanmakuItemData(time, time, text, mode, 25, 0)
        val phonetic = listOf(item("这波操作可以"), item("这波操做可已", 1000))
        assertThat(
            DanmakuMerger.mergeSimilar(phonetic, config = disabled.copy(recognizePinyin = true)).single().content,
        ).isEqualTo("这波操作可以 ×2")
        assertThat(DanmakuMerger.mergeSimilar(phonetic, config = disabled)).hasSize(2)
        val repeated = listOf(item("yeah!~"), item("yeah!~yeah!~yeah!~", 1000))
        assertThat(DanmakuMerger.mergeSimilar(repeated, config = disabled.copy(cosineThreshold = 45))).hasSize(1)
    }

    @Test
    fun `代表时间和固定模式取自原始簇并且可关闭`() {
        val items =
            listOf(
                DanmakuItemData(1, 0, "测试", 1, 25, 0),
                DanmakuItemData(2, 1000, "测试", 5, 25, 0),
                DanmakuItemData(3, 2000, "测试", 1, 25, 0),
            )
        val result =
            DanmakuMerger
                .mergeSimilar(
                    items,
                    config = disabled.copy(representativePercent = 50, preferFixedMode = true),
                ).single()
        assertThat(result.position).isEqualTo(1000)
        assertThat(result.mode).isEqualTo(5)
        val original = DanmakuMerger.mergeSimilar(items, config = disabled).single()
        assertThat(original.position).isEqualTo(0)
        assertThat(original.mode).isEqualTo(1)
        assertThat(
            DanmakuMerger.mergeSimilar(items, config = disabled.copy(representativePercent = 100)).single().position,
        ).isEqualTo(2000)
    }
}
