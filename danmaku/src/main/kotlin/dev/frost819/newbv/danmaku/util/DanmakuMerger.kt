package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData

/**
 * 重复弹幕合并器。
 *
 * 在弹幕数据进入渲染引擎前，把时间窗口内文本相同或相似的弹幕合并为一条，
 * 文本追加 ` ×N` 计数后缀（参考 pakku.js / PiliPlus 的合并语义）：
 * - 匹配条件：全角、空白和尾标点归一化后相同，或编辑距离相似度达到 80%
 * - 时间窗口：默认 30 秒，从簇首弹幕起算——簇首之后超窗的重复弹幕
 *   会关闭旧簇并开启新簇（刷屏持续超过窗口时分成多簇展示）
 * - 显示簇首原文、位置、类型、颜色和字号，保留合并数量与组内最高权重
 *
 * DanmakuPreprocessor 先屏蔽原始内容、过滤显示类型，再合并，避免簇首吞掉未屏蔽的弹幕。
 *
 * 输入需按时间排序（B 站分段数据天然有序，内部仍会兜底排序）；
 * 输出按播放时间排序。
 */
object DanmakuMerger {
    /** 默认合并时间窗口（毫秒），与 pakku.js 默认阈值一致。 */
    const val DEFAULT_WINDOW_MS = 30_000L

    /** 进行中的合并簇：簇首数据 + 归一化注册 key + 重复计数。 */
    private class Cluster(
        val first: DanmakuItemData,
        val normKey: String,
    ) {
        var count = 1
        private var mergedCount = first.mergedCount
        private var score = first.score
        val aliases = hashSetOf(normKey)

        fun add(item: DanmakuItemData) {
            count++
            mergedCount += item.mergedCount
            score = maxOf(score, item.score)
        }

        /** 定案：重复数 >1 时生成带 ×N 后缀的合并项，否则原样返回簇首。 */
        fun flush(): DanmakuItemData =
            if (count > 1) {
                DanmakuItemData(
                    danmakuId = first.danmakuId,
                    position = first.position,
                    content = "${first.content} ×$mergedCount",
                    mode = first.mode,
                    textSize = first.textSize,
                    textColor = first.textColor,
                    score = score,
                    danmakuStyle = first.danmakuStyle,
                    rank = first.rank,
                    userId = first.userId,
                    mergedType = DanmakuItemData.MERGED_TYPE_MERGED,
                    mergedCount = mergedCount,
                )
            } else {
                first
            }
    }

    // ── 相似合并 ──────────────────────────────────────────────────────

    /** 相似度比较的默认阈值（编辑距离比例 >= 0.8 视为相似，参考 pakku.js）。 */
    const val DEFAULT_SIMILARITY = 0.8f

    /** 参与相似度比较的进行中簇数上限：超过后退化为归一化精确匹配，防最坏 O(n²·len²)。 */
    private const val MAX_SIMILAR_CLUSTERS = 64

    /** 编辑距离比较的文本长度上限：超长文本只做归一化精确匹配。 */
    private const val MAX_SIMILAR_LENGTH = 80

    /**
     * 相似合并：归一化后相同
     * （全角转半角/压缩空白/去尾部标点）或编辑距离足够近的弹幕也并入同簇。
     *
     * 合并项显示簇首原文 ×N（pakku.js 取簇内最常见变体，此处从简取簇首）。
     * 为控制计算量：进行中簇超过 [MAX_SIMILAR_CLUSTERS] 条或文本超长时，
     * 该条弹幕仅做归一化精确匹配（pakku.js 用 WASM 编辑距离的工程折衷）。
     *
     * @param items 单个分段内的弹幕数据
     * @param windowMs 合并时间窗口（毫秒，从簇首起算）
     * @param threshold 相似度阈值 [0,1]，越大越严格
     */
    fun mergeSimilar(
        items: List<DanmakuItemData>,
        windowMs: Long = DEFAULT_WINDOW_MS,
        threshold: Float = DEFAULT_SIMILARITY,
    ): List<DanmakuItemData> {
        if (items.size < 2 || windowMs <= 0) return items

        val openClusters = ArrayList<Cluster>()
        val clustersByNorm = HashMap<String, Cluster>()
        val result = ArrayList<DanmakuItemData>(items.size)

        for (item in items.asSequence().sortedBy { it.position }) {
            // 过期簇及时定案，避免历史簇耗尽相似比较预算。
            while (openClusters.isNotEmpty() && item.position - openClusters.first().first.position > windowMs) {
                val expired = openClusters.removeAt(0)
                result.add(expired.flush())
                expired.aliases.forEach { clustersByNorm.remove(it) }
            }
            val norm = normalize(item.content)
            // 先精确（归一化 key），簇数未超限时再做编辑距离相似匹配
            var cluster = clustersByNorm[norm]
            if (cluster == null && openClusters.size <= MAX_SIMILAR_CLUSTERS) {
                cluster = findSimilarCluster(openClusters, norm, windowMs, item.position, threshold)
            }

            if (cluster == null) {
                val newCluster = Cluster(item, norm)
                openClusters.add(newCluster)
                clustersByNorm[norm] = newCluster
            } else if (item.position - cluster.first.position <= windowMs) {
                cluster.add(item)
                cluster.aliases.add(norm)
                clustersByNorm[norm] = cluster
            } else {
                // 簇首已超出窗口：定案旧簇，当前弹幕成为新簇首
                result.add(cluster.flush())
                openClusters.remove(cluster)
                cluster.aliases.forEach { clustersByNorm.remove(it) }
                val newCluster = Cluster(item, norm)
                openClusters.add(newCluster)
                clustersByNorm[norm] = newCluster
            }
        }
        openClusters.forEach { result.add(it.flush()) }
        return result.sortedBy { it.position }
    }

    /** 在进行中簇里找时间窗口内且与 [norm] 相似度达标的簇。 */
    private fun findSimilarCluster(
        clusters: List<Cluster>,
        norm: String,
        windowMs: Long,
        position: Long,
        threshold: Float,
    ): Cluster? =
        clusters.firstOrNull { cluster ->
            position - cluster.first.position <= windowMs &&
                norm.length <= MAX_SIMILAR_LENGTH &&
                cluster.normKey.length <= MAX_SIMILAR_LENGTH &&
                isSimilar(norm, cluster.normKey, threshold)
        }

    /**
     * 判断两个归一化文本是否相似：编辑距离 / 较长者长度 <= 1 - threshold。
     * 长度差预筛（O(1)）先行淘汰大部分比较。
     */
    internal fun isSimilar(
        a: String,
        b: String,
        threshold: Float = DEFAULT_SIMILARITY,
    ): Boolean {
        if (a == b) return true
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return true
        // 长度差已超过允许编辑距离，直接判不相似
        if (1f - (maxLen - minOf(a.length, b.length)).toFloat() / maxLen < threshold) return false
        return 1f - levenshtein(a, b).toFloat() / maxLen >= threshold
    }

    /** 经典 Levenshtein 编辑距离（两行滚动数组）。 */
    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /**
     * 相似比较用的文本归一化：全角转半角、全角空格转空格、
     * 压缩连续空白、去尾部标点（参考 pakku.js 预处理）。
     */
    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        text.forEach { c ->
            when {
                c == '　' -> sb.append(' ')
                c.code in 0xFF01..0xFF5E -> sb.append((c.code - 0xFEE0).toChar())
                else -> sb.append(c)
            }
        }
        return sb
            .toString()
            .replace(Regex("\\s+"), "")
            .trimEnd('.', '。', ',', '，', '?', '？', '!', '！', '~', '～', ' ', '、', ';', '；')
            .ifEmpty { text }
    }
}
