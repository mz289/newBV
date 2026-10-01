package dev.frost819.newbv.app.cast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.frost819.newbv.app.cast.server.CastHttpServer
import dev.frost819.newbv.app.cast.server.CastNetworkUtil
import dev.frost819.newbv.app.cast.server.CastRequestLogger
import dev.frost819.newbv.app.cast.server.CastSsdpServer
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 投屏接收端前台存活的后台服务：持有组播锁，运行 SSDP 与 HTTP/SOAP 服务。
 *
 * 仅在应用存活期间有意义（播放发生在本应用内），因此使用普通启动的
 * Service 并由应用前后台切换时保活（见 BVApplication），不申请
 * 前台服务类型与常驻通知。
 */
@AndroidEntryPoint
class CastReceiverService : Service() {
    private val logger = Loggers.get("CastReceiverService")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null
    private var httpServer: CastHttpServer? = null
    private var ssdpServer: CastSsdpServer? = null

    @Inject
    lateinit var playbackLauncher: CastPlaybackLauncher

    override fun onCreate() {
        super.onCreate()
        val requestLogger = CastRequestLogger(applicationContext)
        val uuid = Prefs.castReceiverUuid.ifBlank {
            UUID.randomUUID().toString().also { Prefs.castReceiverUuid = it }
        }
        acquireMulticastLock()
        httpServer = CastHttpServer(
            uuid = uuid,
            requestLogger = requestLogger,
            playbackLauncher = playbackLauncher,
            scope = scope,
        )
        ssdpServer = CastSsdpServer(
            uuid = uuid,
            requestLogger = requestLogger,
            scope = scope,
        )
        runCatching {
            httpServer?.start()
            ssdpServer?.start()
            requestLogger.log("Cast receiver active addresses=${CastNetworkUtil.localIpv4Addresses()}")
        }.onFailure {
            logger.warn(it) { "Start cast receiver failed" }
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onDestroy() {
        ssdpServer?.stop()
        httpServer?.stop()
        ssdpServer = null
        httpServer = null
        multicastLock?.release()
        multicastLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireMulticastLock() {
        val wifiManager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("newBV-CastReceiver").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, CastReceiverService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastReceiverService::class.java))
        }
    }
}
