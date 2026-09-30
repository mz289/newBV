package dev.frost819.newbv.biliapi.entity

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebInitialStateData
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Test

/**
 * [CarouselData] 实体转换方法的单元测试。
 *
 * 覆盖 `fromPgcWebInitialStateData`（PGC 轮播图）转换路径。
 */
class CarouselDataTest {
    @Test
    fun `fromPgcWebInitialStateData maps anime banner with direct seasonId and episodeId`() {
        val initialState =
            PgcWebInitialStateData(
                modules =
                    PgcWebInitialStateData.Modules(
                        banner =
                            PgcWebInitialStateData.Modules.Banner(
                                title = "番剧",
                                spmid = "",
                                size = 5,
                                style = "v_card",
                                headers = JsonArray(emptyList()),
                                items =
                                    listOf(
                                        fakeBannerItem(
                                            title = "番剧1",
                                            cover = "https://example.com/cover1.jpg",
                                            seasonId = 40000,
                                            episodeId = 800001,
                                            bigCover = "https://example.com/big1.jpg",
                                            id = "1",
                                        ),
                                        fakeBannerItem(
                                            title = "番剧2",
                                            cover = "https://example.com/cover2.jpg",
                                            seasonId = 40001,
                                            episodeId = 800002,
                                            bigCover = null,
                                            id = "2",
                                        ),
                                    ),
                                wids = JsonArray(emptyList()),
                                moduleId = 1668,
                            ),
                    ),
            )

        val carousel = CarouselData.fromPgcWebInitialStateData(initialState)

        assertThat(carousel.items).hasSize(2)
        assertThat(carousel.items[0].title).isEqualTo("番剧1")
        assertThat(carousel.items[0].seasonId).isEqualTo(40000)
        assertThat(carousel.items[0].episodeId).isEqualTo(800001)
        assertThat(carousel.items[0].cover).isEqualTo("https://example.com/big1.jpg")
    }

    @Test
    fun `fromPgcWebInitialStateData parses seasonId and episodeId from URL for movie type`() {
        val initialState =
            PgcWebInitialStateData(
                modules =
                    PgcWebInitialStateData.Modules(
                        banner =
                            PgcWebInitialStateData.Modules.Banner(
                                title = "电影",
                                spmid = "",
                                size = 3,
                                style = "v_card",
                                headers = JsonArray(emptyList()),
                                items =
                                    listOf(
                                        fakeBannerItem(
                                            title = "电影1",
                                            cover = "https://example.com/movie1.jpg",
                                            seasonId = null,
                                            episodeId = null,
                                            bigCover = null,
                                            link = "https://www.bilibili.com/bangumi/play/ep800003",
                                            id = "3",
                                        ),
                                        fakeBannerItem(
                                            title = "剧集2",
                                            cover = "https://example.com/movie2.jpg",
                                            seasonId = null,
                                            episodeId = null,
                                            bigCover = null,
                                            link = "https://www.bilibili.com/bangumi/play/ss40002",
                                            id = "4",
                                        ),
                                    ),
                                wids = JsonArray(emptyList()),
                                moduleId = 1675,
                            ),
                    ),
            )

        val carousel = CarouselData.fromPgcWebInitialStateData(initialState)

        assertThat(carousel.items).hasSize(2)
        assertThat(carousel.items[0].episodeId).isEqualTo(800003)
        assertThat(carousel.items[0].seasonId).isEqualTo(-1)
        assertThat(carousel.items[1].seasonId).isEqualTo(40002)
        assertThat(carousel.items[1].episodeId).isEqualTo(-1)
    }

    @Test
    fun `fromPgcWebInitialStateData filters items without episodeId or seasonId for non-movie types`() {
        val initialState =
            PgcWebInitialStateData(
                modules =
                    PgcWebInitialStateData.Modules(
                        banner =
                            PgcWebInitialStateData.Modules.Banner(
                                title = "番剧",
                                spmid = "",
                                size = 3,
                                style = "v_card",
                                headers = JsonArray(emptyList()),
                                items =
                                    listOf(
                                        fakeBannerItem(
                                            title = "有ep",
                                            cover = "cover1.jpg",
                                            seasonId = 100,
                                            episodeId = 200,
                                            id = "1",
                                        ),
                                        fakeBannerItem(
                                            title = "无ep无ss",
                                            cover = "cover2.jpg",
                                            seasonId = null,
                                            episodeId = null,
                                            link = "https://www.bilibili.com/some/other",
                                            id = "2",
                                        ),
                                    ),
                                wids = JsonArray(emptyList()),
                                moduleId = 1668,
                            ),
                    ),
            )

        val carousel = CarouselData.fromPgcWebInitialStateData(initialState)

        assertThat(carousel.items).hasSize(1)
        assertThat(carousel.items[0].title).isEqualTo("有ep")
    }

    @Test
    fun `fromPgcWebInitialStateData prepends https to protocol-relative cover URL`() {
        val initialState =
            PgcWebInitialStateData(
                modules =
                    PgcWebInitialStateData.Modules(
                        banner =
                            PgcWebInitialStateData.Modules.Banner(
                                title = "番剧",
                                spmid = "",
                                size = 1,
                                style = "v_card",
                                headers = JsonArray(emptyList()),
                                items =
                                    listOf(
                                        fakeBannerItem(
                                            title = "测试",
                                            cover = "//example.com/cover.jpg",
                                            seasonId = 100,
                                            episodeId = 200,
                                            bigCover = "//example.com/big.jpg",
                                            id = "1",
                                        ),
                                    ),
                                wids = JsonArray(emptyList()),
                                moduleId = 1668,
                            ),
                    ),
            )

        val carousel = CarouselData.fromPgcWebInitialStateData(initialState)

        assertThat(carousel.items[0].cover).isEqualTo("https://example.com/big.jpg")
    }

    private fun fakeBannerItem(
        title: String,
        cover: String,
        seasonId: Int? = null,
        episodeId: Int? = null,
        bigCover: String? = null,
        link: String = "https://www.bilibili.com/bangumi/play/ep1",
        id: String,
    ) = PgcWebInitialStateData.Modules.Banner.BannerItem(
        title = title,
        cover = cover,
        link = link,
        rankId = 0,
        id = id,
        showReportData =
            PgcWebInitialStateData.Modules.Banner.BannerItem.ShowReportData(
                moduleType = "banner",
                moduleId = 0,
            ),
        seasonId = seasonId,
        episodeId = episodeId,
        bigCover = bigCover,
    )
}
