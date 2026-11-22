package dev.frost819.newbv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.frost819.newbv.app.ui.navigation.AppNavHost
import dev.frost819.newbv.app.ui.navigation.HomeRoute
import dev.frost819.newbv.app.ui.component.LocalUserFocusIntent
import dev.frost819.newbv.app.ui.component.UserFocusIntentSignal
import dev.frost819.newbv.core.focus.DpadDirection
import dev.frost819.newbv.core.focus.FocusShakeController
import dev.frost819.newbv.core.interaction.InteractionTracker
import dev.frost819.newbv.core.interaction.LocalInteractionTracker
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.SystemBarsEffect
import dev.frost819.newbv.data.datastore.Prefs
import javax.inject.Inject

/**
 * 主 Activity（单 Activity 架构）。
 *
 * 职责：
 * - SplashScreen 显示
 * - 交互模式追踪（触屏/遥控器）
 * - Navigation 宿主（[AppNavHost]）
 * - 主题模式 + 强调色 + density 从 Prefs 实时读取
 *
 * 所有页面通过 Navigation-Compose 导航，不启动新 Activity。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var interactionTracker: InteractionTracker

    /** 用户方向键信号：供 FocusSaver 焦点恢复区分框架噪声写入与用户接管。 */
    private val userFocusIntentSignal = UserFocusIntentSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            // 首帧响应修正：三者均为 StateFlow，无参重载首帧直接同步取 flow.value。
            // Prefs 在 Hilt 构造单例时已阻塞读盘（DatabaseModule.providePrefs），
            // 因此首帧即为用户已保存的缩放/主题/强调色，不再出现"默认尺寸渲染一帧后跳变"。
            // flow 订阅持续保活，运行中修改设置仍即时生效。
            val themeMode by Prefs.themeModeFlow.collectAsStateWithLifecycle()
            val density by Prefs.densityFlow.collectAsStateWithLifecycle()
            val accentColor by Prefs.accentColorFlow.collectAsStateWithLifecycle()

            BVTheme(themeMode = themeMode, density = density, accentColor = accentColor) {
                SystemBarsEffect()
                CompositionLocalProvider(
                    LocalInteractionTracker provides interactionTracker,
                    LocalUserFocusIntent provides userFocusIntentSignal,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(startDestination = HomeRoute)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        interactionTracker.onTouch()
        return super.onTouchEvent(event)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            interactionTracker.onDpadKey()
            // 用户方向键意图信号：在 super 之前计数，被页面消费的方向键同样是
            // 用户意图。FocusSaver 以「恢复期间是否有过按键」区分框架默认聚焦
            // 噪声与用户接管，不设时间窗口（详见 FocusSaver.UserFocusIntentSignal）。
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                -> userFocusIntentSignal.noteDirectionalKey()
            }
        }
        val consumed = super.dispatchKeyEvent(event)
        // 撞墙抖动：方向键未被 Compose 焦点系统与任何节点消费（super 返回 false），
        // 说明焦点已到可聚焦区域边界，广播一次抖动；长按连发不重复抖动。
        // 播放器 / 输入框等场景的方向键均在内部被消费，不会误触发。
        if (!consumed &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            val direction =
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> DpadDirection.Left
                    KeyEvent.KEYCODE_DPAD_RIGHT -> DpadDirection.Right
                    KeyEvent.KEYCODE_DPAD_UP -> DpadDirection.Up
                    KeyEvent.KEYCODE_DPAD_DOWN -> DpadDirection.Down
                    else -> null
                }
            if (direction != null) {
                FocusShakeController.bump(direction)
            }
        }
        return consumed
    }
}
