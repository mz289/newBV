package dev.frost819.newbv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.frost819.newbv.app.cast.CastLaunchBus
import dev.frost819.newbv.app.cast.CastLaunchTarget
import dev.frost819.newbv.app.ui.screen.detail.videoDetailScreen
import dev.frost819.newbv.app.ui.screen.home.homeScreen
import dev.frost819.newbv.app.ui.screen.live.liveAreaScreen
import dev.frost819.newbv.app.ui.screen.live.liveFollowScreen
import dev.frost819.newbv.app.ui.screen.login.loginScreen
import dev.frost819.newbv.app.ui.screen.pgc.pgcFeatureScreen
import dev.frost819.newbv.app.ui.screen.pgc.pgcIndexScreen
import dev.frost819.newbv.app.ui.screen.player.externalMediaScreen
import dev.frost819.newbv.app.ui.screen.player.livePlayerScreen
import dev.frost819.newbv.app.ui.screen.player.videoPlayerScreen
import dev.frost819.newbv.app.ui.screen.search.searchResultScreen
import dev.frost819.newbv.app.ui.screen.settings.settingsScreen
import dev.frost819.newbv.app.ui.screen.user.followScreen
import dev.frost819.newbv.app.ui.screen.user.userSpaceScreen
import dev.frost819.newbv.app.ui.screen.user.userSwitchScreen
import dev.frost819.newbv.app.ui.navigation.ExternalMediaRoute
import dev.frost819.newbv.app.ui.navigation.LivePlayerRoute
import dev.frost819.newbv.app.ui.navigation.VideoPlayerRoute
import dev.frost819.newbv.core.log.Loggers

/**
 * 应用 Navigation 宿主。
 *
 * 单 Activity 架构：所有页面通过 Navigation-Compose 导航，无新 Activity 启动。
 * 路由定义见 [Routes]。
 *
 * @param navController 导航控制器，默认使用 [rememberNavController]
 * @param startDestination 起始路由，默认 [HomeRoute]
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute,
) {
    val logger = Loggers.get("AppNavHost")

    // 投屏导航：CastReceiverService 解析出播放目标后发布到总线，
    // 此处消费并替换当前播放页面（若正在播放）。目标在导航完成后清除，
    // 避免重新进入应用时重复导航
    LaunchedEffect(navController) {
        CastLaunchBus.target.collect { target ->
            if (target == null) return@collect
            logger.info { "[CAST] navigate to $target" }
            val route =
                when (target) {
                    is CastLaunchTarget.Video ->
                        VideoPlayerRoute(
                            aid = target.aid,
                            cid = target.cid,
                            epid = target.epid?.toLong(),
                            title =
                                if (target.partTitle.isBlank()) {
                                    target.title
                                } else {
                                    "${target.title} · ${target.partTitle}"
                                },
                            initialSpeed = target.playSpeed ?: 0f,
                            danmakuEnabled = target.danmakuEnabled,
                        )

                    is CastLaunchTarget.Live ->
                        LivePlayerRoute(
                            roomId = target.roomId,
                            title = target.title,
                        )

                    is CastLaunchTarget.ExternalMedia ->
                        ExternalMediaRoute(
                            url = target.url,
                            title = target.title,
                            seekSeconds = target.seekSeconds,
                            isBilibili = target.isBilibiliMedia,
                            initialSpeed = target.playSpeed ?: 0f,
                        )
                }
            navController.navigate(route) {
                when (target) {
                    is CastLaunchTarget.Video -> popUpTo<VideoPlayerRoute> { inclusive = true }
                    is CastLaunchTarget.Live -> popUpTo<LivePlayerRoute> { inclusive = true }
                    is CastLaunchTarget.ExternalMedia -> popUpTo<ExternalMediaRoute> { inclusive = true }
                }
                launchSingleTop = true
            }
            CastLaunchBus.consume(target)
        }
    }

    LaunchedEffect(navController) {
        var previousRoute: String? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route?.substringAfterLast('.') ?: "unknown"
            if (previousRoute != null && previousRoute != route) {
                logger.info { "[NAV] from=$previousRoute to=$route" }
            }
            previousRoute = route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        // ── 主流程 ────────────────────────────────────────────────────
        homeScreen(navController)
        searchResultScreen(navController)

        // ── 视频详情 ─────────────────────────────────────────────────
        videoDetailScreen(navController)

        // ── 直播 ─────────────────────────────────────────────────────
        liveAreaScreen(navController)
        liveFollowScreen(navController)

        // ── 播放器 ───────────────────────────────────────────────────
        videoPlayerScreen(navController)
        livePlayerScreen(navController)
        externalMediaScreen(navController)

        // ── 用户 ─────────────────────────────────────────────────────
        userSpaceScreen(navController)
        followScreen(navController)

        // ── PGC ──────────────────────────────────────────────────────
        pgcFeatureScreen(navController)
        pgcIndexScreen(navController)

        // ── 设置 ─────────────────────────────────────────────────────
        settingsScreen(navController)
        userSwitchScreen(navController)

        // ── 登录 ─────────────────────────────────────────────────────
        loginScreen(navController)
    }
}
