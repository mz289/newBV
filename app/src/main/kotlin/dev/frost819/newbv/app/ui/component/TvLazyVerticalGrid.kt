package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.data.datastore.Prefs

/** 应用实际布局屏宽：应用覆盖了 [LocalDensity]，须用覆盖后密度换算物理像素。 */
@Composable
private fun appScreenWidthDp(): Dp = with(LocalDensity.current) {
    LocalView.current.resources.displayMetrics.widthPixels.toDp()
}

/**
 * 视频网格水平间距：与卡宽同比例分档。
 *
 * 视觉间隙（间距 + 卡片水平内缩 12dp）约占卡宽 13%，各分辨率一致
 * （4K 36dp/277dp、2K 26dp/217dp、1080p 20dp/158dp）。
 */
@Composable
fun videoGridHSpacing(): Dp {
    val screenWidth = appScreenWidthDp()
    return when {
        screenWidth >= 1600.dp -> 24.dp
        screenWidth >= 1100.dp -> 14.dp
        else -> 8.dp
    }
}

/** 视频网格垂直间距：与水平间距同比例分档。 */
@Composable
fun videoGridVSpacing(): Dp {
    val screenWidth = appScreenWidthDp()
    return when {
        screenWidth >= 1600.dp -> 12.dp
        screenWidth >= 1100.dp -> 10.dp
        else -> 8.dp
    }
}

/**
 * 视频卡片标题/次要文字的字号缩放系数。
 *
 * 列数越多卡片越窄，文字按"当前列数卡宽 / 默认 4 列卡宽"等比缩小，
 * 保持标题占卡宽的比例与默认档一致。
 * 侧边栏宽度按估算值（92dp）计，搜索页无侧边栏带来的少量偏差对字号不敏感。
 */
@Composable
fun videoCardTitleScale(): Float {
    val prefColumns by Prefs.videoColumnsFlow.collectAsState()
    val available = appScreenWidthDp() - 140.dp // 侧边栏 ~92dp + 网格内容边距 48dp
    val spacing = videoGridHSpacing()
    val cardWidth = (available - spacing * (prefColumns.code - 1)) / prefColumns.code
    val defaultCardWidth = (available - spacing * 3) / 4
    return (cardWidth / defaultCardWidth).coerceIn(0.7f, 1f)
}

/** 海报卡片栅格（番剧/影视封面卡）的最小卡片宽度。 */
val POSTER_CARD_MIN_WIDTH: Dp = 260.dp

/** 视频卡片栅格列配置：设置项"视频卡片列数"（4~7 列）驱动，经 [Prefs.videoColumnsFlow] 实时生效。 */
@Composable
fun videoCardGridCells(): GridCells {
    val prefColumns by Prefs.videoColumnsFlow.collectAsState()
    return GridCells.Fixed(prefColumns.code)
}

/**
 * 封装了 TV 焦点定轴逻辑的 [LazyVerticalGrid]。
 *
 * TV 端 D-Pad 导航时，聚焦的 item 会自动滚动到屏幕上方 [pivotFraction] 比例处，
 * 避免焦点被顶部/底部遮挡。默认 0.3f（屏幕上方 30%），符合 TV 端习惯。
 *
 * @param pivotFraction 焦点 item 在屏幕上的停留位置比例 (0.0 - 1.0)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    pivotFraction: Float = 0.3f,
    content: LazyGridScope.() -> Unit,
) {
    val bringIntoViewSpec =
        remember(pivotFraction) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float {
                    val targetPosition = containerSize * pivotFraction
                    return offset - targetPosition
                }
            }
        }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
    ) {
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
