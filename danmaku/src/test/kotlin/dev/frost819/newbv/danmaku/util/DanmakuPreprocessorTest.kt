package dev.frost819.newbv.danmaku.util

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import org.junit.jupiter.api.Test

class DanmakuPreprocessorTest {
    @Test
    fun `屏蔽等级关闭保留全部而开启保留达到阈值的弹幕`() {
        val items = listOf(item(1, score = 0), item(2, score = 8), item(3, score = 9), item(4, score = 12))
        assertThat(DanmakuPreprocessor.process(items, DanmakuState(blockLevel = 0))).containsExactlyElementsIn(items)
        assertThat(DanmakuPreprocessor.process(items, DanmakuState(blockLevel = 9))).containsExactly(items[2], items[3])
        assertThat(DanmakuPreprocessor.process(items, DanmakuState(blockLevel = 12))).containsExactly(items[3])
    }

    @Test
    fun `等级过滤先于合并且不依赖自定义屏蔽开关`() {
        val items = listOf(item(1, score = 8), item(2, score = 9), item(3, score = 11))
        val state = DanmakuState(blockLevel = 9, blockEnabled = false, mergeMode = DanmakuMergeMode.Similar)
        val result = DanmakuPreprocessor.process(items, state).single()
        assertThat(result.mergedCount).isEqualTo(2)
        assertThat(result.content).isEqualTo("abcd ×2")
        assertThat(DanmakuPreprocessor.process(items, state.copy(blockLevel = 0)).single().mergedCount).isEqualTo(3)
    }

    @Test
    fun `关闭合并不再执行密度优选`() {
        val items = (1L..100L).map { item(it) }
        assertThat(DanmakuPreprocessor.process(items, DanmakuState())).containsExactlyElementsIn(items)
    }

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
        // 恢复类型后默认跨类型合并：同文归入簇首（滚动）一簇
        val merged =
            DanmakuPreprocessor
                .process(items, state.copy(enabledTypes = dev.frost819.newbv.danmaku.entity.DanmakuType.entries))
                .single()
        assertThat(merged.mode).isEqualTo(1)
        assertThat(merged.mergedCount).isEqualTo(2)
    }
}
