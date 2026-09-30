package dev.frost819.newbv.core.theme

import androidx.compose.ui.graphics.Color

/**
 * new BV 品牌色板。
 *
 * 以低饱和蓝紫与青绿色为核心，延伸出辅助色与中性色。
 * 所有颜色以 `Color` 表示，供 [BVTheme] 构建 colorScheme。
 *
 * 强调色语义（P0-1）：全应用的选中/激活/强调只使用主品牌蓝紫一个色相，
 * 青绿（Secondary）降级为纯功能色（如卡片已播进度条），不再承担"选中"语义。
 */
object BVColors {
    /** 低饱和蓝紫，作为 new BV 的主品牌色（深色主题主色）。 */
    val Primary = Color(0xFF7773AD)

    /** 深色背景下使用的主色变体（品牌色加深，浅色主题主色/深色主题容器色）。 */
    val PrimaryStrong = Color(0xFF5E5A8B)

    /** 浅色背景下使用的主色变体（品牌色提亮，深色主题边框色）。 */
    val PrimaryLight = Color(0xFF9995CF)

    /** 低饱和青绿，仅作功能色（已播进度等功能性标识），不作选中/强调色。 */
    val Secondary = Color(0xFF5B9B94)

    /** 浅色背景下使用的中性绿色变体，作为强调色文字/图标时保证对比度。 */
    val SecondaryStrong = Color(0xFF3F7A73)

    /**
     * 深色主题中性色（P0-2）：背景提亮为深灰而非近黑，
     * 背景→面板→控件三级灰阶逐级拉开，保证暗场景下分区可辨。
     */
    val DarkBackground = Color(0xFF1E1F23)
    val DarkSurface = Color(0xFF27282D)
    val DarkSurfaceVariant = Color(0xFF36383D)
    val DarkOnBackground = Color(0xFFF2F2F2)
    val DarkOnSurface = Color(0xFFF2F2F2)

    /** 次要文字统一灰：深浅主题共用一个中性灰（参照 wiliwili 的做法）。 */
    val DarkOnSurfaceVariant = Color(0xFF9499A0)
    val DarkBorder = PrimaryLight

    /** 浅色主题中性色。 */
    val LightBackground = Color(0xFFF5F7FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE8ECF5)
    val LightOnBackground = Color(0xFF172033)
    val LightOnSurface = Color(0xFF172033)
    val LightOnSurfaceVariant = Color(0xFF9499A0)
    val LightBorder = PrimaryStrong

    /**
     * 焦点描边色（P0-3）：与品牌色解耦的中性高对比色，
     * 深色主题近白、浅色主题近黑，保证任何底色下焦点位置一眼可辨。
     * 通过 [LocalFocusOutlineColor] 注入主题。
     */
    val FocusOutlineDark = Color(0xFFF2F4F8)
    val FocusOutlineLight = Color(0xFF23272E)
}
