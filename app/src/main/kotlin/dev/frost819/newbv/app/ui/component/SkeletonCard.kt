package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.tv.material3.MaterialTheme

/**
 * 页面是否处于激活槽位（由 TabbedContent 双槽位壳提供）。
 * 骨架屏等每帧动画据此门控：非激活页（备用槽/不可见）零动画器、零每帧唤醒。
 */
val LocalPageActive = staticCompositionLocalOf { true }

/**
 * 骨架屏卡片（P0-4，参照 wiliwili 的同构灰块占位）。
 *
 * 与视频卡片同构：封面比例块 + 两行文字条，整卡做轻微的呼吸明暗，
 * 替代首屏"空白 + 转圈"的加载观感。不可聚焦、不响应点击。
 *
 * 文字条的内边距与间距镜像 [dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard]
 * 的信息区规格（水平 12dp、封面→标题 8dp、标题→次要行 4dp），加载完成后无缝替换。
 *
 * @param coverAspectRatio 封面块宽高比（视频卡 1.6，番剧海报卡 0.75）。
 */
@Composable
fun SkeletonCard(
    coverAspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    // 页面不在激活槽位时不创建无限动画器：备用页的骨架屏保持静态，
    // 不产生每帧唤醒（数据未返回且页面隐藏时会长时间停留骨架态）。
    val alpha: State<Float> =
        if (LocalPageActive.current) {
            val transition = rememberInfiniteTransition(label = "skeleton-pulse")
            // 呼吸幅度 0.6 ~ 0.95：深色底下低点仍清晰可辨（实测 0.45 过暗）
            transition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0.95f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 900),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "skeleton-alpha",
            )
        } else {
            remember { mutableFloatStateOf(0.6f) }
        }
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val coverShape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            modifier.padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .graphicsLayer { this.alpha = alpha.value }
                    .clip(coverShape)
                    .background(blockColor),
        )
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(16.dp)
                        .graphicsLayer { this.alpha = alpha.value }
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp)
                        .graphicsLayer { this.alpha = alpha.value }
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
            )
        }
    }
}

/** 视频卡片骨架（封面 16:10 + 标题/UP 两行）。 */
@Composable
fun SkeletonVideoCard(modifier: Modifier = Modifier) {
    SkeletonCard(coverAspectRatio = 1.6f, modifier = modifier)
}

/** 番剧海报卡片骨架（封面 3:4 + 标题行）。 */
@Composable
fun SkeletonSeasonCard(modifier: Modifier = Modifier) {
    SkeletonCard(coverAspectRatio = 0.75f, modifier = modifier)
}

/**
 * 首屏骨架数量：按卡片宽度估算一屏两行所需的骨架数。
 * 不足时由 LazyGrid 自然留白，超出无副作用。
 */
const val SKELETON_FIRST_SCREEN_COUNT = 12
