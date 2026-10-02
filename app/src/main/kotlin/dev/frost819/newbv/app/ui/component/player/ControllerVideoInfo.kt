package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.entity.player.ChapterMark
import dev.frost819.newbv.app.entity.player.ProgressSegmentMark
import dev.frost819.newbv.app.ui.state.player.SeekerState
import dev.frost819.newbv.app.util.VideoShotImageCache
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.biliapi.entity.video.VideoShot
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.LocalFocusOutlineColor
import kotlinx.coroutines.delay

/**
 * 播放器控制器信息层。
 *
 * 包含顶部标题+时钟和底部进度条+操作按钮两部分。
 * 通过 [AnimatedVisibility] 控制进出场动画。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param isSeeking 是否正在 seek
 * @param goTime seek 预览位置（毫秒）
 * @param seekerState 进度条状态
 * @param title 视频标题
 * @param onlineWatching 同时观看人数文案（空串时不显示）
 * @param videoShot 缩略图数据（为 null 时不显示预览）
 * @param videoShotCache 缩略图缓存
 * @param isPgc 是否来自番剧（为 true 时隐藏详情/UP/相关视频按钮）
 * @param danmakuEnabled 弹幕是否开启
 * @param isLooping 是否循环播放
 * @param isPlaying 是否正在播放，用于切换播放/暂停图标
 * @param chapterMarks 章节标记（进度条刻度 + 章节列表入口，为空时隐藏入口）
 * @param onDirectionLeft seek 左移回调
 * @param onDirectionRight seek 右移回调
 * @param onSeekGoTime 确认 seek 回调
 * @param onSeekDragStart 进度条触摸拖拽开始回调（挂起待执行的 seek）
 * @param onSeekToPosition 触屏拖拽进度条时更新预览位置（毫秒）
 * @param onSeekDragEnd 触屏拖拽结束回调，参数为最终位置（毫秒），此时立即执行 seek
 * @param onPlayPause 播放/暂停回调
 * @param onDanmakuSwitchChange 弹幕开关回调
 * @param onShowSettings 打开设置回调
 * @param onShowRelatedVideos 打开相关视频回调
 * @param onShowChapters 打开章节列表回调（章节入口按钮点击，或焦点在按钮行时按下键）
 * @param onGoToVideoInfo 跳转视频详情回调
 * @param onToggleLoop 切换循环回调
 * @param onGoToUpPage 跳转 UP 主页面回调
 * @param onShowInteraction 打开视频交互弹窗回调
 * @param onShowComments 打开评论弹窗回调
 */
@Composable
fun ControllerVideoInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    sponsorBlockMarks: List<ProgressSegmentMark> = emptyList(),
    title: String,
    onlineWatching: String,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    isPgc: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    isPlaying: Boolean,
    chapterMarks: List<ChapterMark> = emptyList(),
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onSeekDragEnd: (Long?) -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onShowChapters: () -> Unit = {},
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerTopVideoInfo",
        ) {
            ControllerVideoInfoTop(
                modifier = Modifier.align(Alignment.TopCenter),
                title = title,
                onlineWatching = onlineWatching,
            )
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerBottomVideoInfo",
        ) {
            ControllerVideoInfoBottom(
                modifier = Modifier.align(Alignment.BottomCenter),
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                sponsorBlockMarks = sponsorBlockMarks,
                chapterMarks = chapterMarks,
                videoShot = videoShot,
                videoShotCache = videoShotCache,
                isPgc = isPgc,
                danmakuEnabled = danmakuEnabled,
                isLooping = isLooping,
                isPlaying = isPlaying,
                onDirectionLeft = onDirectionLeft,
                onDirectionRight = onDirectionRight,
                onSeekGoTime = onSeekGoTime,
                onSeekDragStart = onSeekDragStart,
                onSeekToPosition = onSeekToPosition,
                onSeekDragEnd = onSeekDragEnd,
                onPlayPause = onPlayPause,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onShowSettings = onShowSettings,
                onShowRelatedVideos = onShowRelatedVideos,
                onShowChapters = onShowChapters,
                onGoToVideoInfo = onGoToVideoInfo,
                onToggleLoop = onToggleLoop,
                onGoToUpPage = onGoToUpPage,
                onShowInteraction = onShowInteraction,
                onShowComments = onShowComments,
            )
        }
    }
}

/**
 * 控制器顶部信息（标题 + 同时观看人数 + 时钟）。
 *
 * @param onlineWatching 同时观看人数文案（服务端预格式化，如 "9.4万+"），空串时不显示
 */
@Composable
fun ControllerVideoInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    onlineWatching: String,
) {
    val currentClock = rememberClock()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(
                    MaterialTheme.shapes.large.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp),
                    ),
                ).background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0f),
                                ),
                        ),
                ).padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                text = title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        shadow = Shadow(color = Color.Black, blurRadius = 1f),
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Clock(hour = currentClock.first, minute = currentClock.second)
        }
        if (onlineWatching.isNotEmpty()) {
            // 同时观看人数 meta 行（标题下方小字，参考 Web 端样式）
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_watching),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "${onlineWatching}人正在看",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            shadow = Shadow(color = Color.Black, blurRadius = 1f),
                        ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * 控制器底部信息（缩略图预览 + 时间 + 进度条 + 操作按钮）。
 */
@Composable
fun ControllerVideoInfoBottom(
    modifier: Modifier = Modifier,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    sponsorBlockMarks: List<ProgressSegmentMark>,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    isPgc: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    isPlaying: Boolean,
    chapterMarks: List<ChapterMark> = emptyList(),
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onSeekDragEnd: (Long?) -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onShowChapters: () -> Unit = {},
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
) {
    val seekFocusRequester = remember { FocusRequester() }
    val buttonsFocusRequester = remember { FocusRequester() }

    var isSeekFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { seekFocusRequester.requestFocus() }
    }

    Column(
        modifier =
            modifier.clip(
                MaterialTheme.shapes.large.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp),
                ),
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Seek 缩略图预览
        if (isSeeking && videoShot != null) {
            VideoShot(
                modifier = Modifier.padding(horizontal = 48.dp),
                videoShot = videoShot,
                imageCache = videoShotCache,
                position = goTime,
                duration = seekerState.totalDuration,
                coercedOffset = (-24).dp,
            )
        }

        // 时间显示
        Row(modifier = Modifier.fillMaxWidth()) {
            val timeText = if (isSeeking) goTime.formatHourMinSec() else seekerState.currentTime.formatHourMinSec()
            Text(
                modifier = Modifier.padding(bottom = 2.dp, start = 24.dp),
                text = "$timeText / ${seekerState.totalDuration.formatHourMinSec()}",
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 1f)),
            )
        }

        // Seek bar（可聚焦，处理方向键 + 触屏拖拽）
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .border(
                        width = 1.dp,
                        color =
                            LocalFocusOutlineColor.current.copy(
                                alpha = if (isSeekFocused) 1f else 0f,
                            ),
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(8.dp),
                    ).focusable()
                    .focusRequester(seekFocusRequester)
                    .pointerInput(seekerState.totalDuration) {
                        awaitEachGesture {
                            val firstDown =
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                            firstDown.consume()
                            // 按下即进入拖拽：挂起待执行的 seek，期间只更新预览
                            onSeekDragStart()

                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: break
                                    val w = this.size.width.toFloat()

                                    if (!change.pressed) {
                                        // 手指抬起：以最终位置立即执行 seek
                                        if (w > 0 && seekerState.totalDuration > 0) {
                                            val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                            onSeekDragEnd((ratio * seekerState.totalDuration).toLong())
                                        } else {
                                            onSeekDragEnd(null)
                                        }
                                        change.consume()
                                        break
                                    }

                                    if (change.positionChanged()) {
                                        // 拖拽中：仅更新预览位置，避免反复 seek 触发重新缓冲卡死
                                        if (w > 0 && seekerState.totalDuration > 0) {
                                            val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                            onSeekToPosition((ratio * seekerState.totalDuration).toLong())
                                        }
                                        change.consume()
                                    }
                                }
                            } finally {
                                // 手势被中断（节点移除/pointerInput 重启）时兜底结束拖拽态，
                                // 以最后预览位置执行 seek；正常抬起路径已先行结束，此处为 no-op
                                onSeekDragEnd(null)
                            }
                        }
                    }.onKeyEvent {
                        when (it.key) {
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                if (isSeeking) onSeekGoTime() else onPlayPause()
                                true
                            }

                            Key.DirectionLeft, Key.MediaRewind -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                onDirectionLeft()
                                true
                            }

                            Key.DirectionRight, Key.MediaFastForward -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                onDirectionRight()
                                true
                            }

                            Key.DirectionDown -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                buttonsFocusRequester.requestFocus()
                                true
                            }

                            else -> false
                        }
                    }.onFocusChanged { isSeekFocused = it.isFocused },
        ) {
            VideoProgressSeek(
                modifier = Modifier.focusable().fillMaxWidth(),
                duration = seekerState.totalDuration,
                position = if (isSeeking) goTime else seekerState.currentTime,
                bufferedPercentage = seekerState.bufferedPercentage,
                isPersistentSeek = false,
                segmentMarks = sponsorBlockMarks,
                chapterMarks = chapterMarks,
            )
        }

        // 操作按钮行
        val icons =
            buildList {
                add(
                    ControllerIcon(
                        if (isPlaying) R.drawable.ic_player_action_pause else R.drawable.ic_player_action_play,
                        "播放/暂停",
                        onPlayPause,
                        if (isPlaying) "正在播放" else "已暂停",
                    ),
                )
                add(
                    ControllerIcon(
                        if (danmakuEnabled) {
                            R.drawable.ic_player_action_danmaku
                        } else {
                            R.drawable.ic_player_action_danmaku_off
                        },
                        "弹幕开关",
                        onDanmakuSwitchChange,
                        if (danmakuEnabled) "已开启" else "已关闭",
                    ),
                )
                add(ControllerIcon(R.drawable.ic_player_action_settings, "打开设置", onShowSettings))
                if (!isPgc) {
                    add(ControllerIcon(R.drawable.ic_player_action_info, "视频信息", onGoToVideoInfo))
                    add(ControllerIcon(R.drawable.ic_player_action_person, "up主页", onGoToUpPage))
                    add(ControllerIcon(R.drawable.ic_player_action_related, "相关视频", onShowRelatedVideos))
                }
                // 章节入口数据驱动：仅当当前视频有章节看点时显示
                if (chapterMarks.isNotEmpty()) {
                    add(ControllerIcon(R.drawable.ic_player_action_chapter, "章节列表", onShowChapters))
                }
                add(
                    ControllerIcon(
                        if (isLooping) R.drawable.ic_player_action_repeat_one else R.drawable.ic_player_action_repeat,
                        "循环播放",
                        onToggleLoop,
                        if (isLooping) "已开启" else "已关闭",
                    ),
                )
                add(
                    ControllerIcon(
                        icon = R.drawable.ic_player_action_interaction,
                        description = "交互",
                        action = onShowInteraction,
                    ),
                )
                add(
                    ControllerIcon(
                        icon = R.drawable.ic_player_action_comment,
                        description = "评论",
                        action = onShowComments,
                    ),
                )
            }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .focusRequester(buttonsFocusRequester)
                    .onKeyEvent {
                        when (it.key) {
                            Key.DirectionUp -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                seekFocusRequester.requestFocus()
                                true
                            }

                            Key.DirectionDown -> {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                // 焦点已在按钮行时再按下键：有章节看点则打开章节列表，
                                // 无章节时返回 false 走默认焦点搜索（保持原行为）
                                if (chapterMarks.isNotEmpty()) onShowChapters()
                                chapterMarks.isNotEmpty()
                            }

                            else -> false
                        }
                    }.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.Start),
        ) {
            icons.forEach { item ->
                key(item.description) {
                    Surface(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .semantics {
                                    item.state?.let { stateDescription = it }
                                }.touchClickable(onClick = item.action),
                        onClick = item.action,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        border =
                            ClickableSurfaceDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None,
                                pressedBorder = Border.None,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.94f),
                        // 未聚焦时用黑色半透明圆底：白色图标在亮、暗画面下都保持
                        // 可读（白色磨砂底在纯白画面上会隐形）；聚焦仍为白色实底
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color(0xFF171717),
                                pressedContainerColor = Color.White.copy(alpha = 0.85f),
                                pressedContentColor = Color(0xFF171717),
                            ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = item.icon),
                                contentDescription = item.description,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 操作按钮及其无障碍状态。描述保持稳定，播放和开关状态变化时不会重建焦点节点。
 */
private data class ControllerIcon(
    val icon: Int,
    val description: String,
    val action: () -> Unit,
    val state: String? = null,
)

/**
 * 时钟组件。
 */
@Composable
private fun Clock(
    modifier: Modifier = Modifier,
    hour: Int,
    minute: Int,
) {
    Text(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 1f)),
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 32.sp)) {
                    append("$hour".padStart(2, '0'))
                    append(":")
                    append("$minute".padStart(2, '0'))
                }
            },
    )
}

// region Previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ControllerVideoInfoPreview() {
    BVTheme {
        ControllerVideoInfo(
            show = true,
            isSeeking = false,
            goTime = 0L,
            seekerState =
                SeekerState(
                    totalDuration = 600_000L,
                    currentTime = 120_000L,
                    bufferedPercentage = 50,
                ),
            title = "示例视频标题",
            onlineWatching = "9.4万+",
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            isPgc = false,
            danmakuEnabled = true,
            isLooping = false,
            isPlaying = true,
            onDirectionLeft = {},
            onDirectionRight = {},
            onSeekGoTime = {},
            onSeekDragStart = {},
            onSeekToPosition = {},
            onSeekDragEnd = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onShowSettings = {},
            onShowRelatedVideos = {},
            onGoToVideoInfo = {},
            onToggleLoop = {},
            onGoToUpPage = {},
            onShowInteraction = {},
            onShowComments = {},
        )
    }
}

// endregion
