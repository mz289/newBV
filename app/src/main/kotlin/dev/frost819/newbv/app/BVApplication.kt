package dev.frost819.newbv.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import dev.frost819.newbv.app.cast.CastReceiverService
import dev.frost819.newbv.app.data.AccountRepositoryImpl
import dev.frost819.newbv.app.data.CookieLoginParser
import dev.frost819.newbv.app.network.CookieLoginRemoteFeature
import dev.frost819.newbv.app.network.DanmakuBlockRemoteFeature
import dev.frost819.newbv.app.network.RemoteConsoleServer
import dev.frost819.newbv.app.network.RemoteSearchEvents
import dev.frost819.newbv.app.network.SearchRemoteFeature
import dev.frost819.newbv.app.network.HttpServer
import dev.frost819.newbv.app.util.CacheManager
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.repositories.AuthRepository
import dev.frost819.newbv.biliapi.repositories.ChannelRepository
import dev.frost819.newbv.core.log.CrashHandler
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

/**
 * new BV 应用入口。
 *
 * 初始化顺序：
 * 1. Hilt 依赖注入（由 [HiltAndroidApp] 自动处理）：
 *    注入 [Prefs] 时由 `DatabaseModule.providePrefs` 完成磁盘加载初始化，
 *    先于一切 Prefs 消费者构造
 * 2. [CrashHandler] 全局崩溃处理（通过 Hilt 注入，构造时自动 install）
 * 3. [HttpServer] 本地日志管理服务器（通过 Hilt 注入，按需启动）
 */
@HiltAndroidApp
class BVApplication : Application() {
    private val logger = Loggers.get("BVApplication")

    @Inject
    lateinit var prefs: Prefs

    @Inject
    lateinit var crashHandler: CrashHandler

    @Inject
    lateinit var httpServer: HttpServer

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var accountRepository: AccountRepositoryImpl

    @Inject
    lateinit var playerHttpClientProvider: Provider<OkHttpClient>

    override fun onCreate() {
        super.onCreate()

        val buvid3 = prefs.buvid3
        val deviceCookies = prefs.deviceCookies
        val sessData = prefs.sessData
        val biliJct = prefs.biliJct
        val mid = prefs.uid.takeIf { it > 0 }
        val accessToken = prefs.accessToken

        authRepository.buvid3 = buvid3
        authRepository.deviceCookies = deviceCookies
        authRepository.sessionData = sessData
        authRepository.biliJct = biliJct
        authRepository.mid = mid
        authRepository.accessToken = accessToken
        BiliHttpApi.init(
            buvid3 = buvid3,
            deviceCookies = deviceCookies,
            sessData = sessData,
            biliJct = biliJct,
            mid = mid,
            accessToken = accessToken,
        )
        if (accessToken.isNotBlank() && prefs.buvid.isNotBlank()) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                channelRepository.initDefaultChannel(accessToken, prefs.buvid)
            }
        }

        // 双账号解析：启动时从多账号库同步解析账号凭证到 bili-api 层
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            accountRepository.syncParseAccountFromPrefs()
        }

        // 统一远程控制台常驻（参考 FongMi/TV：应用启动即启动，随进程存活），
        // 三个功能标签常驻注册，手机控制台页动态出现对应标签
        RemoteConsoleServer.start(this)
        RemoteConsoleServer.register(DanmakuBlockRemoteFeature.create(this))
        RemoteConsoleServer.register(
            CookieLoginRemoteFeature.create { input ->
                val authData =
                    runCatching { CookieLoginParser.parse(input) }.getOrElse { error ->
                        throw IllegalArgumentException(error.message ?: "Cookie 解析失败")
                    }
                accountRepository.addCookieUser(authData)
                "登录成功，本页可以关闭了"
            },
        )
        RemoteConsoleServer.register(
            SearchRemoteFeature.create { keyword -> RemoteSearchEvents.post(keyword) },
        )

        if (!prefs.buvid3FromSpi) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val spiResult = BiliHttpApi.fetchBuvid3FromSpi()
                if (spiResult != null) {
                    prefs.buvid3 = spiResult.buvid3
                    prefs.deviceCookies = spiResult.deviceCookies
                    prefs.buvid3FromSpi = true
                    authRepository.buvid3 = spiResult.buvid3
                    authRepository.deviceCookies = spiResult.deviceCookies
                    BiliHttpApi.buvid3 = spiResult.buvid3
                    BiliHttpApi.deviceCookies = spiResult.deviceCookies
                }
            }
        }

        logger.info { "Application started" }

        // 图片磁盘缓存上限：开启自动清理且设置了阈值时按阈值限制，否则不限制。
        // Coil 在每次写入时自动 LRU 淘汰超限条目（缓存满阈值自动清理）。
        val diskCacheMaxBytes =
            if (prefs.cacheAutoClean && prefs.cacheThreshold > 0) {
                prefs.cacheThreshold * CacheManager.BYTES_PER_MB
            } else {
                CacheManager.UNLIMITED_DISK_CACHE_BYTES
            }
        val imageDiskCache =
            DiskCache
                .Builder()
                .directory(
                    File(cacheDir, CacheManager.IMAGE_CACHE_DIR).absolutePath.toPath(),
                ).maxSizeBytes(diskCacheMaxBytes)
                .build()

        coil3.SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(this)
                .crossfade(true)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCache(imageDiskCache)
                .components {
                    add(OkHttpNetworkFetcherFactory(OkHttpClient()))
                }.build()
        }

        // SSL 初始化涉及系统 CA 库读取，在 IO 线程提前构建播放器共享 client。
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            playerHttpClientProvider.get()
        }

        // 启动时检查缓存阈值，超限自动 LRU 清理（检查时机 = App 启动 + 缓存写入后）
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            CacheManager(this@BVApplication).checkCache()
        }

        // 投屏接收：随应用启动，并在每次界面回到前台时补启（进程后台被杀后由界面拉起）
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    CastReceiverService.start(this@BVApplication)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        CastReceiverService.start(this)
    }
}
