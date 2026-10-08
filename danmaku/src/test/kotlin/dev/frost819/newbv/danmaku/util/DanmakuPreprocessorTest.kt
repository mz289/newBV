package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import org.junit.jupiter.api.Test

class DanmakuPreprocessorTest {
    private fun item(
        id: Long,
        text: String = "abcd",
        time: Long = 0,
        score: Int = 0,
        count: Int = 1,
        user: Long? = null,
        color: Int = 0,
    ) = DanmakuItemData(id, time, text, 1, 25, color, score = score, userId = user, mergedCount = count)

    @Test
    fun `先屏蔽用户再合并不会吞掉未屏蔽用户或虚增数量`() {
        val state =
            DanmakuState(
                mergeMode = DanmakuMergeMode.Similar,
                blockEnabled = true,
                blockRules = listOf(DanmakuBlockRule(DanmakuBlockRuleType.User, "ab")),
            )
        val items = listOf(item(1, user = 0xab), item(2, user = 0xcd), item(3, user = 0xcd))
        val hits = mutableListOf<String>()
        val result = DanmakuPreprocessor.process(items, state, hits::add)
        assertThat(result).hasSize(1)
        assertThat(result.single().content).isEqualTo("abcd ×2")
        assertThat(result.single().userId).isEqualTo(0xcdL)
        assertThat(result.single().mergedCount).isEqualTo(2)
        assertThat(hits).hasSize(1)
        assertThat(
            DanmakuPreprocessor.process(items, state.copy(blockEnabled = false)).single().mergedCount,
        ).isEqualTo(3)
    }

    @Test
    fun `正则匹配原文并兼容颜色关键词与无效规则`() {
        val state =
            DanmakuState(
                mergeMode = DanmakuMergeMode.Similar,
                blockEnabled = true,
                blockRules =
                    listOf(
                        DanmakuBlockRule(DanmakuBlockRuleType.Regex, "^abcd$"),
                        DanmakuBlockRule(DanmakuBlockRuleType.Regex, "["),
                        DanmakuBlockRule(DanmakuBlockRuleType.Color, "ff0000"),
                        DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "HIDE"),
                    ),
            )
        val items = listOf(item(1), item(2), item(3, "red", color = 0xff0000), item(4, "hide"), item(5, "keep"))
        assertThat(DanmakuPreprocessor.process(items, state).map { it.content }).containsExactly("keep")
        assertThat(DanmakuPreprocessor.process(items, state.copy(mergeMode = DanmakuMergeMode.Off))).hasSize(1)
    }

    @Test
    fun `用户颜色屏蔽不受簇首顺序影响`() {
        val state =
            DanmakuState(
                mergeMode = DanmakuMergeMode.Similar,
                blockEnabled = true,
                blockRules =
                    listOf(
                        DanmakuBlockRule(DanmakuBlockRuleType.User, "ab"),
                        DanmakuBlockRule(DanmakuBlockRuleType.Color, "ff0000"),
                    ),
            )
        val items = listOf(item(1, user = 0xab), item(2, color = 0xff0000), item(3, user = 0xcd), item(4, user = 0xcd))
        for (input in listOf(items, items.reversed())) {
            assertThat(DanmakuPreprocessor.process(input, state).single().mergedCount).isEqualTo(2)
        }
    }

    @Test
    fun `隐藏类型不能吞掉允许显示的同文弹幕且切换类型可恢复`() {
        val items =
            listOf(
                DanmakuItemData(1, 0, "same", 1, 25, 0),
                DanmakuItemData(2, 1000, "same", 5, 25, 0),
            )
        val state =
            DanmakuState(
                mergeMode = DanmakuMergeMode.Similar,
                enabledTypes = listOf(dev.frost819.newbv.danmaku.entity.DanmakuType.Top),
            )
        val result = DanmakuPreprocessor.process(items, state).single()
        assertThat(result.mode).isEqualTo(5)
        assertThat(result.mergedCount).isEqualTo(1)
        assertThat(
            DanmakuPreprocessor
                .process(
                    items,
                    state.copy(enabledTypes = dev.frost819.newbv.danmaku.entity.DanmakuType.entries),
                ).single()
                .mergedCount,
        ).isEqualTo(2)
    }

    @Test
    fun `优选优先保留合并项和高分项且不依赖数量标记`() {
        val plain = (1L..100L).map { item(it) }
        val merged = item(101, score = 0, count = 10)
        val highScore = item(102, score = 10)
        val input = plain + merged + highScore
        val output = DanmakuPreprocessor.selectByDensity(input, 10)
        assertThat(output).hasSize(5)
        assertThat(output).containsAtLeast(merged, highScore)
        assertThat(DanmakuPreprocessor.selectByDensity(input.reversed(), 10)).containsExactlyElementsIn(output)
        assertThat(DanmakuPreprocessor.selectByDensity(input, 0)).containsExactlyElementsIn(input)
    }

    @Test
    fun `优选密度边界包含阈值并且不同窗口独立`() {
        val first = (1L..5L).map { item(it, time = 0) }
        val second = (6L..10L).map { item(it, time = 5_000) }
        assertThat(DanmakuPreprocessor.selectByDensity(first + second, 10)).hasSize(10)
        assertThat(DanmakuPreprocessor.selectByDensity(first + second, 4)).hasSize(4)
        assertThat(
            DanmakuPreprocessor.selectByDensity(listOf(item(1, "x".repeat(1000)), item(2, "x".repeat(1000))), 1),
        ).hasSize(1)
    }

    @Test
    fun `合并关闭时优选仍可使用且稀疏弹幕保持原样`() {
        val items = (1L..100L).map { item(it) }
        assertThat(
            DanmakuPreprocessor.process(items, DanmakuState(mergeConfig = DanmakuMergeConfig(dropThreshold = 10))),
        ).hasSize(5)
        assertThat(DanmakuPreprocessor.selectByDensity(items.take(2), 10)).containsExactlyElementsIn(items.take(2))
    }

    @Test
    fun `跨秒边界的密集弹幕共用滑动预算`() {
        val items = (1L..10L).map { item(it, time = if (it <= 5) 4_999 else 5_000) }
        assertThat(DanmakuPreprocessor.selectByDensity(items, 10)).hasSize(5)
    }

    @Test
    fun `连续密集段优选分布于整段而非只保留末尾`() {
        val items = (1L..2000L).map { item(it, time = it * 20) }
        val selected = DanmakuPreprocessor.selectByDensity(items, 20)
        assertThat(selected.size).isGreaterThan(30)
        assertThat(selected.minOf { it.position }).isLessThan(5_000L)
        assertThat(selected.maxOf { it.position }).isGreaterThan(35_000L)
        for (item in selected) {
            assertThat(
                selected.count { it.position >= item.position && it.position < item.position + 5_000 },
            ).isAtMost(10)
        }
    }
}
