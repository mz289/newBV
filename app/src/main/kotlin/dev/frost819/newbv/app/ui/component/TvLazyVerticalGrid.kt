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
 * 视频卡片栅格的最小卡片宽度（P1-1 自适应列数）。
 *
 * 应用覆盖了 [LocalDensity]（默认 2.0，设置内可调），屏宽必须用覆盖后密度换算——
 * LocalConfiguration.screenWidthDp 按系统密度折算，与实际布局宽度不符，不能直接用。
 *
 * 分档（侧边栏 ~92dp + 内容边距 48dp + 间距 24dp）：
 * 4K(1920dp) 6 列、2K(1280dp) 5 列、1080p(960dp) 5 列（5×140 + 4×24 = 796 ≤ 可用 ~820dp）。
 */
@Composable
private fun videoCardMinWidth(): Dp {
    val screenWidth = appScreenWidthDp()
    return when {
        screenWidth >= 1600.dp -> 260.dp // 4K：6 列
        screenWidth >= 1100.dp -> 200.dp // 2K：5 列
        else -> 140.dp // 1080p：5 列
    }
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
 * 固定列数时卡宽随列数变化，文字按"实际卡宽 / 档位自动卡宽"等比缩放，
 * 保持标题占卡宽的比例与自动档一致；"自动"档恒为 1（维持原字号）。
 * 侧边栏宽度按估算值（92dp）计，搜索页无侧边栏带来的少量偏差对字号不敏感。
 */
@Composable
fun videoCardTitleScale(): Float {
    val prefColumns by Prefs.videoColumnsFlow.collectAsState()
    val fixed = prefColumns.code
    if (fixed < 2) return 1f
    val available = appScreenWidthDp() - 140.dp // 侧边栏 ~92dp + 网格内容边距 48dp
    val cardWidth = (available - videoGridHSpacing() * (fixed - 1)) / fixed
    return (cardWidth / videoCardMinWidth() / 1.25f).coerceIn(0.7f, 2f)
}

/** 海报卡片栅格（番剧/影视封面卡）的最小卡片宽度。 */
val POSTER_CARD_MIN_WIDTH: Dp = 260.dp

/**
 * 视频卡片栅格列配置（设置项"视频卡片列数"驱动）。
 *
 * 设置为固定列数（≥2）时用 [GridCells.Fixed] 强制；"自动"按屏宽分档（[videoCardMinWidth]）。
 * 通过 [Prefs.videoColumnsFlow] 实时响应设置变更，改完立即生效。
 */
@Composable
fun videoCardGridCells(): GridCells {
    val prefColumns by Prefs.videoColumnsFlow.collectAsState()
    val fixed = prefColumns.code
    return if (fixed >= 2) {
        GridCells.Fixed(fixed)
    } else {
        GridCells.Adaptive(videoCardMinWidth())
    }
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
