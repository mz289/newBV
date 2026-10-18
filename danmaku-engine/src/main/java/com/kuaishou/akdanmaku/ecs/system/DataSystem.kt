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

package com.kuaishou.akdanmaku.ecs.system

import android.util.Log
import androidx.annotation.WorkerThread
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.kuaishou.akdanmaku.collection.TreeList
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.data.DataSource
import com.kuaishou.akdanmaku.ecs.DanmakuContext
import com.kuaishou.akdanmaku.ecs.DanmakuEngine
import com.kuaishou.akdanmaku.ecs.base.DanmakuSortedSystem
import com.kuaishou.akdanmaku.ecs.component.FilterResultComponent
import com.kuaishou.akdanmaku.ecs.component.ItemDataComponent
import com.kuaishou.akdanmaku.ecs.component.LayoutComponent
import com.kuaishou.akdanmaku.ecs.component.action.ActionComponent
import com.kuaishou.akdanmaku.ecs.component.mode.fixed.BottomComponent
import com.kuaishou.akdanmaku.ecs.component.mode.fixed.TopComponent
import com.kuaishou.akdanmaku.ecs.component.mode.rolling.RollingComponent
import com.kuaishou.akdanmaku.ext.*
import com.kuaishou.akdanmaku.utils.Families
import java.util.*
import kotlin.math.max

/**
 * 持有所有弹幕数据，并根据当前时间确定当前使用切片数据的系统
 * 生成具有 [ItemDataComponent] 的 Entity
 *
 * @author Xana
 * @since 2021-06-16
 */
internal class DataSystem(
    context: DanmakuContext,
) : DanmakuSortedSystem(context, Families.dataFamily),
    DataSource.DataChangeListener {
    /**
     * 有序的弹幕数据合集
     */
    private val sortedData = Collections.synchronizedList(mutableListOf<DanmakuItem>())
    private var currentData = Danmakus(Collections.synchronizedList(TreeList()), 0L, 0L, -1, -1)
    private val comparator = DanmakuItemComparator()
    private val pendingAddItems = mutableListOf<DanmakuItem>()
    private val pendingCreateItems = mutableListOf<DanmakuItem>()
    private val pendingUpdateItems = mutableListOf<DanmakuItem>()
    private var shouldSort = false
    private var startTimeMills = 0L
    private var endTimeMills = 0L

    private var forceUpdate = false
    private var maxSpecialDurationMs = 0L


    private var holdingItem: DanmakuItem? = null

    private val idSet = hashSetOf<Long>()

    override fun removedFromEngine(engine: Engine) {
        super.removedFromEngine(engine)
        sortedData.clear()
    }

    override fun update(deltaTime: Float) {
        withTrace("DataSystem_update") {
            withTrace("DataSystem_processEntity") {
                val config = danmakuContext.config
                for (entity in getEntities()) {
                    val item = entity.dataComponent?.item ?: continue
                    val data = item.data
                    item.duration =
                        if (data.mode == DanmakuItemData.DANMAKU_MODE_ROLLING) {
                            config.rollingDurationMs
                        } else if (data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL) {
                            // 高级弹幕在屏时长由载荷决定：官方动作序列总长为
                            // max(displayDuration, animDelay + animDuration)（运动未走完不结束）
                            data.special?.visibleDurationMs ?: data.lifeTime
                        } else {
                            config.durationMs
                        }
                    if (data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL &&
                        data.special != null &&
                        item.specialBakedGeneration != config.measureGeneration
                    ) {
                        // 显示尺寸 / 样式变化（measureGeneration 变更）后重新烘培动作
                        item.rebakeSpecialActions(
                            danmakuContext.displayer.width,
                            danmakuContext.displayer.height,
                            config.measureGeneration,
                        )
                        entity.action?.also { component ->
                            component.actions.clear()
                            item.actions.forEach { component.addAction(it) }
                        }
                    }
                    if (entity.isTimeout(currentTimeMs)) {
                        if (currentData.data.isNotEmpty()) {
                            currentData.data.remove(item)
                        }
                        idSet.remove(data.danmakuId)
                        engine.removeEntity(entity)
                        currentData.startIndex++
                    } else {
                        if (entity.isLate(endTimeMills)) {
                            idSet.remove(data.danmakuId)
                            engine.removeEntity(entity)
                        }
                    }
                }
                super.update(deltaTime)
            }
        }
    }

    override fun processEntity(
        entity: Entity,
        deltaTime: Float,
    ) {
        val timer = danmakuTimer
        val item = entity.dataComponent?.item ?: return
        val filter = entity.filter ?: createComponent(FilterResultComponent::class.java, entity, item) ?: return

        val config = danmakuContext.config
        if (filter.filterGeneration != config.filterGeneration) {
            val filtered =
                danmakuContext.filter.filterData(item, timer, config)
            filter.update(config.filterGeneration, filtered.filtered)
        }
    }

    fun updateEntities() {
        val config = danmakuContext.config
        val durationMs = config.durationMs
        val rollingDurationMs = config.rollingDurationMs
        val maxDuration = max(durationMs, rollingDurationMs)
        addPendingItems()
        // 高级弹幕可持续超过普通弹幕窗口；seek / 重建后也要恢复仍存活的条目。
        // 只扩大回看窗口，避免长生命周期把远期弹幕也提前创建出来。
        val startTime = currentTimeMs - max(maxDuration, maxSpecialDurationMs)
        val endTime = currentTimeMs + maxDuration
        withTrace("DataSystem_sort") {
            sort()
        }
        // 当 目前的数据切片不能覆盖以当桥时间为起始点的屏幕时，需要重新切片
        if (forceUpdate ||
            startTime < startTimeMills ||
            currentTimeMs > endTimeMills - danmakuContext.config.preCacheTimeMs
        ) {
            startTimeMills = startTime
            endTimeMills = endTime
            updateCurrentSlice()
            forceUpdate = false
        }
        createPendingItems()
    }

    private fun updateCurrentSlice() {
        if (sortedData.isEmpty()) return
        withTrace("DataSystem_updateCurrentSlice") {
            startTrace("DataSystem_createNewSlice")
            val startIndex: Int
            val endIndex: Int
            val newData: MutableList<DanmakuItem>
            synchronized(this) {
                startIndex = sortedData.binarySearchAtLeast(startTimeMills) { it.timePosition }
                endIndex = sortedData.binarySearchAtMost(endTimeMills) { it.timePosition }
                if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
                    Log.w(
                        DanmakuEngine.TAG,
                        "[Data] update current slice failed: invalid start or end index.",
                    )
                    endTrace()
                    return@withTrace
                }
                Log.w(
                    DanmakuEngine.TAG,
                    "[Data] update current slice [$startIndex, $endIndex] in time ($startTimeMills, $endTimeMills)",
                )
                newData = sortedData.subList(startIndex, endIndex + 1)
                endTrace()
            }

            startTrace("DataSystem_getCurrentEntity_${newData.size}")
            val oldData = currentData
            currentData =
                Danmakus(
                    Collections.synchronizedList(newData.toTreeList()),
                    startTimeMills,
                    endTimeMills,
                    startIndex,
                    endIndex,
                )
            endTrace()

            startTrace("DataSystem_diffAndCreateEntity")
            var addCount = 0
            // 不重叠时直接添加所有 newData 至 entity
            if (startIndex > oldData.endIndex || endIndex <= oldData.startIndex) {
                addCount += newData.size
                createEntityBeforeEntry(newData)
                Log.d(DanmakuEngine.TAG, "[Data] Add all new data [$startIndex, $endIndex]")
            } else {
                createEntityBeforeEntry(newData)
            }
            endTrace()

            Log.d(DanmakuEngine.TAG, "[Data] Add $addCount in [$startTimeMills, $endTimeMills]")
        }
    }

    private fun createEntityBeforeEntry(data: List<DanmakuItem>): Int {
        pendingCreateItems.addAll(data)
        return data.size
    }

    private fun createItemEntity(item: DanmakuItem) {
        if (item.data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL) {
            val special = item.data.special
            if (special == null) {
                // mode 7 但载荷解析失败（special 缺失）时没有可执行动作
                return
            }
            // 官方视图创建窗口：n = 当前时间 - 弹幕时间，n > animDelay 即不再创建视图
            // （跳转 / 中途载入时动画已跑过延迟的弹幕不会补显示）
            if (currentTimeMs - item.data.position > special.animDelayMs) {
                return
            }
        }
        if (idSet.contains(item.data.danmakuId)) {
            return
        }

        // 高级弹幕入场前按当前显示尺寸烘培动作（宽高为 0 时按官方舞台基准，尺寸变化后代次机制会重烘培）
        if (item.data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL && item.data.special != null) {
            item.rebakeSpecialActions(
                danmakuContext.displayer.width,
                danmakuContext.displayer.height,
                danmakuContext.config.measureGeneration,
            )
        }

        val entity = engine.createEntity()
        entity.apply {
            createComponent(ItemDataComponent::class.java, entity, item) ?: return
            if (item.data.mode > 0) {
                createComponent(LayoutComponent::class.java, entity, item) ?: return
                when (item.data.mode) {
                    DanmakuItemData.DANMAKU_MODE_ROLLING -> add(RollingComponent())
                    DanmakuItemData.DANMAKU_MODE_CENTER_TOP -> add(TopComponent())
                    DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM -> add(BottomComponent())
                }
            }
            if (!item.actions.isEmpty) {
                createComponent(ActionComponent::class.java, entity, item)?.also { component ->
                    item.actions.forEach { component.addAction(it) }
                }
            }
        }
        engine.addEntity(entity)
        idSet.add(item.data.danmakuId)
    }

    @WorkerThread
    private fun addPendingItems() {
        val pendingItems =
            synchronized(this) {
                val items = pendingAddItems.toList()
                pendingAddItems.clear()
                items
            }
        val updateItems =
            synchronized(this) {
                val items = pendingUpdateItems.toList()
                pendingUpdateItems.clear()
                items
            }

        sortedData.removeAll(updateItems)
        sortedData.addAll(updateItems)
        sortedData.addAll(pendingItems)
        if (pendingItems.isNotEmpty() || updateItems.isNotEmpty()) {
            val specialDuration = sortedData.maxOfOrNull {
                if (it.data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL) {
                    (it.data.special?.visibleDurationMs ?: it.data.lifeTime).coerceAtLeast(0L)
                } else {
                    0L
                }
            } ?: 0L
            if (specialDuration != maxSpecialDurationMs) {
                maxSpecialDurationMs = specialDuration
                forceUpdate = true
            }
        }
        val beforeStartCount = pendingItems.count { it.data.position < startTimeMills }
        val currentItems = pendingItems.filter { it.data.position in startTimeMills until endTimeMills }
        currentData.startIndex += beforeStartCount
        currentData.endIndex += (beforeStartCount + currentItems.size)
        val currentUpdateItems = updateItems.filter { it.data.position in startTimeMills until endTimeMills }
        currentData.data.removeAll(currentUpdateItems)
        currentData.data.addAll(currentUpdateItems)
        currentData.data.addAll(currentItems)
        pendingCreateItems.addAll(currentItems)

        if (pendingItems.isNotEmpty() || updateItems.isNotEmpty()) {
            shouldSort = true
        }
        currentData.shouldSort = currentData.shouldSort || currentItems.isNotEmpty() || currentUpdateItems.isNotEmpty()
    }

    private fun createPendingItems() {
        val pendingItems =
            synchronized(this) {
                val items = pendingCreateItems.toList()
                pendingCreateItems.clear()
                items
            }
        pendingItems.forEach(::createItemEntity)
    }

    fun addItems(items: Collection<DanmakuItem>) {
        synchronized(this) {
            pendingAddItems.addAll(items)
        }
    }

    /**
     * 清空所有弹幕数据与活动实体，引擎回到空数据状态。
     *
     * 用于切换视频时丢弃旧数据：数据列表、待处理队列、实体 id 去重集合、
     * 当前时间切片窗口全部复位，并移除本系统创建的所有实体。
     * 调用后可直接通过 [addItems] 重新灌入新视频的数据。
     *
     * 注意：[updateData] 等价于增量追加而非整体替换，旧数据必须通过本方法清除。
     */
    fun clearData() {
        // 先移除实体，避免 update 回调访问已被复位的数据结构
        for (entity in getEntities().toList()) {
            engine.removeEntity(entity)
        }
        synchronized(this) {
            pendingAddItems.clear()
            pendingUpdateItems.clear()
            pendingCreateItems.clear()
            sortedData.clear()
            idSet.clear()
            holdingItem?.unhold()
            holdingItem = null
            currentData = Danmakus(Collections.synchronizedList(TreeList()), 0L, 0L, -1, -1)
            startTimeMills = 0L
            endTimeMills = 0L
            shouldSort = false
            maxSpecialDurationMs = 0L
            // 强制下一次 updateEntities 重新切片，避免沿用旧窗口索引
            forceUpdate = true
        }
    }

    fun addItem(item: DanmakuItem) {
        synchronized(this) {
            pendingAddItems.add(item)
        }
    }

    fun updateItem(item: DanmakuItem) {
        synchronized(this) {
            pendingUpdateItems.add(item)
        }
    }

    private fun sort() {
        if (shouldSort) {
            synchronized(this) {
                sortedData.sortWith(comparator)
            }
            shouldSort = false
        }
        if (currentData.shouldSort) {
            synchronized(this) {
                currentData.data.sortWith(comparator)
            }
            currentData.shouldSort = false
        }
    }

    override fun onDataAdded(additionalItems: List<DanmakuItem>) {
        addItems(additionalItems)
    }

    override fun onDataRemoved(removalItems: List<DanmakuItem>) {
    }

}

internal class DanmakuItemComparator : Comparator<DanmakuItem> {
    override fun compare(
        o1: DanmakuItem,
        o2: DanmakuItem,
    ): Int = o1.compareTo(o2)
}

private class Danmakus(
    val data: MutableList<DanmakuItem>,
    val startTimeMills: Long,
    val endTimeMills: Long,
    var startIndex: Int,
    var endIndex: Int,
    var shouldSort: Boolean = false,
)
