package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.PlayData
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.video.PgcPlayUrlData
import dev.frost819.newbv.biliapi.http.entity.video.PlayUrlData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [VideoPlayRepository] 双账号解析（解析账号覆写）单元测试。
 *
 * 验证启用解析账号（B）时：
 * - 播放地址以 B 的 Cookie 走 Web 通道解析（UGC + PGC）
 * - 解析账号与当前账号相同或未启用时不覆写
 * - B 解析失败（网络/权限）时回退当前账号的正常解析路径
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，不依赖真实网络。
 */
class VideoPlayRepositoryParseAccountTest {
    private lateinit var authRepository: AuthRepository
    private lateinit var channelRepository: ChannelRepository
    private lateinit var parseAccountRepository: ParseAccountRepository
    private lateinit var repository: VideoPlayRepository

    companion object {
        private const val AID = 993403941L
        private const val CID = 317395919L
        private const val EPID = 900001
        private const val CURRENT_UID = 111L
        private const val PARSE_UID = 222L
        private const val PARSE_COOKIE = "SESSDATA=parse-sessdata; DedeUserID=$PARSE_UID"
    }

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        authRepository = AuthRepository()
        authRepository.mid = CURRENT_UID
        channelRepository = mockk(relaxed = true)
        parseAccountRepository = ParseAccountRepository()
        repository = VideoPlayRepository(authRepository, channelRepository, parseAccountRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    private fun fakePlayUrlData(quality: Int = 120): PlayUrlData =
        PlayUrlData(
            from = "local",
            result = "suee",
            message = "",
            quality = quality,
            format = "dash",
            timeLength = 60000,
            acceptFormat = "dash",
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
        )

    private fun fakePgcPlayUrlData(): PgcPlayUrlData = PgcPlayUrlData(videoInfo = PgcPlayUrlData.VideoInfo())

    private fun stubGetVideoPlayUrl(response: BiliResponse<PlayUrlData>) {
        coEvery {
            BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response
    }

    private fun stubGetPgcPlayUrl(response: BiliResponse<PgcPlayUrlData>) {
        coEvery {
            BiliHttpApi.getPgcPlayUrl(any(), any(), any(), any(), any(), any(), any())
        } returns response
    }

    @Test
    fun `parse account active overrides ugc playurl with parse cookie`() =
        runTest {
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            val response = BiliResponse(code = 0, message = "", data = fakePlayUrlData(quality = 120))
            stubGetVideoPlayUrl(response)

            val playData = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            // 会员画质（120）由解析账号会话决定，原样透传
            assertThat(playData.dashVideos).isEmpty()
            coVerify(exactly = 1) {
                BiliHttpApi.getVideoPlayUrl(
                    av = AID,
                    cid = CID,
                    fnval = 4048,
                    qn = 127,
                    fnver = 0,
                    fourk = 1,
                    cookieOverride = PARSE_COOKIE,
                )
            }
        }

    @Test
    fun `parse account active overrides pgc playurl with parse cookie`() =
        runTest {
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            val response = BiliResponse(code = 0, message = "", result = fakePgcPlayUrlData())
            stubGetPgcPlayUrl(response)

            repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web, epid = EPID)

            coVerify(exactly = 1) {
                BiliHttpApi.getPgcPlayUrl(epId = EPID, cid = CID, cookieOverride = PARSE_COOKIE)
            }
        }

    @Test
    fun `parse account same as current does not override`() =
        runTest {
            authRepository.mid = PARSE_UID
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            val response = BiliResponse(code = 0, message = "", data = fakePlayUrlData())
            stubGetVideoPlayUrl(response)

            repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            coVerify(exactly = 1) {
                BiliHttpApi.getVideoPlayUrl(
                    av = AID,
                    cid = CID,
                    fnval = 4048,
                    qn = 127,
                    fnver = 0,
                    fourk = 1,
                    cookieOverride = null,
                )
            }
        }

    @Test
    fun `parse account inactive uses normal path`() =
        runTest {
            val response = BiliResponse(code = 0, message = "", data = fakePlayUrlData())
            stubGetVideoPlayUrl(response)

            repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            // 仅一次正常路径调用（cookieOverride 为 null），无解析账号覆写
            coVerify(exactly = 1) {
                BiliHttpApi.getVideoPlayUrl(
                    av = AID,
                    cid = CID,
                    fnval = 4048,
                    qn = 127,
                    fnver = 0,
                    fourk = 1,
                    cookieOverride = null,
                )
            }
        }

    @Test
    fun `parse account failure falls back to current account path`() =
        runTest {
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            val response = BiliResponse(code = 0, message = "", data = fakePlayUrlData())
            coEvery {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("parse failed") andThen response

            val playData = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web)

            assertThat(playData).isEqualTo(PlayData.fromPlayUrlData(fakePlayUrlData()))
            coVerify(exactly = 2) {
                BiliHttpApi.getVideoPlayUrl(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `parse account pgc technical failure falls back to current pgc channel`() =
        runTest {
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            // 第一次（解析账号覆写）网络失败，第二次（当前账号正常路径）成功
            coEvery {
                BiliHttpApi.getPgcPlayUrl(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("parse failed") andThen BiliResponse(code = 0, message = "", result = fakePgcPlayUrlData())

            val playData = repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web, epid = EPID)

            assertThat(playData).isEqualTo(PlayData.fromPgcWebPlayUrlData(fakePgcPlayUrlData()))
            coVerify(exactly = 2) {
                BiliHttpApi.getPgcPlayUrl(any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `parse account and current account both permission denied rethrows guidance error`() =
        runTest {
            parseAccountRepository.update(PARSE_UID, "parse-sessdata", "parse-jct")
            // 两侧都是权限错误时，向上抛出携带引导信息（会员专享）的异常供 UI 分类
            coEvery {
                BiliHttpApi.getPgcPlayUrl(any(), any(), any(), any(), any(), any(), any())
            } throws IllegalStateException("大会员专享限制")

            val error =
                runCatching {
                    repository.getPlayData(aid = AID, cid = CID, preferApiType = ApiType.Web, epid = EPID)
                }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error?.message).contains("会员")
        }
}
