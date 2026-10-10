package dev.frost819.newbv.danmaku.util

import com.kuaishou.akdanmaku.data.DanmakuItemData
import kotlin.math.abs

/**
 * 重复弹幕合并器。
 *
 * 在弹幕数据进入渲染引擎前，把时间窗口内文本相同或相似的弹幕合并为一条，
 * 文本追加 ` ×N` 计数后缀（参考 pakku.js / PiliPlus 的合并语义）：
 * - 匹配条件（级联，任一命中即归簇）：全角、空白和尾标点归一化后相同；
 *   字符多重集距离足够近（pakku.js 的 O(n) 编辑距离近似）；
 *   拼音多重集距离足够近（同音字）；
 *   归一化文本的循环 2-gram 余弦相似度达标（语序打乱的重叠文本）
 * - 判定参数与 PiliNara 的移植版一致：距离阈值 5、余弦阈值 45、
 *   短文本距离预算按长度缩放；拼音匹配与跨类型合并默认开启（算法内置行为）
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

    /** 循环 2-gram 的哈希模数，与 pakku.js 一致。 */
    private const val HASH_MOD = 1007

    /**
     * 相似合并的默认距离阈值（字符/拼音多重集距离），pakku.js 与 PiliNara 的默认值。
     * 短文本的预算按长度缩放，见 [withinDistance]。
     */
    const val DEFAULT_MAX_DISTANCE = 5

    /** 拼音距离匹配（同音字合并）默认开启；算法内置行为，非用户设置项。 */
    const val DEFAULT_USE_PINYIN = true

    /** 循环 2-gram 余弦相似度阈值（0–100），同 PiliNara 默认值；>100 视为关闭。 */
    const val DEFAULT_MAX_COSINE = 45

    /** 跨弹幕类型（滚动/顶部/底部）合并默认开启；同文归簇是真实需求，算法内置行为非用户设置项。 */
    const val DEFAULT_CROSS_MODE = true

    /**
     * 参与相似比较的文本预处理结果，每条弹幕只构建一次（pakku 的 cacheline 思路）：
     * 排序后的字符与拼音 token（供 O(n) 多重集距离）、字符布隆掩码（供余弦的无公共
     * 字符守卫）、排序后的循环 2-gram 平行数组（供无装箱余弦）。
     */
    internal class PreparedText private constructor(
        val sortedChars: CharArray,
        val sortedPinyin: CharArray,
        val charMask: Long,
        val grams: Grams?,
    ) {
        companion object {
            /**
             * [usePinyin] 关闭时跳过拼音编码；[maxCosine] >100 时跳过 2-gram 构建。
             * [norm] 须为 [DanmakuMerger.normalize] 的输出。
             */
            fun prepare(
                norm: String,
                usePinyin: Boolean,
                maxCosine: Int,
            ): PreparedText {
                var mask = 0L
                for (c in norm) {
                    mask = mask or charBit(c.code)
                }
                val grams =
                    if (maxCosine in 0..100 && norm.isNotEmpty()) {
                        gramTokens(norm)
                    } else {
                        null
                    }
                return PreparedText(
                    sortedChars = norm.toCharArray().also { it.sort() },
                    sortedPinyin =
                        if (usePinyin) {
                            // copyOf：编码器返回的是共享缓存数组，避免就地排序污染缓存
                            DanmakuPinyinEncoder.encode(norm).copyOf().also { it.sort() }
                        } else {
                            CharArray(0)
                        },
                    charMask = mask,
                    grams = grams,
                )
            }

            /** 字符码点 → 64 位布隆桶；碰撞只会多报公共字符，保证"掩码无交集 ⇒ 无公共字符"。 */
            private fun charBit(code: Int): Long = 1L shl (((code * -0x61c88647).ushr(26)) and 63)

            /** 循环 2-gram：首尾相接的相邻字符对哈希，与 pakku.js/PiliNara 一致。 */
            private fun gramTokens(norm: String): Grams {
                val raw = IntArray(norm.length)
                var prev = norm.last().code % HASH_MOD
                for (i in norm.indices) {
                    val cur = norm[i].code % HASH_MOD
                    raw[i] = prev * HASH_MOD + cur
                    prev = cur
                }
                raw.sort()
                // 去重压缩为 gram/count 平行数组，点积用归并而非 HashMap，避免装箱
                val counts = IntArray(raw.size)
                var distinct = 0
                var selfDot = 0
                var i = 0
                while (i < raw.size) {
                    var j = i + 1
                    while (j < raw.size && raw[j] == raw[i]) j++
                    val count = j - i
                    raw[distinct] = raw[i]
                    counts[distinct] = count
                    selfDot += count * count
                    distinct++
                    i = j
                }
                return Grams(raw.copyOf(distinct), counts.copyOf(distinct), selfDot)
            }
        }
    }

    /** 循环 2-gram 的排序去重 gram 与计数的平行数组及自点积。 */
    internal class Grams(
        val grams: IntArray,
        val counts: IntArray,
        val selfDot: Int,
    )

    /** 进行中的合并簇：簇首数据 + 预处理结果 + 注册 key 集合 + 重复计数。 */
    private class Cluster(
        val first: DanmakuItemData,
        val prepared: PreparedText,
        initialKey: String,
    ) {
        /** 簇首弹幕类型：跨类型合并关闭时不与不同类型的弹幕归簇。 */
        val mode: Int = first.mode

        var count = 1
        private var mergedCount = first.mergedCount
        private var score = first.score

        /** 本簇注册过的精确 key，定案时依此从索引表移除。 */
        val keys = hashSetOf(initialKey)

        fun add(
            item: DanmakuItemData,
            key: String,
        ) {
            count++
            mergedCount += item.mergedCount
            score = maxOf(score, item.score)
            keys.add(key)
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

    /**
     * 相似合并：归一化后相同，或字符距离/拼音距离/循环 2-gram 余弦任一达标的弹幕
     * 也并入同簇。相似比较均为 O(n)（pakku.js 认为教科书编辑距离太慢，
     * PiliNara 移植了同一套近似算法），无簇数与文本长度限制。
     *
     * 合并项显示簇首原文 ×N（pakku.js 取簇内最常见变体，此处从简取簇首）。
     *
     * @param items 单个分段内的弹幕数据
     * @param windowMs 合并时间窗口（毫秒，从簇首起算）
     * @param maxDistance 字符/拼音多重集距离阈值，短文本按长度缩放，见 [withinDistance]
     * @param usePinyin 启用拼音距离匹配同音字
     * @param maxCosine 循环 2-gram 余弦相似度阈值（0–100），>100 关闭
     * @param crossMode 允许跨弹幕类型（滚动/顶部/底部）归簇
     */
    fun mergeSimilar(
        items: List<DanmakuItemData>,
        windowMs: Long = DEFAULT_WINDOW_MS,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
        usePinyin: Boolean = DEFAULT_USE_PINYIN,
        maxCosine: Int = DEFAULT_MAX_COSINE,
        crossMode: Boolean = DEFAULT_CROSS_MODE,
    ): List<DanmakuItemData> {
        if (items.size < 2 || windowMs <= 0) return items

        val openClusters = ArrayList<Cluster>()
        val clustersByNorm = HashMap<String, Cluster>()
        val result = ArrayList<DanmakuItemData>(items.size)

        for (item in items.asSequence().sortedBy { it.position }) {
            // 过期簇及时定案。输入按时间排序、簇按簇首时间有序，出窗的簇全部位于队首；
            // 清理后剩余簇必在窗口内，后续归簇无需再做窗口判断。
            while (openClusters.isNotEmpty() && item.position - openClusters.first().first.position > windowMs) {
                val expired = openClusters.removeAt(0)
                result.add(expired.flush())
                expired.keys.forEach(clustersByNorm::remove)
            }
            val norm = normalize(item.content)
            val key = exactKey(item.mode, norm, crossMode)
            // 先精确（归一化 key）匹配，未命中再做距离/拼音/余弦相似匹配
            var cluster = clustersByNorm[key]
            if (cluster == null) {
                val prepared = PreparedText.prepare(norm, usePinyin, maxCosine)
                cluster = findSimilarCluster(openClusters, prepared, item.mode, maxDistance, usePinyin, maxCosine, crossMode)
                if (cluster == null) {
                    // 未归入任何簇：成为新簇首
                    val newCluster = Cluster(item, prepared, key)
                    openClusters.add(newCluster)
                    clustersByNorm[key] = newCluster
                }
            }
            if (cluster != null) {
                cluster.add(item, key)
                clustersByNorm[key] = cluster
            }
        }
        openClusters.forEach { result.add(it.flush()) }
        return result.sortedBy { it.position }
    }

    /** 在进行中簇里找类型匹配且与 [prepared] 相似的簇；窗口合法性由过期清理保证。 */
    private fun findSimilarCluster(
        clusters: List<Cluster>,
        prepared: PreparedText,
        mode: Int,
        maxDistance: Int,
        usePinyin: Boolean,
        maxCosine: Int,
        crossMode: Boolean,
    ): Cluster? =
        clusters.firstOrNull { cluster ->
            (crossMode || cluster.mode == mode) &&
                isSimilar(prepared, cluster.prepared, maxDistance, usePinyin, maxCosine)
        }

    /**
     * 级联相似判定，顺序与 pakku.js/PiliNara 一致：
     * 字符多重集距离 → 拼音多重集距离 → 循环 2-gram 余弦，任一命中即相似。
     * 各距离阶段用预算早退，失败路径（大量互不相似的簇间比较）尽早终止。
     */
    private fun isSimilar(
        a: PreparedText,
        b: PreparedText,
        maxDistance: Int,
        usePinyin: Boolean,
        maxCosine: Int,
    ): Boolean {
        if (a.sortedChars.contentEquals(b.sortedChars)) return true
        val lenSum = a.sortedChars.size + b.sortedChars.size

        // 1. 字符多重集距离（长度差是距离下界，超预算直接淘汰）
        val charBudget = maxAllowedDistance(lenSum, maxDistance)
        if (abs(a.sortedChars.size - b.sortedChars.size) <= charBudget) {
            val charDist = bagDistanceWithin(a.sortedChars, b.sortedChars, charBudget)
            if (charDist <= charBudget) return true
        }

        // 2. 拼音多重集距离：字符不同但读音相同的同音字
        if (usePinyin) {
            val pyLenSum = a.sortedPinyin.size + b.sortedPinyin.size
            val pyBudget = maxAllowedDistance(pyLenSum, maxDistance)
            if (abs(a.sortedPinyin.size - b.sortedPinyin.size) <= pyBudget &&
                bagDistanceWithin(a.sortedPinyin, b.sortedPinyin, pyBudget) <= pyBudget
            ) {
                return true
            }
        }

        // 3. 循环 2-gram 余弦：布隆掩码无交集 ⇒ 必无公共字符 ⇒ 点积必为 0，
        //    仅在阈值 >= 1 时可据此跳过（阈值 0 时 0 分也算命中，须照常计算）
        if (maxCosine in 0..100 && (maxCosine == 0 || a.charMask and b.charMask != 0L)) {
            val gramsA = a.grams
            val gramsB = b.grams
            if (gramsA != null && gramsB != null) {
                val similarity = cosineSimilarity(gramsA, gramsB)
                if (similarity >= maxCosine) return true
            }
        }
        return false
    }

    /**
     * 判断两个归一化文本是否相似（字符串便捷入口，每次现做预处理；
     * 合并主流程走 [PreparedText] 免重复预处理路径）。
     */
    internal fun isSimilar(
        a: String,
        b: String,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
    ): Boolean =
        isSimilar(
            PreparedText.prepare(a, usePinyin = true, maxCosine = DEFAULT_MAX_COSINE),
            PreparedText.prepare(b, usePinyin = true, maxCosine = DEFAULT_MAX_COSINE),
            maxDistance,
            usePinyin = true,
            maxCosine = DEFAULT_MAX_COSINE,
        )

    /**
     * pakku 的距离判定：长文本预算封顶 [maxDistance]；短文本（长度和 < maxDistance×2）
     * 按长度比例缩放，避免两个两字弹幕因一字符之差被合并。
     */
    internal fun withinDistance(
        distance: Int,
        lenSum: Int,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
    ): Boolean = distance <= maxAllowedDistance(lenSum, maxDistance)

    /**
     * [withinDistance] 允许的最大距离（整数恒等变换，无除法）：
     * 短文本为 `(maxDistance × lenSum − 1) ÷ minDanmakuSize`，长文本为 [maxDistance]。
     */
    internal fun maxAllowedDistance(
        lenSum: Int,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
    ): Int {
        val minDanmakuSize = maxOf(1, maxDistance * 2)
        return if (lenSum < minDanmakuSize) {
            (maxDistance * lenSum - 1) / minDanmakuSize
        } else {
            maxDistance
        }
    }

    /** 精确匹配的注册 key：跨类型合并关闭时把类型编进 key，同文不同类型不会互并。 */
    private fun exactKey(
        mode: Int,
        norm: String,
        crossMode: Boolean,
    ): String = if (crossMode) norm else "$mode\n$norm"

    /**
     * 字符多重集 L1 距离：插入/删除各计 1、替换计 2，是教科书编辑距离的 O(n) 近似
     * （Levenshtein <= 多重集距离 <= 2×Levenshtein），
     * 归并两个排序字符数组计数，参考 pakku.js repo-cpp 的 edit_distance。
     *
     * [budget] 为通过判定的最大距离：累加值一旦超过立即返回（返回值只会更大），
     * 供调用方做"是否达标"判断；需要精确距离时勿用本函数。
     */
    private fun bagDistanceWithin(
        a: CharArray,
        b: CharArray,
        budget: Int,
    ): Int {
        var i = 0
        var j = 0
        var dist = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] < b[j] -> {
                    dist++
                    i++
                }
                a[i] > b[j] -> {
                    dist++
                    j++
                }
                else -> {
                    i++
                    j++
                }
            }
            if (dist > budget) return dist
        }
        return dist + (a.size - i) + (b.size - j)
    }

    /** 循环 2-gram 计数向量的余弦相似度，返回 0–100 整数（pakku 的缩放语义）。 */
    private fun cosineSimilarity(
        a: Grams,
        b: Grams,
    ): Int {
        if (a.selfDot <= 0 || b.selfDot <= 0) return 0
        var i = 0
        var j = 0
        var dot = 0L
        while (i < a.grams.size && j < b.grams.size) {
            when {
                a.grams[i] < b.grams[j] -> i++
                a.grams[i] > b.grams[j] -> j++
                else -> {
                    dot += a.counts[i].toLong() * b.counts[j]
                    i++
                    j++
                }
            }
        }
        if (dot == 0L) return 0
        return (100L * dot * dot / (a.selfDot.toLong() * b.selfDot)).toInt()
    }

    /** 归一化用的空白匹配，预编译避免每条弹幕重复构造。 */
    private val WHITESPACE = Regex("\\s+")

    /**
     * 相似比较用的文本归一化：全角转半角、全角空格转空格、
     * 去全部空白、去尾部标点（参考 pakku.js 预处理）。
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
            .replace(WHITESPACE, "")
            .trimEnd('.', '。', ',', '，', '?', '？', '!', '！', '~', '～', ' ', '、', ';', '；')
            .ifEmpty { text }
    }
}
