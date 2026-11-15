package dev.frost819.newbv.app.ui.component

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * 逐帧等待后请求焦点，替代固定 `delay(50~100)` 的"盲等组合完成"写法。
 *
 * requestFocus 失败（目标尚未附加到窗口/焦点系统拒绝）时等到下一帧重试：
 * 60Hz 下一帧约 16ms，通常 1~2 帧内成功，比固定 50ms 更快且确定。
 * 焦点请求须在组合上下文之外由协程发起，本函数仅可在 LaunchedEffect 中调用。
 *
 * @param maxFrames 最大重试帧数，超过后做最后一次尝试并返回结果。
 * @return 最终是否成功获得焦点。
 */
suspend fun FocusRequester.focusWhenReady(maxFrames: Int = 6): Boolean {
    repeat(maxFrames) {
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
        withFrameNanos { }
    }
    return runCatching { requestFocus() }.getOrDefault(false)
}
