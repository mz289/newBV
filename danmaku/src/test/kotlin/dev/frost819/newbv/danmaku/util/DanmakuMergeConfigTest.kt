package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuCountMark
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import org.junit.jupiter.api.Test

class DanmakuMergeConfigTest {
    @Test
    fun `相似度恰好等于阈值时允许合并`() {
        assertThat(DanmakuMerger.isSimilar("abcde", "abcd", 0.8f)).isTrue()
        assertThat(DanmakuMerger.isSimilar("abcde", "abcd", 0.81f)).isFalse()
    }

    private fun item(
        text: String,
        time: Long = 0,
        mode: Int = 1,
        pool: Int = 0,
        originalMode: Int = mode,
    ) = DanmakuItemData(time, time, text, mode, 25, 0, pool = pool, originalMode = originalMode)

    @Test
    fun `旧模式迁移保留合并开关`() {
        assertThat(DanmakuMergeMode.fromPreference(0)).isEqualTo(DanmakuMergeMode.Off)
        assertThat(DanmakuMergeMode.fromPreference(2)).isEqualTo(DanmakuMergeMode.Similar)
        assertThat(DanmakuMergeMode.fromPreference(99)).isEqualTo(DanmakuMergeMode.Off)
        assertThat(DanmakuMergeMode.Similar.preferenceValue).isEqualTo(2)
    }

    @Test
    fun `窗口从簇首起算并包含边界`() {
        val result =
            DanmakuMerger.mergeSimilar(
                listOf(item("测试", 0), item("测试", 20_000), item("测试", 20_001)),
                config = DanmakuMergeConfig(windowSeconds = 20),
            )
        assertThat(result.map { it.content }).containsExactly("测试 ×2", "测试").inOrder()
    }

    @Test
    fun `匹配算法和归一化可独立配置`() {
        val strict = DanmakuMergeConfig(editDistanceThreshold = 0, cosineThreshold = 101, recognizePinyin = false)
        assertThat(DanmakuMerger.mergeSimilar(listOf(item("abcdef"), item("abcdeg")), config = strict)).hasSize(2)
        assertThat(DanmakuMerger.mergeSimilar(listOf(item("ＡＢＣ！"), item("ABC")), config = strict)).hasSize(1)
        assertThat(
            DanmakuMerger.normalize("Ａ B！", strict.copy(trimWidth = false, trimSpace = false, trimEnding = false)),
        ).isEqualTo("Ａ B！")
        assertThat(DanmakuMerger.normalize("！！！")).isEqualTo("！！！")
    }

    @Test
    fun `禁止跨类型时相同文本仍按类型分别成组`() {
        val items = listOf(item("你好", mode = 1), item("你好", mode = 4), item("你好", mode = 5))
        assertThat(DanmakuMerger.mergeSimilar(items)).hasSize(1)
        assertThat(DanmakuMerger.mergeSimilar(items, config = DanmakuMergeConfig(crossMode = false))).hasSize(3)
    }

    @Test
    fun `字幕和底部豁免保留每条原文`() {
        val config = DanmakuMergeConfig(skipBottom = true)
        val items =
            listOf(
                item("同文", pool = 1),
                item("同文", pool = 1),
                item("同文", mode = 4),
                item("同文", mode = 4),
            )
        assertThat(DanmakuMerger.mergeSimilar(items, config = config)).hasSize(4)
        assertThat(
            DanmakuMerger.mergeSimilar(
                items,
                config = config.copy(skipSubtitle = false, skipBottom = false),
            ),
        ).hasSize(1)
    }

    @Test
    fun `隐藏前缀后缀标记仍然合并并保留元数据`() {
        val items = listOf(item("字幕", pool = 1), item("字幕", pool = 1))
        val config = DanmakuMergeConfig(skipSubtitle = false, markPosition = DanmakuCountMark.Off)
        val result = DanmakuMerger.mergeSimilar(items, config = config).single()
        assertThat(result.content).isEqualTo("字幕")
        assertThat(result.pool).isEqualTo(1)
        assertThat(result.mergedType).isEqualTo(DanmakuItemData.MERGED_TYPE_MERGED)
        assertThat(
            DanmakuMerger
                .mergeSimilar(
                    items,
                    config = config.copy(markPosition = DanmakuCountMark.Prefix),
                ).single()
                .content,
        ).isEqualTo("×2 字幕")
        assertThat(
            DanmakuMerger
                .mergeSimilar(
                    items,
                    config = config.copy(markPosition = DanmakuCountMark.Suffix),
                ).single()
                .content,
        ).isEqualTo("字幕 ×2")
    }

    @Test
    fun `合并数量增加时始终保留原始字号`() {
        for (count in listOf(2, 6, 100)) {
            assertThat(DanmakuMerger.mergeSimilar(List(count) { item("测试") }).single().textSize).isEqualTo(25)
        }
    }

    @Test
    fun `过期簇不会阻止后续相似匹配`() {
        val old = List(70) { item("旧弹幕编号$it", it.toLong()) }
        val current = listOf(item("abcdef", 50_000), item("abcdeg", 50_001))
        val result = DanmakuMerger.mergeSimilar(old + current)
        assertThat(result.last().content).isEqualTo("abcdef ×2")
    }

    @Test
    fun `高密度下重复变体复用已有簇且过期后正确清理别名`() {
        val input =
            listOf(item("abcdef"), item("abcdeg", 1)) +
                List(64) { item("x".repeat(81) + it, 2L + it) } + item("abcdeg", 100)
        val result = DanmakuMerger.mergeSimilar(input)
        assertThat(result.first().content).isEqualTo("abcdef ×3")
        assertThat(result.none { it.content == "abcdeg" }).isTrue()
        val expired = DanmakuMerger.mergeSimilar(input + listOf(item("abcdeg", 40_000), item("abcdeg", 40_001)))
        assertThat(expired.last().content).isEqualTo("abcdeg ×2")
        assertThat(expired.last().position).isEqualTo(40_000)
    }

    @Test
    fun `非法配置会限制到可用范围`() {
        val result =
            DanmakuMergeConfig(
                windowSeconds = -1,
                editDistanceThreshold = -2,
                cosineThreshold = 999,
            ).sanitized()
        assertThat(result.windowSeconds).isEqualTo(1)
        assertThat(result.editDistanceThreshold).isEqualTo(0)
        assertThat(result.cosineThreshold).isEqualTo(101)
    }
}
