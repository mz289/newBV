package dev.frost819.newbv.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.SurfaceDefaults
import androidx.compose.material3.MaterialTheme as CommonMaterialTheme
import androidx.compose.material3.Surface as CommonSurface
import androidx.compose.material3.darkColorScheme as commonDark
import androidx.compose.material3.lightColorScheme as commonLight
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.darkColorScheme as tvDark
import androidx.tv.material3.lightColorScheme as tvLight

/**
 * 当前是否处于深色主题（已计入用户的强制深色/浅色设置）。
 *
 * 接口下发官方日/夜双色（如剧集角标 badge_info）的 UI 据此取色，
 * 不要直接用 isSystemInDarkTheme()，其不感知应用内的主题模式设置。
 */
val LocalIsDark = staticCompositionLocalOf { true }

/**
 * new BV 根主题。
 *
 * 根据 [ThemeMode] 与系统状态选择深/浅色板，并提供自定义密度、字体缩放与强调色。
 * 可嵌套调用：播放器需要在浅色应用下固定使用深色主题时，用
 * `BVTheme(themeMode = ThemeMode.Dark, density = LocalDensity.current.density)` 包裹即可。
 *
 * 系统状态栏的同步由 [SystemBarsEffect] 负责，仅需在根主题处调用一次。
 *
 * @param themeMode 主题模式（跟随系统 / 深色 / 浅色）。
 * @param density 屏幕密度（TV 场景通常为 2.0）。
 * @param accentColor 强调色预设（默认品牌蓝紫）；派生色由 [AccentColor.resolve] 计算。
 * @param surfaceColor 主题 Surface 的底色，null 表示使用 `colorScheme.surface`。
 *                     嵌套调用时（如播放器强制深色）可传 [Color.Black] 覆盖默认深灰底色。
 * @param content 主题包裹的内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BVTheme(
    themeMode: ThemeMode = ThemeMode.FollowSystem,
    density: Float = 1f,
    accentColor: AccentColor = AccentColor.Brand,
    surfaceColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val systemIsDark = isSystemInDarkTheme()
    val isDark = themeMode.isDark(systemIsDark)
    val accent = accentColor.resolve()

    val tvColorScheme =
        if (isDark) {
            tvDark(
                primary = accent.primaryDark,
                onPrimary = Color.White,
                primaryContainer = accent.containerDark,
                onPrimaryContainer = Color.White,
                secondary = BVColors.Secondary,
                onSecondary = BVColors.DarkOnBackground,
                secondaryContainer = BVColors.DarkSurfaceVariant,
                onSecondaryContainer = BVColors.DarkOnSurface,
                background = BVColors.DarkBackground,
                onBackground = BVColors.DarkOnBackground,
                surface = BVColors.DarkSurface,
                onSurface = BVColors.DarkOnSurface,
                surfaceVariant = BVColors.DarkSurfaceVariant,
                onSurfaceVariant = BVColors.DarkOnSurfaceVariant,
                border = accent.borderDark,
            )
        } else {
            tvLight(
                primary = accent.primaryLight,
                onPrimary = Color.White,
                primaryContainer = accent.containerLight,
                onPrimaryContainer = BVColors.LightOnBackground,
                // secondary 作为强调色（选中态文字/图标）使用时需要足够对比度，
                // 浅色背景下使用加深变体，避免低对比导致文字发虚
                secondary = BVColors.SecondaryStrong,
                onSecondary = Color.White,
                secondaryContainer = BVColors.LightSurfaceVariant,
                onSecondaryContainer = BVColors.LightOnBackground,
                background = BVColors.LightBackground,
                onBackground = BVColors.LightOnBackground,
                surface = BVColors.LightSurface,
                onSurface = BVColors.LightOnSurface,
                surfaceVariant = BVColors.LightSurfaceVariant,
                onSurfaceVariant = BVColors.LightOnSurfaceVariant,
                border = accent.borderLight,
            )
        }

    val commonColorScheme =
        if (isDark) {
            commonDark(
                primary = accent.primaryDark,
                onPrimary = Color.White,
                primaryContainer = accent.containerDark,
                onPrimaryContainer = Color.White,
                secondary = BVColors.Secondary,
                onSecondary = BVColors.DarkOnBackground,
                secondaryContainer = BVColors.DarkSurfaceVariant,
                onSecondaryContainer = BVColors.DarkOnSurface,
                background = BVColors.DarkBackground,
                onBackground = BVColors.DarkOnBackground,
                surface = BVColors.DarkSurface,
                onSurface = BVColors.DarkOnSurface,
                surfaceVariant = BVColors.DarkSurfaceVariant,
                onSurfaceVariant = BVColors.DarkOnSurfaceVariant,
            )
        } else {
            commonLight(
                primary = accent.primaryLight,
                onPrimary = Color.White,
                primaryContainer = accent.containerLight,
                onPrimaryContainer = BVColors.LightOnBackground,
                secondary = BVColors.SecondaryStrong,
                onSecondary = Color.White,
                secondaryContainer = BVColors.LightSurfaceVariant,
                onSecondaryContainer = BVColors.LightOnBackground,
                background = BVColors.LightBackground,
                onBackground = BVColors.LightOnBackground,
                surface = BVColors.LightSurface,
                onSurface = BVColors.LightOnSurface,
                surfaceVariant = BVColors.LightSurfaceVariant,
                onSurfaceVariant = BVColors.LightOnSurfaceVariant,
            )
        }

    TvMaterialTheme(
        colorScheme = tvColorScheme,
        typography = BVTypography.tv,
    ) {
        CommonMaterialTheme(
            colorScheme = commonColorScheme,
            typography = BVTypography.common,
        ) {
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalIsDark provides isDark,
                LocalFocusOutlineColor provides
                    if (isDark) BVColors.FocusOutlineDark else BVColors.FocusOutlineLight,
                LocalDensity provides Density(density = density, fontScale = fontScale),
            ) {
                CommonSurface(color = Color.Transparent) {
                    TvSurface(
                        shape = RoundedCornerShape(0.dp),
                        // 显式给 contentColor：SurfaceDefaults.colors 默认按
                        // contentColorFor(containerColor) 自动配对，但 Black 不在配色方案中，
                        // 会回退到外层继承的 LocalContentColor（浅色 App 主题下是深色文字），
                        // 叠在黑底上不可见。旧值 colorScheme.surface 恰好命中 surface 才自动得到 onSurface。
                        colors =
                            SurfaceDefaults.colors(
                                containerColor = surfaceColor ?: tvColorScheme.surface,
                                contentColor = tvColorScheme.onSurface,
                            ),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 将当前主题色同步到系统状态栏（背景色 + 图标明暗）。
 *
 * 必须在根主题 [BVTheme] 内调用一次。嵌套主题（如播放器强制深色）不需要、
 * 也不应重复调用，否则退出后状态栏会停留在内层主题的颜色上。
 *
 * 状态栏跟随主题底色（surface）而非品牌色，避免浅色模式下顶部出现突兀的色块。
 */
@Composable
fun SystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val statusBarColor = TvMaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.context as Activity).window
        // Android 15 起状态栏固定为透明，仅旧系统需要设置背景色。
        if (android.os.Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarColor.toArgb()
        }
        // 状态栏图标外观由背景亮度决定：暗色状态栏配浅色图标，反之配深色图标
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            statusBarColor.luminance() > 0.5f
    }
}
