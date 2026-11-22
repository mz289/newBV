package dev.frost819.newbv.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

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
        if (restoring) return
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
     * @param onRestoreFailed 重试用尽（key 已失效、目标元素不存在）后的兜底，
     *   如 MainScreen 回退到聚焦顶部 Tab。
     */
    @Composable
    fun RestoreFocus(onRestoreFailed: (() -> Unit)? = null) {
        val capturedKey = remember { savedKey.value }
        LaunchedEffect(capturedKey) {
            if (capturedKey.isEmpty()) return@LaunchedEffect
            restoring = true
            try {
                repeat(RESTORE_MAX_FRAMES) {
                    val requester = requesters[capturedKey]
                    if (requester != null && requester.focusWhenReady(maxFrames = 2)) {
                        // 回写捕获值：抹掉恢复窗口开启前框架默认聚焦写下的 key，
                        // 保证下一次「离开→返回」仍恢复到本次目标
                        savedKey.value = capturedKey
                        return@LaunchedEffect
                    }
                    withFrameNanos { }
                }
                onRestoreFailed?.invoke()
            } finally {
                restoring = false
            }
        }
    }
}

/** 恢复窗口上限（帧）：覆盖「壳帧 + Lazy 项注册 + 焦点请求」的正常时序，过期即兜底。 */
private const val RESTORE_MAX_FRAMES = 12

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
