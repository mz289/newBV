package dev.frost819.newbv.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import dev.frost819.newbv.core.log.Loggers

/** 焦点恢复链路诊断日志开关（排查「返回后焦点不回原位」用，定位后关闭）。 */
private const val FOCUS_RESTORE_DEBUG = true
private val logger = Loggers.get("FocusSaver")

/**
 * 焦点恢复器。
 *
 * 保存最后获得焦点的元素 key，在 composition 恢复后（如从详情页返回）
 * 通过 [FocusRequester] 重新请求焦点到对应元素。
 *
 * 统一使用 String key，适用于列表/网格（key = `"item_$index"`）
 * 和混合布局（key = `"cover"`、`"like"` 等）。
 *
 * 用法：
 * ```
 * val focusSaver = rememberFocusSaver()
 * focusSaver.RestoreFocus()
 * // 在可聚焦元素上：
 * Card(
 *     modifier = Modifier.focusSaverItem(focusSaver, "cover"),
 * )
 * ```
 */
class FocusSaver(
    private val savedKey: MutableState<String>,
) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /**
     * 恢复窗口标志。窗口内 [saveFocusedKey] 丢弃写入：返回导航后框架会把焦点
     * 落到首个可聚焦控件（如左侧栏头像），其 onFocusChanged 若在此刻覆写 key，
     * 待恢复 key 就被污染，RestoreFocus 会把焦点恢复到那个默认控件上。
     */
    private var restoring = false

    /**
     * 获取指定 key 的 [FocusRequester]。
     * 每个 key 独立一个 requester，在组合恢复后仍有效。
     */
    fun focusRequesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * 保存当前聚焦的 key。
     *
     * 恢复窗口（[RestoreFocus] 进行中）内丢弃写入：此时框架默认聚焦产生的
     * onFocusChanged 不能污染待恢复 key。
     */
    fun saveFocusedKey(key: String) {
        if (restoring) {
            if (FOCUS_RESTORE_DEBUG && key != savedKey.value) {
                logger.info { "[SAVE] suppressed key='$key' (restoring, saved='${savedKey.value}')" }
            }
            return
        }
        if (FOCUS_RESTORE_DEBUG && key != savedKey.value) {
            logger.info { "[SAVE] key='$key'" }
        }
        savedKey.value = key
    }

    /**
     * 获取当前保存的 key（可能为空）。
     * 用于首次进入页面时判断是否需要手动聚焦默认元素。
     */
    fun savedKeyValue(): String = savedKey.value

    /**
     * 清除保存的焦点 key。
     *
     * 用于首次进入页面时丢弃"启动阶段系统自动聚焦"产生的 key，
     * 避免 [RestoreFocus] 将焦点错误恢复到该元素（如左侧栏头像）。
     */
    fun clearFocusedKey() {
        savedKey.value = ""
    }

    /**
     * 恢复焦点到之前保存的 key。
     *
     * 应在 composition 恢复后调用（通常在屏幕顶层调一次）。
     *
     * 时序：目标元素的 [FocusRequester] 由 Lazy 容器在测量期组合时才注册，
     * 晚于本 effect 首跑；壳层冷装载（KeptTabPage deferContent）还会把内容
     * 再推迟一帧。所以这里逐帧等待注册 + 请求，而不是一次性查表——
     * 首跑查到 null 就静默放弃会让焦点落回框架默认控件（如左侧栏头像）。
     *
     * 返回导航后的框架默认聚焦（焦点落到首个可聚焦控件）可能在恢复窗口开启
     * 之前就覆写 key，因此捕获 key 在挂载首帧冻结、每个实例只恢复一次：
     * 后续 key 变化（默认聚焦、用户移动）不再触发恢复——那是新交互，
     * 不是「回到原位」。恢复成功后回写捕获值，抹掉窗口前落下的污染。
     *
     * 等待终止于**状态**而非帧数预算：
     * - 目标注册并成功聚焦 → 完成（回写捕获值）；
     * - [contentSteady] 声明内容到达稳态（不再会有新元素注册）且目标仍未注册
     *   → 目标已不存在，走 [onRestoreFailed] 兜底；
     * - savedKey 被真实焦点移动改写（≠ 挂载帧基线）→ 用户已接管，立刻让位，
     *   不恢复也不兜底。
     * 唯一的帧数宽限是 [RESTORE_SETTLE_FRAMES]：静默期上限，覆盖「框架默认聚焦
     * 在挂载头几帧落点」这一固定时序；期间写入被拦截。观测到首次写入即提前结束。
     *
     * @param onRestoreFailed 稳态下目标不存在的兜底，如 MainScreen 回退到聚焦顶部 Tab。
     * @param contentSteady 内容稳态判定：false 表示页面仍在骨架/重载（目标可能稍后
     *   注册），恢复持续等待；true 表示内容已稳定。默认恒 true（内容立即可判稳态的
     *   屏幕，如详情页）。数据驱动屏幕经 [LocalContentSettled] 或此参数接入。
     */
    @Composable
    fun RestoreFocus(
        onRestoreFailed: (() -> Unit)? = null,
        contentSteady: () -> Boolean = { true },
    ) {
        val capturedKey = remember { savedKey.value }
        LaunchedEffect(capturedKey) {
            if (FOCUS_RESTORE_DEBUG) {
                logger.info { "[RESTORE] start captured='$capturedKey' current='${savedKey.value}'" }
            }
            if (capturedKey.isEmpty()) return@LaunchedEffect
            try {
                // 挂载帧先行：Lazy 项注册发生在测量期，稳态判断在首帧测量前不可信
                if (restoreAttempt(capturedKey)) return@LaunchedEffect
                withFrameNanos { }

                // 静默期：框架默认聚焦（返回后落首个可聚焦控件）在挂载头几帧写入，
                // 其 onFocusChanged 不代表用户意图，拦截；观测到首次写入即提前结束
                restoring = true
                var settled = false
                var settleFrames = 0
                while (!settled && settleFrames < RESTORE_SETTLE_FRAMES) {
                    if (restoreAttempt(capturedKey)) return@LaunchedEffect
                    if (savedKey.value != capturedKey) {
                        settled = true // 默认聚焦已落点，静默期使命完成
                    } else {
                        withFrameNanos { }
                        settleFrames++
                    }
                }
                restoring = false

                // 稳态驱动等待：无帧数预算，终止于状态
                val baseline = savedKey.value
                while (true) {
                    if (restoreAttempt(capturedKey)) return@LaunchedEffect
                    if (savedKey.value != baseline) {
                        if (FOCUS_RESTORE_DEBUG) {
                            logger.info { "[RESTORE] abandoned key='$capturedKey' userMovedTo='${savedKey.value}'" }
                        }
                        return@LaunchedEffect // 用户接管，让位：不恢复、不兜底
                    }
                    if (contentSteady() && capturedKey !in requesters) {
                        if (FOCUS_RESTORE_DEBUG) {
                            logger.info { "[RESTORE] exhausted key='$capturedKey' registered=${requesters.keys}" }
                        }
                        onRestoreFailed?.invoke()
                        return@LaunchedEffect
                    }
                    withFrameNanos { }
                }
            } finally {
                restoring = false
            }
        }
    }

    /** 单轮恢复：目标已注册则请求焦点，成功后回写捕获 key。 */
    private suspend fun restoreAttempt(key: String, attempt: Int = 0): Boolean {
        val requester = requesters[key] ?: return false
        if (!requester.focusWhenReady(maxFrames = 2)) return false
        // 回写捕获值：抹掉恢复窗口开启前框架默认聚焦写下的 key，
        // 保证下一次「离开→返回」仍恢复到本次目标
        if (FOCUS_RESTORE_DEBUG) {
            logger.info { "[RESTORE] ok key='$key' attempt=$attempt savedWas='${savedKey.value}'" }
        }
        savedKey.value = key
        return true
    }
}

/**
 * 静默期帧数宽限：框架默认聚焦（返回后焦点落首个可聚焦控件）发生在挂载头几帧，
 * 其写入需被拦截。观测到首次写入即提前结束；无默认聚焦时以此上限收尾。
 */
private const val RESTORE_SETTLE_FRAMES = 12

/**
 * 页面内容稳态：当前激活页声明其内容不再会产生新的可聚焦元素
 * （加载完成/失败/空态均算稳态；骨架屏、分页重载中为非稳态）。
 *
 * 数据驱动屏幕在内容壳内写入（仅激活页写），RestoreFocus 所在的屏幕顶层读取——
 * 焦点恢复的等待时长因此由加载实际耗时决定，而非固定帧数预算。
 */
val LocalContentSettled = compositionLocalOf { mutableStateOf(true) }

/**
 * 创建 [FocusSaver]，使用 [rememberSaveable] 持久化焦点 key。
 *
 * 焦点 key 在跨导航（页面移出再恢复 composition）和配置变更（如旋转）后保持。
 */
@Composable
fun rememberFocusSaver(): FocusSaver {
    val savedKey = rememberSaveable { mutableStateOf("") }
    return remember { FocusSaver(savedKey) }
}

// region Modifier 扩展

/**
 * 将 [FocusSaver] 绑定到可聚焦元素。
 *
 * 封装 `.focusRequester()` + `.onFocusChanged()` 两行 boilerplate 为一行。
 * 适用于列表/网格和混合布局中所有可聚焦元素的焦点追踪。
 *
 * 用法：
 * ```
 * SmallVideoCard(
 *     modifier = Modifier.focusSaverItem(focusSaver, "rcmd_$index"),
 *     ...
 * )
 * ```
 *
 * @param focusSaver 焦点恢复器。
 * @param key 元素的唯一标识（如 `"cover"`、`"tag_${tag.id}"`、`"rcmd_$index"`）。
 */
fun Modifier.focusSaverItem(
    focusSaver: FocusSaver,
    key: String,
): Modifier =
    this
        .focusRequester(focusSaver.focusRequesterFor(key))
        .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(key) }

// endregion
