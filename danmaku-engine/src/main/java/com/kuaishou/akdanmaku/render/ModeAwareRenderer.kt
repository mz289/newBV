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

package com.kuaishou.akdanmaku.render

import com.kuaishou.akdanmaku.data.DanmakuItem
import com.kuaishou.akdanmaku.data.DanmakuItemData

/**
 * 按弹幕模式分发渲染器的默认渲染器：mode 7 高级弹幕走 [SpecialDanmakuRenderer]，
 * 其余模式保持按 danmakuStyle 分发的 [SimpleRenderer] 行为。
 */
open class ModeAwareRenderer(
    defaultRenderer: DanmakuRenderer = SimpleRenderer(),
) : TypedDanmakuRenderer(
        defaultRenderer,
        DanmakuItemData.DANMAKU_MODE_SPECIAL to SpecialDanmakuRenderer(),
    ) {
    override fun getDanmakuType(item: DanmakuItem): Int =
        if (item.data.mode == DanmakuItemData.DANMAKU_MODE_SPECIAL) {
            item.data.mode
        } else {
            item.data.danmakuStyle
        }
}
