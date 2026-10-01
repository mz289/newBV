package dev.frost819.newbv.core.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin

/** 撞墙抖动的方向。 */
enum class DpadDirection {
    Left,
    Right,
    Up,
    Down,
}

/**
 * 撞墙抖动控制器（P2-1，参照 wiliwili 的焦点边界抖动）。
 *
 * 信号链路：
 * 1. Activity 捕获"焦点移动失败"的方向键
 *    （Compose 焦点搜索与所有节点 handler 都未消费该按键，即焦点已到边界）；
 * 2. [bump] 广播方向；
 * 3. 所有 [Modifier.focusShakeTarget] 中当前持有焦点的元素播放一次方向性抖动。
 *
 * 广播用 [MutableSharedFlow] 承载，无订阅者时事件丢弃，无需清理。
 */
object FocusShakeController {
    internal val bumps = MutableSharedFlow<DpadDirection>(extraBufferCapacity = 16)

    /** 报告一次撞墙事件。 */
    fun bump(direction: DpadDirection) {
        bumps.tryEmit(direction)
    }
}

/**
 * 焦点元素挂载：当前元素持有焦点时，收到撞墙广播播放一次方向性抖动。
 *
 * 已内置在 [controlFocusOutline] / [focusedBorder] 中；
 * 使用 TV Material 卡片等自带焦点样式的组件需显式挂载（如 SmallVideoCard）。
 *
 * 抖动表现：沿按键方向往复摆动一次（约 4dp，220ms 衰减），与 wiliwili 的边界抖动一致。
 */
fun Modifier.focusShakeTarget(controller: FocusShakeController = FocusShakeController): Modifier =
    composed {
        var hasFocus by remember { mutableStateOf(false) }
        var direction by remember { mutableStateOf(DpadDirection.Left) }
        // decay 从 1 衰减到 0，位移 = sin(decay * 2π) * amplitude * decay：
        // 先正向摆出、回摆、再小幅回正，随 decay 收敛到 0。
        val decay = remember { Animatable(0f) }
        val density = LocalDensity.current
        val amplitudePx = with(density) { 4.dp.toPx() }

        LaunchedEffect(controller) {
            controller.bumps.collectLatest { dir ->
                if (!hasFocus) return@collectLatest
                direction = dir
                decay.snapTo(1f)
                decay.animateTo(0f, tween(durationMillis = 220))
            }
        }

        this
            .onFocusChanged { hasFocus = it.hasFocus }
            .graphicsLayer {
                val d = decay.value
                if (d <= 0f) return@graphicsLayer
                // 取负号：decay 从 1 → 0 时 sin(d·2π) 先负后正，
                // 取反后第一下摆向按键方向（如按"右"撞墙先向右探）。
                val offset = (-sin(d * 2.0 * PI) * d * amplitudePx).toFloat()
                translationX =
                    when (direction) {
                        DpadDirection.Left -> -offset
                        DpadDirection.Right -> offset
                        else -> 0f
                    }
                translationY =
                    when (direction) {
                        DpadDirection.Up -> -offset
                        DpadDirection.Down -> offset
                        else -> 0f
                    }
            }
    }
