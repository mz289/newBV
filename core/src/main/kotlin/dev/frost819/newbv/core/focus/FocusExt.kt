package dev.frost819.newbv.core.focus

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import dev.frost819.newbv.core.interaction.InputMethod
import dev.frost819.newbv.core.interaction.LocalInteractionTracker
import dev.frost819.newbv.core.theme.LocalFocusOutlineColor

/**
 * 圆角 TV 控件的外侧焦点描边：2dp 线宽，内缘紧贴控件边缘。
 *
 * TV Border 的正 inset 向外扩展；描边中心外移 1dp 后，其内缘与控件边缘重合，外沿最多伸出 2dp。
 * 调用方需预留至少 2dp 绘制空间，避免 Lazy 容器裁切。
 *
 * 颜色取自 [LocalFocusOutlineColor]（与品牌色解耦的中性高对比色）。
 *
 * @param cornerRadius 控件自身的圆角半径，描边圆角同步向外扩展。
 */
@Composable
@ReadOnlyComposable
fun outerFocusBorder(cornerRadius: Dp = 8.dp): Border =
    Border(
        border = BorderStroke(2.dp, LocalFocusOutlineColor.current),
        inset = 1.dp,
        shape = RoundedCornerShape(cornerRadius + 1.dp),
    )

/**
 * 为不提供 TV Border 参数的导航控件显示统一外框：2dp 描边内缘紧贴控件边缘。
 * modifier 自带 6dp padding 作绘制余量兼布局间距；应放在背景裁切修饰符之前；
 * 不会添加点击或焦点节点，沿用原控件的语义和导航。
 *
 * 聚焦时描边带辉光（两层低透明度外扩描边）并 ~150ms 渐入，
 * 颜色取自 [LocalFocusOutlineColor]；同时挂载撞墙抖动（[focusShakeTarget]）。
 */
fun Modifier.controlFocusOutline(): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        val color = LocalFocusOutlineColor.current
        val outlineAlpha by animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(durationMillis = 150),
            label = "control-focus-outline-alpha",
        )
        focusShakeTarget()
            .padding(6.dp)
            .onFocusChanged { focused = it.hasFocus }
            .drawWithContent {
                drawContent()
                if (outlineAlpha > 0f) {
                    inset(-1.dp.toPx()) {
                        // 辉光：两层低透明度描边向外扩展，叠出柔和光晕
                        drawRoundRect(
                            color.copy(alpha = 0.10f * outlineAlpha),
                            cornerRadius = CornerRadius(13.dp.toPx()),
                            style = Stroke(6.dp.toPx()),
                        )
                        drawRoundRect(
                            color.copy(alpha = 0.22f * outlineAlpha),
                            cornerRadius = CornerRadius(11.dp.toPx()),
                            style = Stroke(4.dp.toPx()),
                        )
                        drawRoundRect(
                            color.copy(alpha = outlineAlpha),
                            cornerRadius = CornerRadius(9.dp.toPx()),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
    }

/**
 * 获取焦点时显示边框。
 *
 * 仅在 [InputMethod.DPad] 模式下显示焦点边框，
 * 触屏模式下隐藏（触屏点击自带视觉反馈，无需额外焦点提示）。
 *
 * 当 [LocalInteractionTracker] 未注入时，始终显示边框（兼容未接入 tracker 的场景）。
 *
 * @param shape 边框形状，默认 [ShapeDefaults.Large]。
 * @param animate 是否启用呼吸动画。
 * @param color 边框颜色，默认使用焦点描边色（[LocalFocusOutlineColor]，与品牌色解耦）。
 * @param width 边框宽度，默认 3dp。
 */
fun Modifier.focusedBorder(
    shape: Shape = ShapeDefaults.Large,
    animate: Boolean = false,
    color: Color? = null,
    width: Dp = 3.dp,
): Modifier =
    composed {
        val tracker = LocalInteractionTracker.current
        val resolvedColor = color ?: LocalFocusOutlineColor.current
        var hasFocus by remember { mutableStateOf(false) }

        val showBorder =
            if (tracker != null) {
                val method by tracker.inputMethod.collectAsState()
                hasFocus && method == InputMethod.DPad
            } else {
                hasFocus
            }

        val infiniteTransition = rememberInfiniteTransition(label = "focused-border-transition")
        val animateColor by infiniteTransition.animateColor(
            initialValue = resolvedColor.copy(alpha = 1f),
            targetValue = resolvedColor.copy(alpha = 0.1f),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focused-border-color",
        )
        val borderColor =
            if (showBorder) {
                if (animate) animateColor else resolvedColor
            } else {
                Color.Transparent
            }

        onFocusChanged { hasFocus = it.hasFocus }
            .border(width = width, color = borderColor, shape = shape)
    }


