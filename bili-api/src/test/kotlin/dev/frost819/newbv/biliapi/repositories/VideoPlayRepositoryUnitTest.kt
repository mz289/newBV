package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.RiskControlException
import dev.frost819.newbv.biliapi.http.entity.danmaku.DanmakuData
import dev.frost819.newbv.biliapi.http.entity.video.Dash
import dev.frost819.newbv.biliapi.http.entity.video.DashData
import dev.frost819.newbv.biliapi.http.entity.video.DashDolby
import dev.frost819.newbv.biliapi.http.entity.video.DashFlac
import dev.frost819.newbv.biliapi.http.entity.video.Durl
import dev.frost819.newbv.biliapi.http.entity.video.PlayUrlData
import dev.frost819.newbv.biliapi.http.entity.video.SegmentBase
import dev.frost819.newbv.biliapi.http.entity.video.SupportFormat
import dev.frost819.newbv.biliapi.http.entity.video.VideoMoreInfo
import dev.frost819.newbv.biliapi.http.entity.video.VideoShot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [VideoPlayRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，验证 Web 路径下的播放数据获取、
 * PGC 播放数据、字幕、心跳、弹幕蒙版、视频截图等功能。
 * gRPC 路径因 [ChannelRepository.defaultChannel] 为 null 而走 stub 为 null 的降级逻辑。
 */
class VideoPlayRepositoryUnitTest {
    private lateinit var repository: VideoPlayRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var channelRepository: ChannelRepository

    companion object {
        private const val AID = 993403941L
        private const val CID = 1051761130L
        private const val EPID = 469110
        private const val BILI_JCT = "test-bili-jct"
        private const val ACCESS_TOKEN = "test-access-token"
    }

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        authRepository = AuthRepository()
        authRepository.biliJct = BILI_JCT
        authRepository.accessToken = ACCESS_TOKEN
        channelRepository = ChannelRepository()
        repository = VideoPlayRepository(authRepository, channelRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    // ------------------------------------------------------------------
    // getPlayData - Web
    // ------------------------------------------------------------------

    @Test
    fun `getPlayData Web returns PlayData with dash videos`() =
        runTest {
            val playUrlData = fakePlayUrlDataWithDash()
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = playUrlData)

            val result = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result.dashVideos).hasSize(1)
            assertThat(result.dashVideos[0].quality).isEqualTo(80)
            assertThat(result.dashVideos[0].baseUrl).isEqualTo("https://video.example.com/80.m4s")
            assertThat(result.dashVideos[0].codecId).isEqualTo(7)
            assertThat(result.dashAudios).hasSize(1)
            assertThat(result.dashAudios[0].baseUrl).isEqualTo("https://audio.example.com/30280.m4s")
            assertThat(result.needPay).isFalse()
        }

    @Test
    fun `getPlayData Web maps preview durl when dash is null`() =
        runTest {
            val playUrlData = fakePlayUrlDataWithDurlOnly()
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = playUrlData)

            val result = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result.dashVideos).hasSize(1)
            assertThat(result.dashVideos[0].baseUrl).isEqualTo("https://video.example.com/preview.flv")
            assertThat(result.dashVideos[0].quality).isEqualTo(80)
            assertThat(result.needPay).isTrue()
        }

    @Test
    fun `getPlayData Web maps dolby and flac audio`() =
        runTest {
            val playUrlData = fakePlayUrlDataWithDolbyAndFlac()
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = playUrlData)

            val result = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result.dolby).isNotNull()
            assertThat(result.dolby!!.codecId).isEqualTo(30250)
            assertThat(result.flac).isNotNull()
            assertThat(result.flac!!.codecId).isEqualTo(30251)
        }

    @Test
    fun `getPlayData Web preserves video stream codec`() =
        runTest {
            val playUrlData = fakePlayUrlDataWithDash()
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = playUrlData)

            val result = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result.dashVideos.first().codecId).isEqualTo(7)
            assertThat(result.dashVideos.first().codecs).startsWith("avc1")
        }

    @Test
    fun `getPlayData Web passes fnval 4048 and qn 127`() =
        runTest {
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = fakePlayUrlDataWithDash())

            repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            coVerify {
                BiliHttpApi.getVideoPlayUrl(
                    av = eq(AID),
                    cid = eq(CID),
                    fnval = eq(4048),
                    qn = eq(127),
                    fnver = eq(0),
                    fourk = eq(1),
                )
            }
        }

    @Test
    fun `getPlayData Web throws RiskControlException on code -352`() =
        runTest {
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = -352, message = "risk control", data = null)

            assertThrows<RiskControlException> {
                repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)
            }
        }

    // ------------------------------------------------------------------
    // getPlayData - App (channel is null, stub is null)
    // ------------------------------------------------------------------

    @Test
    fun `getPlayData App throws when all codec types fail with null channel`() =
        runTest {
            assertThrows<IllegalStateException> {
                repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.App)
            }
        }

    // ------------------------------------------------------------------
    // sendHeartbeat
    // ------------------------------------------------------------------

    @Test
    fun `sendHeartbeat Web calls BiliHttpApi sendHeartbeat with csrf`() =
        runTest {
            coEvery { BiliHttpApi.sendHeartbeat(any(), any(), any(), any()) } returns "{\"code\":0}"

            repository.sendHeartbeat(
                aid = AID,
                cid = CID,
                time = 60,
                preferApiType = ApiType.Web,
            )

            coVerify {
                BiliHttpApi.sendHeartbeat(
                    avid = eq(AID),
                    cid = eq(CID),
                    playedTime = eq(60),
                    csrf = eq(BILI_JCT),
                )
            }
        }

    @Test
    fun `sendHeartbeat App uses HTTP App API not gRPC`() =
        runTest {
            coEvery {
                BiliHttpApi.sendHeartbeatApp(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns "{\"code\":0}"

            repository.sendHeartbeat(aid = AID, cid = CID, time = 30, preferApiType = ApiType.App)

            coVerify {
                BiliHttpApi.sendHeartbeatApp(
                    avid = eq(AID),
                    cid = eq(CID),
                    playedTime = eq(30),
                    accessKey = any(),
                )
            }
        }

    @Test
    fun `sendHeartbeat Web uses null csrf when biliJct is null`() =
        runTest {
            authRepository.biliJct = null
            coEvery { BiliHttpApi.sendHeartbeat(any(), any(), any(), any()) } returns "{\"code\":0}"

            repository.sendHeartbeat(aid = AID, cid = CID, time = 10, preferApiType = ApiType.Web)

            coVerify {
                BiliHttpApi.sendHeartbeat(
                    avid = eq(AID),
                    cid = eq(CID),
                    playedTime = eq(10),
                    csrf = isNull(),
                )
            }
        }

    @Test
    fun `sendHeartbeat App passes accessKey from auth`() =
        runTest {
            coEvery {
                BiliHttpApi.sendHeartbeatApp(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns "{\"code\":0}"

            repository.sendHeartbeat(aid = AID, cid = CID, time = 5, preferApiType = ApiType.App)

            coVerify {
                BiliHttpApi.sendHeartbeatApp(
                    avid = eq(AID),
                    cid = eq(CID),
                    playedTime = eq(5),
                    accessKey = eq(ACCESS_TOKEN),
                )
            }
        }

    // ------------------------------------------------------------------
    // getDanmakuMask - Web
    // ------------------------------------------------------------------

    @Test
    fun `getDanmakuMask Web returns null when dmMask is null`() =
        runTest {
            coEvery { BiliHttpApi.getVideoMoreInfo(any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeVideoMoreInfoNoSubtitle())

            val result = repository.getDanmakuMask(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result).isNull()
        }

    // ------------------------------------------------------------------
    // getDanmakuMask - App (channel is null, stub is null)
    // ------------------------------------------------------------------

    @Test
    fun `getDanmakuMask App returns null when danmakuStub is null`() =
        runTest {
            val result = repository.getDanmakuMask(aid = AID, cid = CID, preferApiType = ApiType.App)

            assertThat(result).isNull()
        }

    // ------------------------------------------------------------------
    // getVideoShot - Web
    // ------------------------------------------------------------------

    @Test
    fun `getVideoShot Web returns null when pvData is null and images empty`() =
        runTest {
            val videoShot =
                dev.frost819.newbv.biliapi.http.entity.video.VideoShot(
                    pvData = null,
                    image = emptyList(),
                )
            coEvery { BiliHttpApi.getWebVideoShot(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = videoShot)

            val result = repository.getVideoShot(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result).isNull()
        }

    @Test
    fun `getVideoShot Web returns null when image download fails`() =
        runTest {
            val videoShot =
                dev.frost819.newbv.biliapi.http.entity.video.VideoShot(
                    pvData = "https://pv.example.com/data.bin",
                    image = listOf("https://img.example.com/shot.jpg"),
                )
            coEvery { BiliHttpApi.getWebVideoShot(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = videoShot)
            coEvery { BiliHttpApi.download(any()) } throws RuntimeException("network error")

            val result = repository.getVideoShot(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(result).isNull()
        }

    // ------------------------------------------------------------------
    // getVideoShot - App
    // ------------------------------------------------------------------

    @Test
    @Disabled("Video shots are Web-only")
    fun `getVideoShot App returns null when pvData is null and images empty`() =
        runTest {
            val videoShot =
                dev.frost819.newbv.biliapi.http.entity.video.VideoShot(
                    pvData = null,
                    image = emptyList(),
                )
            coEvery { BiliHttpApi.getAppVideoShot(any(), any()) } returns
                BiliResponse(code = 0, message = "", data = videoShot)

            val result = repository.getVideoShot(aid = AID, cid = CID, preferApiType = ApiType.App)

            assertThat(result).isNull()
        }

    @Test
    fun `getVideoShot Web calls getWebVideoShot with aid and cid`() =
        runTest {
            coEvery { BiliHttpApi.getWebVideoShot(any(), any(), any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.video
                            .VideoShot(),
                )

            repository.getVideoShot(aid = AID, cid = CID, preferApiType = ApiType.Web)

            coVerify {
                BiliHttpApi.getWebVideoShot(aid = eq(AID), cid = eq(CID))
            }
        }

    @Test
    @Disabled("Video shots are Web-only")
    fun `getVideoShot App calls getAppVideoShot with aid and cid`() =
        runTest {
            coEvery { BiliHttpApi.getAppVideoShot(any(), any()) } returns
                BiliResponse(
                    code = 0,
                    message = "",
                    data =
                        dev.frost819.newbv.biliapi.http.entity.video
                            .VideoShot(),
                )

            repository.getVideoShot(aid = AID, cid = CID, preferApiType = ApiType.App)

            coVerify {
                BiliHttpApi.getAppVideoShot(aid = eq(AID), cid = eq(CID))
            }
        }

    // ------------------------------------------------------------------
    // getDanmakuSegment
    // ------------------------------------------------------------------

    @Test
    fun `getDanmakuSegment Web calls HTTP seg API with aid cid and segmentIndex`() =
        runTest {
            val fake = listOf(fakeDanmakuData(61.5f), fakeDanmakuData(62f))
            coEvery {
                BiliHttpApi.getDanmakuSeg(cid = any(), avid = any(), segmentIndex = any())
            } returns fake

            val result =
                repository.getDanmakuSegment(
                    aid = AID,
                    cid = CID,
                    segmentIndex = 1,
                    preferApiType = ApiType.Web,
                )

            assertThat(result).hasSize(2)
            assertThat(result[0].dmid).isEqualTo(fake[0].dmid)
            coVerify {
                BiliHttpApi.getDanmakuSeg(
                    cid = eq(CID),
                    avid = eq(AID),
                    segmentIndex = eq(1),
                )
            }
        }

    @Test
    fun `getDanmakuSegment App throws when danmakuStub is null`() =
        runTest {
            assertThrows<IllegalStateException> {
                repository.getDanmakuSegment(
                    aid = AID,
                    cid = CID,
                    segmentIndex = 1,
                    preferApiType = ApiType.App,
                )
            }
        }

    @Test
    fun `getDanmakuSegment Web propagates network error`() =
        runTest {
            coEvery {
                BiliHttpApi.getDanmakuSeg(cid = any(), avid = any(), segmentIndex = any())
            } throws RuntimeException("network error")

            assertThrows<RuntimeException> {
                repository.getDanmakuSegment(
                    aid = AID,
                    cid = CID,
                    segmentIndex = 2,
                    preferApiType = ApiType.Web,
                )
            }
        }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private fun fakeDanmakuData(time: Float): DanmakuData =
        DanmakuData(
            time = time,
            type = 1,
            size = 25,
            color = 0xFFFFFF,
            timestamp = 1700000000,
            pool = 0,
            midHash = "hash",
            dmid = (time * 1000).toLong(),
            level = 5,
            text = "seg danmaku",
        )

    private fun fakePlayUrlDataWithDash(): PlayUrlData =
        PlayUrlData(
            from = "server",
            result = "suee",
            message = "",
            quality = 80,
            format = "flv",
            timeLength = 300000,
            acceptFormat = "flv,flv720,flv480",
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
            dash =
                Dash(
                    duration = 300,
                    minBufferTime = 1.5f,
                    video =
                        listOf(
                            DashData(
                                id = 80,
                                baseUrl = "https://video.example.com/80.m4s",
                                backupUrl = listOf("https://backup.example.com/80.m4s"),
                                bandwidth = 500000,
                                mimeType = "video/mp4",
                                codecs = "avc1.640032",
                                width = 1920,
                                height = 1080,
                                frameRate = "60",
                                sar = "1:1",
                                startWithSap = 1,
                                segmentBase = SegmentBase(initialization = "0-1000", indexRange = "1001-2000"),
                                codecId = 7,
                            ),
                        ),
                    audio =
                        listOf(
                            DashData(
                                id = 30280,
                                baseUrl = "https://audio.example.com/30280.m4s",
                                backupUrl = emptyList(),
                                bandwidth = 128000,
                                mimeType = "audio/mp4",
                                codecs = "mp4a.40.2",
                                width = 0,
                                height = 0,
                                frameRate = "",
                                sar = "",
                                startWithSap = 0,
                                segmentBase = SegmentBase(initialization = "0-500", indexRange = "501-1000"),
                                codecId = 0,
                            ),
                        ),
                    dolby = DashDolby(),
                    flac = null,
                ),
            supportFormats =
                listOf(
                    SupportFormat(
                        quality = 80,
                        format = "flv",
                        newDescription = "1080P",
                        description = "1080P",
                        displayDesc = "1080P",
                        superScript = "",
                        codecs = listOf("avc1.640032,mp4a.40.2"),
                    ),
                ),
        )

    private fun fakePlayUrlDataWithDolbyAndFlac(): PlayUrlData =
        PlayUrlData(
            from = "server",
            result = "suee",
            message = "",
            quality = 80,
            format = "flv",
            timeLength = 300000,
            acceptFormat = "flv",
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
            dash =
                Dash(
                    duration = 300,
                    minBufferTime = 1.5f,
                    video =
                        listOf(
                            DashData(
                                id = 80,
                                baseUrl = "https://video.example.com/80.m4s",
                                backupUrl = emptyList(),
                                bandwidth = 500000,
                                mimeType = "video/mp4",
                                codecs = "avc1.640032",
                                width = 1920,
                                height = 1080,
                                frameRate = "60",
                                sar = "1:1",
                                startWithSap = 1,
                                segmentBase = SegmentBase(initialization = "0-1000", indexRange = "1001-2000"),
                                codecId = 7,
                            ),
                        ),
                    audio =
                        listOf(
                            DashData(
                                id = 30280,
                                baseUrl = "https://audio.example.com/30280.m4s",
                                backupUrl = emptyList(),
                                bandwidth = 128000,
                                mimeType = "audio/mp4",
                                codecs = "mp4a.40.2",
                                width = 0,
                                height = 0,
                                frameRate = "",
                                sar = "",
                                startWithSap = 0,
                                segmentBase = SegmentBase(initialization = "0-500", indexRange = "501-1000"),
                                codecId = 0,
                            ),
                        ),
                    dolby =
                        DashDolby(
                            audio =
                                listOf(
                                    DashData(
                                        id = 30250,
                                        baseUrl = "https://audio.example.com/dolby.m4s",
                                        backupUrl = emptyList(),
                                        bandwidth = 256000,
                                        mimeType = "audio/mp4",
                                        codecs = "ec-3",
                                        width = 0,
                                        height = 0,
                                        frameRate = "",
                                        sar = "",
                                        startWithSap = 0,
                                        segmentBase = SegmentBase(initialization = "0-500", indexRange = "501-1000"),
                                        codecId = 0,
                                    ),
                                ),
                            type = 2,
                        ),
                    flac =
                        DashFlac(
                            display = true,
                            audio =
                                DashData(
                                    id = 30251,
                                    baseUrl = "https://audio.example.com/flac.m4s",
                                    backupUrl = emptyList(),
                                    bandwidth = 320000,
                                    mimeType = "audio/mp4",
                                    codecs = "fLaC",
                                    width = 0,
                                    height = 0,
                                    frameRate = "",
                                    sar = "",
                                    startWithSap = 0,
                                    segmentBase = SegmentBase(initialization = "0-500", indexRange = "501-1000"),
                                    codecId = 0,
                                ),
                        ),
                ),
            supportFormats = emptyList(),
        )

    private fun fakePlayUrlDataWithDurlOnly(): PlayUrlData =
        PlayUrlData(
            from = "server",
            result = "suee",
            message = "",
            quality = 80,
            format = "flv",
            timeLength = 300000,
            acceptFormat = "flv",
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
            durl =
                listOf(
                    Durl(
                        order = 1,
                        length = 300000,
                        size = 1000000,
                        ahead = "",
                        vhead = "",
                        url = "https://video.example.com/preview.flv",
                        backupUrl = listOf("https://backup.example.com/preview.flv"),
                    ),
                ),
            dash = null,
            supportFormats = emptyList(),
        )

    private fun fakeVideoMoreInfoNoSubtitle(): VideoMoreInfo =
        VideoMoreInfo(
            aid = AID,
            bvid = "BV1xx",
            allowBp = false,
            noShare = false,
            cid = CID,
            maxLimit = 0,
            pageNo = 1,
            hasNext = false,
            ipInfo =
                VideoMoreInfo.IpInfo(
                    ip = "",
                    zoneIp = "",
                    zoneId = 0,
                    country = "",
                    province = "",
                    city = "",
                ),
            loginMid = 0L,
            loginMidHash = "",
            isOwner = false,
            name = "UP主",
            permission = "",
            levelInfo =
                dev.frost819.newbv.biliapi.http.entity.user.LevelInfo(
                    currentLevel = 0,
                    currentMin = 0,
                    currentExp = 0,
                    nextExp = 0,
                ),
            vip =
                dev.frost819.newbv.biliapi.http.entity.user.Vip(
                    type = 0,
                    status = 0,
                    dueDate = 0L,
                    vipPayType = 0,
                    themeType = 0,
                    label =
                        dev.frost819.newbv.biliapi.http.entity.user.Vip.Label(
                            path = "",
                            text = "",
                            labelTheme = "",
                            textColor = "",
                            bgStyle = 0,
                            bgColor = "",
                            borderColor = "",
                        ),
                    avatarSubscript = 0,
                    nicknameColor = "",
                    role = 0,
                    avatarSubscriptUrl = "",
                    tvVipStatus = 0,
                    tvVipPayType = 0,
                ),
            answerStatue = 0,
            blockTime = 0,
            role = "",
            lastPlayTime = 0,
            lastPlayCid = 0L,
            nowTime = 0,
            onlineCount = 0,
            dmMask = null,
            subtitle = null,
            playerIcon = null,
            viewPoints = emptyList(),
            isUgcPayPreview = false,
            previewToast = "",
            pcdnLoader = null,
            options = VideoMoreInfo.Options(is360 = false, withoutVip = false),
            guideAttention = kotlinx.serialization.json.JsonArray(emptyList()),
            jumpCard = kotlinx.serialization.json.JsonArray(emptyList()),
            operationCard = kotlinx.serialization.json.JsonArray(emptyList()),
            onlineSwitch =
                VideoMoreInfo.OnlineSwitch(
                    enableGrayDashPlayback = "",
                    newBroadcast = "",
                    realtimeDm = "",
                    subtitleSubmitSwitch = "",
                ),
            fawkes = VideoMoreInfo.Fawkes(configVersion = 0, ffVersion = 0),
            showSwitch = VideoMoreInfo.ShowSwitch(longProgress = false),
            toastBlock = false,
        )
}
