package dev.frost819.newbv.app.ui.component.videocard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.core.focus.focusShakeTarget
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.theme.LocalFocusOutlineColor

/** 封面圆角：与视频卡统一。 */
private val coverShape = RoundedCornerShape(8.dp)

/**
 * 合集/系列/收藏夹卡片数据。
 *
 * @param title 标题
 * @param subTitle 副标题（如"共26个视频"）
 * @param cover 封面 URL；为空时显示占位块（收藏夹列表接口可能不返回封面）
 * @param badge 封面右上角角标（合集/系列/收藏）
 */
data class UpCollectionCardData(
    val title: String,
    val subTitle: String,
    val cover: String,
    val badge: String,
)

/**
 * 合集/系列/收藏夹卡片（16:9 横版，与投稿视频卡一致，对齐 B 站空间版式）。
 */
@Composable
fun UpCollectionCard(
    data: UpCollectionCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier =
            modifier
                .focusShakeTarget()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .fillMaxWidth()
                .touchClickable(onClick = onClick),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
        shape = CardDefaults.shape(coverShape),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        border = androidx.compose.foundation.BorderStroke(3.dp, LocalFocusOutlineColor.current),
                        shape = coverShape,
                    ),
            ),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (data.cover.isNotEmpty()) {
                    AsyncImage(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(coverShape),
                        model = data.cover,
                        contentDescription = data.title,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(48.dp),
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BadgeTag(
                    text = data.badge,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                )
            }

            Column(
                // 水平微内缩：文字不直接顶到焦点描边
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** 角标：半透明黑底小标签（合集/系列/收藏）。 */
@Composable
private fun BadgeTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
        )
    }
}
