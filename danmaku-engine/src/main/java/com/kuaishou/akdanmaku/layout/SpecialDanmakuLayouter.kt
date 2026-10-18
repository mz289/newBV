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

package com.kuaishou.akdanmaku.layout

import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.ui.DanmakuDisplayer

/**
 * mode 7 高级弹幕排版器：不做碰撞与排队，可见窗口内直接可见。
 *
 * 高级弹幕的位置由 [com.kuaishou.akdanmaku.ecs.component.action.SpecialDanmakuAction]
 * 经变换矩阵给出，布局仅负责可见性开关（入场前不可见，避免动作未启动时被画到原点）。
 */
open class SpecialDanmakuLayouter : DanmakuLayouter {
    override fun preLayout(
        item: DanmakuItem,
        currentTimeMills: Long,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ): Boolean {
        // 可见窗口为 (0, duration)；duration = 官方 `max(displayDuration, animDelay + animDuration)`
        // （见 DataSystem）。官方「n > animDelay 不创建视图」的创建窗口在实体创建时判定，
        // 这里只负责按时间开关可见性（入场前不可见，避免动作未启动时被画到原点）。
        val elapsed = currentTimeMills - item.timePosition
        return elapsed > 0 && elapsed < item.duration
    }

    override fun layout(
        item: DanmakuItem,
        currentTimeMills: Long,
        displayer: DanmakuDisplayer,
        config: DanmakuConfig,
    ) {
        val drawState = item.drawState
        // 位置走 ActionComponent 变换矩阵，此处保持原点即可
        drawState.positionX = 0f
        drawState.positionY = 0f
        drawState.visibility = true
    }

    override fun updateScreenPart(
        startTop: Int,
        endTop: Int,
    ) {
    }

    override fun clear() {
    }

    override fun remove(item: DanmakuItem) {
    }
}
