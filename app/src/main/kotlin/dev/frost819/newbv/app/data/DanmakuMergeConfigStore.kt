package dev.frost819.newbv.app.data

import dev.frost819.newbv.danmaku.config.DanmakuCountMark
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 将合并配置作为一个整体保存，避免多个偏好键出现部分更新。 */
object DanmakuMergeConfigStore {
    fun load(): DanmakuMergeConfig = decode(Prefs.danmakuMergeConfig)

    fun decode(source: String): DanmakuMergeConfig =
        runCatching {
            val json = Json.parseToJsonElement(source).jsonObject
            val defaults = DanmakuMergeConfig()
            // 迁移旧百分比阈值：保留严格匹配（100%），不额外打开旧版没有的算法。
            val legacySimilarity = (json["similarityPercent"] as? JsonPrimitive)?.intOrNull
            val defaultCosine = if (legacySimilarity == null) defaults.cosineThreshold else 101
            DanmakuMergeConfig(
                windowSeconds = json.optInt("windowSeconds", defaults.windowSeconds),
                editDistanceThreshold =
                    json.optInt(
                        "editDistanceThreshold",
                        legacySimilarity?.let { (100 - it.coerceIn(50, 100)) / 4 } ?: defaults.editDistanceThreshold,
                    ),
                cosineThreshold = json.optInt("cosineThreshold", defaultCosine),
                recognizePinyin =
                    json.optBoolean(
                        "recognizePinyin",
                        legacySimilarity == null && defaults.recognizePinyin,
                    ),
                trimWidth = json.optBoolean("trimWidth", defaults.trimWidth),
                trimSpace = json.optBoolean("trimSpace", defaults.trimSpace),
                trimEnding = json.optBoolean("trimEnding", defaults.trimEnding),
                crossMode = json.optBoolean("crossMode", defaults.crossMode),
                skipSubtitle = json.optBoolean("skipSubtitle", defaults.skipSubtitle),
                skipBottom = json.optBoolean("skipBottom", defaults.skipBottom),
                markPosition =
                    DanmakuCountMark.entries.firstOrNull {
                        it.name == (json["markPosition"] as? JsonPrimitive)?.content
                    }
                        ?: defaults.markPosition,
                scrollThreshold = json.optInt("scrollThreshold", defaults.scrollThreshold),
                dropThreshold = json.optInt("dropThreshold", defaults.dropThreshold),
            ).sanitized()
        }.getOrDefault(DanmakuMergeConfig())

    fun save(config: DanmakuMergeConfig) {
        Prefs.danmakuMergeConfig = encode(config)
    }

    fun encode(config: DanmakuMergeConfig): String {
        val value = config.sanitized()
        return buildJsonObject {
            put("windowSeconds", value.windowSeconds)
            put("editDistanceThreshold", value.editDistanceThreshold)
            put("cosineThreshold", value.cosineThreshold)
            put("recognizePinyin", value.recognizePinyin)
            put("trimWidth", value.trimWidth)
            put("trimSpace", value.trimSpace)
            put("trimEnding", value.trimEnding)
            put("crossMode", value.crossMode)
            put("skipSubtitle", value.skipSubtitle)
            put("skipBottom", value.skipBottom)
            put("markPosition", value.markPosition.name)
            put("scrollThreshold", value.scrollThreshold)
            put("dropThreshold", value.dropThreshold)
        }.toString()
    }

    private fun JsonObject.optInt(
        key: String,
        fallback: Int,
    ): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: fallback

    private fun JsonObject.optBoolean(
        key: String,
        fallback: Boolean,
    ): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
}
