package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import dev.frost819.newbv.danmaku.config.DanmakuCountMark
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig

/**
 * 重复弹幕合并器。
 *
 * 在弹幕数据进入渲染引擎前，把时间窗口内文本完全相同的弹幕合并为一条，
 * 文本追加 ` ×N` 计数后缀（参考 pakku.js / PiliPlus 的合并语义）：
 * - 匹配条件：content 完全相等（不做归一化，保持原文展示）
 * - 时间窗口：默认 30 秒，从簇首弹幕起算——簇首之后超窗的重复弹幕
 *   会关闭旧簇并开启新簇（刷屏持续超过窗口时分成多簇展示）
 * - 合并项属性取簇首（位置/类型/颜色/字号），仅 content 变化
 *
 * 屏蔽顺序由 DanmakuPreprocessor 决定：可先匹配原始数据再合并，
 * 或保留原来的引擎侧屏蔽行为。
 *
 * 输入需按时间排序（B 站分段数据天然有序，内部仍会兜底排序）；
 * 输出为簇定案顺序（非严格时间序），引擎内部会懒排序，无需预先排序。
 */
object DanmakuMerger {
    /** 旧合并 API 的默认时间窗口；播放器使用高级设置中的 20 秒默认值。 */
    const val DEFAULT_WINDOW_MS = 30_000L

    /**
     * 合并文本相同的重复弹幕。
     *
     * @param items 单个分段内的弹幕数据
     * @param windowMs 合并时间窗口（毫秒），<= 0 时直接返回原列表
     * @return 合并后的弹幕数据（条数 <= [items]）
     */
    fun mergeDuplicate(
        items: List<DanmakuItemData>,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): List<DanmakuItemData> {
        if (items.size < 2 || windowMs <= 0) return items

        val openClusters = LinkedHashMap<String, Cluster>()
        val result = ArrayList<DanmakuItemData>(items.size)

        for (item in items.asSequence().sortedBy { it.position }) {
            val cluster = openClusters[item.content]
            if (cluster == null) {
                openClusters[item.content] = Cluster(item, item.content)
            } else if (item.position - cluster.first.position <= windowMs) {
                cluster.add(item)
            } else {
                // 簇首已超出窗口：定案旧簇，当前弹幕成为新簇首
                result.add(cluster.flush())
                openClusters[item.content] = Cluster(item, item.content)
            }
        }
        openClusters.values.forEach { result.add(it.flush()) }
        return result
    }

    /** 进行中的合并簇：簇首数据 + 归一化注册 key + 重复计数。 */
    private class Cluster(
        val first: DanmakuItemData,
        val normKey: String,
        val features: DanmakuSimilarity.Features? = null,
    ) {
        var count = 1
        val members = arrayListOf(first)

        fun add(item: DanmakuItemData) {
            count++
            members.add(item)
        }

        /** 定案：重复数 >1 时生成带 ×N 后缀的合并项，否则原样返回簇首。 */
        fun flush(config: DanmakuMergeConfig = DanmakuMergeConfig()): DanmakuItemData =
            if (count > 1) {
                DanmakuItemData(
                    danmakuId = first.danmakuId,
                    position = members[(members.size - 1) * config.representativePercent / 100].position,
                    content =
                        if (count <= config.markThreshold) {
                            first.content
                        } else {
                            when (config.markPosition) {
                                DanmakuCountMark.Off -> first.content
                                DanmakuCountMark.Prefix -> "×$count ${first.content}"
                                DanmakuCountMark.Suffix -> "${first.content} ×$count"
                            }
                        },
                    mode =
                        if (config.preferFixedMode) {
                            members.firstOrNull { it.mode == 4 || it.mode == 5 }?.mode
                                ?: first.mode
                        } else {
                            first.mode
                        },
                    textSize =
                        if (config.enlarge && count > 5) {
                            (first.textSize * (1f + (count - 5) * 0.05f).coerceAtMost(2f)).toInt()
                        } else {
                            first.textSize
                        },
                    textColor = first.textColor,
                    score = members.maxOf { it.score },
                    danmakuStyle = first.danmakuStyle,
                    rank = first.rank,
                    userId = first.userId,
                    mergedType = DanmakuItemData.MERGED_TYPE_MERGED,
                    pool = first.pool,
                    originalMode = first.originalMode,
                    mergedCount = members.sumOf { it.mergedCount },
                )
            } else {
                first
            }
    }

    // ── 相似合并 ──────────────────────────────────────────────────────

    /** 旧调用参数的默认相似度；播放器使用 DanmakuMergeConfig 的独立算法阈值。 */
    const val DEFAULT_SIMILARITY = 0.8f

    /** 参与相似度比较的进行中簇数上限：超过后退化为归一化精确匹配，防最坏 O(n²·len²)。 */
    private const val MAX_SIMILAR_CLUSTERS = 64

    /** 编辑距离比较的文本长度上限：超长文本只做归一化精确匹配。 */
    private const val MAX_SIMILAR_LENGTH = 80

    /**
     * 相似合并：在 [mergeDuplicate] 的精确语义基础上，归一化后相同
     * （全角转半角/压缩空白/去尾部标点），再比较字符频次差、拼音和循环 2-Gram 向量。
     *
     * 合并项显示簇首原文 ×N（pakku.js 取簇内最常见变体，此处从简取簇首）。
     * 为控制计算量：进行中簇超过 [MAX_SIMILAR_CLUSTERS] 条或文本超长时，
     * 该条弹幕仅做归一化精确匹配。
     *
     * @param items 单个分段内的弹幕数据
     * @param windowMs 合并时间窗口（毫秒，从簇首起算）
     * @param threshold 相似度阈值 [0,1]，越大越严格
     * @param config 高级设置，传入时覆盖 windowMs / threshold，输出按时间排序
     */
    fun mergeSimilar(
        items: List<DanmakuItemData>,
        windowMs: Long = DEFAULT_WINDOW_MS,
        threshold: Float = DEFAULT_SIMILARITY,
        config: DanmakuMergeConfig =
            DanmakuMergeConfig(
                windowSeconds = (windowMs / 1000).toInt(),
                editDistanceThreshold = if (threshold == DEFAULT_SIMILARITY) 5 else ((1f - threshold) * 25).toInt(),
                cosineThreshold = if (threshold == DEFAULT_SIMILARITY) 45 else 101,
                recognizePinyin = threshold == DEFAULT_SIMILARITY,
            ),
    ): List<DanmakuItemData> {
        if (items.size < 2 || windowMs <= 0) return items

        val options = config.sanitized()
        val mergeWindow = options.windowSeconds * 1000L

        val openClusters = ArrayList<Cluster>()
        val clustersByNorm = HashMap<Pair<String, Int>, Cluster>()
        val result = ArrayList<DanmakuItemData>(items.size)

        for (item in items.asSequence().sortedBy { it.position }) {
            // 及时移除过期簇，避免整段历史簇耗尽相似比较预算。
            while (openClusters.isNotEmpty() && item.position - openClusters.first().first.position > mergeWindow) {
                val expired = openClusters.removeAt(0)
                result.add(expired.flush(options))
                clustersByNorm.remove(expired.normKey to if (options.crossMode) 0 else expired.first.originalMode)
            }
            val skipSubtitle = options.skipSubtitle && item.pool == 1
            val skipAdvanced = options.skipAdvanced && item.originalMode !in listOf(1, 4, 5)
            val skipBottom = options.skipBottom && item.mode == 4
            if (skipSubtitle || skipAdvanced || skipBottom) {
                result.add(item)
                continue
            }
            val norm = normalize(item.content, options)
            // 纯标点归一化为空时保留原文，防止无关弹幕被合并。
            val key = norm to if (options.crossMode) 0 else item.originalMode
            val features = if (norm.length <= MAX_SIMILAR_LENGTH) DanmakuSimilarity.prepare(norm, options) else null
            // 先精确（归一化 key），簇数未超限时再做编辑距离相似匹配
            var cluster = clustersByNorm[key]
            if (cluster == null && features != null && openClusters.size <= MAX_SIMILAR_CLUSTERS) {
                cluster =
                    findSimilarCluster(
                        openClusters.filter { options.crossMode || it.first.originalMode == item.originalMode },
                        features,
                        mergeWindow,
                        item.position,
                        options,
                    )
            }

            if (cluster == null) {
                val newCluster = Cluster(item, norm, features)
                openClusters.add(newCluster)
                clustersByNorm[key] = newCluster
            } else if (item.position - cluster.first.position <= mergeWindow) {
                cluster.add(item)
            } else {
                // 簇首已超出窗口：定案旧簇，当前弹幕成为新簇首
                result.add(cluster.flush(options))
                openClusters.remove(cluster)
                clustersByNorm.remove(cluster.normKey to if (options.crossMode) 0 else cluster.first.originalMode)
                val newCluster = Cluster(item, norm, features)
                openClusters.add(newCluster)
                clustersByNorm[key] = newCluster
            }
        }
        openClusters.forEach { result.add(it.flush(options)) }
        return result.sortedBy { it.position }
    }

    /** 在进行中簇里找时间窗口内且至少一种已启用匹配算法达标的簇。 */
    private fun findSimilarCluster(
        clusters: List<Cluster>,
        features: DanmakuSimilarity.Features,
        windowMs: Long,
        position: Long,
        config: DanmakuMergeConfig,
    ): Cluster? =
        clusters.firstOrNull { cluster ->
            position - cluster.first.position <= windowMs &&
                cluster.features?.let { DanmakuSimilarity.matches(features, it, config) } == true
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
    internal fun normalize(
        text: String,
        config: DanmakuMergeConfig = DanmakuMergeConfig(),
    ): String {
        val sb = StringBuilder(text.length)
        text.forEach { c ->
            when {
                config.trimWidth && c == '　' -> sb.append(' ')
                config.trimWidth && c.code in 0xFF01..0xFF5E -> sb.append((c.code - 0xFEE0).toChar())
                else -> sb.append(c)
            }
        }
        var normalized = sb.toString()
        if (config.trimSpace) normalized = normalized.replace(Regex("\\s+"), "")
        if (config.trimEnding) {
            normalized =
                normalized.trimEnd('.', '。', ',', '，', '?', '？', '!', '！', '~', '～', ' ', '、', ';', '；')
        }
        return normalized.ifEmpty { text }
    }
}
