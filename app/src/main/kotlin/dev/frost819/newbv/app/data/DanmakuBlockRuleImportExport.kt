package dev.frost819.newbv.app.data

import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter
import java.util.zip.CRC32

/*
 * 弹幕屏蔽规则导入/导出（屏蔽串）。
 *
 * 支持的导入格式（自动嗅探）：
 * 1. B 站 XML 屏蔽串（tv.bilibili.player.xml）：
 *    - 新版 `<item enabled="true">t=关键词</item>`，前缀 t=关键词 / u=用户 / r=正则 / c=颜色
 *    - 旧版 `<f t="类型码">值</f>`（类型码 0=关键词 1=正则 2=用户）
 * 2. 纯文本每行一条：`/regex/` 包裹或含正则元字符的行按正则导入，其余按关键词；
 *    兼容行内 `t=`/`u=`/`r=`/`c=` 前缀（从 XML 复制单行的场景）。
 *
 * 用户规则值兼容两种形态：8 位十六进制 midHash 原样保留；
 * 纯数字 UID 按 B 站 midHash 算法（CRC32(UID 十进制 ASCII) 的十六进制）转换。
 * 颜色规则值为 6 位十六进制 RGB（兼容可选 # 前缀），统一大写存储。
 */

/** 单条规则值长度上限，超长视为损坏数据丢弃。 */
private const val MAX_RULE_VALUE_LENGTH = 300

/** 剥离控制字符（社区词库常含 emoji/特殊符号导致的控制字节）。 */
private val CONTROL_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")

/** XML 注释节点。 */
private val XML_COMMENT = Regex("<!--[\\s\\S]*?-->")

/** 新版 item 节点（enabled 属性 + 内文本）。 */
private val XML_ITEM = Regex("<item\\b([^>]*)>([\\s\\S]*?)</item>")

/** 旧版 f 节点（t 属性为类型码）。 */
private val XML_LEGACY_F = Regex("<f\\b[^>]*t\\s*=\\s*[\"']?([0-9])[\"']?[^>]*>([\\s\\S]*?)</f>")

/** XML 属性值提取。 */
private val XML_ATTR_ENABLED = Regex("enabled\\s*=\\s*[\"']?([^\\s\"'>]+)")

/** 正则元字符（纯文本行判型，参考社区词库生成脚本惯例）。 */
private const val REGEX_META = "\\^\$*+?{}[]().|"

/** 导入结果。 */
data class BlockRuleImportResult(
    /** 解析成功的规则。 */
    val rules: List<DanmakuBlockRule>,
    /** 被跳过的条数（非法正则/不支持的颜色/用户值非法/超长/空值）。 */
    val skipped: Int,
)

/**
 * 解析屏蔽串文本为规则列表（不与已有规则去重，由调用方决定合并策略）。
 */
fun parseBlockRules(rawText: String): BlockRuleImportResult {
    val text = rawText.trim().removePrefix("\uFEFF").let { CONTROL_CHARS.replace(it, "") }
    if (text.isEmpty()) return BlockRuleImportResult(emptyList(), 0)
    return if (text.startsWith("<")) parseXmlRules(text) else parsePlainTextRules(text)
}

/**
 * 解析 B 站 XML 屏蔽串（新版 item + 旧版 f 双格式）。
 */
private fun parseXmlRules(text: String): BlockRuleImportResult {
    val cleaned = XML_COMMENT.replace(text, "")
    val rules = ArrayList<DanmakuBlockRule>()
    var skipped = 0

    val parseEntry: (rawValue: String, type: DanmakuBlockRuleType?, enabled: Boolean) -> Unit =
        { rawValue, type, enabled ->
            val value = xmlUnescape(rawValue).trim()
            if (type == null) {
                skipped++
            } else {
                toRule(type, value, enabled)?.let { rules.add(it) } ?: run { skipped++ }
            }
        }

    XML_ITEM.findAll(cleaned).forEach { match ->
        val enabledAttr = XML_ATTR_ENABLED.find(match.groupValues[1])?.groupValues?.get(1)
        val enabled = enabledAttr == "true" || enabledAttr == "1"
        val body = match.groupValues[2].trim()
        // 仅当完整 "x=" 前缀（首字符为类型字母且第二个字符是 '='）才剥前缀，
        // 修复官方解析器对无 '=' 的 r/c 开头文本误截断的问题
        val type =
            if (body.length >= 2 && body[1] == '=') {
                prefixToType(body[0])
            } else {
                DanmakuBlockRuleType.Keyword
            }
        val value = if (body.length >= 2 && body[1] == '=') body.substring(2) else body
        parseEntry(value, type, enabled)
    }

    XML_LEGACY_F.findAll(cleaned).forEach { match ->
        val type =
            when (match.groupValues[1]) {
                "0" -> DanmakuBlockRuleType.Keyword
                "1" -> DanmakuBlockRuleType.Regex
                "2" -> DanmakuBlockRuleType.User
                else -> null
            }
        parseEntry(match.groupValues[2], type, true)
    }

    return BlockRuleImportResult(rules, skipped)
}

/**
 * 解析纯文本行格式：每行一条规则，空行跳过。
 */
private fun parsePlainTextRules(text: String): BlockRuleImportResult {
    val rules = ArrayList<DanmakuBlockRule>()
    var skipped = 0
    text.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        val rule =
            when {
                // 行内前缀（从 XML 复制出来的单行）
                line.length >= 2 && line[1] == '=' && prefixToType(line[0]) != null ->
                    toRule(prefixToType(line[0])!!, line.substring(2).trim(), enabled = true)
                // /regex/ 包裹 → 正则
                line.length > 2 && line.first() == '/' && line.last() == '/' ->
                    toRule(DanmakuBlockRuleType.Regex, line, enabled = true)
                // 含正则元字符 → 正则（参考 mark9804 词库生成脚本判型）
                line.any { it in REGEX_META } -> toRule(DanmakuBlockRuleType.Regex, line, enabled = true)
                else -> toRule(DanmakuBlockRuleType.Keyword, line, enabled = true)
            }
        if (rule != null) rules.add(rule) else skipped++
    }
    return BlockRuleImportResult(rules, skipped)
}

/** XML 前缀字母 → 规则类型。 */
private fun prefixToType(prefix: Char): DanmakuBlockRuleType? =
    when (prefix) {
        't' -> DanmakuBlockRuleType.Keyword
        'r' -> DanmakuBlockRuleType.Regex
        'u' -> DanmakuBlockRuleType.User
        'c' -> DanmakuBlockRuleType.Color
        else -> null
    }

/**
 * 构造规则并做合法性校验：空值/超长/非法正则/非法用户值返回 null。
 */
private fun toRule(
    type: DanmakuBlockRuleType,
    value: String,
    enabled: Boolean,
): DanmakuBlockRule? {
    if (value.isEmpty() || value.length > MAX_RULE_VALUE_LENGTH) return null
    return when (type) {
        DanmakuBlockRuleType.Regex ->
            if (DanmakuBlockFilter.parseBlockRegex(value) == null) null else DanmakuBlockRule(type, value, enabled)
        DanmakuBlockRuleType.User ->
            when {
                // 纯数字：按 B 站 UID 处理，转换为 midHash（CRC32(UID 十进制 ASCII) 十六进制）
                // 注意判序：UID 优先——8 位纯数字也可能是合法 hex 的 midHash，
                // 但 B 站 XML 的 u= 官方语义是 UID，且 midHash 通常含 a-f
                value.all { it in '0'..'9' } ->
                    DanmakuBlockRule(type, midHashOfUid(value.toLong()), enabled)
                // 8 位十六进制：已是 midHash，原样保留（小写化）
                value.length == 8 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ->
                    DanmakuBlockRule(type, value.lowercase(), enabled)
                else -> null
            }
        DanmakuBlockRuleType.Color ->
            // 6 位十六进制 RGB（兼容可选的 # 前缀），统一大写存储
            value
                .removePrefix("#")
                .takeIf { it.length == 6 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
                ?.let { DanmakuBlockRule(type, it.uppercase(), enabled) }
        DanmakuBlockRuleType.Keyword -> DanmakuBlockRule(type, value, enabled)
    }
}

/** B 站 midHash 算法：CRC32(UID 十进制字符串 ASCII 字节) 的 8 位十六进制（不足补零）。 */
internal fun midHashOfUid(uid: Long): String {
    val crc = CRC32()
    crc.update(uid.toString().toByteArray(Charsets.US_ASCII))
    return "%08x".format(crc.value)
}

/** 解码 XML 实体（官方最小集 + 常见工具产出的引号实体与数字实体）。 */
private fun xmlUnescape(text: String): String =
    text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(NUMERIC_ENTITY, ::decodeNumericEntity)
        .replace("&amp;", "&") // amp 最后解，避免二次解码

/** XML 数字字符实体（十进制或十六进制）。 */
private val NUMERIC_ENTITY = Regex("&#x?([0-9a-fA-F]+);")

/** 解码单个数字字符实体。 */
private fun decodeNumericEntity(match: MatchResult): String {
    val code =
        if (match.value.startsWith("&#x")) {
            match.groupValues[1].toInt(16)
        } else {
            match.groupValues[1].toInt()
        }
    return code.toChar().toString()
}

/**
 * 导出规则列表为 B 站 XML 屏蔽串格式（文件名约定 tv.bilibili.player.xml）。
 */
fun exportBlockRulesXml(state: DanmakuBlockRuleStore.State): String =
    buildString {
        append("<filters>\n")
        state.rules.forEach { rule ->
            val prefix =
                when (rule.type) {
                    DanmakuBlockRuleType.Keyword -> "t"
                    DanmakuBlockRuleType.Regex -> "r"
                    DanmakuBlockRuleType.User -> "u"
                    DanmakuBlockRuleType.Color -> "c"
                }
            append("<item enabled=\"")
            append(rule.enabled)
            append("\">")
            append(prefix)
            append('=')
            append(xmlEscape(rule.value))
            append("</item>\n")
        }
        append("</filters>\n")
    }

/** XML 转义（对齐官方导出行为：仅转义 < > &）。 */
private fun xmlEscape(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
