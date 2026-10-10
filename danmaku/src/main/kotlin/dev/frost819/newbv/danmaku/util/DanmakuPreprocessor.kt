package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.config.DanmakuState
import dev.frost819.newbv.danmaku.entity.DanmakuType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter

/** 屏蔽原始内容 → 过滤显示类型 → 初版相似合并。每次处理使用同一份状态快照。 */
object DanmakuPreprocessor {
    fun process(
        items: List<DanmakuItemData>,
        state: DanmakuState,
        onBlockHit: ((String) -> Unit)? = null,
        onItemBlockHit: ((Long, String) -> Unit)? = null,
    ): List<DanmakuItemData> {
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
                DanmakuMerger.mergeSimilar(visible)
            }
        return merged
    }
}
