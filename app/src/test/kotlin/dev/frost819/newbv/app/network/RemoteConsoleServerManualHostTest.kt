package dev.frost819.newbv.app.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 手动浏览器测试宿主：在本机 JVM 起动 [RemoteConsoleServer] 并注册全部
 * 功能标签，保持运行供浏览器访问控制台页做端到端验证（Robolectric 提供
 * assets 与 Context）。
 *
 * 运行方式：
 * 先设置环境变量 `NEWBV_MANUAL_REMOTE_CONSOLE=true`，再运行
 * `./gradlew :app:testDebugUnitTest --tests "*RemoteConsoleServerManualHostTest*" --rerun-tasks`
 * 端口写入 `app/build/remote-console-server.port`，浏览器访问
 * `http://127.0.0.1:<port>/`，验证完成后终止 gradle 进程即可。
 *
 * 常规测试通过 assumption 跳过；显式设置环境变量时才启动宿主。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteConsoleServerManualHostTest {
    @Test
    fun serveUntilInterrupted() {
        assumeTrue("仅用于手动浏览器验证", System.getenv("NEWBV_MANUAL_REMOTE_CONSOLE") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        RemoteConsoleServer.start(context)
        RemoteConsoleServer.register(DanmakuBlockRemoteFeature.create(context))
        RemoteConsoleServer.register(CookieLoginRemoteFeature.create { "登录成功（手动验证宿主不执行真实登录）" })
        val port = checkNotNull(RemoteConsoleServer.getUrl()).substringAfterLast(':')
        val portFile = File(System.getProperty("user.dir"), "build/remote-console-server.port")
        portFile.parentFile.mkdirs()
        portFile.writeText(port)
        println("REMOTE_CONSOLE_PORT=$port url=http://127.0.0.1:$port/")

        // 保持服务器运行供外部浏览器访问；上限 15 分钟，防止悬挂
        try {
            Thread.sleep(15 * 60_000L)
        } finally {
            RemoteConsoleServer.stop()
        }
    }
}
