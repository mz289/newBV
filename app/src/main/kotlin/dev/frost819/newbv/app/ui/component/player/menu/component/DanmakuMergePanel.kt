package dev.frost819.newbv.app.ui.component.player.menu.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.danmaku.config.DanmakuCountMark
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import kotlinx.coroutines.flow.first

/** 复用播放器的双列菜单、单选列表与数值调节器。高级设置在菜单内进入和返回。 */
@Composable
fun DanmakuMergeMenuPanel(
    modifier: Modifier = Modifier,
    mode: DanmakuMergeMode,
    config: DanmakuMergeConfig,
    onModeChange: (DanmakuMergeMode) -> Unit,
    onConfigChange: (DanmakuMergeConfig) -> Unit,
    onFocusBackToParent: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    var commonSelected by remember { mutableStateOf(MergeSetting.Switch) }
    var advancedSelected by remember { mutableStateOf(MergeSetting.EditDistance) }
    val selected = if (advanced) advancedSelected else commonSelected
    var focusValue by remember { mutableStateOf(false) }
    val categoryFocus = remember { FocusRequester() }
    val valueFocus = remember { FocusRequester() }
    val commonListState = rememberLazyListState()
    val advancedListState = rememberLazyListState()
    val settings = MergeSetting.entries.filter { it.advanced == advanced }

    fun enterAdvanced() {
        advanced = true
        advancedSelected = MergeSetting.EditDistance
    }

    fun back() {
        if (advanced) {
            advanced = false
            commonSelected = MergeSetting.Advanced
        } else {
            onFocusBackToParent()
        }
    }
    LaunchedEffect(advanced) {
        if (advanced || selected == MergeSetting.Advanced) {
            val state = if (advanced) advancedListState else commonListState
            val target = if (advanced) MergeSetting.EditDistance else MergeSetting.Advanced
            state.scrollToItem(settings.indexOf(target))
            // LazyColumn 的条目在布局时才创建，等待目标入屏后再恢复焦点。
            snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.key == target.name } }.first { it }
            withFrameNanos {}
            categoryFocus.requestFocus()
        }
    }
    BackHandler(enabled = advanced) { back() }
    LaunchedEffect(selected, focusValue) {
        if (focusValue) {
            valueFocus.requestFocus()
            focusValue = false
        }
    }
    Row(modifier = modifier.fillMaxHeight()) {
        val returnToCategories: () -> Unit = { categoryFocus.requestFocus() }
        MergeSettingValue(
            modifier = Modifier.width(216.dp).padding(horizontal = 8.dp).focusRequester(valueFocus),
            setting = selected,
            mode = mode,
            config = config,
            onModeChange = onModeChange,
            onConfigChange = onConfigChange,
            onAdvanced = ::enterAdvanced,
            onBack = ::back,
            onFocusBackToParent = returnToCategories,
        )
        LazyColumn(
            state = if (advanced) advancedListState else commonListState,
            modifier =
                Modifier.width(216.dp).onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionRight, Key.Back, Key.Escape -> {
                            if (it.type == KeyEventType.KeyDown) back()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (it.type == KeyEventType.KeyDown) valueFocus.requestFocus()
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(settings, key = { it.name }) { setting ->
                MenuListItem(
                    modifier = if (selected == setting) Modifier.focusRequester(categoryFocus) else Modifier,
                    text = setting.label,
                    selected = selected == setting,
                    selectionFollowsFocus = true,
                    onFocus = {
                        if (setting.advanced) advancedSelected = setting else commonSelected = setting
                    },
                    onClick = {
                        if (setting.advanced) advancedSelected = setting else commonSelected = setting
                        when (setting) {
                            MergeSetting.Advanced -> enterAdvanced()
                            MergeSetting.Return -> back()
                            else -> focusValue = true
                        }
                    },
                )
            }
        }
    }
}

private enum class MergeSetting(
    val label: String,
    val advanced: Boolean = false,
) {
    Switch("开关"),
    Window("时间窗口"),
    CrossMode("合并不同类型"),
    SkipSubtitle("放过字幕弹幕"),
    SkipAdvanced("放过高级弹幕"),
    SkipBottom("放过底部弹幕"),
    MarkPosition("数量标记"),
    MarkThreshold("数量标记阈值"),
    Enlarge("合并后增大字号"),
    Drop("自动弹幕优选"),
    Scroll("超长固定弹幕转滚动"),
    Advanced("高级设置"),
    Reset("恢复默认设置"),
    EditDistance("编辑距离阈值", true),
    Cosine("词频向量阈值", true),
    Pinyin("识别谐音弹幕", true),
    TrimWidth("全角转半角", true),
    TrimSpace("忽略空白", true),
    TrimEnding("忽略尾部标点", true),
    Representative("显示时间百分位", true),
    PreferFixed("优先固定弹幕", true),
    FilterBeforeMerge("合并前执行屏蔽", true),
    Return("返回", true),
}

@Composable
private fun MergeSettingValue(
    modifier: Modifier,
    setting: MergeSetting,
    mode: DanmakuMergeMode,
    config: DanmakuMergeConfig,
    onModeChange: (DanmakuMergeMode) -> Unit,
    onConfigChange: (DanmakuMergeConfig) -> Unit,
    onAdvanced: () -> Unit,
    onBack: () -> Unit,
    onFocusBackToParent: () -> Unit,
) {
    val toggleValue =
        when (setting) {
            MergeSetting.Switch -> mode != DanmakuMergeMode.Off
            MergeSetting.CrossMode -> config.crossMode
            MergeSetting.SkipSubtitle -> config.skipSubtitle
            MergeSetting.SkipAdvanced -> config.skipAdvanced
            MergeSetting.SkipBottom -> config.skipBottom
            MergeSetting.Enlarge -> config.enlarge
            MergeSetting.Pinyin -> config.recognizePinyin
            MergeSetting.TrimWidth -> config.trimWidth
            MergeSetting.TrimSpace -> config.trimSpace
            MergeSetting.TrimEnding -> config.trimEnding
            MergeSetting.PreferFixed -> config.preferFixedMode
            MergeSetting.FilterBeforeMerge -> config.filterBeforeMerge
            else -> null
        }
    if (toggleValue != null) {
        RadioMenuList(
            modifier = modifier,
            items = listOf("关闭", "开启"),
            selected = if (toggleValue) 1 else 0,
            onSelectedChanged = { index ->
                val enabled = index == 1
                if (setting == MergeSetting.Switch) {
                    onModeChange(if (enabled) DanmakuMergeMode.Similar else DanmakuMergeMode.Off)
                } else {
                    onConfigChange(
                        when (setting) {
                            MergeSetting.CrossMode -> config.copy(crossMode = enabled)
                            MergeSetting.SkipSubtitle -> config.copy(skipSubtitle = enabled)
                            MergeSetting.SkipAdvanced -> config.copy(skipAdvanced = enabled)
                            MergeSetting.SkipBottom -> config.copy(skipBottom = enabled)
                            MergeSetting.Enlarge -> config.copy(enlarge = enabled)
                            MergeSetting.Pinyin -> config.copy(recognizePinyin = enabled)
                            MergeSetting.TrimWidth -> config.copy(trimWidth = enabled)
                            MergeSetting.TrimSpace -> config.copy(trimSpace = enabled)
                            MergeSetting.TrimEnding -> config.copy(trimEnding = enabled)
                            MergeSetting.PreferFixed -> config.copy(preferFixedMode = enabled)
                            MergeSetting.FilterBeforeMerge -> config.copy(filterBeforeMerge = enabled)
                            else -> config
                        },
                    )
                }
            },
            onFocusBackToParent = onFocusBackToParent,
        )
        return
    }
    when (setting) {
        MergeSetting.Window ->
            MergeNumberValue(modifier, config.windowSeconds, 1..120, "${config.windowSeconds} 秒", onFocusBackToParent) {
                onConfigChange(config.copy(windowSeconds = it))
            }
        MergeSetting.MarkThreshold ->
            MergeNumberValue(
                modifier,
                config.markThreshold,
                1..1000,
                "大于 ${config.markThreshold} 条",
                onFocusBackToParent,
            ) {
                onConfigChange(config.copy(markThreshold = it))
            }
        MergeSetting.EditDistance ->
            MergeNumberValue(
                modifier,
                config.editDistanceThreshold,
                0..20,
                if (config.editDistanceThreshold ==
                    0
                ) {
                    "禁用"
                } else {
                    "≤ ${config.editDistanceThreshold}"
                },
                onFocusBackToParent,
            ) {
                onConfigChange(config.copy(editDistanceThreshold = it))
            }
        MergeSetting.Cosine ->
            MergeNumberValue(
                modifier,
                config.cosineThreshold,
                0..101,
                if (config.cosineThreshold >
                    100
                ) {
                    "禁用"
                } else {
                    "${config.cosineThreshold}%"
                },
                onFocusBackToParent,
            ) {
                onConfigChange(config.copy(cosineThreshold = it))
            }
        MergeSetting.Representative ->
            MergeNumberValue(
                modifier,
                config.representativePercent,
                0..100,
                "${config.representativePercent}%",
                onFocusBackToParent,
            ) {
                onConfigChange(config.copy(representativePercent = it))
            }
        MergeSetting.Drop ->
            MergeNumberValue(
                modifier,
                config.dropThreshold,
                0..9999,
                if (config.dropThreshold == 0) "关闭（0）" else "密度阈值 ${config.dropThreshold}",
                onFocusBackToParent,
            ) { onConfigChange(config.copy(dropThreshold = it)) }
        MergeSetting.Scroll ->
            MergeNumberValue(
                modifier,
                config.scrollThreshold,
                0..9999,
                if (config.scrollThreshold == 0) "关闭（0）" else "超过 ${config.scrollThreshold} 像素",
                onFocusBackToParent,
            ) { onConfigChange(config.copy(scrollThreshold = it)) }
        MergeSetting.MarkPosition ->
            RadioMenuList(
                modifier = modifier,
                items = DanmakuCountMark.entries.map { it.displayName },
                selected = config.markPosition.ordinal,
                onSelectedChanged = { onConfigChange(config.copy(markPosition = DanmakuCountMark.entries[it])) },
                onFocusBackToParent = onFocusBackToParent,
            )
        MergeSetting.Advanced, MergeSetting.Return, MergeSetting.Reset ->
            RadioMenuList(
                modifier = modifier,
                items = if (setting == MergeSetting.Reset) listOf("取消", "恢复默认") else listOf(setting.label),
                selected = 0,
                onSelectedChanged = {
                    when (setting) {
                        MergeSetting.Advanced -> onAdvanced()
                        MergeSetting.Return -> onBack()
                        MergeSetting.Reset -> if (it == 1) onConfigChange(DanmakuMergeConfig())
                    }
                },
                onFocusBackToParent = onFocusBackToParent,
            )
        else -> Unit
    }
}

@Composable
private fun MergeNumberValue(
    modifier: Modifier,
    value: Int,
    range: IntRange,
    text: String,
    onFocusBackToParent: () -> Unit,
    onChange: (Int) -> Unit,
) {
    StepLessMenuItem(
        modifier = modifier,
        value = value.toFloat(),
        step = 1f,
        range = range.first.toFloat()..range.last.toFloat(),
        text = text,
        onValueChange = { onChange(it.toInt()) },
        onFocusBackToParent = onFocusBackToParent,
    )
}
