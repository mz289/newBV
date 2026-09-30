package dev.frost819.newbv.app.data

import dev.frost819.newbv.danmaku.entity.DanmakuType as DanmakuEntityDanmakuType
import dev.frost819.newbv.data.datastore.DanmakuType as DataDanmakuType

/*
 * data 层与 danmaku 模块 [DanmakuType][DataDanmakuType] 的映射。
 *
 * 两个枚举分属 data（持久化）与 danmaku（渲染）模块，按名字互转
 * （All/Top/Rolling/Bottom）；未知名字回退 [DataDanmakuType.All]。
 * 全部映射必须经由本文件，避免内联转换散落各处。
 */

/**
 * 映射为 danmaku 模块的弹幕类型。
 */
fun DataDanmakuType.toDanmakuEntity(): DanmakuEntityDanmakuType =
    runCatching { DanmakuEntityDanmakuType.valueOf(name) }.getOrDefault(DanmakuEntityDanmakuType.All)

/**
 * 映射为 data 层弹幕类型。
 */
fun DanmakuEntityDanmakuType.toDataDanmakuType(): DataDanmakuType =
    runCatching { DataDanmakuType.valueOf(name) }.getOrDefault(DataDanmakuType.All)

/**
 * 批量映射为 danmaku 模块的弹幕类型列表。
 */
fun List<DataDanmakuType>.toDanmakuEntities(): List<DanmakuEntityDanmakuType> = map { it.toDanmakuEntity() }

/**
 * 批量映射为 data 层弹幕类型列表。
 */
fun List<DanmakuEntityDanmakuType>.toDataDanmakuTypes(): List<DataDanmakuType> = map { it.toDataDanmakuType() }
