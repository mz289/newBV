package dev.frost819.newbv.data.datastore

/**
 * SponsorBlock 片段跳过策略。
 *
 * @property code 持久化用的单字符编码。
 * @property displayName 设置页展示名。
 */
enum class SkipPolicy(
    val code: String,
    val displayName: String,
) {
    /** 播放位置进入片段时自动 seek 到片段结束。 */
    Auto("A", "自动跳过"),

    /** 播放位置进入片段时提示，由用户按确认键决定是否跳过。 */
    Prompt("P", "手动确认"),

    /** 不做任何处理。 */
    Disabled("D", "不跳过"),
    ;

    companion object {
        /** 从持久化编码还原，未知编码按不跳过处理。 */
        fun fromCode(code: String): SkipPolicy = entries.firstOrNull { it.code == code } ?: Disabled
    }
}

/**
 * SponsorBlock 偏好默认值与编解码。
 *
 * 分类名与 bsbsb.top（Bilibili SponsorBlock 社区服务器）API 保持一致，
 * 策略表持久化为 `分类:策略编码` 的逗号串（如 `sponsor:A,intro:D`）。
 */
object SponsorBlockDefaults {
    /** 支持的片段分类（顺序即设置页展示顺序）。 */
    val supportedCategories: List<String> =
        listOf(
            "sponsor",
            "selfpromo",
            "exclusive_access",
            "interaction",
            "poi_highlight",
            "intro",
            "outro",
            "preview",
            "filler",
            "music_offtopic",
        )

    /** 默认策略：仅自动跳过赞助推广，其余分类默认不处理。 */
    val policies: Map<String, SkipPolicy> =
        supportedCategories.associateWith { category ->
            if (category == "sponsor") SkipPolicy.Auto else SkipPolicy.Disabled
        }

    /** 解析持久化串；缺失的分类回退到默认策略，未知的分类/编码忽略。 */
    fun parse(encoded: String): Map<String, SkipPolicy> =
        buildMap {
            putAll(policies)
            encoded
                .split(',')
                .forEach { entry ->
                    val parts = entry.split(':', limit = 2)
                    if (parts.size == 2 && parts[0] in supportedCategories) {
                        put(parts[0], SkipPolicy.fromCode(parts[1].trim()))
                    }
                }
        }

    /** 编码为持久化串。 */
    fun encode(policies: Map<String, SkipPolicy>): String =
        policies.entries.joinToString(",") { (category, policy) -> "$category:${policy.code}" }
}
