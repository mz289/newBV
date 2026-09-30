package dev.frost819.newbv.core.focus

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SuggestionChipDefaults

/** 全应用操作控件的焦点样式：2dp 外描边、内缘紧贴控件边缘，聚焦时保留普通与选中底色；列表类控件例外，聚焦改用底色填充。 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
object ControlFocusDefaults {
    /** 标准按钮、图标按钮和标签共用的圆角。 */
    val shape = RoundedCornerShape(8.dp)

    /** 可点击 Surface 的普通、聚焦和按下配色，禁用态使用组件自身的降透明度规则。 */
    @Composable
    fun surfaceColors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
    ) = ClickableSurfaceDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = containerColor,
        focusedContentColor = contentColor,
        pressedContainerColor = containerColor,
        pressedContentColor = contentColor,
    )

    /** 标准按钮配色，支持调用方保留主要操作或警告操作的底色。 */
    @Composable
    fun buttonColors(
        containerColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    ) = ButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = containerColor,
        focusedContentColor = contentColor,
        pressedContainerColor = containerColor,
        pressedContentColor = contentColor,
    )

    /** 普通按钮和图标按钮的聚焦边框；禁用按钮不显示焦点提示。 */
    @Composable
    fun buttonBorder() =
        ButtonDefaults.border(
            focusedBorder = outerFocusBorder(),
            focusedDisabledBorder = Border.None,
        )

    /** 列表项焦点由底色填充表达，所有状态不再绘制描边（覆盖组件默认边框）。 */
    @Composable
    fun listBorder() =
        ListItemDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            focusedSelectedBorder = Border.None,
            pressedSelectedBorder = Border.None,
            focusedDisabledBorder = Border.None,
        )

    /**
     * 列表项配色：聚焦与按下使用选中态的底色填充表达焦点，文字随之切换。
     *
     * 设置列表等场景的焦点不用描边，而是与左侧导航一致的改变背景方式。
     */
    @Composable
    fun listColors() =
        ListItemDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            pressedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            pressedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            pressedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

    /** 筛选标签使用选中底色表达筛选条件，焦点只增加外框。 */
    @Composable
    fun filterColors() =
        FilterChipDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            pressedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            pressedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        )

    /** 筛选标签的所有聚焦状态使用统一外框。 */
    @Composable
    fun filterBorder() =
        FilterChipDefaults.border(
            border = Border.None,
            selectedBorder = Border.None,
            pressedSelectedBorder = outerFocusBorder(),
            focusedBorder = outerFocusBorder(),
            focusedSelectedBorder = outerFocusBorder(),
            focusedDisabledBorder = Border.None,
        )

    /** 普通标签在聚焦和按下时保持文字、底色不变。 */
    @Composable
    fun suggestionColors() =
        SuggestionChipDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        )

    /** 普通标签的焦点边框。 */
    @Composable
    fun suggestionBorder() =
        SuggestionChipDefaults.border(
            border = Border.None,
            focusedBorder = outerFocusBorder(),
            focusedDisabledBorder = Border.None,
        )
}
