package dev.frost819.newbv.core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 焦点描边色（P0-3）：与品牌色解耦的中性高对比色。
 *
 * 深色主题近白、浅色主题近黑（见 [BVColors.FocusOutlineDark] / [BVColors.FocusOutlineLight]），
 * 由 [BVTheme] 注入；焦点系统（FocusExt / ControlFocusDefaults / 卡片边框）统一读取，
 * 使"焦点在哪"与"选中了什么"在视觉上是两种语义。
 */
val LocalFocusOutlineColor = staticCompositionLocalOf { Color.White }
