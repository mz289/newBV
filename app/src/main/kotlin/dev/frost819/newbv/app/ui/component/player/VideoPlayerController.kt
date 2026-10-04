package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.data.toDanmakuEntities
import dev.frost819.newbv.app.entity.player.VideoAspectRatio
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutAction
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutCatalog
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutKeys
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutsStore
import dev.frost819.newbv.app.entity.player.shortcut.pgcUnsupportedShortcutActions
import dev.frost819.newbv.app.ui.action.player.DanmakuSettingAction
import dev.frost819.newbv.app.ui.action.player.MediaProfileSettingAction
import dev.frost819.newbv.app.ui.action.player.SubtitleSettingAction
import dev.frost819.newbv.app.ui.component.player.menu.MenuController
import dev.frost819.newbv.app.ui.state.player.PlayerOverlayState
import dev.frost819.newbv.app.ui.state.player.PlayerState
import dev.frost819.newbv.app.ui.state.player.PlayerUiState
import dev.frost819.newbv.app.ui.state.player.SeekerState
import dev.frost819.newbv.app.util.PlayerConstants
import dev.frost819.newbv.app.util.VideoShotImageCache
import dev.frost819.newbv.biliapi.entity.video.Subtitle
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.ThemeMode
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器根控制器。
 *
 * 管理所有覆盖层的可见性和焦点路由，处理 D-pad 按键事件、seek 加速、
 * 自定义快捷键、以及视频列表/菜单/相关信息控制器之间的协调。
 *
 * 布局层次（从底到顶）：
 * 1. content() — 视频画面 + 弹幕层
 * 2. BottomSubtitle — 字幕文本
 * 3. SkipTips — 跳转提示
 * 4. PlayStateTips — 播放状态提示
 * 5. RelatedVideosController — 相关视频
 * 6. ControllerVideoInfo — 信息栏 + 进度条 + 按钮
 * 7. VideoListController — 分集列表
 * 8. ChapterListController — 章节列表
 * 9. MenuController — 设置菜单
 */
@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun VideoPlayerController(
    modifier: Modifier = Modifier,
    isPgc: Boolean,
    isLooping: Boolean,
    videoShotCache: VideoShotImageCache,
    uiState: PlayerUiState,
    overlayState: PlayerOverlayState,
    seekerState: State<SeekerState>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onGoTime: (time: Long) -> Unit,
    onBackToStart: () -> Unit,
    onCancelSkipToNextEp: () -> Unit,
    onConfirmSponsorSkip: () -> Unit,
    onDismissSponsorSkip: () -> Unit,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onGoToUpPage: () -> Unit,
    onGoToVideoDetail: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
    onMediaProfileSettingChange: (MediaProfileSettingAction) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSettingChange: (SubtitleSettingAction) -> Unit,
    onRelatedVideoClicked: (dev.frost819.newbv.app.ui.component.videocard.VideoCardData) -> Unit,
    onToggleDanmaku: () -> Unit,
    onShowShortcutTip: (keyName: String?, text: String) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 覆盖层可见性
    var showListController by remember { mutableStateOf(false) }
    var showChapterListController by remember { mutableStateOf(false) }
    var showMenuController by remember { mutableStateOf(false) }
    var showInfoSeekController by remember { mutableStateOf(false) }
    var showRelatedVideosController by remember { mutableStateOf(false) }
    val showClickableControllers by remember {
        derivedStateOf {
            showListController ||
                showChapterListController ||
                showMenuController ||
                showInfoSeekController ||
                showRelatedVideosController
        }
    }

    // Seek 加速状态
    var goTime by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    // 进度条焦点重抓取激励：章节/分集面板点击关闭后，原焦点节点随面板一起移除，
    // 窗口内焦点悬空导致方向键无人接收；递增此计数让进度条重新请求焦点恢复响应
    var seekFocusKick by remember { mutableIntStateOf(0) }

    /** 触摸拖拽 seek 进行中（手指未抬起）。拖拽期间只更新预览位置，不触发真实 seek。 */
    var isSeekDragActive by remember { mutableStateOf(false) }
    var seekChangeCount by remember { mutableLongStateOf(0L) }
    var lastSeekChangeTime by remember { mutableLongStateOf(0L) }
    var seekCountdown: Job? by remember { mutableStateOf(null) }
    var hideInfoSeekCountdown: Job? by remember { mutableStateOf(null) }

    // 常显进度条
    var showPersistentSeek by remember { mutableStateOf(Prefs.showPersistentSeek) }

    // 手势状态
    val gestureTipState = rememberGestureTipState()
    var currentBrightness by remember { mutableFloatStateOf(-1f) }

    fun calCoefficient(): Long =
        if (System.currentTimeMillis() - lastSeekChangeTime < PlayerConstants.SEEK_ACCELERATION_WINDOW_MS) {
            seekChangeCount++
            seekChangeCount / 5
        } else {
            seekChangeCount = 0
            0
        }

    /**
     * 立即执行 seek 并收尾：取消挂起的倒计时、上报目标位置、恢复播放状态、
     * 收起信息栏。D-pad 倒计时到期、确认键和触摸拖拽抬手共用此收尾逻辑。
     */
    fun executeSeek() {
        seekCountdown?.cancel()
        seekCountdown = null
        onGoTime(goTime)
        if (uiState.playerState != PlayerState.Playing) onPlay()
        isSeeking = false
        showInfoSeekController = false
    }

    fun startSeekCountdown() {
        seekCountdown?.cancel()
        seekCountdown =
            scope.launch {
                // 触摸拖拽进行中不执行，逐秒等待抬手；正常路径由
                // [onSeekDragEnd] 抬手立即执行并取消本倒计时，此处仅兜底
                while (isSeekDragActive) {
                    delay(PlayerConstants.SEEK_EXECUTE_DELAY_MS)
                }
                delay(PlayerConstants.SEEK_EXECUTE_DELAY_MS)
                executeSeek()
            }
    }

    /**
     * 触摸拖拽 seek 开始（进度条按下 / 画面水平拖动判定为 seek）。
     *
     * 拖拽期间挂起待执行的 seek 倒计时并停用控制器自动隐藏，
     * 避免长拖过程中真实 seek 反复触发重新缓冲导致画面卡死。
     */
    fun onSeekDragStart() {
        isSeekDragActive = true
        seekCountdown?.cancel()
        hideInfoSeekCountdown?.cancel()
    }

    /** 触摸拖拽 seek 结束（手指抬起）。[positionMs] 非空时以其为最终位置，立即执行 seek。 */
    fun onSeekDragEnd(positionMs: Long?) {
        if (!isSeekDragActive) return
        isSeekDragActive = false
        if (positionMs != null) {
            goTime = positionMs.coerceIn(0L, seekerState.value.totalDuration)
        }
        executeSeek()
    }

    fun onTimeBack() {
        isSeeking = true
        val coefficient = calCoefficient()
        val step = PlayerConstants.SEEK_BASE_INCREMENT_MS + coefficient * PlayerConstants.SEEK_STEP_INCREMENT_MS
        goTime = (goTime - step).coerceAtLeast(0L)
        lastSeekChangeTime = System.currentTimeMillis()
    }

    fun onTimeForward() {
        isSeeking = true
        val coefficient = calCoefficient()
        val step = PlayerConstants.SEEK_BASE_INCREMENT_MS + coefficient * PlayerConstants.SEEK_STEP_INCREMENT_MS
        goTime = (goTime + step).coerceAtMost(seekerState.value.totalDuration)
        lastSeekChangeTime = System.currentTimeMillis()
    }

    fun onDirectionLeft() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeBack()
        startSeekCountdown()
    }

    fun onDirectionRight() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeForward()
        startSeekCountdown()
    }

    fun onSeekGoTime() {
        executeSeek()
    }

    /**
     * 控制器自动隐藏计时器。触屏交互后 5 秒无操作自动收起控制器。
     */
    fun startControllerAutoHide() {
        if (!showInfoSeekController) return
        hideInfoSeekCountdown?.cancel()
        hideInfoSeekCountdown =
            scope.launch {
                delay(PlayerConstants.CONTROLLER_AUTO_HIDE_MS)
                showInfoSeekController = false
            }
    }

    /**
     * 进度条触摸拖拽中：仅更新预览位置。
     *
     * 真实 seek 统一延迟到抬手（[onSeekDragEnd]）立即执行，拖拽过程中
     * 反复 seek 会让 ExoPlayer 不断丢弃缓冲重新拉流，长拖时表现为画面卡死。
     */
    fun onSeekToPosition(positionMs: Long) {
        isSeeking = true
        goTime = positionMs.coerceIn(0L, seekerState.value.totalDuration)
    }

    fun closeAllControllers() {
        showListController = false
        showChapterListController = false
        showMenuController = false
        showInfoSeekController = false
        showRelatedVideosController = false
    }

    /**
     * 显示自定义快捷键触发提示浮层。
     *
     * 复用 [PlayerTip] 组件显示动作名称，定时结束后自动消失。
     * 快捷键提示始终显示，新的提示会覆盖旧提示。
     *
     * 对于开关类/参数类动作，[status] 会追加在动作名称后面（如"字幕：开"）。
     * [keyName] 为触发按键的显示名，非空时提示条以键帽图标呈现（P2-4）。
     */
    fun showShortcutTip(
        action: PlayerCustomShortcutAction,
        status: String? = null,
        keyName: String? = null,
    ) {
        val name = PlayerCustomShortcutCatalog.getActionDisplayName(action)
        val tip = if (status != null) "$name：$status" else name
        onShowShortcutTip(keyName, tip)
    }

    fun executeCustomShortcut(
        action: PlayerCustomShortcutAction,
        keyName: String? = null,
    ) {
        // PGC 默认走详情页且无 UP/相关视频，对应的路由类快捷键直接禁用并提示
        if (isPgc && action in pgcUnsupportedShortcutActions) {
            showShortcutTip(action, "番剧不支持", keyName)
            return
        }
        var status: String? = null
        when (action) {
            PlayerCustomShortcutAction.OpenSettings -> showMenuController = true
            PlayerCustomShortcutAction.OpenRelatedVideos -> showRelatedVideosController = true
            PlayerCustomShortcutAction.PlayPrevious -> onPlayPrevious()
            PlayerCustomShortcutAction.PlayNext -> onPlayNext()
            PlayerCustomShortcutAction.OpenVideoDetail -> onGoToVideoDetail()
            PlayerCustomShortcutAction.OpenUpPage -> onGoToUpPage()
            PlayerCustomShortcutAction.OpenComments -> onShowComments()
            PlayerCustomShortcutAction.OpenInteraction -> onShowInteraction()
            PlayerCustomShortcutAction.ToggleLoop -> {
                status = if (!isLooping) "开" else "关"
                onToggleLoop()
            }
            PlayerCustomShortcutAction.ToggleDanmaku -> {
                status = if (overlayState.danmakuState.enabled) "关" else "开"
                onToggleDanmaku()
            }
            PlayerCustomShortcutAction.ToggleSubtitle -> {
                status = if (overlayState.subtitleId == -1L) "开" else "关"
                onToggleSubtitle()
            }
            PlayerCustomShortcutAction.TogglePersistentBottomProgress -> {
                showPersistentSeek = !showPersistentSeek
                Prefs.showPersistentSeek = showPersistentSeek
                status = if (showPersistentSeek) "开" else "关"
            }

            is PlayerCustomShortcutAction.TogglePlaybackSpeed -> {
                val targetSpeed = if (uiState.playSpeed == action.speed) 1f else action.speed
                status = "${targetSpeed}x"
                onPlaySpeedChange(targetSpeed)
            }

            is PlayerCustomShortcutAction.ToggleDanmakuMask -> {
                val newMaskEnabled = !overlayState.danmakuState.maskEnabled
                status = if (newMaskEnabled) "开" else "关"
                onDanmakuSettingChange(
                    DanmakuSettingAction.SetMaskEnabled(newMaskEnabled),
                )
            }
        }
        showShortcutTip(action, status, keyName)
    }

    fun handleCustomShortcut(event: KeyEvent): Boolean {
        if (showClickableControllers) return false
        val keyCode = event.nativeKeyEvent.keyCode
        if (!PlayerCustomShortcutKeys.isAllowedKeyCode(keyCode)) return false
        val shortcut = PlayerCustomShortcutsStore.getByKey()[keyCode] ?: return false
        if (event.type == KeyEventType.KeyUp) return true
        if (event.type != KeyEventType.KeyDown) return false
        if (event.nativeKeyEvent.repeatCount != 0) return true
        executeCustomShortcut(shortcut.action, PlayerCustomShortcutKeys.getDisplayName(keyCode))
        return true
    }

    /** 始终生效的媒体/系统键。 */
    fun handleSystemKeys(
        event: KeyEvent,
        confirmKeys: List<Key>,
    ): Boolean =
        when (event.key) {
            Key.Back -> {
                when {
                    // 待确认跳过片段：返回键为"忽略"，不退出播放器/关闭覆盖层
                    uiState.pendingSponsorSkip != null -> onDismissSponsorSkip()
                    showClickableControllers -> closeAllControllers()
                    else -> onExit()
                }
                true
            }

            Key.Menu, Key(763) -> {
                showInfoSeekController = false
                showMenuController = !showMenuController
                true
            }

            Key.MediaPlayPause -> {
                onPlay()
                true
            }

            Key.MediaPlay -> {
                if (uiState.playerState != PlayerState.Playing) onPlay()
                true
            }

            Key.MediaPause -> {
                if (uiState.playerState == PlayerState.Playing) onPause()
                true
            }

            else -> false
        }

    /** 覆盖层未打开时生效的方向/确认键（KeyUp 已被顶层过滤，此处均为 KeyDown）。 */
    fun handleNavigationKeys(
        event: KeyEvent,
        confirmKeys: List<Key>,
    ): Boolean =
        when (event.key) {
            in confirmKeys -> {
                if (event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.isLongPress) {
                        showMenuController = true
                    }
                } else {
                    when {
                        uiState.showBackToStart -> onBackToStart()
                        // 待确认跳过片段：确认键跳过（优先于播放/暂停）
                        uiState.pendingSponsorSkip != null -> onConfirmSponsorSkip()
                        else -> onPlay()
                    }
                }
                true
            }

            Key.DirectionUp -> {
                showListController = true
                true
            }

            Key.DirectionDown -> {
                showInfoSeekController = true
                true
            }

            Key.DirectionLeft, Key.MediaRewind -> {
                if (uiState.showSkipToNextEp) {
                    onCancelSkipToNextEp()
                } else {
                    showInfoSeekController = true
                    onDirectionLeft()
                }
                true
            }

            Key.DirectionRight, Key.MediaFastForward -> {
                showInfoSeekController = true
                onDirectionRight()
                true
            }

            else -> false
        }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val confirmKeys = listOf(Key.DirectionCenter, Key.Enter, Key.Spacebar)

        // 非 confirm 键的 KeyUp 事件消费掉
        if (event.type == KeyEventType.KeyUp && event.key !in confirmKeys) {
            return true
        }

        // 自定义快捷键
        if (handleCustomShortcut(event)) return true

        // 媒体/系统键（KeyUp 已被顶层过滤，此处均为 KeyDown）
        if (handleSystemKeys(event, confirmKeys)) return true

        // 覆盖层未打开时的方向/确认键
        if (!showClickableControllers && handleNavigationKeys(event, confirmKeys)) return true

        return false
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .focusable()
                .onPreviewKeyEvent { event ->
                    startControllerAutoHide()
                    handleKeyEvent(event)
                }.playerGestures(
                    totalDuration = { seekerState.value.totalDuration },
                    controllerVisible = { showInfoSeekController },
                    callbacks =
                        PlayerGestureCallbacks(
                            onSingleTap = {
                                if (!showClickableControllers) {
                                    showInfoSeekController = !showInfoSeekController
                                    if (showInfoSeekController) startControllerAutoHide()
                                } else {
                                    closeAllControllers()
                                }
                            },
                            onDoubleTap = { onPlay() },
                            onSeekDelta = { deltaMs ->
                                if (!isSeeking) goTime = seekerState.value.currentTime
                                goTime = (goTime + deltaMs).coerceIn(0L, seekerState.value.totalDuration)
                                isSeeking = true
                                showInfoSeekController = true
                                startSeekCountdown()
                            },
                            onSeekStart = { onSeekDragStart() },
                            onSeekEnd = { onSeekDragEnd(null) },
                            onBrightnessChange = { deltaY ->
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    currentBrightness = adjustBrightness(activity, deltaY, currentBrightness)
                                    gestureTipState.value =
                                        GestureTipState(
                                            isActive = true,
                                            type = GestureTipType.Brightness,
                                            value = currentBrightness,
                                        )
                                }
                            },
                            onVolumeChange = { deltaY ->
                                val audioManager =
                                    context.getSystemService(android.content.Context.AUDIO_SERVICE)
                                        as? android.media.AudioManager
                                if (audioManager != null) {
                                    val volumePercent = adjustVolume(audioManager, deltaY)
                                    gestureTipState.value =
                                        GestureTipState(
                                            isActive = true,
                                            type = GestureTipType.Volume,
                                            value = volumePercent.toFloat(),
                                        )
                                }
                            },
                        ),
                    gestureTipState = gestureTipState,
                ),
    ) {
        // 播放器画面与覆盖层始终基于黑色背景，固定使用深色主题，
        // 避免浅色应用下默认取色变成深色文字叠在黑底上不可见。
        // surfaceColor = Black：BVTheme 内部的 TvSurface 默认会用 colorScheme.surface
        // （深灰 #222222）铺满整屏，导致 4:3 视频在 16:9 屏上左右留白呈灰色。
        BVTheme(
            themeMode = ThemeMode.Dark,
            density = LocalDensity.current.density,
            surfaceColor = Color.Black,
        ) {
            // 视频画面 + 弹幕层
            content()

            // 调试信息
            if (Prefs.showPlayerDebugInfo) {
                Box(
                    modifier =
                        Modifier
                            .align(androidx.compose.ui.Alignment.TopStart)
                            .padding(8.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = seekerState.value.debugInfo,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 常显进度条
            if (showPersistentSeek && !showInfoSeekController) {
                VideoProgressSeek(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    duration = seekerState.value.totalDuration,
                    position = seekerState.value.currentTime,
                    bufferedPercentage = seekerState.value.bufferedPercentage,
                    isPersistentSeek = true,
                    segmentMarks = uiState.sponsorBlockMarks,
                    chapterMarks = uiState.chapterMarks,
                )
            }

            // 字幕
            if (overlayState.subtitleId != -1L) {
                BottomSubtitle(
                    subtitleData = overlayState.subtitleData,
                    currentTime = seekerState.value.currentTime,
                    fontSize =
                        androidx.compose.ui.unit.TextUnit(
                            overlayState.subtitleState.fontSize.toFloat(),
                            androidx.compose.ui.unit.TextUnitType.Sp,
                        ),
                    opacity = overlayState.subtitleState.opacity,
                    padding =
                        androidx.compose.ui.unit
                            .Dp(overlayState.subtitleState.bottomPadding.toFloat()),
                )
            }

            // 跳转提示
            SkipTips(
                showBackToStart = uiState.showBackToStart,
                showSkipToNextEp = uiState.showSkipToNextEp,
                previewTipText = uiState.previewTipText,
                shortcutTipText = uiState.shortcutTipText,
                shortcutTipKey = uiState.shortcutTipKey,
                sponsorBlockTip = uiState.sponsorBlockTip,
                // 仅待确认片段时可点击跳过（自动跳过提示为纯展示）
                onSponsorTipClick =
                    if (uiState.pendingSponsorSkip != null) {
                        { onConfirmSponsorSkip() }
                    } else {
                        null
                    },
            )

            // 播放状态提示
            PlayStateTips(
                isPlaying = uiState.playerState == PlayerState.Playing,
                isBuffering = uiState.isBuffering,
                isError = uiState.playerState is PlayerState.Error,
                errorMessage = (uiState.playerState as? PlayerState.Error)?.message,
                errorGuide = (uiState.playerState as? PlayerState.Error)?.guide,
            )

            // 手势提示（亮度/音量/倍速反馈）
            GestureTip(
                state = gestureTipState.value,
                modifier = Modifier.align(Alignment.Center),
            )

            // 相关视频
            RelatedVideosController(
                show = showRelatedVideosController,
                relatedVideos = overlayState.relatedVideos,
                onVideoClicked = onRelatedVideoClicked,
            )

            // 信息栏 + 进度条 + 按钮
            ControllerVideoInfo(
                modifier = Modifier.focusable(),
                show = showInfoSeekController,
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState.value,
                seekFocusKick = seekFocusKick,
                sponsorBlockMarks = uiState.sponsorBlockMarks,
                chapterMarks = uiState.chapterMarks,
                title = uiState.title,
                onlineWatching = uiState.onlineWatching,
                videoShot = uiState.videoShot,
                videoShotCache = videoShotCache,
                isPgc = isPgc,
                danmakuEnabled = overlayState.danmakuState.enabled,
                isLooping = isLooping,
                isPlaying = uiState.playerState == PlayerState.Playing,
                onDirectionLeft = ::onDirectionLeft,
                onDirectionRight = ::onDirectionRight,
                onSeekGoTime = ::onSeekGoTime,
                onSeekDragStart = ::onSeekDragStart,
                onSeekToPosition = ::onSeekToPosition,
                onSeekDragEnd = ::onSeekDragEnd,
                onPlayPause = {
                    onPlay()
                    startControllerAutoHide()
                },
                onDanmakuSwitchChange = {
                    onToggleDanmaku()
                    startControllerAutoHide()
                },
                onShowSettings = { showMenuController = true },
                onShowRelatedVideos = { showRelatedVideosController = true },
                onShowChapters = { showChapterListController = true },
                onGoToVideoInfo = onGoToVideoDetail,
                onToggleLoop = {
                    onToggleLoop()
                    startControllerAutoHide()
                },
                onGoToUpPage = onGoToUpPage,
                onShowInteraction = onShowInteraction,
                onShowComments = onShowComments,
            )

            // 分集列表
            VideoListController(
                show = showListController,
                currentCid = uiState.cid,
                videoList = overlayState.videoList,
                onPlayNewVideo = { item ->
                    onPlayNewVideo(item)
                    showListController = false
                    // 面板关闭会带走焦点，重新唤出控制栏并把焦点交还进度条
                    showInfoSeekController = true
                    startControllerAutoHide()
                    seekFocusKick++
                },
            )

            // 章节列表
            ChapterListController(
                show = showChapterListController,
                chapterMarks = uiState.chapterMarks,
                currentTimeMs = seekerState.value.currentTime,
                onSeekToChapter = { chapter ->
                    onGoTime(chapter.startMs)
                    if (uiState.playerState != PlayerState.Playing) onPlay()
                    showChapterListController = false
                    // 面板关闭会带走焦点，重新唤出控制栏并把焦点交还进度条
                    showInfoSeekController = true
                    startControllerAutoHide()
                    seekFocusKick++
                },
            )

            // 设置菜单
            MenuController(
                show = showMenuController,
                uiState = uiState,
                overlayState = overlayState,
                onResolutionChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetQuality(it)) },
                onCodecChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetVideoCodec(it)) },
                onAspectRatioChange = onAspectRatioChange,
                onPlaySpeedChange = onPlaySpeedChange,
                onAudioChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetAudio(it)) },
                onDanmakuSwitchChange = { types ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(types.toDanmakuEntities()))
                },
                onDanmakuSizeChange = { onDanmakuSettingChange(DanmakuSettingAction.SetScale(it)) },
                onDanmakuOpacityChange = { onDanmakuSettingChange(DanmakuSettingAction.SetOpacity(it)) },
                onDanmakuSpeedFactorChange = { onDanmakuSettingChange(DanmakuSettingAction.SetSpeedFactor(it)) },
                onDanmakuAreaChange = { onDanmakuSettingChange(DanmakuSettingAction.SetArea(it)) },
                onDanmakuMaskChange = { onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(it)) },
                onBlockEnabledChange = { onDanmakuSettingChange(DanmakuSettingAction.SetBlockEnabled(it)) },
                onBlockRulesChange = { onDanmakuSettingChange(DanmakuSettingAction.SetBlockRules(it)) },
                onMergeModeChange = { onDanmakuSettingChange(DanmakuSettingAction.SetMergeMode(it)) },
                onMergeConfigChange = { onDanmakuSettingChange(DanmakuSettingAction.SetMergeConfig(it)) },
                onSubtitleChange = { subtitle -> onSubtitleChange(subtitle) },
                onSubtitleSizeChange = { onSubtitleSettingChange(SubtitleSettingAction.SetFontSize(it)) },
                onSubtitleBackgroundOpacityChange = { onSubtitleSettingChange(SubtitleSettingAction.SetOpacity(it)) },
                onSubtitleBottomPadding = { onSubtitleSettingChange(SubtitleSettingAction.SetBottomPadding(it)) },
            )
        }
    }
}
