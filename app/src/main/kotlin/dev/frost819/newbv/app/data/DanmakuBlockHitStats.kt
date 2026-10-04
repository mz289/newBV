package dev.frost819.newbv.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 弹幕屏蔽命中统计（会话级，不持久化）。
 *
 * [DanmakuBlockFilter] 在引擎线程命中规则时经 [record] 上报
 * （key 为 [dev.frost819.newbv.danmaku.filter.DanmakuBlockFilter.ruleKey] 生成的稳定 key），
 * TV 屏蔽面板订阅 [totalHits] 显示会话累计，手机网页管理端经 [snapshot]
 * 拉取每规则命中数。切换视频时由 [DanmakuViewModel] 调用 [reset] 清零。
 *
 * 计数走 [ConcurrentHashMap.merge] 原子操作，上报路径无锁、轻量，
 * 适配引擎渲染线程的高频调用。
 */
object DanmakuBlockHitStats {
    private val ruleCounts = ConcurrentHashMap<String, Int>()

    private val _totalHits = MutableStateFlow(0)
    val totalHits: StateFlow<Int> = _totalHits.asStateFlow()

    /** 记录一次规则命中（引擎线程调用）。 */
    fun record(ruleKey: String) {
        ruleCounts.merge(ruleKey, 1, Int::plus)
        _totalHits.value = _totalHits.value + 1
    }

    /** 当前每规则命中快照（key 为 ruleKey）。 */
    fun snapshot(): Map<String, Int> = HashMap(ruleCounts)

    /** 清零（切换视频时调用）。 */
    fun reset() {
        ruleCounts.clear()
        _totalHits.value = 0
    }
}
