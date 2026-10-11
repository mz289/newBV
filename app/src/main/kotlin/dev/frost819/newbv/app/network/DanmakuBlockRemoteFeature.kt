package dev.frost819.newbv.app.network

import android.content.Context
import dev.frost819.newbv.app.data.DanmakuBlockHitStats
import dev.frost819.newbv.app.data.DanmakuBlockRuleStore
import dev.frost819.newbv.app.data.exportBlockRulesXml
import dev.frost819.newbv.app.data.midHashOfUid
import dev.frost819.newbv.app.data.parseBlockRules
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 弹幕屏蔽远程管理功能标签。
 *
 * 面板提供规则增删/启停/总开关/导入导出，与 TV 端通过
 * [DanmakuBlockRuleStore]（进程内 StateFlow）实时同步：手机端的任何修改
 * 即时生效到正在播放的弹幕过滤，TV 端的修改也会反映到手机页面（轮询）。
 *
 * 动作（body 语义见各处理器）：
 * - `state` — 当前开关 + 规则列表 JSON（含会话命中数）
 * - `setEnabled` — body `{"enabled":true}`
 * - `addRule` / `toggleRule` / `deleteRule` — body `{"type":"Keyword","value":".."}`
 * - `import` — body 原始屏蔽串文本（自动嗅探 XML/纯文本）
 * - `export` — 返回 B 站 XML 屏蔽串文本（页面端转 Blob 下载）
 */
class DanmakuBlockRemoteFeature private constructor(
    panelHtml: String,
    actions: Map<String, suspend (String) -> String>,
) : RemoteConsoleFeature(
        id = ID,
        label = "弹幕屏蔽",
        iconPath = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
        panelHtml = panelHtml,
        actions = actions,
    ) {
    companion object {
        const val ID = "danmaku_block"

        /** encodeDefaults 必须为 true：否则 enabled=true（默认值）被省略，网页端会把启用规则误读为停用。 */
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * @param context 用于读取面板片段资源（面板 JS 含模板字面量，放 assets 避免 Kotlin 转义）
         */
        fun create(context: Context): DanmakuBlockRemoteFeature =
            DanmakuBlockRemoteFeature(
                panelHtml = loadPanel(context),
                actions =
                    mapOf(
                        "state" to { _ -> json.encodeToString(stateDto()) },
                        "setEnabled" to { body ->
                            val enabled = json.decodeFromString<EnabledDto>(body).enabled
                            DanmakuBlockRuleStore.setEnabled(enabled)
                            json.encodeToString(stateDto())
                        },
                        "addRule" to { body ->
                            val dto = json.decodeFromString<RuleDto>(body)
                            val added = dto.toRule()?.let { DanmakuBlockRuleStore.addRule(it) } ?: false
                            if (added) {
                                json.encodeToString(stateDto())
                            } else {
                                """{"ok":false,"message":"规则已存在或无效"}"""
                            }
                        },
                        "toggleRule" to { body ->
                            val dto = json.decodeFromString<RuleDto>(body)
                            if (DanmakuBlockRuleStore.toggleRule(dto.typeEnum(), dto.value)) {
                                json.encodeToString(stateDto())
                            } else {
                                """{"ok":false,"message":"规则不存在"}"""
                            }
                        },
                        "deleteRule" to { body ->
                            val dto = json.decodeFromString<RuleDto>(body)
                            if (DanmakuBlockRuleStore.removeRule(dto.typeEnum(), dto.value)) {
                                json.encodeToString(stateDto())
                            } else {
                                """{"ok":false,"message":"规则不存在"}"""
                            }
                        },
                        "import" to { body ->
                            val result = parseBlockRules(body)
                            if (result.rules.isEmpty()) {
                                """{"ok":false,"imported":0,"skipped":${result.skipped},"message":"未解析到有效规则"}"""
                            } else {
                                val imported = DanmakuBlockRuleStore.addRules(result.rules)
                                val duplicates = result.rules.size - imported
                                """{"ok":true,"imported":$imported,"duplicates":$duplicates,"skipped":${result.skipped}}"""
                            }
                        },
                        "export" to { _ -> exportBlockRulesXml(DanmakuBlockRuleStore.state.value) },
                    ),
            )

        /** 面板片段：HTML/JS 在 assets/remote_console_ui/panel_danmaku_block.html（JS 含模板字面量，避免 Kotlin 转义）。 */
        private fun loadPanel(context: Context): String =
            context.assets
                .open("remote_console_ui/panel_danmaku_block.html")
                .bufferedReader()
                .use { it.readText() }

        /** 当前状态序列化为响应 DTO（含每规则会话命中数）。 */
        private fun stateDto(): BlockStateDto {
            val hitCounts = DanmakuBlockHitStats.snapshot()
            return BlockStateDto(
                enabled = DanmakuBlockRuleStore.state.value.enabled,
                totalHits = DanmakuBlockHitStats.totalHits.value,
                rules =
                    DanmakuBlockRuleStore.state.value.rules.map { rule ->
                        val hits = hitCounts[DanmakuBlockFilter.ruleKey(rule.type, rule.value)] ?: 0
                        RuleDto
                            .from(rule)
                            .copy(hitCount = hits)
                    },
            )
        }

        @Serializable
        private data class BlockStateDto(
            val enabled: Boolean,
            val rules: List<RuleDto>,
            /** 本会话总命中数（切换视频时清零）。 */
            val totalHits: Int = 0,
        )

        /** 规则 DTO：type 用 [DanmakuBlockRuleType] 名称（Keyword/Regex/User/Color）。 */
        @Serializable
        private data class RuleDto(
            val type: String,
            val value: String,
            val enabled: Boolean = true,
            /** 本会话该规则命中数。 */
            val hitCount: Int = 0,
        ) {
            fun toRule(): DanmakuBlockRule? =
                runCatching { DanmakuBlockRule(type = typeEnum(), value = normalizeValue(), enabled = enabled) }
                    .getOrNull()
                    ?.takeIf { it.value.isNotEmpty() && isValueValid(it.type, it.value) }

            fun typeEnum(): DanmakuBlockRuleType = DanmakuBlockRuleType.valueOf(type)

            /**
             * 规范化规则值，与屏蔽串导入语义一致：
             * 用户规则纯数字按 B 站 UID 转 midHash（否则会被过滤器按十六进制误读），
             * 颜色统一大写并剥可选 # 前缀。
             */
            private fun normalizeValue(): String {
                val trimmed = value.trim()
                return when (typeEnum()) {
                    DanmakuBlockRuleType.User ->
                        if (trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }) {
                            midHashOfUid(trimmed.toLong())
                        } else {
                            trimmed
                        }
                    DanmakuBlockRuleType.Color -> trimmed.removePrefix("#").uppercase()
                    else -> trimmed
                }
            }

            /**
             * 服务端合法性校验（非法规则在过滤器编译时会被静默丢弃，必须在入口拒绝）：
             * 正则须可编译；用户须为 8 位十六进制 midHash；颜色须为 6 位十六进制 RGB。
             */
            private fun isValueValid(
                type: DanmakuBlockRuleType,
                value: String,
            ): Boolean =
                when (type) {
                    DanmakuBlockRuleType.Regex -> DanmakuBlockFilter.parseBlockRegex(value) != null
                    DanmakuBlockRuleType.User ->
                        value.length == 8 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                    DanmakuBlockRuleType.Color ->
                        value.length == 6 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                    DanmakuBlockRuleType.Keyword -> true
                }

            companion object {
                fun from(rule: DanmakuBlockRule): RuleDto =
                    RuleDto(
                        type = rule.type.name,
                        value = rule.value,
                        enabled = rule.enabled,
                    )
            }
        }

        @Serializable
        private data class EnabledDto(
            val enabled: Boolean,
        )
    }
}
