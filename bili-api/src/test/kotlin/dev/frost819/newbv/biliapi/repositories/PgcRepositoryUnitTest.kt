package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.SeasonIndexType
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [PgcRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，验证轮播图获取和 PGC Feed 获取逻辑。
 * 不依赖真实网络。
 */
class PgcRepositoryUnitTest {
    private lateinit var repository: PgcRepository

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        repository = PgcRepository()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    // ------------------------------------------------------------------
    // getPgcIndex
    // ------------------------------------------------------------------

    @Test
    fun `getPgcIndex Anime calls seasonIndexAnimeResult and maps data`() =
        runTest {
            coEvery {
                BiliHttpApi.seasonIndexAnimeResult(
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
            } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Anime,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
            assertThat(result.list[0].seasonId).isEqualTo(400)
            assertThat(result.nextPage.hasNext).isTrue()
            assertThat(result.nextPage.nextPage).isEqualTo(2)
        }

    @Test
    fun `getPgcIndex GuoChuang calls seasonIndexGuochuangResult`() =
        runTest {
            coEvery {
                BiliHttpApi.seasonIndexGuochuangResult(
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
            } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.GuoChuang,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
        }

    @Test
    fun `getPgcIndex Movie calls seasonIndexMovieResult`() =
        runTest {
            coEvery {
                BiliHttpApi.seasonIndexMovieResult(any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Movie,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
        }

    @Test
    fun `getPgcIndex Documentary calls seasonIndexDocumentaryResult`() =
        runTest {
            coEvery {
                BiliHttpApi.seasonIndexDocumentaryResult(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Documentary,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
        }

    @Test
    fun `getPgcIndex Tv calls seasonIndexTvResult`() =
        runTest {
            coEvery { BiliHttpApi.seasonIndexTvResult(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Tv,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
        }

    @Test
    fun `getPgcIndex Variety calls seasonIndexVarietyResult`() =
        runTest {
            coEvery { BiliHttpApi.seasonIndexVarietyResult(any(), any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData())

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Variety,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.list).hasSize(1)
        }

    @Test
    fun `getPgcIndex maps hasNext false when API returns hasNext 0`() =
        runTest {
            coEvery {
                BiliHttpApi.seasonIndexAnimeResult(
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
            } returns
                BiliResponse(code = 0, message = "", data = fakeIndexResultData(hasNext = 0))

            val result =
                repository.getPgcIndex(
                    pgcType = PgcType.Anime,
                    indexOrder =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
                            .values()
                            .first(),
                    indexOrderType =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
                            .values()
                            .first(),
                    seasonVersion =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
                            .values()
                            .first(),
                    spokenLanguage =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
                            .values()
                            .first(),
                    area =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Area
                            .values()
                            .first(),
                    isFinish =
                        dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
                            .values()
                            .first(),
                    copyright =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
                            .values()
                            .first(),
                    seasonStatus =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
                            .values()
                            .first(),
                    seasonMonth =
                        dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
                            .values()
                            .first(),
                    producer =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Producer
                            .values()
                            .first(),
                    year =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Year
                            .values()
                            .first(),
                    releaseDate =
                        dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
                            .values()
                            .first(),
                    style =
                        dev.frost819.newbv.biliapi.entity.pgc.index.Style
                            .values()
                            .first(),
                    page =
                        dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
                            .PgcIndexPage(),
                )

            assertThat(result.nextPage.hasNext).isFalse()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun fakeIndexResultData(hasNext: Int = 1) =
        dev.frost819.newbv.biliapi.http.entity.index.IndexResultData(
            hasNext = hasNext,
            list =
                listOf(
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem(
                        badge = "",
                        badgeInfo =
                            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.BadgeInfo(
                                bgColor = "",
                                bgColorNight = "",
                                text = "",
                            ),
                        badgeType = 0,
                        cover = "http://cover.test",
                        firstEp =
                            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.FirstEp(
                                cover = "",
                                epId = 0,
                            ),
                        indexShow = "全12话",
                        isFinish = 1,
                        link = "https://www.bilibili.com/bangumi/play/ep1",
                        mediaId = 100,
                        order = "",
                        orderType = "",
                        score = "9.0",
                        seasonId = 400,
                        seasonStatus = 0,
                        seasonType = 1,
                        subTitle = "副标题",
                        title = "测试番剧",
                        titleIcon = "",
                    ),
                ),
            num = 1,
            size = 20,
            total = 1,
        )

}
