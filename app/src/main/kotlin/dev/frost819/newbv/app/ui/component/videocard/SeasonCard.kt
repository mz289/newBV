package dev.frost819.newbv.app.ui.component.videocard

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.core.focus.ControlFocusDefaults
import dev.frost819.newbv.core.focus.focusShakeTarget
import dev.frost819.newbv.core.focus.outerFocusBorder
import dev.frost819.newbv.core.focus.touchClickable

/** 封面圆角：与视频卡统一的 8dp。 */
private val coverShape = RoundedCornerShape(8.dp)

/**
 * 番剧/影视卡片。
 *
 * 封面（0.75 宽高比）+ 底部渐变评分 + 标题/副标题。
 * 焦点选中时显示主题边框。
 *
 * @param data 卡片数据。
 * @param onClick 点击回调。
 * @param onGoToDetailPage 跳转详情页回调（长按或菜单键）。
 * @param modifier Modifier。
 */
@Composable
fun SeasonCard(
    data: SeasonCardData,
    onClick: () -> Unit,
    onGoToDetailPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .focusShakeTarget()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ControlFocusDefaults.surfaceColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(focusedBorder = outerFocusBorder(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(coverShape),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AsyncImage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(coverShape),
                    model = data.cover,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                )

                if (data.hasRating) {
                    Box(
                        modifier =
                            Modifier
                                .height(48.dp)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.8f),
                                            ),
                                    ),
                                ),
                    )
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .fillMaxWidth()
                                .padding(8.dp, 0.dp),
                        text = data.rating ?: "",
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!data.subTitle.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = data.subTitle,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
