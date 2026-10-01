package dev.frost819.newbv.app.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 投屏解析出的播放目标，交由 UI 层（AppNavHost）转换为路由导航。
 *
 * newBV 为单 Activity + Navigation-Compose 架构，后台服务不能直接启动
 * 播放界面，因此服务侧只发布目标，由常驻的导航宿主消费。
 */
sealed interface CastLaunchTarget {
    /** 站内视频（UGC/PGC），复用 [dev.frost819.newbv.app.ui.navigation.VideoPlayerRoute]。 */
    data class Video(
        val aid: Long,
        val cid: Long,
        val epid: Int? = null,
        val seasonId: Int? = null,
        val title: String,
        val partTitle: String = "",
        val seekSeconds: Int = 0,
        val quality: Int? = null,
        val playSpeed: Float? = null,
        val danmakuEnabled: Boolean? = null,
    ) : CastLaunchTarget

    /** 直播间。 */
    data class Live(
        val roomId: Long,
        val title: String,
        val danmakuEnabled: Boolean? = null,
    ) : CastLaunchTarget

    /** 非站内视频：直接媒体直链（含 B 站 CDN 直链）。 */
    data class ExternalMedia(
        val url: String,
        val title: String,
        val seekSeconds: Int = 0,
        val isBilibiliMedia: Boolean = false,
        val playSpeed: Float? = null,
    ) : CastLaunchTarget
}

/**
 * 投屏导航请求总线。
 *
 * [publish] 由 [CastPlaybackLauncher] 调用；AppNavHost 以 collect + consume
 * 方式消费（消费后清除，避免返回播放器时重复导航）。
 */
object CastLaunchBus {
    private val _target = MutableStateFlow<CastLaunchTarget?>(null)
    val target = _target.asStateFlow()

    fun publish(target: CastLaunchTarget) {
        _target.value = target
    }

    /** 消费者导航完成后调用；仅当仍是自己发布的目标时清除，不吞掉更新的目标。 */
    fun consume(target: CastLaunchTarget) {
        if (_target.value === target) {
            _target.value = null
        }
    }
}
