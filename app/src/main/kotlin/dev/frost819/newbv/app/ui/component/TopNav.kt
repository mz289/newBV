package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.controlFocusOutline
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 顶部导航 Tab 栏。
 *
 * TV Material3 [TabRow] 封装，支持 D-Pad 焦点导航。
 * Tab 切换时触发 [onSelectedChanged]，点击同一 Tab 触发 [onClick]（用于刷新）。
 *
 * @param items Tab 项列表（已按首选项排序）。
 * @param displayName Tab 项显示名称。
 * @param selectedIndex 当前选中的 Tab 索引（由外部控制，用于导航返回后恢复）。
 * @param isLargePadding 内容区未获焦点时使用较大内边距。
 * @param onSelectedChanged Tab 焦点切换回调。
 * @param onClick Tab 点击回调。
 */
@Composable
fun <T> TopNav(
    modifier: Modifier = Modifier,
    items: List<T>,
    displayName: (T) -> String,
    selectedIndex: Int = 0,
    isLargePadding: Boolean,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    var selectedTabIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        selectedTabIndex = selectedIndex
    }
    val verticalPadding by animateDpAsState(
        targetValue = if (isLargePadding) 12.dp else 6.dp,
        label = "top-nav-padding",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.Start,
    ) {
        TabRow(
            modifier =
                Modifier
                    .focusRestorer(focusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
            indicator = { _, _ -> },
        ) {
            items.forEachIndexed { index, tab ->
                NavItemTab(
                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    text = displayName(tab),
                    selected = index == selectedTabIndex,
                    onFocus = {
                        selectedTabIndex = index
                        onSelectedChanged(tab)
                    },
                    onClick = { onClick(tab) },
                )
            }
        }
    }
}

@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    // 强调色统一为主品牌色（P0-1）：全应用的选中态只用一个色相
    val accentColor = MaterialTheme.colorScheme.primary
    // 扁平文字 Tab（P1-3）：选中态靠文字变色表达，仅保留极淡的底色作辅助
    val containerColor =
        if (selected) {
            accentColor.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }

    Tab(
        modifier =
            modifier
                .controlFocusOutline()
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .touchClickable(onClick = onClick),
        selected = selected,
        onFocus = onFocus,
        onClick = onClick,
    ) {
        // 用固定高度的 Box 居中文字：直接对 Text 设 height 会把字形顶对齐，
        // 字体行高与剩余空间不等时文字就偏上，不同字体/字号下表现不一致。
        Box(
            modifier =
                Modifier
                    .height(32.dp)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopNavPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        TopNav(
            items = listOf("推荐", "热门", "动态"),
            displayName = { it },
            isLargePadding = true,
        )
    }
}
