package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import dev.frost819.newbv.danmaku.entity.DanmakuType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter

/** 权重屏蔽 → 屏蔽原始内容 → 过滤显示类型 → 相似合并。每次处理使用同一份状态快照。 */
object DanmakuPreprocessor {

    /** 已编译屏蔽规则的缓存：规则未变时复用，避免分段重建时反复编译正则。 */
    private var cachedRules: List<DanmakuBlockRule>? = null
    private var cachedFilter: DanmakuBlockFilter? = null

    @Synchronized
    private fun blockFilter(rules: List<DanmakuBlockRule>): DanmakuBlockFilter {
        if (cachedFilter == null || cachedRules != rules) {
            cachedFilter =
                DanmakuBlockFilter().apply {
                    enable = true
                    setRules(rules)
                }
            cachedRules = rules
        }
        return cachedFilter!!
    }

    fun process(
        items: List<DanmakuItemData>,
        state: DanmakuState,
        onBlockHit: ((String) -> Unit)? = null,
        onItemBlockHit: ((Long, String) -> Unit)? = null,
    ): List<DanmakuItemData> {
        val level = state.blockLevel.coerceIn(0, 12)
        val eligible = if (level == 0) items else items.filter { it.score >= level }
        val filtered =
            if (state.blockEnabled) {
                val filter = blockFilter(state.blockRules)
                eligible.filterNot { item ->
                    val key = filter.matchedRuleKey(item.content, item.userId, item.textColor)
                    if (key != null) {
                        onBlockHit?.invoke(key)
                        onItemBlockHit?.invoke(item.danmakuId, key)
                    }
                    key != null
                }
            } else {
                eligible
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
                // 拼音同音匹配与跨类型归簇都是合并的内置行为（默认开启），不作为用户设置暴露
                DanmakuMerger.mergeSimilar(visible)
            }
        return merged
    }
}
