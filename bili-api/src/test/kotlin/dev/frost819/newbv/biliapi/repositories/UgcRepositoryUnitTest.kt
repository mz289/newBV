package dev.frost819.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedPage
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.BiliResponse
import dev.frost819.newbv.biliapi.http.entity.region.RegionFeedRcmd
import dev.frost819.newbv.biliapi.http.entity.search.SearchResultData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [UgcRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，验证分区推荐视频与筛选搜索的
 * 参数传递、分页递增与数据转换。不依赖真实网络。
 */
class UgcRepositoryUnitTest {
    private lateinit var repository: UgcRepository

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        repository = UgcRepository()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    // ------------------------------------------------------------------
    // getRegionFeedRcmd
    // ------------------------------------------------------------------

    @Test
    fun `getRegionFeedRcmd returns mapped UgcFeedData with items`() =
        runTest {
            val regionFeedRcmd =
                RegionFeedRcmd(
                    archives =
                        listOf(
                            fakeArchive(aid = 1L, title = "video-1"),
                            fakeArchive(aid = 2L, title = "video-2"),
                        ),
                )
            coEvery { BiliHttpApi.getRegionFeedRcmd(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = regionFeedRcmd)

            val result = repository.getRegionFeedRcmd(ugcType = UgcTypeV2.Douga, page = UgcFeedPage(nextPage = 1))

            assertThat(result.hasNext).isTrue()
            assertThat(result.items).hasSize(2)
            assertThat(result.items[0].aid).isEqualTo(1L)
            assertThat(result.items[0].title).isEqualTo("video-1")
            assertThat(result.items[1].aid).isEqualTo(2L)
        }

    @Test
    fun `getRegionFeedRcmd sets nextPage to page plus one`() =
        runTest {
            coEvery { BiliHttpApi.getRegionFeedRcmd(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = RegionFeedRcmd(archives = listOf(fakeArchive())))

            val result = repository.getRegionFeedRcmd(ugcType = UgcTypeV2.Game, page = UgcFeedPage(nextPage = 3))

            assertThat(result.nextPage.nextPage).isEqualTo(4)
        }

    @Test
    fun `getRegionFeedRcmd passes displayId and fromRegion tid`() =
        runTest {
            coEvery { BiliHttpApi.getRegionFeedRcmd(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = RegionFeedRcmd(archives = emptyList()))

            repository.getRegionFeedRcmd(ugcType = UgcTypeV2.Music, page = UgcFeedPage(nextPage = 5))

            coVerify {
                BiliHttpApi.getRegionFeedRcmd(
                    displayId = eq(5),
                    fromRegion = eq(UgcTypeV2.Music.tid),
                )
            }
        }

    @Test
    fun `getRegionFeedRcmd returns hasNext false when archives is empty`() =
        runTest {
            coEvery { BiliHttpApi.getRegionFeedRcmd(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = RegionFeedRcmd(archives = emptyList()))

            val result = repository.getRegionFeedRcmd(ugcType = UgcTypeV2.Douga, page = UgcFeedPage())

            assertThat(result.hasNext).isFalse()
            assertThat(result.items).isEmpty()
        }

    @Test
    fun `getRegionFeedRcmd maps author and stat fields`() =
        runTest {
            val archive =
                RegionFeedRcmd.Archive(
                    aid = 100L,
                    bvid = "BV100",
                    cid = 200L,
                    title = "test",
                    cover = "http://cover.test",
                    duration = 300,
                    pubdate = 1000L,
                    stat = RegionFeedRcmd.Archive.Stat(view = 9999, like = 100, danmaku = 50),
                    author = RegionFeedRcmd.Archive.Author(mid = 555L, name = "up-name"),
                    trackid = "t1",
                    goto = "av",
                    recReason = "hot",
                )
            coEvery { BiliHttpApi.getRegionFeedRcmd(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = RegionFeedRcmd(archives = listOf(archive)))

            val result = repository.getRegionFeedRcmd(ugcType = UgcTypeV2.Douga, page = UgcFeedPage())

            val item = result.items[0]
            assertThat(item.author).isEqualTo("up-name")
            assertThat(item.authorMid).isEqualTo(555L)
            assertThat(item.play).isEqualTo(9999)
            assertThat(item.danmaku).isEqualTo(50)
            assertThat(item.duration).isEqualTo(300)
        }

    private fun fakeArchive(
        aid: Long = 1L,
        title: String = "title",
    ) = RegionFeedRcmd.Archive(
        aid = aid,
        bvid = "BV$aid",
        cid = aid * 10,
        title = title,
        cover = "http://cover.test/$aid",
        duration = 120,
        pubdate = 1000L,
        stat = RegionFeedRcmd.Archive.Stat(view = 100, like = 10, danmaku = 5),
        author = RegionFeedRcmd.Archive.Author(mid = 1L, name = "up"),
        trackid = "t$aid",
        goto = "av",
        recReason = "",
    )

    // ------------------------------------------------------------------
    // getRegionFeedBySearch（筛选）
    // ------------------------------------------------------------------

    /** 构造分类搜索接口的单条视频 JSON（SearchResultData 的 init 会解析 result 数组）。 */
    private fun fakeSearchVideoJson(
        aid: Long,
        title: String = "title",
    ) = buildJsonObject {
        put("type", "video")
        put("id", aid)
        put("author", "up-$aid")
        put("mid", aid * 10)
        put("typeid", "24")
        put("typename", "MAD·AMV")
        put("arcurl", "https://www.bilibili.com/video/BV$aid")
        put("aid", aid)
        put("bvid", "BV$aid")
        put("title", title)
        put("description", "desc")
        put("pic", "//i0.hdslb.com/bfs/$aid.jpg")
        put("play", 1000)
        put("video_review", 10)
        put("favorites", 5)
        put("tag", "tag")
        put("review", 1)
        put("pubdate", 1_700_000_000)
        put("senddate", 1_700_000_000)
        put("duration", "3:59")
        put("badgepay", false)
        put("hit_columns", buildJsonArray {})
        put("view_type", "")
        put("is_pay", 0)
        put("is_union_video", 0)
        put("like", 9)
        put("upic", "//i0.hdslb.com/bfs/face/$aid.jpg")
        put("corner", "")
        put("cover", "")
        put("desc", "desc")
        put("url", "")
        put("rec_reason", "")
        put("danmaku", 10)
        put("vt_display", "")
        put("subtitle", "")
        put("episode_count_text", "")
        put("release_status", 0)
        put("is_intervene", 0)
    }

    private fun fakeSearchResult(
        page: Int,
        numPages: Int,
        aids: List<Long>,
    ) = SearchResultData(
        page = page,
        numPages = numPages,
        result =
            aids.map { aid ->
                fakeSearchVideoJson(aid, title = "video-$aid")
            },
    )

    @Test
    fun `getRegionFeedBySearch passes keyword tid order duration and page`() =
        runTest {
            coEvery { BiliHttpApi.searchType(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeSearchResult(page = 1, numPages = 26, aids = listOf(1L)))

            repository.getRegionFeedBySearch(
                searchTid = 1,
                keyword = "动画",
                order = SearchFilterOrderType.MostClicks,
                duration = SearchFilterDuration.Between10And30Minutes,
                page = UgcFeedPage(nextPage = 1),
            )

            coVerify {
                BiliHttpApi.searchType(
                    keyword = eq("动画"),
                    type = eq("video"),
                    page = eq(1),
                    tid = eq(1),
                    order = eq("click"),
                    duration = eq(2),
                )
            }
        }

    @Test
    fun `getRegionFeedBySearch maps search videos to UgcItems`() =
        runTest {
            coEvery { BiliHttpApi.searchType(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeSearchResult(page = 1, numPages = 26, aids = listOf(1L, 2L)))

            val result =
                repository.getRegionFeedBySearch(
                    searchTid = 1,
                    keyword = "动画",
                    order = SearchFilterOrderType.LatestPublish,
                    duration = SearchFilterDuration.All,
                    page = UgcFeedPage(nextPage = 1),
                )

            assertThat(result.items).hasSize(2)
            assertThat(result.items[0].aid).isEqualTo(1L)
            assertThat(result.items[0].cover).isEqualTo("https://i0.hdslb.com/bfs/1.jpg")
            assertThat(result.items[0].authorMid).isEqualTo(10L)
            assertThat(result.items[0].play).isEqualTo(1000)
            assertThat(result.items[0].danmaku).isEqualTo(10)
            assertThat(result.items[0].duration).isEqualTo(239)
            assertThat(result.hasNext).isTrue()
            assertThat(result.nextPage.nextPage).isEqualTo(2)
        }

    @Test
    fun `getRegionFeedBySearch hasMore false when page reaches numPages`() =
        runTest {
            coEvery { BiliHttpApi.searchType(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeSearchResult(page = 26, numPages = 26, aids = listOf(1L)))

            val result =
                repository.getRegionFeedBySearch(
                    searchTid = 4,
                    keyword = "游戏",
                    order = SearchFilterOrderType.ComprehensiveSort,
                    duration = SearchFilterDuration.MoreThan60Minutes,
                    page = UgcFeedPage(nextPage = 26),
                )

            assertThat(result.hasNext).isFalse()
        }

    @Test
    fun `getRegionFeedBySearch omits order param on comprehensive sort`() =
        runTest {
            coEvery { BiliHttpApi.searchType(any(), any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = fakeSearchResult(page = 1, numPages = 1, aids = emptyList()))

            repository.getRegionFeedBySearch(
                searchTid = 129,
                keyword = "舞蹈",
                order = SearchFilterOrderType.ComprehensiveSort,
                duration = SearchFilterDuration.All,
                page = UgcFeedPage(nextPage = 1),
            )

            coVerify {
                BiliHttpApi.searchType(
                    keyword = eq("舞蹈"),
                    type = eq("video"),
                    page = eq(1),
                    tid = eq(129),
                    order = isNull(),
                    duration = isNull(),
                )
            }
        }
}
