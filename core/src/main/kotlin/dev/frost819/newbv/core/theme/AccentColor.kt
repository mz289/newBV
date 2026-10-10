package dev.frost819.newbv.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 强调色预设（自定义强调色，参照 wiliwili 的 custom_theme_color 思路）。
 *
 * 只预设基础色相；主色的深浅变体与容器色由 [resolve] 按混合比例派生，
 * 保证任意强调色下深浅两套主题的对比度都成立。
 * 默认使用 [BiliPink]。
 */
enum class AccentColor(
    val argb: Long,
    val displayName: String,
) {
    /** B 站粉（默认）。 */
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

    /** 从持久化名称安全解析，未知值使用默认色。 */
    companion object {
        fun fromName(name: String?): AccentColor = entries.find { it.name == name } ?: BiliPink
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
 * 预设按比例派生：深色主色 = 原色、容器 = 混黑 22%、边框 = 混白 30%；
 * 浅色主色 = 混黑 15%、容器 = 混白 35%、边框 = 混黑 15%。
 */
fun AccentColor.resolve(): AccentScheme {
    val base = Color(argb)
    return AccentScheme(
        primaryDark = base,
        containerDark = lerp(base, Color.Black, 0.22f),
        borderDark = lerp(base, Color.White, 0.30f),
        primaryLight = lerp(base, Color.Black, 0.15f),
        containerLight = lerp(base, Color.White, 0.35f),
        borderLight = lerp(base, Color.Black, 0.15f),
    )
}
