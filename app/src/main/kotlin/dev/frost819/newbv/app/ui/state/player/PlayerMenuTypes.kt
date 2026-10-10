package dev.frost819.newbv.app.ui.state.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/** 设置菜单导航 Tab。 */
enum class VideoPlayerMenuNavItem(
    val displayName: String,
    val icon: ImageVector,
) {
    PlaySpeed("倍速", Icons.Outlined.Speed),
    Picture("画质", Icons.Outlined.Image),
    Danmaku("弹幕", Icons.Outlined.ClearAll),
    ClosedCaption("字幕", Icons.Outlined.ClosedCaption),
}

/** 画质设置子项。 */
enum class VideoPlayerPictureMenuItem(
    val displayName: String,
) {
    Resolution("分辨率"),
    Codec("编码"),
    AspectRatio("宽高比"),
    Audio("音轨"),
}

/** 弹幕设置子项。 */
enum class VideoPlayerDanmakuMenuItem(
    val displayName: String,
) {
    Switch("开关"),
    Size("大小"),
    Opacity("不透明度"),
    SpeedFactor("速度"),
    Area("区域"),
    Mask("防遮挡"),
    Block("屏蔽"),
    BlockLevel("弹幕屏蔽等级"),
    Merge("合并重复"),
}

/** 字幕设置子项。 */
enum class VideoPlayerClosedCaptionMenuItem(
    val displayName: String,
) {
    Switch("开关"),
    Size("大小"),
    Opacity("不透明度"),
    Padding("间距"),
}

/**
 * 菜单焦点状态。
 *
 * 三态焦点模型：
 * - [MenuNav] — 焦点在右侧导航列表
 * - [Menu] — 焦点在中间子项列表
 * - [Items] — 焦点在最左侧选项值面板
 */
enum class MenuFocusState {
    MenuNav,
    Menu,
    Items,
}

/**
 * 菜单焦点状态数据，通过 CompositionLocal 传递给子组件。
 */
data class MenuFocusStateData(
    val focusState: MenuFocusState = MenuFocusState.MenuNav,
)

/**
 * 菜单焦点状态 CompositionLocal。
 */
val LocalMenuFocusStateData = compositionLocalOf { MenuFocusStateData() }
