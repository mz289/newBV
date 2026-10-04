package dev.frost819.newbv.app.ui.component.videocard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** B 站大会员/充电专属角标的官方粉色。 */
private val BiliVipPink = Color(0xFFFB7299)

/** 付费类角标使用的暖橙色。 */
private val PaidOrange = Color(0xFFFF9F43)

/**
 * 角标 chip：封面/标题旁的付费类标识（如“会员”“充电专属”“付费”）。
 *
 * 底色优先取 [bgColor]（接口下发的官方色），为 null 时按文案选择：
 * 会员/充电类用 B 站粉，付费类用暖橙，其余用主题 primary。
 *
 * @param text 角标文字。
 * @param modifier 修饰符（定位与外边距由调用方提供）。
 * @param bgColor 底色，null 时按文案兜底。
 */
@Composable
fun BadgeChip(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color? = null,
) {
    val container =
        bgColor
            ?: when {
                text.contains("会员") || text.contains("充电") -> BiliVipPink
                text.contains("付费") || text.contains("购买") -> PaidOrange
                else -> MaterialTheme.colorScheme.primary
            }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(container, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 解析 B 站接口下发的十六进制颜色（如 "#FB7299"），非法格式返回 null 由 UI 兜底。
 */
fun parseHexColorOrNull(hex: String): Color? {
    val value = hex.removePrefix("#")
    if (value.length != 6 && value.length != 8) return null
    return runCatching {
        val parsed = value.toLong(16)
        if (value.length == 6) Color(0xFF000000L or parsed) else Color(parsed)
    }.getOrNull()
}
