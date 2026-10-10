package dev.frost819.newbv.danmaku.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 汉字 → 拼音 token 编码器，用于弹幕合并的同音字匹配
 * （算法与字典格式参考 pakku.js / PiliNara 的 pinyin encoder）。
 *
 * 字典数据由 MIT 协议的 [mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data)
 * 重新生成（pakku.js 原字典为 GPL-3.0，避免许可混入），格式与 pakku.js 一致：
 * 每行 `{0x码点, {主读音序号, 次读音序号}}`，序号 0 表示无次读音；
 * 读音仅内嵌为序号 token，不还原拼音字符串。
 *
 * 编码规则：字典命中的汉字映射为 `0xE000` 起的私用区 token（主读音 + 至多 1 个次读音），
 * ASCII 大写转小写，其余码点原样保留，因此中英混合文本也能稳定比较。
 */
internal object DanmakuPinyinEncoder {
    private const val TOKEN_BASE = 0xE000
    private const val DICT_RESOURCE = "/danmaku/pinyin_dict.txt"

    /** 生成脚本只输出 CJK 统一表意文字（U+3400–U+9FFF）与 〇，按此上限分配查找表。 */
    private const val MAX_CODE = 0x9FFF

    private class Tables(
        val primary: CharArray,
        val secondary: CharArray,
    )

    /** 按码点索引的扁平查找表，0 表示非表内汉字；字典缺失时为 null，编码退化为透传。 */
    private val tables: Tables? by lazy(::loadDict)

    private val cache = ConcurrentHashMap<String, CharArray>()

    /** 把文本编码为拼音 token 序列；同文本命中缓存。 */
    fun encode(text: String): CharArray = cache.computeIfAbsent(text, ::encodeUncached)

    private fun encodeUncached(text: String): CharArray {
        val dict = tables
        val tokens = CharArray(text.length * 2)
        var len = 0
        for (c in text) {
            val code = c.code
            if (dict != null && code < dict.primary.size) {
                val p = dict.primary[code]
                if (p.code != 0) {
                    tokens[len++] = p
                    val s = dict.secondary[code]
                    if (s.code != 0) tokens[len++] = s
                    continue
                }
            }
            tokens[len++] = if (c in 'A'..'Z') c.lowercaseChar() else c
        }
        return tokens.copyOf(len)
    }

    private fun loadDict(): Tables? {
        val lines =
            DanmakuPinyinEncoder::class.java
                .getResourceAsStream(DICT_RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()
                ?: return null
        val primary = CharArray(MAX_CODE + 1)
        val secondary = CharArray(MAX_CODE + 1)
        for (raw in lines) {
            parseDictLine(raw.trim(), primary, secondary)
        }
        return Tables(primary, secondary)
    }

    /** 解析一行 `{0x4f60, {123, 0}},`；注释行与非法行静默跳过。 */
    private fun parseDictLine(
        line: String,
        primary: CharArray,
        secondary: CharArray,
    ) {
        if (!line.startsWith("{0x")) return
        val codeEnd = line.indexOf(',', 3)
        val pStart = line.indexOf('{', codeEnd) + 1
        val pEnd = line.indexOf(',', pStart)
        val sEnd = line.indexOf('}', pEnd)
        if (codeEnd < 0 || pStart == 0 || pEnd < 0 || sEnd < 0) return
        val code = line.substring(3, codeEnd).toIntOrNull(16) ?: return
        if (code !in primary.indices) return
        val p = line.substring(pStart, pEnd).trim().toIntOrNull() ?: return
        val s = line.substring(pEnd + 1, sEnd).trim().toIntOrNull() ?: return
        primary[code] = (TOKEN_BASE + p).toChar()
        if (s != 0) secondary[code] = (TOKEN_BASE + s).toChar()
    }
}
