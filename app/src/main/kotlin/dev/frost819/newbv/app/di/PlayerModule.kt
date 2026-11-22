package dev.frost819.newbv.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.frost819.newbv.app.util.MediaCodecVideoCapabilityProvider
import dev.frost819.newbv.app.util.VideoCapabilityProvider
import dev.frost819.newbv.player.CdnHostModel
import dev.frost819.newbv.player.OkHttpUtil
import dev.frost819.newbv.player.impl.exo.ExoPlayerFactory
import dev.frost819.newbv.player.racing.CdnRacingRegistry
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * 播放器相关 Hilt 模块。
 *
 * 提供播放器工厂等播放器相关的依赖绑定。
 * VideoInfoRepository 已通过 `@Inject constructor` + `@Singleton` 自动绑定，无需在此声明。
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    /** 播放请求共用单例，避免每轮播放重建 SSL 配置和连接池。 */
    @Provides
    @Singleton
    fun providePlayerHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient = OkHttpUtil.generateCustomSslOkHttpClient(context)

    /**
     * 提供 [ExoPlayerFactory] 单例。
     *
     * 用于创建 Media3 ExoPlayer 实例。通过工厂模式抽象播放器创建，
     * 便于未来扩展其他播放器实现。附带 CDN 竞速注册表（会话级单例，
     * 主机池/熔断状态跨播放会话保留）。
     */
    @Provides
    @Singleton
    fun provideExoPlayerFactory(
        playerHttpClient: OkHttpClient,
        cdnRacingRegistry: CdnRacingRegistry,
    ): ExoPlayerFactory = ExoPlayerFactory(playerHttpClient, cdnRacingRegistry)

    /** 提供 CDN 主机状态模型：竞速层与播放侧回报共用的进程级单例。 */
    @Provides
    @Singleton
    fun provideCdnHostModel(): CdnHostModel = CdnHostModel()

    /**
     * 提供 CDN 竞速注册表：已知 upgcxcode 主机池、原生回退窗口、
     * 连续失败熔断与完整性熔断（PR #31 thread-ripper 的 page 级状态）。
     */
    @Provides
    @Singleton
    fun provideCdnRacingRegistry(
        cdnHostModel: CdnHostModel,
    ): CdnRacingRegistry = CdnRacingRegistry(cdnHostModel)

    /**
     * 提供设备视频解码能力查询器。
     *
     * 基于 Media3 `MediaCodecVideoRenderer.supportsFormat` 实现，供播放选流时
     * 过滤超出本机解码能力的编码/画质组合。
     */
    @Provides
    @Singleton
    fun provideVideoCapabilityProvider(
        @ApplicationContext context: Context,
    ): VideoCapabilityProvider = MediaCodecVideoCapabilityProvider(context)
}
