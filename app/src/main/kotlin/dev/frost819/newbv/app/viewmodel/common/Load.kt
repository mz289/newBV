package dev.frost819.newbv.app.viewmodel.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** 列表/详情类加载单次网络请求的默认整体超时（毫秒）。 */
const val LOAD_TIMEOUT_MS = 10_000L

/**
 * 重抛非超时导致的取消（页面销毁、父协程取消等），吞掉它们会破坏结构化并发；
 * 加载超时（[TimeoutCancellationException]）不重抛，继续走错误处理分支。
 */
fun Throwable.rethrowUnlessTimeout() {
    if (this is CancellationException && this !is TimeoutCancellationException) throw this
}
