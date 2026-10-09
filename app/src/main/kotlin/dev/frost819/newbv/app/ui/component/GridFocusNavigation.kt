package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import dev.frost819.newbv.core.focus.DpadDirection
import dev.frost819.newbv.core.focus.FocusShakeController

/** 主页面网格的左侧出口；独立页面和弹窗不设置出口。 */
internal val LocalGridLeftExit = staticCompositionLocalOf<FocusRequester?> { null }

/** 使用网格全局索引和实际行号路由，不依赖页面列表索引或卡片尺寸。 */
internal class GridFocusNavigation(
    private val state: LazyGridState,
    private val leftExit: () -> FocusRequester?,
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()
    var initialKeyDown = false

    private fun moveHorizontally(
        index: Int,
        direction: FocusDirection,
    ): Boolean {
        val visible = state.layoutInfo.visibleItemsInfo
        val source = visible.firstOrNull { it.index == index } ?: return false
        if (source.row < 0) return false
        val toRight = direction == FocusDirection.Right
        val candidates =
            visible
                .asSequence()
                .filter {
                    it.row == source.row &&
                        if (toRight) it.offset.x > source.offset.x else it.offset.x < source.offset.x
                }.sortedBy { if (toRight) it.offset.x else -it.offset.x }
        for (candidate in candidates) {
            val target = requesters[candidate.index] ?: continue
            if (target.requestFocus(direction)) return true
        }
        if (!toRight && runCatching { leftExit()?.requestFocus(direction) == true }.getOrDefault(false)) {
            return true
        }
        if (initialKeyDown) {
            FocusShakeController.bump(if (toRight) DpadDirection.Right else DpadDirection.Left)
        }
        return false
    }

    /** 每项独立成组，内部按钮正常导航；注册和注销与项目的组合生命周期绑定。 */
    @Composable
    fun Item(
        index: Int,
        content: @Composable () -> Unit,
    ) {
        val requester = remember { FocusRequester() }
        DisposableEffect(this, index, requester) {
            requesters[index] = requester
            onDispose {
                if (requesters[index] === requester) requesters.remove(index)
            }
        }
        Box(
            modifier =
                Modifier
                    .focusRequester(requester)
                    .focusProperties {
                        // focusRestorer 也设置 onExit，直接叠加会使其中一组回调被覆盖，因此统一管理。
                        onEnter = { requester.restoreFocusedChild() }
                        onExit = {
                            val direction = requestedFocusDirection
                            if (direction == FocusDirection.Left || direction == FocusDirection.Right) {
                                requester.saveFocusedChild()
                                if (!moveHorizontally(index, direction)) cancelFocusChange()
                            }
                        }
                    }.focusGroup(),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
