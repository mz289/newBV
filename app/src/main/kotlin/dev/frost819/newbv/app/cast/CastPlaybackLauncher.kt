package dev.frost819.newbv.app.cast

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.frost819.newbv.app.MainActivity
import dev.frost819.newbv.app.cast.protocol.CastContent
import dev.frost819.newbv.biliapi.repositories.VideoDetailRepository
import dev.frost819.newbv.biliapi.util.AvBvConverter
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把解析出的 [CastContent] 转换为站内播放目标并交给 [CastLaunchBus]。
 *
 * 解析优先级：直播间身份 → 站内视频身份（aid/bvid/cid/epid/seasonId，
 * 必要时通过详情接口补齐 cid）→ 直接媒体直链。
 * 发布目标后同时把主界面调到前台，确保 TV 端在后台时投屏也能立即出画。
 */
@Singleton
class CastPlaybackLauncher
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val videoDetailRepository: VideoDetailRepository,
    ) {
        private val logger = Loggers.get("CastPlaybackLauncher")
        private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
        private var lastLaunch: LastLaunch? = null

        suspend fun launch(content: CastContent): Boolean =
            withContext(mainDispatcher) {
                if (isDuplicateLaunch(content)) {
                    logger.info { "Skip duplicate cast launch: $content" }
                    return@withContext true
                }

                // 新投屏打断当前播放，避免两路声音叠加
                CastPlaybackSessionRegistry.pauseCurrent()

                when {
                    content.hasLiveIdentity -> {
                        navigate(
                            CastLaunchTarget.Live(
                                roomId = content.roomId!!.toLong(),
                                title = content.title ?: "投屏直播 ${content.roomId}",
                                danmakuEnabled = content.danmakuEnabled,
                            ),
                        )
                        true
                    }

                    content.hasVideoIdentity -> launchVideo(content)

                    content.hasDirectMedia -> {
                        navigate(
                            CastLaunchTarget.ExternalMedia(
                                url = content.directMediaUrl!!,
                                title = content.title ?: content.creator ?: "投屏视频",
                                seekSeconds = content.seekSeconds ?: 0,
                                isBilibiliMedia = content.isBilibiliDirectMedia,
                                playSpeed = content.playSpeed,
                            ),
                        )
                        true
                    }

                    else -> false
                }
            }

        private suspend fun launchVideo(content: CastContent): Boolean {
            val resolved =
                withContext(Dispatchers.IO) {
                    resolveVideo(content)
                } ?: return false

            navigate(
                CastLaunchTarget.Video(
                    aid = resolved.aid,
                    cid = resolved.cid,
                    epid = resolved.epid,
                    seasonId = resolved.seasonId,
                    title = resolved.title,
                    partTitle = resolved.partTitle,
                    seekSeconds = content.seekSeconds ?: 0,
                    quality = content.quality,
                    playSpeed = content.playSpeed,
                    danmakuEnabled = content.danmakuEnabled,
                ),
            )
            return true
        }

        private suspend fun resolveVideo(content: CastContent): ResolvedVideo? {
            val aid = content.aid
                ?: content.bvid?.let { runCatching { AvBvConverter.bv2av(it) }.getOrNull() }

            if (aid != null && aid > 0L && content.cid != null && content.cid > 0L) {
                return ResolvedVideo(
                    aid = aid,
                    cid = content.cid,
                    epid = content.epid,
                    seasonId = content.seasonId,
                    title = content.title ?: "投屏视频",
                    partTitle = content.partTitle ?: "",
                )
            }

            if (content.epid != null || content.seasonId != null) {
                val season = runCatching {
                    videoDetailRepository.getPgcVideoDetail(
                        epid = content.epid,
                        seasonId = content.seasonId,
                        preferApiType = Prefs.apiType,
                    )
                }.onFailure {
                    logger.warn(it) {
                        "Resolve PGC cast content failed: epid=${content.epid}, seasonId=${content.seasonId}"
                    }
                }.getOrNull()
                val episode = season?.episodes?.firstOrNull { episode ->
                    (content.epid != null && episode.epid == content.epid) ||
                        (aid != null && episode.aid == aid) ||
                        (content.cid != null && episode.cid == content.cid)
                } ?: season?.episodes?.firstOrNull()
                if (season != null && episode != null) {
                    return ResolvedVideo(
                        aid = episode.aid,
                        cid = episode.cid,
                        epid = episode.epid ?: content.epid,
                        seasonId = season.seasonId,
                        title = content.title ?: season.title,
                        partTitle = content.partTitle ?: episode.longTitle.ifBlank { episode.title },
                    )
                }
            }

            if (aid != null && aid > 0L) {
                val detail = runCatching {
                    videoDetailRepository.getVideoDetail(aid = aid, preferApiType = Prefs.apiType)
                }.onFailure {
                    logger.warn(it) { "Resolve UGC cast content failed: aid=$aid" }
                }.getOrNull()
                if (detail != null) {
                    val page = content.cid?.let { cid -> detail.pages.firstOrNull { it.cid == cid } }
                        ?: detail.pages.firstOrNull { it.cid == detail.cid }
                        ?: detail.pages.firstOrNull()
                    return ResolvedVideo(
                        aid = detail.aid,
                        cid = page?.cid ?: detail.cid,
                        epid = content.epid ?: detail.epid,
                        seasonId = content.seasonId,
                        title = content.title ?: detail.title,
                        partTitle = content.partTitle ?: page?.title.orEmpty(),
                    )
                }
            }

            return null
        }

        private fun navigate(target: CastLaunchTarget) {
            logger.info { "Navigate cast target: $target" }
            CastLaunchBus.publish(target)
            bringMainActivityToFront()
        }

        private fun bringMainActivityToFront() {
            runCatching {
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
            }.onFailure { logger.warn(it) { "Bring MainActivity to front failed" } }
        }

        private fun isDuplicateLaunch(content: CastContent): Boolean {
            val now = SystemClock.elapsedRealtime()
            val key = listOf(
                content.aid,
                content.bvid,
                content.cid,
                content.epid,
                content.seasonId,
                content.roomId,
                content.seekSeconds,
                content.playSpeed,
                content.danmakuEnabled,
                content.directMediaUrl,
            ).joinToString(separator = ":")
            val duplicate = lastLaunch?.let { it.key == key && now - it.atMillis < DUPLICATE_LAUNCH_WINDOW_MS } == true
            if (!duplicate) lastLaunch = LastLaunch(key, now)
            return duplicate
        }

        private data class ResolvedVideo(
            val aid: Long,
            val cid: Long,
            val epid: Int?,
            val seasonId: Int?,
            val title: String,
            val partTitle: String,
        )

        private data class LastLaunch(
            val key: String,
            val atMillis: Long,
        )

        private companion object {
            /** 官方客户端重试/多端点重复投递时 2s 内的重复内容视为同一次投屏。 */
            const val DUPLICATE_LAUNCH_WINDOW_MS = 2_000L
        }
    }
