package dev.frost819.newbv.danmaku.util

import com.github.promeg.pinyinhelper.Pinyin
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import kotlin.math.abs

/** 独立实现 pakku 的字符频次差、循环 2-Gram 向量和无声调拼音比较。 */
internal object DanmakuSimilarity {
    data class Features(
        val text: String,
        val characters: Map<Int, Int>,
        val grams: Map<Long, Int>,
        val pinyin: Map<String, Int>,
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
                    }.groupingBy { it }
                    .eachCount()
            } else {
                emptyMap()
            }
        return Features(text, points.groupingBy { it }.eachCount(), grams, pinyin, points.size)
    }

    fun matches(
        a: Features,
        b: Features,
        config: DanmakuMergeConfig,
    ): Boolean {
        if (a.text == b.text) return true
        if (a.length == 0 || b.length == 0) return false
        val threshold = config.editDistanceThreshold
        val totalLength = a.length + b.length
        if (threshold > 0 &&
            abs(a.length - b.length) <= threshold &&
            withinDistance(frequencyDistance(a.characters, b.characters), totalLength, threshold)
        ) {
            return true
        }
        if (config.recognizePinyin && abs(a.length - b.length) <= threshold) {
            val distance = frequencyDistance(a.pinyin, b.pinyin)
            if (distance == 0 || (threshold > 0 && withinDistance(distance, totalLength, threshold))) return true
        }
        return config.cosineThreshold <= 100 && squaredCosinePercent(a.grams, b.grams) >= config.cosineThreshold
    }

    private fun withinDistance(
        distance: Int,
        length: Int,
        threshold: Int,
    ): Boolean = if (length < threshold * 2) distance < length / 2 else distance <= threshold

    private fun <T> frequencyDistance(
        a: Map<T, Int>,
        b: Map<T, Int>,
    ): Int = (a.keys + b.keys).sumOf { abs((a[it] ?: 0) - (b[it] ?: 0)) }

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
