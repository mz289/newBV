package dev.frost819.newbv.app.data

import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * danmaku 模块屏蔽规则的持久化映射与 JSON 编解码。
 *
 * 规则模型只存在于 danmaku 模块（[DanmakuBlockRule]），data 模块仅存原始
 * JSON 串（Prefs.danmakuBlockRules）；持久化编解码与默认值兜底全部经由本文件，
 * 避免内联解析散落各处。
 */
private val blockRuleJson = Json { ignoreUnknownKeys = true }

/** 屏蔽规则的持久化结构。 */
@Serializable
private data class DanmakuBlockRuleData(
    val type: String,
    val value: String,
    val enabled: Boolean = true,
)

/**
 * 解析持久化的屏蔽规则 JSON 串。
 *
 * 空串、损坏的 JSON、未知规则类型均按跳过处理（不抛异常，返回已解析的部分）。
 */
fun String.decodeDanmakuBlockRules(): List<DanmakuBlockRule> =
    runCatching {
        blockRuleJson
            .decodeFromString<List<DanmakuBlockRuleData>>(this)
            .mapNotNull { data ->
                val type =
                    runCatching { DanmakuBlockRuleType.valueOf(data.type) }.getOrNull()
                        ?: return@mapNotNull null
                DanmakuBlockRule(type = type, value = data.value, enabled = data.enabled)
            }
    }.getOrDefault(emptyList())

/**
 * 编码屏蔽规则列表为持久化 JSON 串。
 */
fun List<DanmakuBlockRule>.encodeDanmakuBlockRules(): String =
    blockRuleJson.encodeToString(
        map { DanmakuBlockRuleData(type = it.type.name, value = it.value, enabled = it.enabled) },
    )
