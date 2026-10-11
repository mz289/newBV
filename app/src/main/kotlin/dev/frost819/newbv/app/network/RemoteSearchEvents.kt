package dev.frost819.newbv.app.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 手机远程搜索关键词事件（进程内单例）。
 *
 * 常驻的 [SearchRemoteFeature] 收到手机提交后发布事件，
 * UI 层（MainScreen）订阅并执行与遥控器一致的搜索导航。
 */
object RemoteSearchEvents {
    private val _keywords = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val keywords: SharedFlow<String> = _keywords

    /** 发布关键词（服务器协程调用；缓冲满时丢弃最旧由 UI 侧防抖兜底）。 */
    fun post(keyword: String) {
        _keywords.tryEmit(keyword)
    }
}
