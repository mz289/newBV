package dev.frost819.newbv.danmaku.util

import com.github.promeg.pinyinhelper.Pinyin
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig

/** 有序编辑距离、循环 2-Gram 向量和有序无声调拼音比较。 */
internal object DanmakuSimilarity {
    data class Features(
        val text: String,
        val characters: List<Int>,
        val grams: Map<Long, Int>,
        val pinyin: List<String>,
        val length: Int,
    )

    fun prepare(
        text: String,
        config: DanmakuMergeConfig,
    ): Features {
        // 不依赖 API 24 的 String.codePoints，兼容应用的 Android 5.0 最低版本。
        val points = ArrayList<Int>(text.length)
        var offset = 0
        while (offset < text.length) {
            val point = Character.codePointAt(text, offset)
            points.add(point)
            offset += Character.charCount(point)
        }
        val grams = HashMap<Long, Int>()
        if (config.cosineThreshold <= 100 && points.isNotEmpty()) {
            points.forEachIndexed { index, point ->
                val previous = points[(index + points.size - 1) % points.size]
                val key = (previous.toLong() shl 32) or point.toLong()
                grams[key] = (grams[key] ?: 0) + 1
            }
        }
        val pinyin =
            if (config.recognizePinyin) {
                points
                    .map { point ->
                        if (point <= Char.MAX_VALUE.code && Pinyin.isChinese(point.toChar())) {
                            "p:${Pinyin.toPinyin(point.toChar())}"
                        } else {
                            "c:${String(Character.toChars(point)).lowercase()}"
                        }
                    }
            } else {
                emptyList()
            }
        return Features(text, points, grams, pinyin, points.size)
    }

    fun matches(
        a: Features,
        b: Features,
        config: DanmakuMergeConfig,
    ): Boolean {
        if (a.text == b.text) return true
        if (a.length == 0 || b.length == 0) return false
        // 轻微字面变化也可能反转含义；所有模糊算法都必须遵守这些保护。
        val repeated = repeatedText(a.text, b.text)
        if (!repeated && a.text.filter { it in NEGATIONS } != b.text.filter { it in NEGATIONS }) return false
        if (!repeated &&
            NUMBERS.findAll(a.text).map { it.value }.toList() != NUMBERS.findAll(b.text).map { it.value }.toList()
        ) {
            return false
        }
        val threshold = config.editDistanceThreshold
        val length = maxOf(a.length, b.length)
        val maxEdits = if (length < 4) 0 else (length + 4) / 5
        val ordered = editDistance(a.characters, b.characters, replacementCost = 1) <= maxEdits
        if (threshold > 0 && ordered && editDistance(a.characters, b.characters) <= threshold) {
            return true
        }
        if (config.recognizePinyin) {
            val distance = editDistance(a.pinyin, b.pinyin)
            if (distance == 0 ||
                (
                    threshold > 0 &&
                        distance <= threshold &&
                        editDistance(a.pinyin, b.pinyin, replacementCost = 1) <= maxEdits
                )
            ) {
                return true
            }
        }
        return config.cosineThreshold <= 100 &&
            (ordered || repeated) &&
            squaredCosinePercent(a.grams, b.grams) >= config.cosineThreshold
    }

    private const val NEGATIONS = "不没无非莫勿未别甭"
    private val NUMBERS = Regex("\\p{N}+")
    private val ENDING_MARKS = Regex("[.。,，?？!！~～、;；\\s]+")

    /** 重复片段允许长度差很大，但片段内部字序必须一致。 */
    private fun repeatedText(
        a: String,
        b: String,
    ): Boolean {
        val cleanA = a.replace(ENDING_MARKS, "")
        val cleanB = b.replace(ENDING_MARKS, "")
        val shorter = if (cleanA.length <= cleanB.length) cleanA else cleanB
        val longer = if (cleanA.length <= cleanB.length) cleanB else cleanA
        return shorter.isNotEmpty() &&
            longer.length > shorter.length &&
            longer.length % shorter.length == 0 &&
            longer == shorter.repeat(longer.length / shorter.length)
    }

    private fun <T> editDistance(
        a: List<T>,
        b: List<T>,
        replacementCost: Int = 2,
    ): Int {
        var previous = IntArray(b.size + 1) { it }
        var current = IntArray(b.size + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] =
                    minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (a[i] == b[j]) 0 else replacementCost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.size]
    }

    private fun squaredCosinePercent(
        a: Map<Long, Int>,
        b: Map<Long, Int>,
    ): Double {
        val normA = a.values.sumOf { it.toDouble() * it }
        val normB = b.values.sumOf { it.toDouble() * it }
        if (normA == 0.0 || normB == 0.0) return 0.0
        val dot = a.entries.sumOf { (key, count) -> count.toDouble() * (b[key] ?: 0) }
        return 100 * dot * dot / (normA * normB)
    }
}
