package dev.frost819.newbv.player.impl.exo

import android.content.Context
import dev.frost819.newbv.player.VideoPlayerOptions
import dev.frost819.newbv.player.racing.CdnRacingRegistry
import okhttp3.OkHttpClient

/**
 * ExoPlayer 播放器工厂。
 *
 * 创建 [ExoMediaPlayer] 实例，供 Hilt Module 注入或直接使用。
 *
 * @param client 播放与 CDN 请求共用的 OkHttpClient
 * @param racingRegistry CDN 竞速注册表（null 时竞速层不可用，忽略 cdnRacingEnabled）
 */
class ExoPlayerFactory(
    val client: OkHttpClient,
    private val racingRegistry: CdnRacingRegistry? = null,
) {
    fun create(
        context: Context,
        options: VideoPlayerOptions,
    ): ExoMediaPlayer = ExoMediaPlayer(context, options, client, racingRegistry)
}
