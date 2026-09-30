package dev.frost819.newbv.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 强调色预设（自定义强调色，参照 wiliwili 的 custom_theme_color 思路）。
 *
 * 只预设基础色相；主色的深浅变体与容器色由 [resolve] 按混合比例派生，
 * 保证任意强调色下深浅两套主题的对比度都成立。
 * 默认 [Brand] 直接使用 [BVColors] 中手工调校的品牌色，不做派生。
 */
enum class AccentColor(
    val argb: Long,
    val displayName: String,
) {
    /** 品牌蓝紫（默认）。 */
    Brand(0xFF7773AD, "品牌蓝紫"),

    /** B 站粉。 */
    BiliPink(0xFFFB7299, "B站粉"),

    /** 亮粉。 */
    BrightPink(0xFFFF6699, "亮粉"),

    /** 青绿。 */
    Teal(0xFF3F9C93, "青绿"),

    /** 晴蓝。 */
    SkyBlue(0xFF4C7DD8, "晴蓝"),

    /** 暖橙。 */
    WarmOrange(0xFFD97C39, "暖橙"),
    ;

    /** 从持久化名称安全解析，未知值回退 [Brand]。 */
    companion object {
        fun fromName(name: String?): AccentColor = entries.find { it.name == name } ?: Brand
    }
}

/** 一套强调色在深浅两套主题下的主色 / 容器色 / 边框色。 */
data class AccentScheme(
    val primaryDark: Color,
    val containerDark: Color,
    val borderDark: Color,
    val primaryLight: Color,
    val containerLight: Color,
    val borderLight: Color,
)

/**
 * 将强调色解析为深浅两套主题的实际用色。
 *
 * [AccentColor.Brand] 返回 [BVColors] 中的手工调校值，保持默认观感不变；
 * 其余预设按比例派生：深色主色 = 原色、容器 = 混黑 22%、边框 = 混白 30%；
 * 浅色主色 = 混黑 15%、容器 = 混白 35%、边框 = 混黑 15%。
 */
fun AccentColor.resolve(): AccentScheme =
    when (this) {
        AccentColor.Brand ->
            AccentScheme(
                primaryDark = BVColors.Primary,
                containerDark = BVColors.PrimaryStrong,
                borderDark = BVColors.PrimaryLight,
                primaryLight = BVColors.PrimaryStrong,
                containerLight = BVColors.PrimaryLight,
                borderLight = BVColors.PrimaryStrong,
            )
        else -> {
            val base = Color(argb)
            AccentScheme(
                primaryDark = base,
                containerDark = lerp(base, Color.Black, 0.22f),
                borderDark = lerp(base, Color.White, 0.30f),
                primaryLight = lerp(base, Color.Black, 0.15f),
                containerLight = lerp(base, Color.White, 0.35f),
                borderLight = lerp(base, Color.Black, 0.15f),
            )
        }
    }
