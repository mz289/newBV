package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * [DanmakuMerger] / [DanmakuPreprocessor] 性能基准。
 *
 * JVM 粗测（非 JMH）：预热 2 轮取 5 轮最小值，只打印耗时供回归对比，
 * 不做耗时断言以免 CI 抖动误报。
 */
class DanmakuMergerPerfTest {
    /** 常用汉字池：让拼音字典真实参与编码。 */
    private val hanziPool = "今天天气真好前方高能直接三连主播太可爱了直播吧兄弟们哈哈笑死弹幕护体经典名场面打卡"
    private val random = Random(42)

    @Test
    fun `合并管线性能基准`() {
        // 字典懒加载（一次性，含解析 2.7 万行）
        val dictStart = System.nanoTime()
        DanmakuPinyinEncoder.encode("测")
        println(
            "pinyin dict load: ${(System.nanoTime() - dictStart) / 1_000_000.0} ms",
        )

        bench("exact-spam 6000 items (HashMap fast path)") {
            DanmakuMerger.mergeSimilar(spamItems(6, 1000, spreadMs = 600_000))
        }
        bench("worst-case 3000 unique texts in one 30s window") {
            DanmakuMerger.mergeSimilar(uniqueItems(3000, spreadMs = 30_000))
        }
        bench("worst-case 3000 unique, usePinyin=false") {
            DanmakuMerger.mergeSimilar(uniqueItems(3000, spreadMs = 30_000), usePinyin = false)
        }
        bench("mixed 12000 items over 12min") {
            DanmakuMerger.mergeSimilar(mixedItems(12_000))
        }
        bench("full pipeline (block rules + merge) on mixed 12000") {
            DanmakuPreprocessor.process(mixedItems(12_000), pipelineState)
        }
    }

    // ── 数据构造 ──────────────────────────────────────────────────

    private val pipelineState =
        DanmakuState(
            mergeMode = DanmakuMergeMode.Similar,
            blockEnabled = true,
            blockRules =
                buildList {
                    repeat(10) { add(DanmakuBlockRule(DanmakuBlockRuleType.Keyword, "关键词$it")) }
                    repeat(5) { add(DanmakuBlockRule(DanmakuBlockRuleType.Regex, "测试$it\\d+")) }
                    add(DanmakuBlockRule(DanmakuBlockRuleType.User, "abcdef01"))
                    add(DanmakuBlockRule(DanmakuBlockRuleType.Color, "ff0000"))
                },
        )

    private fun randomText(): String {
        val len = 10 + random.nextInt(11)
        val sb = StringBuilder(len)
        repeat(len) { sb.append(hanziPool[random.nextInt(hanziPool.length)]) }
        return sb.toString()
    }

    /** 数条刷屏文本各重复 [copies] 次，穿插分布在 [spreadMs] 时间轴上。 */
    private fun spamItems(
        texts: Int,
        copies: Int,
        spreadMs: Long,
    ): List<DanmakuItemData> {
        val contents = (1..texts).map { "刷屏文案$it" + "！".repeat(it) }
        val items = ArrayList<DanmakuItemData>(texts * copies)
        var id = 1L
        repeat(copies) { copy ->
            contents.forEachIndexed { index, content ->
                val position = (spreadMs * (copy * texts + index) / (copies * texts)).toLong()
                items.add(DanmakuItemData(id++, position, content, 1, 25, 0))
            }
        }
        return items
    }

    /** [count] 条互不相同的随机文本，分布在 [spreadMs] 内（窗口内高簇数的最坏情况）。 */
    private fun uniqueItems(
        count: Int,
        spreadMs: Long,
    ): List<DanmakuItemData> {
        val seen = HashSet<String>(count * 2)
        val items = ArrayList<DanmakuItemData>(count)
        var id = 1L
        while (items.size < count) {
            val text = randomText()
            if (!seen.add(text)) continue
            val position = (random.nextDouble() * spreadMs).toLong()
            items.add(DanmakuItemData(id++, position, text, 1, 25, 0))
        }
        return items
    }

    /** 70% 随机独特文本 + 30% 二十组刷屏复读，模拟真实热门视频分段。 */
    private fun mixedItems(count: Int): List<DanmakuItemData> {
        val spamPool = (1..20).map { "前方高能$it 自留" }
        val items = ArrayList<DanmakuItemData>(count)
        var id = 1L
        for (i in 0 until count) {
            val content = if (random.nextInt(10) < 7) randomText() else spamPool[random.nextInt(spamPool.size)]
            val position = (random.nextDouble() * 720_000).toLong()
            items.add(DanmakuItemData(id++, position, content, 1, 25, 0))
        }
        return items
    }

    private fun bench(
        label: String,
        block: () -> List<DanmakuItemData>,
    ) {
        repeat(2) { block() }
        var best = Long.MAX_VALUE
        repeat(5) {
            val start = System.nanoTime()
            val result = block()
            val elapsed = System.nanoTime() - start
            best = minOf(best, elapsed)
            check(result.isNotEmpty())
        }
        println("$label: best ${"%.1f".format(best / 1_000_000.0)} ms")
    }
}
