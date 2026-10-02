package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.entity.pgc.PgcFeedData
import dev.frost819.newbv.biliapi.entity.pgc.PgcPageTab
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.PgcWebPage
import dev.frost819.newbv.biliapi.entity.pgc.index.Area
import dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
import dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
import dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
import dev.frost819.newbv.biliapi.entity.pgc.index.Producer
import dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
import dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.pgc.index.Year
import dev.frost819.newbv.biliapi.http.BiliHttpApi

class PgcRepository {
    suspend fun getCarousel(pgcType: PgcType): CarouselData {
        val initialStateData = BiliHttpApi.getPgcWebInitialStateData(pgcType)
        val carouselData = CarouselData.fromPgcWebInitialStateData(initialStateData)
        return carouselData
    }

    /**
     * 获取 PGC 分区页数据（轮播 + 索引快捷筛选 + 服务端下发的板块列表）。
     *
     * 板块标题与内容均来自分区页 `__INITIAL_STATE__`，页面按板块数据驱动渲染。
     */
    suspend fun getPgcWebPage(pgcType: PgcType): PgcWebPage =
        PgcWebPage.fromPgcWebInitialStateData(BiliHttpApi.getPgcWebInitialStateData(pgcType))

    suspend fun getFeed(
        pgcType: PgcType,
        cursor: Int,
    ): PgcFeedData {
        val data =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang ->
                    PgcFeedData.fromPgcFeedData(
                        BiliHttpApi
                            .getPgcFeedV3(
                                name = pgcType.name.lowercase(),
                                cursor = cursor,
                            ).getResponseData(),
                    )

                PgcType.Movie, PgcType.Tv, PgcType.Documentary, PgcType.Variety ->
                    PgcFeedData.fromPgcFeedData(
                        BiliHttpApi
                            .getPgcFeed(
                                name = pgcType.name.lowercase(),
                                cursor = cursor,
                            ).getResponseData(),
                    )
            }
        return data
    }

    /** 获取 PGC 热播榜（番剧/国创等分区的排行榜，免登录）。 */
    suspend fun getPgcRankList(pgcType: PgcType): PgcRankData =
        PgcRankData.fromPgcWebRankData(
            BiliHttpApi
                .getPgcRankList(seasonType = pgcType.rankSeasonTypeId)
                .getResponseData(),
        )

    /**
     * 获取番剧页模块化数据（免登录可用，登录后 follow/猜你喜欢个性化）。
     *
     * 页面按模块 style 数据驱动渲染；猜你喜欢翻页时把上一页的
     * [PgcPageTab.nextCursor] 作为 [cursor] 传入。
     *
     * @param isRefresh 1 表示刷新推荐内容。
     * @param cursor 翻页游标，首页传 "0"。
     */
    suspend fun getPgcPageTab(
        cursor: String = "0",
        isRefresh: Int = 0,
    ): PgcPageTab =
        PgcPageTab.fromPgcPageTabData(
            BiliHttpApi
                .getPgcPageTab(isRefresh = isRefresh, cursor = cursor)
                .getResponseData(),
        )

    /** 各分区在 /pgc/web/rank/list 中的 season_type 参数。 */
    private val PgcType.rankSeasonTypeId: Int
        get() =
            when (this) {
                PgcType.Anime -> 1
                PgcType.Movie -> 2
                PgcType.Documentary -> 3
                PgcType.GuoChuang -> 4
                PgcType.Tv -> 5
                PgcType.Variety -> 7
            }

    suspend fun getPgcIndex(
        pgcType: PgcType,
        indexOrder: IndexOrder,
        indexOrderType: IndexOrderType,
        seasonVersion: SeasonVersion,
        spokenLanguage: SpokenLanguage,
        area: Area,
        isFinish: IsFinish,
        copyright: Copyright,
        seasonStatus: SeasonStatus,
        seasonMonth: SeasonMonth,
        producer: Producer,
        year: Year,
        releaseDate: ReleaseDate,
        style: Style,
        page: PgcIndexData.PgcIndexPage,
    ): PgcIndexData {
        val data =
            PgcIndexData.fromIndexResultData(
                when (pgcType) {
                    PgcType.Anime ->
                        BiliHttpApi.seasonIndexAnimeResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonVersion = seasonVersion.id,
                            spokenLanguageType = spokenLanguage.id,
                            area = area.id,
                            isFinish = isFinish.id,
                            copyright = copyright.id,
                            seasonStatus = seasonStatus.id,
                            seasonMonth = seasonMonth.id,
                            year = year.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.GuoChuang ->
                        BiliHttpApi.seasonIndexGuochuangResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonVersion = seasonVersion.id,
                            isFinish = isFinish.id,
                            copyright = copyright.id,
                            seasonStatus = seasonStatus.id,
                            year = year.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Movie ->
                        BiliHttpApi.seasonIndexMovieResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Documentary ->
                        BiliHttpApi.seasonIndexDocumentaryResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            producerId = producer.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Tv ->
                        BiliHttpApi.seasonIndexTvResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Variety ->
                        BiliHttpApi.seasonIndexVarietyResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonStatus = seasonStatus.id,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )
                }.getResponseData(),
            )
        return data
    }
}
