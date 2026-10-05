package dev.frost819.newbv.biliapi.repositories

import bilibili.app.interfaces.v1.suggestionResult3Req
import bilibili.pagination.pagination
import bilibili.polymer.app.search.v1.Item
import bilibili.polymer.app.search.v1.SearchByTypeRequest
import bilibili.polymer.app.search.v1.searchByTypeRequest
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.search.Hotword
import dev.frost819.newbv.biliapi.grpc.utils.handleGrpcException
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.util.smartDate
import dev.frost819.newbv.biliapi.util.convertStringTimeToSeconds

class SearchRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
) {
    private val searchSuggestStub
        get() =
            runCatching {
                bilibili.app.interfaces.v1.SearchGrpcKt
                    .SearchCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    private val searchResultStub
        get() =
            runCatching {
                bilibili.polymer.app.search.v1.SearchGrpcKt.SearchCoroutineStub(
                    channelRepository.requireDefaultChannel(),
                )
            }.getOrNull()

    suspend fun getSearchHotwords(
        limit: Int = 30,
        preferApiType: ApiType,
    ): List<Hotword> =
        when (preferApiType) {
            ApiType.Web ->
                BiliHttpApi
                    .getWebSearchSquare(limit = limit)
                    .getResponseData()
                    .trending.list
                    .map { Hotword.fromHttpWebHotword(it) }

            ApiType.App ->
                BiliHttpApi
                    .getSearchTrendRank(limit = limit)
                    .getResponseData()
                    .list
                    .map { Hotword.fromHttpAppSearchTrendingHotword(it) }
        }

        suspend fun getSearchSuggest(
        keyword: String,
        preferApiType: ApiType,
    ): List<String> =
        when (preferApiType) {
            ApiType.Web ->
                BiliHttpApi
                    .getKeywordSuggest(
                        term = keyword,
                        buvid = authRepository.buvid ?: "",
                    ).suggests
                    .map { it.value }

            // TODO 返回的关键词提示中可能包含通过avid/bvid/专栏id等的直达跳转结果项，需要过滤掉或进行单独处理
            ApiType.App ->
                searchSuggestStub
                    ?.suggest3(
                        suggestionResult3Req {
                            this.keyword = keyword
                        },
                    )?.listList
                    ?.map { it.keyword } ?: emptyList()
        }

    /**
     * 按分类进行搜索
     *
     * app 端的接口无法对视频投稿结果进行筛选搜索
     */
    suspend fun searchType(
        keyword: String,
        type: SearchType,
        tid: Int?,
        order: SearchFilterOrderType,
        duration: SearchFilterDuration,
        page: SearchTypePage,
        preferApiType: ApiType,
    ): SearchTypeResult =
        when (preferApiType) {
            ApiType.Web -> {
                val response =
                    BiliHttpApi
                        .searchType(
                            keyword = keyword,
                            type = type.httpTypeParam,
                            page = page.nextPageForWeb,
                            tid = tid,
                            order = order.httpOrderParam,
                            duration = duration.httpDurationParam,
                        ).getResponseData()
                SearchTypeResult.fromSearchTypeResult(response)
            }

            ApiType.App -> {
                val searchTypeReply =
                    runCatching {
                        val searchTypeRequest =
                            searchByTypeRequest {
                                this.keyword = keyword
                                this.type = type.grpcTypeParam
                                categorySort = order.grpcOrderParam
                                userType = SearchByTypeRequest.UserType.ALL
                                userSort = SearchByTypeRequest.UserSort.USER_SORT_DEFAULT
                                pagination =
                                    pagination {
                                        next = page.nextPageForApp
                                    }
                            }
                        searchResultStub?.searchByType(searchTypeRequest)
                            ?: throw IllegalStateException("Search result stub is not initialized")
                    }.onFailure { handleGrpcException(it) }.getOrThrow()
                SearchTypeResult.fromSearchTypeResult(searchTypeReply)
            }
        }
}

data class SearchTypePage(
    val nextPageForWeb: Int = 1,
    val nextPageForApp: String = "",
)

enum class SearchType(
    val httpTypeParam: String,
    val grpcTypeParam: Int,
) {
    Video(httpTypeParam = "video", grpcTypeParam = 10),
    MediaBangumi(httpTypeParam = "media_bangumi", grpcTypeParam = 7),
    MediaFt(httpTypeParam = "media_ft", grpcTypeParam = 8),
    BiliUser(httpTypeParam = "bili_user", grpcTypeParam = 2),
    LiveRoom(httpTypeParam = "live_room", grpcTypeParam = 4),
    // Article grpcTypeParam = 6
}

enum class SearchFilterOrderType(
    val httpOrderParam: String?,
    val grpcOrderParam: SearchByTypeRequest.CategorySort,
) {
    ComprehensiveSort(
        httpOrderParam = null,
        grpcOrderParam = SearchByTypeRequest.CategorySort.CATEGORY_SORT_DEFAULT,
    ),
    MostClicks(
        httpOrderParam = "click",
        grpcOrderParam = SearchByTypeRequest.CategorySort.CATEGORY_SORT_CLICK_COUNT,
    ),
    LatestPublish(
        httpOrderParam = "pubdate",
        grpcOrderParam = SearchByTypeRequest.CategorySort.CATEGORY_SORT_PUBLISH_TIME,
    ),
    MostDanmaku(
        httpOrderParam = "dm",
        grpcOrderParam = SearchByTypeRequest.CategorySort.UNRECOGNIZED,
    ),
    MostFavorites(
        httpOrderParam = "stow",
        grpcOrderParam = SearchByTypeRequest.CategorySort.UNRECOGNIZED,
    ),
    MostComment(
        httpOrderParam = null,
        grpcOrderParam = SearchByTypeRequest.CategorySort.CATEGORY_SORT_COMMENT_COUNT,
    ),
    MostLikes(
        httpOrderParam = null,
        grpcOrderParam = SearchByTypeRequest.CategorySort.CATEGORY_SORT_LIKE_COUNT,
    ),
    ;

    companion object {
        val webFilters =
            listOf(ComprehensiveSort, MostClicks, LatestPublish, MostDanmaku, MostFavorites)
        val allFilters =
            listOf(ComprehensiveSort, MostClicks, LatestPublish, MostComment, MostLikes)
    }
}

enum class SearchFilterDuration(
    val httpDurationParam: Int?,
    // val grpcOrderParam: SearchByTypeRequest.
) {
    All(null),
    LessThan10Minutes(1),
    Between10And30Minutes(2),
    Between30And60Minutes(3),
    MoreThan60Minutes(4),
}

data class SearchTypeResult(
    val videos: List<Video> = emptyList(),
    val pgcs: List<Pgc> = emptyList(),
    val users: List<User> = emptyList(),
    val liveRooms: List<LiveRoom> = emptyList(),
    val page: SearchTypePage,
    val hasMore: Boolean = true,
) {
    companion object {
        fun fromSearchTypeResult(
            result: dev.frost819.newbv.biliapi.http.entity.search.SearchResultData,
        ): SearchTypeResult {
            val hasMore = result.page < result.numPages
            return when (result.searchTypeResults.firstOrNull()) {
                is dev.frost819.newbv.biliapi.http.entity.search.SearchVideoResult -> {
                    SearchTypeResult(
                        videos =
                            result.searchTypeResults.map {
                                Video.fromSearchVideoResult(
                                    it as dev.frost819.newbv.biliapi.http.entity.search.SearchVideoResult,
                                )
                            },
                        page = SearchTypePage(nextPageForWeb = result.page + 1),
                        hasMore = hasMore,
                    )
                }

                is dev.frost819.newbv.biliapi.http.entity.search.SearchMediaResult -> {
                    SearchTypeResult(
                        pgcs =
                            result.searchTypeResults.map {
                                Pgc.fromSearchPgcResult(
                                    it as dev.frost819.newbv.biliapi.http.entity.search.SearchMediaResult,
                                )
                            },
                        page = SearchTypePage(nextPageForWeb = result.page + 1),
                        hasMore = hasMore,
                    )
                }

                is dev.frost819.newbv.biliapi.http.entity.search.SearchBiliUserResult -> {
                    SearchTypeResult(
                        users =
                            result.searchTypeResults.map {
                                User.fromSearchUserResult(
                                    it as dev.frost819.newbv.biliapi.http.entity.search.SearchBiliUserResult,
                                )
                            },
                        page = SearchTypePage(nextPageForWeb = result.page + 1),
                        hasMore = hasMore,
                    )
                }

                is dev.frost819.newbv.biliapi.http.entity.search.SearchLiveRoomResult -> {
                    SearchTypeResult(
                        liveRooms =
                            result.searchTypeResults.map {
                                LiveRoom.fromSearchLiveRoomResult(
                                    it as dev.frost819.newbv.biliapi.http.entity.search.SearchLiveRoomResult,
                                )
                            },
                        page = SearchTypePage(nextPageForWeb = result.page + 1),
                        hasMore = hasMore,
                    )
                }

                else -> {
                    SearchTypeResult(
                        page = SearchTypePage(nextPageForWeb = result.page + 1),
                        hasMore = hasMore,
                    )
                }
            }
        }

        fun fromSearchTypeResult(result: bilibili.polymer.app.search.v1.SearchByTypeResponse): SearchTypeResult =
            when (result.itemsList.firstOrNull()?.cardItemCase) {
                bilibili.polymer.app.search.v1.Item.CardItemCase.AV -> {
                    SearchTypeResult(
                        videos = result.itemsList.map { Video.fromSearchVideoCard(it) },
                        page = SearchTypePage(nextPageForApp = result.pagination.next),
                    )
                }

                bilibili.polymer.app.search.v1.Item.CardItemCase.BANGUMI -> {
                    SearchTypeResult(
                        pgcs = result.itemsList.map { Pgc.fromSearchPgcCard(it) },
                        page = SearchTypePage(nextPageForApp = result.pagination.next),
                    )
                }

                bilibili.polymer.app.search.v1.Item.CardItemCase.AUTHOR -> {
                    SearchTypeResult(
                        users = result.itemsList.map { User.fromSearchUserCard(it) },
                        page = SearchTypePage(nextPageForApp = result.pagination.next),
                    )
                }

                else -> {
                    SearchTypeResult(page = SearchTypePage(nextPageForApp = result.pagination.next))
                }
            }
    }

    interface SearchTypeResultItem

    data class Video(
        val aid: Long,
        val bvid: String,
        val title: String,
        val cover: String,
        val author: String,
        val mid: Long,
        val duration: Int,
        val play: Int,
        val danmaku: Int,
        val pubTime: String? = null,
        /** 付费相关角标（“充电专属”“付费”），普通视频为 null。 */
        val badge: String? = null,
    ) : SearchTypeResultItem {
        companion object {
            fun fromSearchVideoResult(video: dev.frost819.newbv.biliapi.http.entity.search.SearchVideoResult) =
                Video(
                    aid = video.aid,
                    bvid = video.bvid,
                    title = video.title,
                    cover = "https:${video.pic}",
                    author = video.author,
                    mid = video.mid,
                    duration = video.duration.convertStringTimeToSeconds(),
                    play = video.play ?: 0,
                    danmaku = video.danmaku,
                    pubTime = video.pubDate.smartDate,
                    badge = paidVideoBadge(video.isChargeVideo, video.isPay, video.badgePay),
                )

            fun fromSearchVideoCard(video: bilibili.polymer.app.search.v1.Item) =
                Video(
                    aid = video.param.toLong(),
                    bvid = video.av.share.video.bvid,
                    title = video.av.title,
                    cover = video.av.cover,
                    author = video.av.author,
                    mid = video.av.mid,
                    duration = video.av.duration.convertStringTimeToSeconds(),
                    play = video.av.play,
                    danmaku = video.av.danmaku,
                )
        }
    }

    data class Pgc(
        val title: String,
        val cover: String,
        val star: Float,
        val seasonId: Int,
        /** 官方角标（如“会员”“独家”），无角标为 null。 */
        val badge: String? = null,
    ) : SearchTypeResultItem {
        companion object {
            fun fromSearchPgcResult(pgc: dev.frost819.newbv.biliapi.http.entity.search.SearchMediaResult) =
                Pgc(
                    title = pgc.title,
                    cover = pgc.cover,
                    star = pgc.mediaScore.score,
                    seasonId = pgc.seasonId,
                    badge =
                        (
                            pgc.badges ?: pgc.displayInfo
                        ).orEmpty()
                            .firstOrNull { it.text.isNotBlank() }
                            ?.text,
                )

            fun fromSearchPgcCard(pgc: bilibili.polymer.app.search.v1.Item) =
                Pgc(
                    title = pgc.bangumi.title,
                    cover = pgc.bangumi.cover,
                    star = pgc.bangumi.rating.toFloat(),
                    seasonId = pgc.bangumi.seasonId.toInt(),
                )
        }
    }

    data class User(
        val mid: Long,
        val name: String,
        val avatar: String,
        val sign: String,
    ) : SearchTypeResultItem {
        companion object {
            fun fromSearchUserResult(user: dev.frost819.newbv.biliapi.http.entity.search.SearchBiliUserResult) =
                User(
                    mid = user.mid,
                    name = user.uname,
                    avatar = "https:${user.upic}",
                    sign = user.usign,
                )

            fun fromSearchUserCard(user: bilibili.polymer.app.search.v1.Item) =
                User(
                    mid = user.param.toLong(),
                    name = user.author.title,
                    avatar = user.author.cover,
                    sign = user.author.sign,
                )
        }
    }

    data class LiveRoom(
        val roomId: Long,
        val title: String,
        val uname: String,
        val uid: Long,
        val cover: String,
        val userCover: String,
        val face: String,
        val areaName: String,
        val online: Int,
        val liveStatus: Int,
    ) : SearchTypeResultItem {
        companion object {
            fun fromSearchLiveRoomResult(room: dev.frost819.newbv.biliapi.http.entity.search.SearchLiveRoomResult) =
                LiveRoom(
                    roomId = room.roomid,
                    title = room.title,
                    uname = room.uname,
                    uid = room.uid,
                    cover = room.cover.toHttpsUrl(),
                    userCover = room.userCover.toHttpsUrl(),
                    face = room.uface.toHttpsUrl(),
                    areaName = room.cateName,
                    online = room.online,
                    liveStatus = room.liveStatus,
                )
        }
    }
}

private fun String.toHttpsUrl(): String = if (startsWith("//")) "https:$this" else this

/**
 * 由搜索结果判定付费类角标文案。
 *
 * 优先级：充电专属（is_charge_video）> 付费（is_pay / badgepay）。
 */
private fun paidVideoBadge(
    isChargeVideo: Int,
    isPay: Int,
    badgePay: Boolean,
): String? =
    when {
        isChargeVideo == 1 -> "充电专属"
        isPay == 1 || badgePay -> "付费"
        else -> null
    }

/**
 * 全量搜索（`Search.SearchAll` / HTTP `/search/all/v2`）的结果。
 *
 * 聚合视频、番剧/影视、用户、直播间四类主要结果，忽略其余卡片类型。
 *
 * @property keyword 搜索关键词
 * @property videos 视频结果
 * @property pgcs 番剧/影视结果
 * @property users 用户结果
 * @property liveRooms 直播间结果
 * @property page 当前页码
 * @property pages 总页数
 * @property hasMore 是否还有更多
 */
