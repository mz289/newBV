/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Kwai, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.kuaishou.akdanmaku.data

import kotlin.math.max

/**
 * B 站 mode 7 高级弹幕 JSON 数组载荷解析器。
 *
 * 载荷形如 `[起始X, 起始Y, "1-0", 时长秒, "文本", rotateZ, rotateY, 终点X, 终点Y,
 * 运动毫秒, 延迟毫秒, 描边, "字体", 缓动, "M...路径"]`，
 * **至少 14 项**，第 15 项（路径）可选；任一类型校验不通过整条丢弃。
 *
 * 语义基准：官方客户端弹幕引擎的载荷解析行为：
 * - `JSON.parse` 语义（空槽位/尾逗号非法）；
 * - `u.length < 14 → null`；`u[4]`、`u[12]` 必须为字符串；
 *   `u[11]`、`u[13]` 必须为字符串/数字/布尔；其余数值位必须为字符串/数字（布尔非法）；
 * - 坐标归一化：先 `Math.max(0, ·)` 截负，再「≤ 1 视为比例，> 1 除以 861 / 572」；
 *   y 为**文本顶边**距画面顶部的比例（官方舞台原点在左下、锚点为精灵左上角）；
 * - 透明度：字符串按 `split("-")` 分 1 / 2 / ≥3 段，单值恒定、两值渐变、≥3 段为 0；
 *   数字为恒定透明度；
 * - `stroked`、`easeInOut` 的布尔判定：布尔原样、数字 `> 0`、字符串仅 `"true"`；
 * - 路径 `substr(1).split(/M|L/)`，每段 `split(",")` 取两点且同样归一化；
 *   有路径时路径点覆盖起终点，路径解析为空则整条丢弃（官方视为永不显示）。
 *
 * 纯 Kotlin 实现（不依赖 org.json），弹幕内容可包含逗号、引号与转义，
 * 因此使用字符串感知的轻量切分而非逗号 split。
 */
object SpecialDanmakuParser {
    /**
     * 解析高级弹幕载荷。
     *
     * @return 解析成功返回 [SpecialDanmakuSpec]；载荷非数组、字段不足 14 项、
     * 类型校验不通过或必需数值无法解析时返回 null，由调用方丢弃该条
     */
    fun parse(payload: String): SpecialDanmakuSpec? {
        val elements = parseArray(payload) ?: return null
        // 官方：u.length < 14 直接丢弃（旧 5/13 字段载荷在现代 App 上不显示）
        if (elements.size < 14) return null

        // u[4] 文本、u[12] 字体名必须是字符串（空串合法）；字体名官方不使用，仅做类型校验
        val text = elements[4] as? String ?: return null
        if (elements[12] !is String) return null

        // u[11]、u[13]：字符串 / 数字 / 布尔，按官方宽松布尔判定
        val strokedRaw = elements[11] ?: return null
        if (!strokedRaw.isStringOrNumberOrBoolean()) return null
        val stroked = strokedRaw.parseBooleanFlag()
        val easeRaw = elements[13] ?: return null
        if (!easeRaw.isStringOrNumberOrBoolean()) return null
        val easeInOut = easeRaw.parseBooleanFlag()

        // u[0] u[1] u[7] u[8]：必须为字符串 / 数字（布尔非法）
        for (index in intArrayOf(0, 1, 7, 8)) {
            val value = elements[index] ?: return null
            if (!value.isStringOrNumber()) return null
        }
        // u[5] 旋转角同样受类型校验；u[6] 官方未校验且渲染器不使用
        val rotateRaw = elements[5] ?: return null
        if (!rotateRaw.isStringOrNumber()) return null

        // 时长三兄弟：第 4 项单位为秒（×1000），第 10 / 11 项官方即为毫秒；
        // 三项都按 Math.max(Number(v), 0) 截负
        val displayDurationMs = ((elements[3] ?: return null).numberOrNull() ?: return null).secondsToMs()
        val animDurationMs = ((elements[9] ?: return null).numberOrNull() ?: return null).msClamped()
        val animDelayMs = ((elements[10] ?: return null).numberOrNull() ?: return null).msClamped()

        val (beginAlpha, endAlpha) = parseAlpha(elements[2] ?: return null) ?: return null

        // 有第 15 项且为字符串时，路径点覆盖起终点；路径解析结果为空则整条丢弃
        // （官方在路径解析为空时视为永不显示；
        //   空串路径同样进入此分支，不再回退起终点）
        val path = elements.getOrNull(14) as? String
        val keyPoints =
            if (path != null) {
                val points = path.parseMotionPath()
                if (points.isEmpty()) return null
                points
            } else {
                listOf(
                    pointOf(elements[0], elements[1]) ?: return null,
                    pointOf(elements[7], elements[8]) ?: return null,
                )
            }

        return SpecialDanmakuSpec(
            // 官方对文本不做任何替换：JSON.parse 解出的内容原样进光栅化器，
            // "\n"（JSON 转义解出的真实换行）由原生画布按行布局，"/n" 按字面渲染
            text = text,
            keyPoints = keyPoints,
            beginAlpha = beginAlpha,
            endAlpha = endAlpha,
            displayDurationMs = displayDurationMs,
            animDurationMs = animDurationMs,
            animDelayMs = animDelayMs,
            // 官方 rotateZ = -deg/180*π（顺时针为正）；本实现存角度并在投影时取负
            rotateZ = (elements[5] ?: return null).numberOrNull() ?: 0f,
            // 官方解析 rotateY 但渲染器不使用，此处同样不参与渲染
            rotateY = elements.getOrNull(6)?.numberOrNull() ?: 0f,
            stroked = stroked,
            easeInOut = easeInOut,
        )
    }

    /** 官方 `relativeCoords(v, STAGE)`：截负后「≤ 1 视为比例，> 1 除以基准」。 */
    private fun normalize(v: Float, stage: Float): Float {
        val clamped = max(0f, v)
        return if (clamped <= 1f) clamped else clamped / stage
    }

    /** 起终点对：`[x 比例, 文本顶边 y 比例]`。 */
    private fun pointOf(
        xElement: Any?,
        yElement: Any?,
    ): FloatArray? {
        val x = xElement?.numberOrNull() ?: return null
        val y = yElement?.numberOrNull() ?: return null
        return floatArrayOf(normalize(x, SpecialDanmakuSpec.STAGE_WIDTH), normalize(y, SpecialDanmakuSpec.STAGE_HEIGHT))
    }

    /**
     * 官方透明度解析：
     * - 字符串 `split("-")`：1 段恒定、2 段渐变、≥3 段为初始值 0（整条透明）；
     * - 数字：恒定透明度。
     * 非数字片段按官方 `Number()` 的 NaN 处理，落到 0（不可见）。
     */
    private fun parseAlpha(value: Any?): Pair<Float, Float>? {
        if (!value.isStringOrNumber()) return null
        if (value is String) {
            val parts = value.split('-')
            return when (parts.size) {
                1 -> {
                    val alpha = parts[0].toFloatOrNull() ?: Float.NaN
                    alpha.toSafeAlpha() to alpha.toSafeAlpha()
                }

                2 -> {
                    val from = parts[0].toFloatOrNull() ?: Float.NaN
                    val to = parts[1].toFloatOrNull() ?: Float.NaN
                    from.toSafeAlpha() to to.toSafeAlpha()
                }

                else -> 0f to 0f
            }
        }
        val alpha = (value as Number).toFloat()
        return alpha.toSafeAlpha() to alpha.toSafeAlpha()
    }

    /** NaN（无法解析的字段）按不可见处理，其余原样（下游按 0-1 钳制）。 */
    private fun Float.toSafeAlpha(): Float = if (isNaN()) 0f else this

    /** 官方 `Math.max(Number(v), 0)`，单位为秒 → 毫秒。 */
    private fun Float.secondsToMs(): Long = (max(this, 0f) * 1000f).toLong()

    /** 官方 `Math.max(Number(v), 0)`，载荷本身即为毫秒。 */
    private fun Float.msClamped(): Long = max(this, 0f).toLong()

    /** 字符串视为数字（JS `Number()`）；空串/空白串为 0，其余无法解析返回 null。 */
    private fun Any?.numberOrNull(): Float? =
        when (this) {
            is Long -> toFloat()
            is Double -> toFloat()
            is String ->
                trim().toFloatOrNull()
                    ?: if (isBlank()) {
                        0f
                    } else {
                        null
                    }

            else -> null
        }

    /** 官方类型校验 `isStringOrNumber`。 */
    private fun Any?.isStringOrNumber(): Boolean = this is String || this is Long || this is Double

    /** 官方类型校验 `isStringOrNumber || isBoolean`。 */
    private fun Any?.isStringOrNumberOrBoolean(): Boolean = isStringOrNumber() || this is Boolean

    /** 官方宽松布尔判定：布尔原样、数字 `> 0`、字符串仅 `"true"`（忽略大小写）。 */
    private fun Any?.parseBooleanFlag(): Boolean =
        when (this) {
            is Boolean -> this
            is Long -> this > 0L
            is Double -> this > 0.0
            is String -> equals("true", ignoreCase = true)
            else -> false
        }

    /**
     * 官方路径解析 `path.substr(1).split(/M|L/)`：丢弃首字符（通常为 `M`/`L`），
     * 按 `M` / `L` 切段，每段 `split(",")` 恰好两个数即取点并归一化；
     * 坐标走 `Number()`（空串 / 空白为 0），解析不出数字的段跳过。
     *
     * 字符串长度 ≤ 1 时 `substr(1)` 得到空串，切不出任何点（官方 keyCoords 为空，
     * 视图永不创建）。
     */
    private fun String.parseMotionPath(): List<FloatArray> {
        if (length <= 1) return emptyList()
        val points = mutableListOf<FloatArray>()
        for (segment in substring(1).split('M', 'L')) {
            if (segment.isEmpty()) continue
            val xy = segment.split(',')
            if (xy.size != 2) continue
            val x = xy[0].numberOrNull() ?: continue
            val y = xy[1].numberOrNull() ?: continue
            points.add(
                floatArrayOf(
                    normalize(x, SpecialDanmakuSpec.STAGE_WIDTH),
                    normalize(y, SpecialDanmakuSpec.STAGE_HEIGHT),
                ),
            )
        }
        return points
    }

    /**
     * 轻量 JSON 数组解析：支持字符串 / 数字 / true / false / null。
     *
     * 与 `JSON.parse` 一致：空槽位（`,,`、`[,`）与尾逗号（`,]`）非法，整条丢弃；
     * 右括号之后存在非空白字符同样非法。嵌套数组 / 对象在本载荷形态中不出现，
     * 出现即按解析失败处理（官方类型校验同样会丢弃这些位置的值）。
     */
    private fun parseArray(source: String): List<Any?>? {
        var index = source.indexOfFirst { !it.isWhitespace() }
        if (index < 0 || source[index] != '[') return null
        index++
        val elements = mutableListOf<Any?>()
        // true 表示下一个非空白字符应当是元素值（'[' 或 ',' 之后）
        var expectValue = true
        while (true) {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return null
            when (val ch = source[index]) {
                ']' -> {
                    // 空槽位在遇到 ']' 时暴露出来（如 "[1,]"），按非法 JSON 丢弃
                    if (expectValue && elements.isNotEmpty()) return null
                    val hasTail = source.indexOfFirstFrom(index + 1) { !it.isWhitespace() } >= 0
                    return elements.takeIf { !hasTail }
                }

                ',' -> {
                    if (expectValue) return null
                    expectValue = true
                    index++
                }

                '"' -> {
                    val str = parseString(source, index) ?: return null
                    elements.add(str.first)
                    index = str.second
                    expectValue = false
                }

                't' -> {
                    if (!source.startsWith("true", index)) return null
                    elements.add(true)
                    index += 4
                    expectValue = false
                }

                'f' -> {
                    if (!source.startsWith("false", index)) return null
                    elements.add(false)
                    index += 5
                    expectValue = false
                }

                'n' -> {
                    if (!source.startsWith("null", index)) return null
                    elements.add(null)
                    index += 4
                    expectValue = false
                }

                else -> {
                    if (ch == '-' || ch == '+' || ch == '.' || ch in '0'..'9') {
                        val end =
                            source
                                .indexOfFirstFrom(index) { it == ',' || it == ']' }
                                .let { if (it >= 0) it else source.length }
                        val token = source.substring(index, end).trim()
                        // JSON 整数存 Long、小数存 Double：Long/Double 类型即保留数值语义
                        val number =
                            if (token.contains('.') || token.contains('e') || token.contains('E')) {
                                token.toDoubleOrNull()
                            } else {
                                token.toLongOrNull()
                            } ?: return null
                        elements.add(number)
                        index = end
                        expectValue = false
                    } else {
                        return null
                    }
                }
            }
        }
    }

    /** 解析转义字符串，返回 (内容, 结束后索引)。 */
    private fun parseString(
        source: String,
        startQuoteIndex: Int,
    ): Pair<String, Int>? {
        val builder = StringBuilder()
        var index = startQuoteIndex + 1
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == '"' -> return builder.toString() to (index + 1)
                ch == '\\' -> {
                    if (index + 1 >= source.length) return null
                    when (val escaped = source[index + 1]) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'n' -> builder.append('\n')
                        't' -> builder.append('\t')
                        'r' -> builder.append('\r')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'u' -> {
                            if (index + 6 > source.length) return null
                            val hex = source.substring(index + 2, index + 6)
                            val code = hex.toIntOrNull(16) ?: return null
                            builder.append(code.toChar())
                            index += 4
                        }

                        else -> return null
                    }
                    index += 2
                }

                else -> {
                    builder.append(ch)
                    index++
                }
            }
        }
        return null
    }

    private fun String.indexOfFirstFrom(
        startIndex: Int,
        predicate: (Char) -> Boolean,
    ): Int {
        for (i in startIndex until length) {
            if (predicate(this[i])) return i
        }
        return -1
    }
}
