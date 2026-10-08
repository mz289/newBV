package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import dev.frost819.newbv.danmaku.entity.DanmakuType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter
import kotlin.math.pow
import kotlin.math.sqrt

/** 屏蔽原始内容 → 相似合并 → 密度优选。每次处理使用同一份状态快照。 */
object DanmakuPreprocessor {
    fun process(
        items: List<DanmakuItemData>,
        state: DanmakuState,
        onBlockHit: ((String) -> Unit)? = null,
        onItemBlockHit: ((Long, String) -> Unit)? = null,
    ): List<DanmakuItemData> {
        val config = state.mergeConfig.sanitized()
        val filtered =
            if (state.blockEnabled) {
                val filter =
                    DanmakuBlockFilter().apply {
                        enable = true
                        setRules(state.blockRules)
                    }
                items.filterNot { item ->
                    val key = filter.matchedRuleKey(item.content, item.userId, item.textColor)
                    if (key != null) {
                        onBlockHit?.invoke(key)
                        onItemBlockHit?.invoke(item.danmakuId, key)
                    }
                    key != null
                }
            } else {
                items
            }
        // 先按原始类型过滤，避免被隐藏的簇首吞掉允许显示的其他类型。
        val visible =
            if (DanmakuType.All in state.enabledTypes) {
                filtered
            } else {
                val modes = state.enabledTypes.map { it.modeValue }.toSet()
                filtered.filter { it.mode in modes }
            }
        val merged =
            if (state.mergeMode == DanmakuMergeMode.Off) {
                visible
            } else {
                DanmakuMerger.mergeSimilar(visible, config = config)
            }
        return selectByDensity(merged, config.dropThreshold)
    }

    /**
     * 每条弹幕在随后的 5 秒贡献文字密度。按优先级选择，使任意滑动窗口不超预算。
     * 同权重使用稳定散列分散选择，重载、倒退播放时结果一致。
     * 区间预算树避免逐项扫描所有重叠弹幕，处理复杂度 O(n log n)。
     */
    internal fun selectByDensity(
        items: List<DanmakuItemData>,
        threshold: Int,
    ): List<DanmakuItemData> {
        if (threshold <= 0 || items.size < 2) return items
        val times = items.map { it.position }.distinct().sorted()
        val budget = DensityBudget(times.size)
        val ranked =
            items.sortedWith(
                compareByDescending<DanmakuItemData> {
                    it.rank > 0 || it.danmakuStyle == DanmakuItemData.DANMAKU_STYLE_SELF_SEND
                }.thenByDescending { it.mergedCount > 1 }
                    .thenByDescending { it.score }
                    .thenByDescending { it.mergedCount }
                    .thenBy { stableOrder(it) }
                    .thenBy { it.danmakuId },
            )
        val result = ArrayList<DanmakuItemData>()
        for (item in ranked) {
            val start = times.binarySearch(item.position)
            val boundary = times.binarySearch(item.position + 5_000L)
            val end = (if (boundary >= 0) boundary else -boundary - 1) - 1
            // 超长单条的预算封顶，允许它在无其他弹幕的窗口显示。
            val cost = density(item).coerceAtMost(threshold.toDouble())
            if (budget.maximum(start, end) + cost <= threshold + 1e-9) {
                budget.add(start, end, cost)
                result.add(item)
            }
        }
        return result.sortedBy { it.position }
    }

    /** 区间增加、区间最大值；时间坐标仅需包含弹幕出现点。 */
    private class DensityBudget(
        private val size: Int,
    ) {
        private val peak = DoubleArray(size * 4)
        private val lazy = DoubleArray(size * 4)

        fun maximum(
            start: Int,
            end: Int,
        ): Double = query(1, 0, size - 1, start, end)

        fun add(
            start: Int,
            end: Int,
            amount: Double,
        ) = update(1, 0, size - 1, start, end, amount)

        private fun query(
            node: Int,
            left: Int,
            right: Int,
            start: Int,
            end: Int,
        ): Double {
            if (start <= left && right <= end) return peak[node]
            val mid = (left + right) / 2
            var value = 0.0
            if (start <= mid) value = maxOf(value, query(node * 2, left, mid, start, end))
            if (end > mid) value = maxOf(value, query(node * 2 + 1, mid + 1, right, start, end))
            return value + lazy[node]
        }

        private fun update(
            node: Int,
            left: Int,
            right: Int,
            start: Int,
            end: Int,
            amount: Double,
        ) {
            if (start <= left && right <= end) {
                peak[node] += amount
                lazy[node] += amount
                return
            }
            val mid = (left + right) / 2
            if (start <= mid) update(node * 2, left, mid, start, end, amount)
            if (end > mid) update(node * 2 + 1, mid + 1, right, start, end, amount)
            peak[node] = lazy[node] + maxOf(peak[node * 2], peak[node * 2 + 1])
        }
    }

    private fun density(item: DanmakuItemData): Double {
        val length = item.content.codePointCount(0, item.content.length).coerceAtLeast(1)
        return sqrt(length.toDouble()) * (item.textSize / 25.0).coerceIn(0.7, 2.5).pow(1.5)
    }

    private fun stableOrder(item: DanmakuItemData): Int {
        var hash = (item.danmakuId xor item.position).hashCode() xor item.content.hashCode()
        hash = (hash xor (hash ushr 16)) * -2048144789
        hash = (hash xor (hash ushr 13)) * -1028477387
        return hash xor (hash ushr 16)
    }
}
