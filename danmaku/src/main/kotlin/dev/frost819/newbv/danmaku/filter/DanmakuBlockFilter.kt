package dev.frost819.newbv.danmaku.filter

import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.ecs.component.filter.DanmakuDataFilter
import com.kuaishou.akdanmaku.utils.DanmakuTimer
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType

/**
 * 弹幕屏蔽过滤器。
 *
 * 按用户配置的屏蔽规则过滤弹幕，匹配语义对齐 B 站客户端：
 * - 关键词：忽略大小写的子串匹配
 * - 正则：`/pattern/flags` 剥壳或裸 pattern，默认忽略大小写的搜索式匹配；
 *   非法正则静默忽略（保证输入过程中规则实时生效不崩溃）
 * - 用户：midHash（crc32 十六进制串）精确匹配，与 `DanmakuItemData.userId`
 *   比对（由数据映射方负责把 midHash 十六进制串转成 Long 填入）
 * - 颜色：弹幕文本颜色 RGB（6 位十六进制）精确匹配
 *
 * 规则在 [setRules] 时一次性编译为逐规则条目（正则预编译、midHash 转 Long、
 * 颜色转 Int），每条启用规则携带稳定 key（[ruleKey]），命中时经 [onHit]
 * 回调上报，供命中统计使用。
 *
 * `filter` 在引擎线程高频调用，`setRules`/`enable`/`onHit` 在主线程更新，
 * 临界区加锁保护；[onHit] 在锁内调用，回调方须轻量（如仅做计数累加）。
 */
class DanmakuBlockFilter : DanmakuDataFilter(FILTER_TYPE_BLOCK) {
    /** 屏蔽总开关；关闭时所有规则不生效、不计数。 */
    @Volatile
    var enable: Boolean = false

    /**
     * 命中回调（参数为命中规则的 [ruleKey]），在引擎线程调用；
     * 由上层接入命中统计，未设置时不产生任何开销。
     */
    @Volatile
    var onHit: ((String) -> Unit)? = null

    private val lock = Any()
    private var compiled: List<CompiledRule> = emptyList()

    /** 用规则列表重建匹配集合；停用、空白值及无法编译的规则直接丢弃。 */
    fun setRules(rules: List<DanmakuBlockRule>) {
        val newCompiled =
            rules
                .filter { it.enabled }
                .mapNotNull { rule ->
                    val key = ruleKey(rule.type, rule.value)
                    when (rule.type) {
                        DanmakuBlockRuleType.Keyword ->
                            rule.value
                                .trim()
                                .lowercase()
                                .takeIf { it.isNotEmpty() }
                                ?.let { CompiledRule.Keyword(key, it) }
                        DanmakuBlockRuleType.Regex ->
                            parseBlockRegex(rule.value)?.let { CompiledRule.Regex(key, it) }
                        DanmakuBlockRuleType.User ->
                            rule.value
                                .trim()
                                .toLongOrNull(16)
                                ?.let { CompiledRule.User(key, it) }
                        DanmakuBlockRuleType.Color ->
                            rule.value
                                .trim()
                                .removePrefix("#")
                                .takeIf { value -> value.length == 6 && value.isHex() }
                                ?.let { CompiledRule.Color(key, it.toInt(16)) }
                    }
                }
        synchronized(lock) { compiled = newCompiled }
    }

    override fun filter(
        item: DanmakuItem,
        timer: DanmakuTimer,
        config: DanmakuConfig,
    ): Boolean = blocks(item.data)

    /** 数据预处理与引擎过滤共用相同匹配及命中统计。 */
    fun blocks(data: com.kuaishou.akdanmaku.data.DanmakuItemData): Boolean {
        val key = matchedRuleKey(data.content, data.userId, data.textColor) ?: return false
        onHit?.invoke(key)
        return true
    }

    /**
     * 判断单条弹幕是否命中规则，命中返回该规则的 [ruleKey]，未命中返回 null；
     * [enable] 为 false 时恒返回 null。单测/统计侧可直接使用，无需构造引擎 Item。
     */
    internal fun matchedRuleKey(
        content: String,
        userId: Long? = null,
        color: Int? = null,
    ): String? = synchronized(lock) { matchOf(content, userId, color)?.key }

    /** [matchedRuleKey] 的布尔形式：判断单条弹幕是否命中任一启用规则。 */
    internal fun isBlocked(
        content: String,
        userId: Long? = null,
        color: Int? = null,
    ): Boolean = matchedRuleKey(content, userId, color) != null

    /** 遍历编译条目找命中（用户 → 颜色 → 关键词 → 正则），未命中返回 null；调用方须已持有 [lock]。 */
    private fun matchOf(
        content: String,
        userId: Long?,
        color: Int?,
    ): CompiledRule? {
        if (!enable || compiled.isEmpty()) return null
        if (userId != null) {
            compiled
                .filterIsInstance<CompiledRule.User>()
                .firstOrNull { it.userId == userId }
                ?.let { return it }
        }
        if (color != null) {
            val rgb = color and RGB_MASK
            compiled
                .filterIsInstance<CompiledRule.Color>()
                .firstOrNull { it.rgb == rgb }
                ?.let { return it }
        }
        val lower = content.lowercase()
        compiled
            .filterIsInstance<CompiledRule.Keyword>()
            .firstOrNull { lower.contains(it.lower) }
            ?.let { return it }
        return compiled.filterIsInstance<CompiledRule.Regex>().firstOrNull { it.regex.containsMatchIn(content) }
    }

    /** 编译后的单条规则。 */
    private sealed interface CompiledRule {
        /** 命中统计用的稳定 key。 */
        val key: String

        data class Keyword(
            override val key: String,
            val lower: String,
        ) : CompiledRule

        data class Regex(
            override val key: String,
            val regex: kotlin.text.Regex,
        ) : CompiledRule

        data class User(
            override val key: String,
            val userId: Long,
        ) : CompiledRule

        data class Color(
            override val key: String,
            val rgb: Int,
        ) : CompiledRule
    }

    companion object {
        /** 过滤器命中标记，区别于类型过滤的 `DanmakuFilters.FILTER_TYPE_TYPE`。 */
        const val FILTER_TYPE_BLOCK = 2

        /** 弹幕颜色的 RGB 掩码（textColor 为 ARGB，取低 24 位比较）。 */
        private const val RGB_MASK = 0xFFFFFF

        /** 是否为 6 位十六进制颜色值。 */
        private fun String.isHex(): Boolean = all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

        /**
         * 生成规则的稳定统计 key（`类型名:规范值`）。
         *
         * 规范值与匹配语义一致：关键词统一小写、颜色统一大写，其余原样。
         * 过滤器命中回调与上层命中统计（TV 面板 / 手机网页）统一使用本函数。
         */
        fun ruleKey(
            type: DanmakuBlockRuleType,
            value: String,
        ): String {
            val normalized =
                when (type) {
                    DanmakuBlockRuleType.Keyword -> value.trim().lowercase()
                    DanmakuBlockRuleType.Color -> value.trim().removePrefix("#").uppercase()
                    else -> value.trim()
                }
            return "${type.name}:$normalized"
        }

        /**
         * 解析正则规则：`/pattern/flags` 剥壳（flags 支持 i/m/s），裸 pattern
         * 按忽略大小写处理；空白或无法编译的返回 null（静默忽略）。
         *
         * 暴露为公开方法供添加规则的 UI 实时校验输入。
         */
        fun parseBlockRegex(raw: String): Regex? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val wrapped = WRAPPED_REGEX.matchEntire(trimmed)
            return try {
                if (wrapped != null) {
                    val pattern = wrapped.groupValues[1]
                    val options = wrapped.groupValues[2].mapNotNull { regexOptionOf(it) }.toSet()
                    Regex(pattern, options)
                } else {
                    Regex(trimmed, setOf(RegexOption.IGNORE_CASE))
                }
            } catch (_: Exception) {
                null
            }
        }

        private val WRAPPED_REGEX = Regex("^/(.+)/([ims]*)$")

        private fun regexOptionOf(flag: Char): RegexOption? =
            when (flag) {
                'i' -> RegexOption.IGNORE_CASE
                'm' -> RegexOption.MULTILINE
                's' -> RegexOption.DOT_MATCHES_ALL
                else -> null
            }
    }
}
