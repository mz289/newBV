package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridScopeMarker
import androidx.compose.runtime.Composable

/**
 * 薄适配层：仅累计全局索引并包装项目；布局、key、span 和复用仍由原生网格管理。
 * Compose 1.8 的 LazyGridScope 是 sealed 接口，无法通过接口委托装饰，因此保留此接入层。
 * 不在页面计算边界，避免标题、多个 items 区段和跨列项目使局部索引失效。
 */
@LazyGridScopeMarker
class TvLazyGridScope internal constructor(
    private val delegate: LazyGridScope,
    private val navigation: GridFocusNavigation,
) {
    private var nextIndex = 0

    fun item(
        key: Any? = null,
        span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
        contentType: Any? = null,
        content: @Composable LazyGridItemScope.() -> Unit,
    ) {
        val index = nextIndex++
        val routing = navigation
        delegate.item(key, span, contentType) {
            routing.Item(index) { content() }
        }
    }

    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        span: (LazyGridItemSpanScope.(Int) -> GridItemSpan)? = null,
        contentType: (Int) -> Any? = { null },
        itemContent: @Composable LazyGridItemScope.(Int) -> Unit,
    ) {
        val start = nextIndex
        nextIndex += count
        val routing = navigation
        delegate.items(count, key, span, contentType) { index ->
            routing.Item(start + index) { itemContent(index) }
        }
    }

    fun <T> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        span: (LazyGridItemSpanScope.(T) -> GridItemSpan)? = null,
        contentType: (T) -> Any? = { null },
        itemContent: @Composable LazyGridItemScope.(T) -> Unit,
    ) {
        items(
            count = items.size,
            key = key?.let { factory -> { index -> factory(items[index]) } },
            span = span?.let { factory -> { index -> factory(items[index]) } },
            contentType = { contentType(items[it]) },
        ) { itemContent(items[it]) }
    }

    fun <T> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)? = null,
        span: (LazyGridItemSpanScope.(Int, T) -> GridItemSpan)? = null,
        contentType: (Int, T) -> Any? = { _, _ -> null },
        itemContent: @Composable LazyGridItemScope.(Int, T) -> Unit,
    ) {
        items(
            count = items.size,
            key = key?.let { factory -> { index -> factory(index, items[index]) } },
            span = span?.let { factory -> { index -> factory(index, items[index]) } },
            contentType = { contentType(it, items[it]) },
        ) { itemContent(it, items[it]) }
    }

    fun stickyHeader(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyGridItemScope.(Int) -> Unit,
    ) {
        val index = nextIndex++
        val routing = navigation
        delegate.stickyHeader(key, contentType) { globalIndex ->
            routing.Item(index) { content(globalIndex) }
        }
    }
}
