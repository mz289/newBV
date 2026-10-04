package dev.frost819.newbv.danmaku.config

/**
 * 弹幕屏蔽规则类型。
 *
 * 分类与匹配语义对齐 B 站客户端及主流第三方实现（关键词 / 正则 / 用户三类）。
 */
enum class DanmakuBlockRuleType {
    /** 关键词：忽略大小写的子串匹配。 */
    Keyword,

    /** 正则表达式：支持 `/pattern/flags` 或裸 pattern，按搜索式匹配（含即命中）。 */
    Regex,

    /** 用户：按弹幕发送者 midHash（crc32 十六进制串）精确匹配。 */
    User,

    /** 颜色：按弹幕文本颜色（6 位十六进制 RGB，如 FF6699）精确匹配。 */
    Color,
}

/**
 * 弹幕屏蔽规则。
 *
 * @property type 规则类型
 * @property value 规则值：关键词文本、正则表达式或 midHash 十六进制串
 * @property enabled 是否启用；停用的规则保留在列表中但不参与匹配
 */
data class DanmakuBlockRule(
    val type: DanmakuBlockRuleType,
    val value: String,
    val enabled: Boolean = true,
)
