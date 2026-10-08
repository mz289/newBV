package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import dev.frost819.newbv.core.focus.DpadDirection
import dev.frost819.newbv.core.focus.FocusShakeController
import dev.frost819.newbv.core.focus.isDpadLeft
import dev.frost819.newbv.core.focus.isDpadRight
import dev.frost819.newbv.core.focus.isInitialKeyDown
import dev.frost819.newbv.core.focus.isKeyDown
import kotlin.math.abs

/**
 * 当前 [TvLazyVerticalGrid] 的网格状态，供 item 侧做行锁定判定。
 *
 * 由 [TvLazyVerticalGrid] 提供；item 挂载 [dpadGridRowLock] 后，
 * 左右键在"行内该方向已无元素"时不再放行给焦点系统，
 * 避免几何搜索把焦点跳到其他行的卡片上（视觉上像随机乱跳）。
 */
class LazyGridFocusScope(val state: LazyGridState)

/** 当前最近一级 [TvLazyVerticalGrid] 的网格 scope；网格外为 null，锁定逻辑不生效。 */
val LocalLazyGridFocusScope = staticCompositionLocalOf<LazyGridFocusScope?> { null }

/**
 * 网格 item 的左右键行锁定。
 *
 * 触发条件：按下左右键且同行该方向已无相邻 item（行末/行首的边缘元素）。
 * 此时消费事件不让焦点系统执行几何搜索（默认会命中其他行在该方向上的卡片），
 * 并在初始按下时广播撞墙抖动；长按连发的重复事件只吞掉、不重复抖动。
 * 行内仍有相邻 item，或 item 不在网格内（[index] 为 null / 无网格 scope）时完全放行，行为不变。
 *
 * 上下键不在此处理：网格的纵向移动由焦点系统自然按列寻位，到顶/底后交给既有路径
 * （向上进入顶部 Tab、向下触达页脚）。
 */
fun Modifier.dpadGridRowLock(index: Int?): Modifier =
    composed {
        val scope = LocalLazyGridFocusScope.current
        if (scope == null || index == null) {
            Modifier
        } else {
            onPreviewKeyEvent { event ->
                if (!event.isKeyDown()) return@onPreviewKeyEvent false
                val dir =
                    when {
                        event.isDpadLeft() -> DpadDirection.Left
                        event.isDpadRight() -> DpadDirection.Right
                        else -> return@onPreviewKeyEvent false
                    }
                val info = scope.state.layoutInfo
                val me = info.visibleItemsInfo.firstOrNull { it.index == index }
                    ?: return@onPreviewKeyEvent false
                val hasRowMate =
                    info.visibleItemsInfo.any { mate ->
                        mate.index != me.index &&
                            me.sameRow(mate) &&
                            if (dir == DpadDirection.Right) {
                                mate.offset.x > me.offset.x
                            } else {
                                mate.offset.x < me.offset.x
                            }
                    }
                if (hasRowMate) return@onPreviewKeyEvent false
                if (event.isInitialKeyDown()) {
                    FocusShakeController.bump(dir)
                }
                true
            }
        }
    }

/** 两 item 是否同一行：行心距小于两行高之和的一半（同行的 item 行心重合，相邻行差一个行高加间距）。 */
private fun LazyGridItemInfo.sameRow(other: LazyGridItemInfo): Boolean {
    val center = offset.y + size.height / 2f
    val otherCenter = other.offset.y + other.size.height / 2f
    return abs(otherCenter - center) * 2f < (size.height + other.size.height)
}
