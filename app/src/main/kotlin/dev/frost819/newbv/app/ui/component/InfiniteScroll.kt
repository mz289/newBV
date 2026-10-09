package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 无限滚动触发器。
 *
 * 可见区域最后一项进入距列表末尾 [threshold] 条范围内时调用 [onLoadMore]。
 * 重复触发是安全的：加载方自行以 loading/hasMore 守卫。
 *
 * @param itemCount 当前列表总项数（每次求值取最新值）。
 * @param threshold 距底部多少条时预加载。
 */
@Composable
fun InfiniteScrollEffect(
    state: LazyGridState,
    itemCount: () -> Int,
    threshold: Int = 20,
    onLoadMore: () -> Unit,
) {
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow {
            val count = currentItemCount()
            val index =
                state.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
            index to count
        }.distinctUntilChanged()
            .filter { (index, count) -> count > 0 && index != null && index >= count - threshold }
            .collect { currentOnLoadMore() }
    }
}
