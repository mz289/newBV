package dev.frost819.newbv.biliapi.repositories

import bilibili.app.dynamic.v2.DynamicGrpcKt
import bilibili.app.dynamic.v2.Refresh
import bilibili.app.dynamic.v2.dynVideoReq
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.user.DynamicVideoData
import dev.frost819.newbv.biliapi.entity.user.FollowedUser
import dev.frost819.newbv.biliapi.entity.user.SpaceCollectionData
import dev.frost819.newbv.biliapi.entity.user.SpaceCollectionPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoData
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoPage
import dev.frost819.newbv.biliapi.entity.user.UpCollection
import dev.frost819.newbv.biliapi.entity.user.UserFavFolder
import dev.frost819.newbv.biliapi.entity.user.UserFavFolderData
import dev.frost819.newbv.biliapi.grpc.utils.handleGrpcException
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.user.FollowAction
import dev.frost819.newbv.biliapi.http.entity.user.FollowActionSource
import dev.frost819.newbv.biliapi.http.entity.user.RelationType
import dev.frost819.newbv.biliapi.util.BiliLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class UserRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
) {
    private val dynamicStub
        get() =
            runCatching {
                DynamicGrpcKt.DynamicCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    private suspend fun modifyFollow(
        mid: Long,
        action: FollowAction,
        preferApiType: ApiType,
    ): Boolean {
        val response =
            when (authRepository.effectiveApiType(preferApiType, "user")) {
                ApiType.Web ->
                    BiliHttpApi.modifyFollow(
                        mid = mid,
                        action = action,
                        actionSource = FollowActionSource.Space,
                        csrf = authRepository.biliJct,
                    )

                ApiType.App ->
                    BiliHttpApi.modifyFollow(
                        mid = mid,
                        action = action,
                        actionSource = FollowActionSource.Space,
                        accessKey = authRepository.accessToken,
                    )
            }
        return response.code == 0
    }

    suspend fun followUser(
        mid: Long,
        preferApiType: ApiType,
    ): Boolean = modifyFollow(mid, FollowAction.AddFollow, preferApiType)

    suspend fun unfollowUser(
        mid: Long,
        preferApiType: ApiType,
    ): Boolean = modifyFollow(mid, FollowAction.DelFollow, preferApiType)

    suspend fun checkIsFollowing(mid: Long): Boolean? {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return null
        return runCatching {
            val response = BiliHttpApi.getRelations(mid = mid).getResponseData()
            listOf(
                RelationType.Followed,
                RelationType.FollowedQuietly,
                RelationType.BothFollowed,
            ).contains(response.relation.attribute)
        }.onFailure {
            BiliLogger.error(it) { "checkIsFollowing failed" }
        }.getOrNull()
    }

    suspend fun addSeasonFollow(
        seasonId: Int,
        preferApiType: ApiType,
    ): String =
        when (authRepository.effectiveApiType(preferApiType, "user")) {
            ApiType.Web ->
                BiliHttpApi
                    .addSeasonFollow(
                        seasonId = seasonId,
                        csrf = authRepository.biliJct!!,
                    ).getResponseData()
                    .toast

            ApiType.App ->
                BiliHttpApi
                    .addSeasonFollowApp(
                        seasonId = seasonId,
                        accessKey = authRepository.accessToken ?: "",
                    ).getResponseData()
                    .toast
        }

    suspend fun delSeasonFollow(
        seasonId: Int,
        preferApiType: ApiType,
    ): String =
        when (authRepository.effectiveApiType(preferApiType, "user")) {
            ApiType.Web ->
                BiliHttpApi
                    .delSeasonFollow(
                        seasonId = seasonId,
                        csrf = authRepository.biliJct!!,
                    ).getResponseData()
                    .toast

            ApiType.App ->
                BiliHttpApi
                    .delSeasonFollowApp(
                        seasonId = seasonId,
                        accessKey = authRepository.accessToken ?: "",
                    ).getResponseData()
                    .toast
        }

    suspend fun getSpaceVideos(
        mid: Long,
        order: SpaceVideoOrder = SpaceVideoOrder.PubDate,
        page: SpaceVideoPage = SpaceVideoPage(),
        preferApiType: ApiType,
    ): SpaceVideoData =
        when (authRepository.effectiveApiType(preferApiType, "user")) {
            ApiType.Web -> getWebSpaceVideos(mid, order, page)

            ApiType.App ->
                runCatching { getAppSpaceVideos(mid, order, page) }.getOrElse { appError ->
                    // App cursor 接口对"最多收藏"（stow）的支持未在公开文档确认：
                    // 非默认排序失败时回退 Web 通道重试；默认排序原样抛出，避免掩盖真实错误
                    if (order == SpaceVideoOrder.PubDate) throw appError
                    getWebSpaceVideos(mid, order, page)
                }
        }

    private suspend fun getWebSpaceVideos(
        mid: Long,
        order: SpaceVideoOrder,
        page: SpaceVideoPage,
    ): SpaceVideoData =
        BiliHttpApi
            .getWebUserSpaceVideos(
                mid = mid,
                order = order.value,
                pageNumber = page.nextWebPageNumber,
                pageSize = page.nextWebPageSize,
            ).getResponseData()
            .let { SpaceVideoData.fromWebSpaceVideoData(it) }

    private suspend fun getAppSpaceVideos(
        mid: Long,
        order: SpaceVideoOrder,
        page: SpaceVideoPage,
    ): SpaceVideoData =
        BiliHttpApi
            .getAppUserSpaceVideos(
                mid = mid,
                lastAvid = page.lastAvid,
                order = order.value,
                ts = System.currentTimeMillis(),
                accessKey = authRepository.accessToken ?: "",
            ).getResponseData()
            .let { SpaceVideoData.fromAppSpaceVideoData(it) }

    /**
     * 获取用户[mid]的合集或系列列表。
     *
     * 仅 Web 通道：polymer 接口没有 App/gRPC 对应端点，忽略 [preferApiType]。
     *
     * @param isSeason true=合集（seasons_series_list 的 seasons，翻完即止）
     *                 false=系列（home/seasons_series 专用端点）
     */
    suspend fun getSpaceCollections(
        mid: Long,
        isSeason: Boolean,
        page: SpaceCollectionPage = SpaceCollectionPage(),
        preferApiType: ApiType,
    ): SpaceCollectionData =
        when (isSeason) {
            true ->
                BiliHttpApi
                    .getWebUserSpaceSeasonsSeries(
                        mid = mid,
                        pageNumber = page.nextPageNumber,
                        pageSize = page.pageSize,
                    ).getResponseData()
                    .let { SpaceCollectionData.fromWebSeasonsOnly(it) }

            false ->
                BiliHttpApi
                    .getWebUserSpaceSeries(
                        mid = mid,
                        pageNumber = page.nextPageNumber,
                        pageSize = page.pageSize,
                    ).getResponseData()
                    .let { SpaceCollectionData.fromWebSeriesOnly(it) }
        }

    /**
     * 获取用户[mid]的公开收藏夹列表下一页。
     *
     * 仅 Web 通道；分页接口带封面与 has_more；无收藏夹时 list 为空。
     */
    suspend fun getSpaceFavFolders(
        mid: Long,
        page: SpaceCollectionPage = SpaceCollectionPage(),
        preferApiType: ApiType,
    ): UserFavFolderData {
        val response = BiliHttpApi.getWebUserFavFolders(mid = mid, pageNumber = page.nextPageNumber, pageSize = page.pageSize)
        // 非 0 错误码仍走统一异常；code=0 且 data=null 按空列表处理
        if (response.code != 0) response.getResponseData()
        val folders =
            response.data?.list.orEmpty().mapNotNull { folder ->
                val title = folder.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                UserFavFolder(
                    id = folder.id,
                    title = title,
                    cover = folder.cover ?: "",
                    mediaCount = folder.mediaCount ?: 0,
                )
            }
        return UserFavFolderData(
            folders = folders,
            page =
                SpaceCollectionPage(
                    hasNext = response.data?.hasMore == true,
                    nextPageNumber = page.nextPageNumber + 1,
                    pageSize = page.pageSize,
                ),
        )
    }

    /** 获取收藏夹[folderId]的内容视频列表（Web，按收藏时间倒序）。 */
    suspend fun getFavFolderVideos(
        folderId: Long,
        page: SpaceVideoPage = SpaceVideoPage(),
        preferApiType: ApiType,
    ): SpaceVideoData =
        BiliHttpApi
            .getWebUserFavResources(
                mediaId = folderId,
                pageNumber = page.nextWebPageNumber,
                pageSize = page.nextWebPageSize,
            ).getResponseData()
            .let { SpaceVideoData.fromWebFavResourceList(it, page.nextWebPageNumber, page.nextWebPageSize) }

    /**
     * 获取合集/系列[collection]内的视频列表。
     *
     * 合集走 polymer seasons_archives_list，系列走 x/series/archives，均为 Web 通道。
     */
    suspend fun getCollectionVideos(
        mid: Long,
        collection: UpCollection,
        page: SpaceVideoPage = SpaceVideoPage(),
        preferApiType: ApiType,
    ): SpaceVideoData =
        when (collection.isSeason) {
            true ->
                BiliHttpApi
                    .getWebUserSeasonArchives(
                        mid = mid,
                        seasonId = collection.id,
                        pageNumber = page.nextWebPageNumber,
                        pageSize = page.nextWebPageSize,
                    ).getResponseData()
                    .let { SpaceVideoData.fromWebSeasonArchives(it) }

            false ->
                BiliHttpApi
                    .getWebUserSeriesArchives(
                        mid = mid,
                        seriesId = collection.id,
                        pageNumber = page.nextWebPageNumber,
                        pageSize = page.nextWebPageSize,
                    ).getResponseData()
                    .let { SpaceVideoData.fromWebSeriesArchives(it) }
        }

    suspend fun getDynamicVideos(
        page: Int,
        offset: String,
        updateBaseline: String,
        preferApiType: ApiType,
    ): DynamicVideoData =
        when (authRepository.effectiveApiType(preferApiType, "user")) {
            ApiType.Web -> {
                val responseData =
                    BiliHttpApi
                        .getDynamicList(
                            type = "video",
                            page = page,
                            offset = offset,
                        ).getResponseData()
                DynamicVideoData.fromDynamicData(responseData)
            }

            ApiType.App -> {
                // App 通道未就绪时给出明确异常，而不是在 !! 处抛 NPE，
                // 便于上层统一处理为错误状态并提供重试
                val stub = dynamicStub ?: throw IllegalStateException("App gRPC channel is not initialized")
                runCatching {
                    stub.dynVideo(
                        dynVideoReq {
                            this.page = page
                            this.offset = offset
                            this.updateBaseline = updateBaseline
                            localTime = 8
                            refreshType =
                                if (offset == "") Refresh.refresh_new else Refresh.refresh_history
                        },
                    )
                }.getOrElse { handleGrpcException(it) }
                    .let { DynamicVideoData.fromDynamicData(it) }
            }
        }

    suspend fun getFollowedUsers(
        mid: Long,
        preferApiType: ApiType,
    ): List<FollowedUser> =
        when (authRepository.effectiveApiType(preferApiType, "user")) {
            ApiType.Web -> {
                val result = mutableListOf<FollowedUser>()
                val firstResponse =
                    BiliHttpApi
                        .getUserFollow(
                            mid = mid,
                        ).getResponseData()
                val userCount = firstResponse.total
                val pageCount = ceil((userCount.toFloat() / 50)).toInt()
                result.addAll(firstResponse.list.map { FollowedUser.fromHttpFollowedUser(it) })
                withContext(Dispatchers.IO) {
                    (2..pageCount)
                        .map { pageNumber ->
                            async {
                                BiliHttpApi
                                    .getUserFollow(
                                        mid = mid,
                                        pageNumber = pageNumber,
                                    ).getResponseData()
                            }
                        }.awaitAll()
                        .forEach { userFollowData ->
                            result.addAll(userFollowData.list.map { FollowedUser.fromHttpFollowedUser(it) })
                        }
                }
                result
            }

            ApiType.App -> {
                val result = mutableListOf<FollowedUser>()
                val firstResponse =
                    BiliHttpApi
                        .getUserFollow(
                            mid = mid,
                            accessKey = authRepository.accessToken,
                        ).getResponseData()
                val userCount = firstResponse.total
                val pageCount = ceil((userCount.toFloat() / 50)).toInt()
                result.addAll(firstResponse.list.map { FollowedUser.fromHttpFollowedUser(it) })
                withContext(Dispatchers.IO) {
                    (2..pageCount)
                        .map { pageNumber ->
                            async {
                                BiliHttpApi
                                    .getUserFollow(
                                        mid = mid,
                                        pageNumber = pageNumber,
                                        accessKey = authRepository.accessToken,
                                    ).getResponseData()
                            }
                        }.awaitAll()
                        .forEach { userFollowData ->
                            result.addAll(userFollowData.list.map { FollowedUser.fromHttpFollowedUser(it) })
                        }
                }
                result
            }
        }
}
