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

import android.graphics.RectF
import android.util.Log
import com.badlogic.gdx.utils.Array
import com.kuaishou.akdanmaku.data.DanmakuItemData.Companion.DANMAKU_ITEM_DATA_EMPTY
import com.kuaishou.akdanmaku.data.state.DrawState
import com.kuaishou.akdanmaku.data.state.HoldState
import com.kuaishou.akdanmaku.ecs.DanmakuContext.Companion.NONE_CONTEXT
import com.kuaishou.akdanmaku.ecs.DanmakuEngine
import com.kuaishou.akdanmaku.ecs.component.action.Action
import com.kuaishou.akdanmaku.ecs.component.action.SpecialDanmakuAction
import com.kuaishou.akdanmaku.ui.DanmakuPlayer

/**
 * 弹幕 Item，包括弹幕数据，以及其他绘制、排版等所需要的状态集合
 *
 * @author Xana
 * @since 2021-06-29
 */
open class DanmakuItem(
    var data: DanmakuItemData,
    player: DanmakuPlayer? = null,
) : Comparable<DanmakuItem> {
    var state = ItemState.Uninitialized
    var duration: Long = 0

    internal var timer = player?.engine?.timer ?: NONE_CONTEXT.timer

    internal val actions = Array<Action>(0)

    private val holdState = HoldState(timer)
    internal val drawState = DrawState()
    internal var shownGeneration = -1

    val rect: RectF
        get() = drawState.rect

    val isHolding: Boolean
        get() = holdState.isHolding
    val timePosition: Long
        get() = data.position + holdState.holdTime

    val isLate: Boolean
        get() = timePosition > timer.currentTimeMs
    val isTimeout: Boolean
        get() = timer.currentTimeMs - timePosition > duration
    val isOutside: Boolean
        get() = isLate || isTimeout

    fun hold() = holdState.hold()

    fun unhold() = holdState.unhold()

    fun reset() {
        Log.d(DanmakuEngine.TAG, "[Item] Reset $this")
        state = ItemState.Uninitialized
        rect.setEmpty()
        holdState.reset()
        drawState.reset()
        // 对象池复用：清掉上一条弹幕的动作、烘培代次与已展示代次，避免旧状态挂到新数据上
        actions.clear()
        specialBakedGeneration = -1
        shownGeneration = -1
    }

    override fun compareTo(other: DanmakuItem): Int = data.compareTo(other.data)

    fun recycle() {
        data = DANMAKU_ITEM_DATA_EMPTY
        timer = NONE_CONTEXT.timer
        reset()
    }

    fun cacheRecycle() {
        drawState.recycle()
        if (state > ItemState.Measured) {
            state = ItemState.Measured
        }
    }

    fun addAction(vararg action: Action) {
        actions.addAll(*action)
    }

    /**
     * mode 7 高级弹幕动作的最后烘培代次（跟随 config.measureGeneration），
     * 显示尺寸或样式变化后据此重新烘培。
     */
    internal var specialBakedGeneration: Int = -1

    /**
     * 按当前显示尺寸把高级弹幕规格烘培为动作序列（可重复调用以覆盖）。
     *
     * 宽高为 0（View 未完成布局）时按官方舞台基准（861×572）处理，等尺寸变化后由
     * DataSystem 依据代次重新烘培。
     */
    internal fun rebakeSpecialActions(
        displayWidth: Int,
        displayHeight: Int,
        generation: Int,
    ) {
        val spec = data.special ?: return
        actions.clear()
        actions.add(
            SpecialDanmakuAction(
                spec,
                displayWidth.takeIf { it > 0 }?.toFloat() ?: SpecialDanmakuSpec.STAGE_WIDTH,
                displayHeight.takeIf { it > 0 }?.toFloat() ?: SpecialDanmakuSpec.STAGE_HEIGHT,
            ),
        )
        specialBakedGeneration = generation
    }

    companion object {
        val DANMAKU_ITEM_EMPTY = DanmakuItem(DANMAKU_ITEM_DATA_EMPTY)
    }
}
