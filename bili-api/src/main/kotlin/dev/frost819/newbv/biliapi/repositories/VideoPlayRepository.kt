package dev.frost819.newbv.biliapi.repositories

import bilibili.app.playerunite.v1.PlayerGrpcKt
import bilibili.app.playerunite.v1.playViewUniteReq
import bilibili.community.service.dm.v1.DMGrpcKt
import bilibili.community.service.dm.v1.dmSegMobileReq
import bilibili.community.service.dm.v1.dmViewReq
import bilibili.pgc.gateway.player.v2.PlayURLGrpcKt
import bilibili.pgc.gateway.player.v2.playViewReq
import bilibili.playershared.videoVod
import bilibili.pgc.gateway.player.v2.CodeType as PgcCodeType
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.CodeType
import dev.frost819.newbv.biliapi.entity.PlayData
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMask
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskType
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMeta
import dev.frost819.newbv.biliapi.entity.danmaku.toDanmakuMeta
import dev.frost819.newbv.biliapi.entity.video.Subtitle
import dev.frost819.newbv.biliapi.entity.video.VideoShot
import dev.frost819.newbv.biliapi.grpc.utils.handleGrpcException
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.danmaku.DanmakuData
import dev.frost819.newbv.biliapi.http.entity.video.ViewPoint
import dev.frost819.newbv.biliapi.util.BiliLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class VideoPlayRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
    private val parseAccountRepository: ParseAccountRepository,
) {
    private val playerStub
        get() =
            runCatching {
                PlayerGrpcKt.PlayerCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    private val pgcPlayerStub
        get() =
            runCatching {
                PlayURLGrpcKt.PlayURLCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    private val danmakuStub
        get() =
            runCatching {
                DMGrpcKt.DMCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    /**
     * 获取播放数据。
     *
     * 双账号解析（参考油猴脚本「哔哩哔哩双账号助手-A身份-B大会员权益」）：
     * 启用解析账号（B，大会员）且与当前账号（A）不同时，播放地址优先以 B 的 Cookie
     * 走 Web 通道解析（解锁会员画质/会员专享内容），心跳、历史等仍以 A 身份上报；
     * B 解析失败（网络/风控/权限）时回退 A 的正常解析路径，保证始终可播。
     *
     * @param epid 非 0 时走 PGC（番剧/影视）通道
     */
    suspend fun getPlayData(
        aid: Long,
        cid: Long,
        preferApiType: ApiType,
        epid: Int = 0,
    ): PlayData {
        val parseCookie = parseAccountRepository.playCookie(authRepository.mid)
        if (parseCookie != null) {
            try {
                val playData = getWebPlayDataWithCookie(aid = aid, cid = cid, epid = epid, cookie = parseCookie)
                BiliLogger.info { "play data resolved with parse account: [aid=$aid, cid=$cid, epid=$epid]" }
                return playData
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BiliLogger.error(e) { "parse account play data failed, fallback to current account: [aid=$aid, cid=$cid, epid=$epid]" }
            }
        }

        return if (epid != 0) {
            try {
                getPgcPlayData(cid = cid, epid = epid, preferApiType = preferApiType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isPermissionError(e.message)) throw e
                // PGC 通道技术性失败（网络/风控/解析）时回退 UGC 通道兜底，保证免费剧集仍可播；
                // 权限类错误携带引导信息（会员/充电/付费），直接抛出供 UI 分类展示
                BiliLogger.error(e) { "pgc play data failed, fallback to ugc channel: [epid=$epid, cid=$cid]" }
                getUgcPlayData(aid = aid, cid = cid, preferApiType = preferApiType)
            }
        } else {
            getUgcPlayData(aid = aid, cid = cid, preferApiType = preferApiType)
        }
    }

    /**
     * 以指定 Cookie（解析账号身份）走 Web 通道获取播放数据。
     *
     * 解析账号可能仅有 Cookie（无 access_token），故固定走 Web playurl 通道；
     * fnval=4048 + qn=127 + fourk=1 与 UGC Web 通道一致，会员账号可返回全部画质。
     */
    private suspend fun getWebPlayDataWithCookie(
        aid: Long,
        cid: Long,
        epid: Int,
        cookie: String,
    ): PlayData =
        if (epid != 0) {
            PlayData.fromPgcWebPlayUrlData(
                BiliHttpApi
                    .getPgcPlayUrl(
                        epId = epid,
                        cid = cid,
                        cookieOverride = cookie,
                    ).getResponseData(),
            )
        } else {
            PlayData.fromPlayUrlData(
                BiliHttpApi
                    .getVideoPlayUrl(
                        av = aid,
                        cid = cid,
                        fnval = 4048,
                        qn = 127,
                        fnver = 0,
                        fourk = 1,
                        cookieOverride = cookie,
                    ).getResponseData(),
            )
        }

    /** 错误消息是否携带权限引导信息（会员/充电/付费），此类错误直接抛出供 UI 引导。 */
    private fun isPermissionError(message: String?): Boolean {
        val text = message ?: return false
        return text.contains("会员") || text.contains("充电") || text.contains("付费") || text.contains("购买")
    }

    /**
     * 获取 UGC（普通投稿）播放数据。
     *
     * Web 走 /x/player/playurl 单请求；App 走 playViewUnite 三编码并发合并。
     */
    private suspend fun getUgcPlayData(
        aid: Long,
        cid: Long,
        preferApiType: ApiType,
    ): PlayData =
        when (preferApiType) {
            ApiType.Web -> {
                val playUrlData =
                    BiliHttpApi
                        .getVideoPlayUrl(
                            av = aid,
                            cid = cid,
                            fnval = 4048,
                            qn = 127,
                            fnver = 0,
                            fourk = 1,
                        ).getResponseData()
                PlayData.fromPlayUrlData(playUrlData)
            }

            ApiType.App -> {
                withContext(Dispatchers.IO) {
                    val codecTypes =
                        listOf(
                            CodeType.Code264,
                            CodeType.Code265,
                            CodeType.CodeAv1,
                        )
                    val replies =
                        codecTypes
                            .map { codecType ->
                                async {
                                    val playUniteReply =
                                        runCatching {
                                            playerStub?.playViewUnite(
                                                playViewUniteReq {
                                                    vod =
                                                        videoVod {
                                                            this.aid = aid
                                                            this.cid = cid
                                                            fnval = 4048
                                                            qn = 127
                                                            fnver = 0
                                                            fourk = true
                                                            forceHost = 2
                                                            preferCodecType = codecType.toPlayerSharedCodeType()
                                                        }
                                                },
                                            ) ?: throw IllegalStateException("Player stub is not initialized")
                                        }.onFailure {
                                            // dont throw
                                            runCatching { handleGrpcException(it) }
                                                .onFailure {
                                                    BiliLogger.error(it) {
                                                        "get play data failed: [aid=$aid, cid=$cid, codec=$codecType, api=$preferApiType]"
                                                    }
                                                }
                                        }.getOrNull()
                                    playUniteReply
                                }
                            }.awaitAll()
                    val result =
                        replies
                            .map {
                                it?.let { PlayData.fromPlayViewUniteReply(it) }
                            }.reduce { acc, playData ->
                                acc?.let { playData?.let { acc + playData } ?: acc } ?: playData
                            } ?: throw IllegalStateException("All codec types are failed to get play data")
                    result
                }
            }
        }

    /**
     * 获取 PGC（番剧/影视）播放数据。
     *
     * PGC 内容走专用播放通道（Web /pgc/player/web/v2/playurl、App pgc.gateway PlayView）：
     * 会员专享剧集在无权限时返回试看流或明确的权限错误（如“大会员专享限制”）；
     * UGC 通道对会员专享剧集一律返回 -404“啥都木有”，无法区分权限与内容缺失。
     *
     * @param cid 视频 CID
     * @param epid 剧集 EP ID（非 0 时启用本通道）
     * @param preferApiType 接口类型
     */
    private suspend fun getPgcPlayData(
        cid: Long,
        epid: Int,
        preferApiType: ApiType,
    ): PlayData =
        when (preferApiType) {
            ApiType.Web ->
                PlayData.fromPgcWebPlayUrlData(
                    BiliHttpApi
                        .getPgcPlayUrl(
                            epId = epid,
                            cid = cid,
                        ).getResponseData(),
                )

            ApiType.App ->
                withContext(Dispatchers.IO) {
                    // PGC 编码枚举仅 264/265，无 AV1
                    val codecTypes = listOf(PgcCodeType.CODE264, PgcCodeType.CODE265)
                    val results =
                        codecTypes
                            .map { codecType ->
                                async {
                                    runCatching {
                                        val reply =
                                            pgcPlayerStub?.playView(
                                                playViewReq {
                                                    this.epid = epid.toLong()
                                                    this.cid = cid
                                                    qn = 127
                                                    fnval = 4048
                                                    fnver = 0
                                                    fourk = true
                                                    forceHost = 2
                                                    preferCodecType = codecType
                                                },
                                            ) ?: throw IllegalStateException("PGC player stub is not initialized")
                                        PlayData.fromPgcPlayViewReply(reply)
                                    }.recoverCatching { error ->
                                        // 统一转换为带 B 站业务消息的异常（如“大会员专享限制”），
                                        // 供播放器的无权限引导按关键词分类
                                        if (error is CancellationException) throw error
                                        throw runCatching { handleGrpcException(error) }.exceptionOrNull() ?: error
                                    }
                                }
                            }.awaitAll()

                    val playDataList = results.mapNotNull { it.getOrNull() }
                    if (playDataList.isNotEmpty()) {
                        playDataList.reduce { acc, playData -> acc + playData }
                    } else {
                        // 全部失败时抛首个业务异常，保留 B 站原始错误消息
                        throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                            ?: IllegalStateException("All codec types are failed to get pgc play data")
                    }
                }
        }

    suspend fun getSubtitle(
        aid: Long,
        cid: Long,
        preferApiType: ApiType,
    ): List<Subtitle> =
        when (preferApiType) {
            ApiType.Web -> {
                val response =
                    BiliHttpApi
                        .getVideoMoreInfo(
                            avid = aid,
                            cid = cid,
                        ).getResponseData()
                response.subtitle
                    ?.subtitles
                    ?.map { Subtitle.fromSubtitleItem(it) }
                    ?: emptyList()
            }

            ApiType.App -> {
                val dmViewReply =
                    runCatching {
                        danmakuStub?.dmView(
                            dmViewReq {
                                pid = aid
                                oid = cid
                                type = 1
                            },
                        )
                    }.onFailure { handleGrpcException(it) }.getOrThrow()
                dmViewReply
                    ?.subtitle
                    ?.subtitlesList
                    ?.map { Subtitle.fromSubtitleItem(it) }
                    ?: emptyList()
            }
        }

    /**
     * 获取视频章节（看点）列表。
     *
     * 固定走 Web playerinfo 通道（/x/player/wbi/v2，已有 WBI 签名拦截器自动处理）：
     * App gRPC 的 PlayArc 不含 view_points 字段；该接口未登录可用，单一数据源最简。
     * 章节按分 P（cid）独立，多 P 视频需按当前 cid 查询。
     *
     * @param aid 视频 AV 号
     * @param cid 视频 CID
     * @return 章节列表，无章节时为空列表
     * @throws Exception 网络失败或响应解析失败时抛出，由调用方决定兜底策略
     */
    suspend fun getViewPoints(
        aid: Long,
        cid: Long,
    ): List<ViewPoint> =
        BiliHttpApi
            .getVideoMoreInfo(
                avid = aid,
                cid = cid,
            ).getResponseData()
            .viewPoints

    suspend fun sendHeartbeat(
        aid: Long,
        cid: Long,
        time: Int,
        preferApiType: ApiType,
    ) {
        val result =
            when (preferApiType) {
                ApiType.Web ->
                    BiliHttpApi.sendHeartbeat(
                        avid = aid,
                        cid = cid,
                        playedTime = time,
                        csrf = authRepository.biliJct,
                    )

                ApiType.App ->
                    BiliHttpApi.sendHeartbeatApp(
                        avid = aid,
                        cid = cid,
                        playedTime = time,
                        mid = authRepository.mid,
                        accessKey = authRepository.accessToken,
                    )
            }
        BiliLogger.info { "send heartbeat result: $result" }
    }

    /**
     * 获取弹幕元数据（分段配置）。
     *
     * 固定走 Web dm/view 通道：App gRPC 的 DmViewReply 不含 dmSge 字段，
     * 且该接口未登录可用，单一数据源最简（参考 blbl 项目做法）。
     *
     * @param aid 视频 AV 号
     * @param cid 视频 CID
     * @return 弹幕元数据（分段大小、分段总数、弹幕是否关闭）
     * @throws Exception 网络失败或响应解析失败时抛出，由调用方决定兜底策略
     */
    suspend fun getDanmakuMeta(
        aid: Long,
        cid: Long,
    ): DanmakuMeta = BiliHttpApi.getDanmakuView(cid = cid, avid = aid).toDanmakuMeta()

    /**
     * 获取指定 6 分钟分段的弹幕。
     *
     * @param aid 视频 AV 号
     * @param cid 视频 CID
     * @param segmentIndex 从 1 开始的分段索引
     * @param preferApiType 优先使用的接口类型
     * @return 该分段的弹幕列表
     */
    suspend fun getDanmakuSegment(
        aid: Long,
        cid: Long,
        segmentIndex: Int,
        preferApiType: ApiType,
    ): List<DanmakuData> =
        when (preferApiType) {
            ApiType.Web ->
                BiliHttpApi.getDanmakuSeg(
                    cid = cid,
                    avid = aid,
                    segmentIndex = segmentIndex,
                )

            ApiType.App ->
                withContext(Dispatchers.IO) {
                    val reply =
                        runCatching {
                            danmakuStub?.dmSegMobile(
                                dmSegMobileReq {
                                    pid = aid
                                    oid = cid
                                    type = 1
                                    this.segmentIndex = segmentIndex.toLong()
                                },
                            ) ?: throw IllegalStateException("Danmaku stub is not initialized")
                        }.onFailure { handleGrpcException(it) }.getOrThrow()
                    reply.elemsList.map { DanmakuData.fromDanmakuElem(it) }
                }
        }

    suspend fun getDanmakuMask(
        aid: Long,
        cid: Long,
        preferApiType: ApiType,
    ): DanmakuMask? {
        val danmakuMaskUrl =
            when (preferApiType) {
                ApiType.Web -> {
                    val response =
                        BiliHttpApi
                            .getVideoMoreInfo(
                                avid = aid,
                                cid = cid,
                            ).getResponseData()
                    response.dmMask?.maskUrl
                }

                ApiType.App -> {
                    val dmViewReply =
                        runCatching {
                            danmakuStub?.dmView(
                                dmViewReq {
                                    pid = aid
                                    oid = cid
                                    type = 1
                                },
                            )
                        }.onFailure { handleGrpcException(it) }.getOrThrow()
                    dmViewReply?.mask?.maskUrl
                }
            } ?: return null

        val maskUrl =
            when (preferApiType) {
                ApiType.Web -> danmakuMaskUrl.replace("mobmask", "webmask")
                ApiType.App -> danmakuMaskUrl.replace("webmask", "mobmask")
            }
        val danmakuMaskType =
            when (preferApiType) {
                ApiType.Web -> DanmakuMaskType.WebMask
                ApiType.App -> DanmakuMaskType.MobMask
            }
        // 直接拿流，不缓冲到 ByteArray
        val maskStream = BiliHttpApi.downloadAsStream(maskUrl)
        return DanmakuMask.fromStream(maskStream, danmakuMaskType)
    }

    suspend fun getVideoShot(
        aid: Long,
        cid: Long,
        preferApiType: ApiType,
    ): VideoShot? {
        val videoShortResponse =
            when (preferApiType) {
                ApiType.Web -> BiliHttpApi.getWebVideoShot(aid = aid, cid = cid)
                ApiType.App -> BiliHttpApi.getAppVideoShot(aid = aid, cid = cid)
            }
        val videoShot = VideoShot.fromVideoShot(videoShortResponse.getResponseData())
        return videoShot
    }
}
