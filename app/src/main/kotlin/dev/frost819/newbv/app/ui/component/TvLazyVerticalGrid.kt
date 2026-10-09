package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.core.focus.isKeyDown
import dev.frost819.newbv.data.datastore.Prefs
import kotlin.math.ceil
import kotlin.math.max

/** 应用实际布局屏宽：应用覆盖了 [LocalDensity]，须用覆盖后密度换算物理像素。 */
@Composable
private fun appScreenWidthDp(): Dp =
    with(LocalDensity.current) {
        LocalView.current.resources.displayMetrics.widthPixels
            .toDp()
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
 * 番剧页卡片放大系数：横版 200dp、竖版 150dp、时间表 260dp 等基准宽度
 * 乘以该系数得到实际卡宽。
 *
 * 与 [videoGridHSpacing] 同一屏宽分档（<800 / 1080p / 2K / 4K，密度覆盖后换算）。
 * 分辨率越低卡宽越小、越高适度放大：写死卡宽时 1080p 下每行卡片过少（元素过大），
 * 4K 下卡片又相对屏宽过小，分档让各分辨率下每行可见卡片数量保持可读。
 */
@Composable
fun animeCardScale(): Float = animeCardScale(appScreenWidthDp())

internal fun animeCardScale(screenWidth: Dp): Float =
    when {
        screenWidth >= 1600.dp -> 1.3f
        screenWidth >= 1100.dp -> 1.15f
        screenWidth >= 800.dp -> 1f
        else -> 0.85f
    }

/**
 * "卡片宽度上限"栅格：社区通用语义（Flutter 的 [SliverGridDelegateWithMaxCrossAxisExtent]、
 * PiliPlus 的卡宽设置）——列数 = ceil(可用宽 / (卡宽上限 + 间距))，
 * 卡片实际宽度不超过 [maxWidth]：屏幕越宽加列而非拉宽卡片，卡片观感保持稳定。
 */
private class MaxCardWidthCells(
    private val maxWidth: Dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> {
        val count =
            max(1, ceil(availableSize / (maxWidth.roundToPx() + spacing.toFloat())).toInt())
        val cellSize = (availableSize - spacing * (count - 1)) / count
        return List(count) { index ->
            if (index == count - 1) {
                availableSize - spacing * (count - 1) - cellSize * (count - 1)
            } else {
                cellSize
            }
        }
    }
}

/**
 * 视频卡片栅格：设置项"视频卡片宽度"驱动，列数按屏宽自适应
 * （卡片不超过设置宽度，屏宽越大列数越多），经 [Prefs.videoCardWidthFlow] 实时生效。
 */
@Composable
fun videoCardGridCells(): GridCells {
    val cardWidth by Prefs.videoCardWidthFlow.collectAsState()
    return MaxCardWidthCells(cardWidth.dp)
}

/**
 * 「最少滚动」bring-into-view 策略：焦点项不完全可见时最小滚动贴边即停，可见则不动。
 *
 * 时间表看板这类内部自持滚动的固定高度板块用它跟随焦点（不定轴）；
 * 也作为 [TvLazyVerticalGrid] scrollLock 期间的退化策略，避免锁死后焦点落在视口外。
 */
@OptIn(ExperimentalFoundationApi::class)
internal val minimalBringIntoViewSpec =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float =
            when {
                offset < 0f -> offset
                offset + size > containerSize -> offset + size - containerSize
                else -> 0f
            }
    }

/**
 * 封装了 TV 焦点定轴逻辑的 [LazyVerticalGrid]。
 *
 * TV 端 D-Pad 导航时，聚焦的 item 会自动滚动到屏幕上方 [pivotFraction] 比例处，
 * 避免焦点被顶部/底部遮挡。默认 0.3f（屏幕上方 30%），符合 TV 端习惯。
 *
 * @param pivotFraction 焦点 item 在屏幕上的停留位置比例 (0.0 - 1.0)。
 * @param scrollLock 返回 true 期间不定轴：滚动退化为最少滚动（贴边即停），
 *   供新番时间表这类内部自持滚动的固定高度板块使用——板块持焦时定轴会把整页
 *   反复拉去对齐 30% 线、把看板推出视口；保留最少滚动是为了焦点从板块外进入时
 *   仍能被带入视口（板块整体在屏内时页面纹丝不动）。滚算发生在焦点请求期，
 *   lambda 每次调用都会执行，须读取 remember 的状态才能当帧感知锁的切换。
 * @param leftExitRequester 首列左键的出口；主页面默认回到选中侧边栏项，独立页面或弹窗传 null。
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
    scrollLock: () -> Boolean = { false },
    leftExitRequester: FocusRequester? = LocalGridLeftExit.current,
    content: TvLazyGridScope.() -> Unit,
) {
    val leftExit by rememberUpdatedState(leftExitRequester)
    val navigation = remember(state) { GridFocusNavigation(state) { leftExit } }
    val bringIntoViewSpec =
        remember(pivotFraction, scrollLock) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float {
                    if (scrollLock()) {
                        return minimalBringIntoViewSpec.calculateScrollDistance(
                            offset,
                            size,
                            containerSize,
                        )
                    }
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
            modifier =
                modifier.onPreviewKeyEvent {
                    navigation.initialKeyDown = it.isKeyDown() && it.nativeKeyEvent.repeatCount == 0
                    false
                },
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = { content(TvLazyGridScope(this, navigation)) },
        )
    }
}
