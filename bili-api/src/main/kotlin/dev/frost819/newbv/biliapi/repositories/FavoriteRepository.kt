package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.CollectedFavoriteFolderList
import dev.frost819.newbv.biliapi.entity.FavoriteFolderData
import dev.frost819.newbv.biliapi.entity.FavoriteFolderMetadata
import dev.frost819.newbv.biliapi.entity.FavoriteItemType
import dev.frost819.newbv.biliapi.entity.toFavoriteFolderData
import dev.frost819.newbv.biliapi.http.BiliHttpApi

class FavoriteRepository(
    private val authRepository: AuthRepository,
) {
    suspend fun checkVideoFavoured(
        aid: Long,
        preferApiType: ApiType,
    ): Boolean =
        BiliHttpApi.checkVideoFavoured(
            avid = aid,
            accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
        )

    suspend fun addVideoToFavoriteFolder(
        aid: Long,
        addMediaIds: List<Long>,
        preferApiType: ApiType,
    ) {
        BiliHttpApi.setVideoToFavorite(
            avid = aid,
            type = FavoriteItemType.Video.value,
            addMediaIds = addMediaIds,
            csrf = authRepository.biliJct.takeIf { preferApiType == ApiType.Web },
            accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
        )
    }

    suspend fun delVideoFromFavoriteFolder(
        aid: Long,
        delMediaIds: List<Long>,
        preferApiType: ApiType,
    ) {
        BiliHttpApi.setVideoToFavorite(
            avid = aid,
            type = FavoriteItemType.Video.value,
            delMediaIds = delMediaIds,
            csrf = authRepository.biliJct.takeIf { preferApiType == ApiType.Web },
            accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
        )
    }

    suspend fun updateVideoToFavoriteFolder(
        aid: Long,
        addMediaIds: List<Long>,
        delMediaIds: List<Long>,
        preferApiType: ApiType,
    ) {
        BiliHttpApi.setVideoToFavorite(
            avid = aid,
            type = FavoriteItemType.Video.value,
            addMediaIds = addMediaIds,
            delMediaIds = delMediaIds,
            csrf = authRepository.biliJct.takeIf { preferApiType == ApiType.Web },
            accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
        )
    }

    suspend fun getAllFavoriteFolderMetadataList(
        mid: Long,
        type: FavoriteItemType = FavoriteItemType.Video,
        rid: Long? = null,
        preferApiType: ApiType,
    ): List<FavoriteFolderMetadata> {
        val userFavoriteFoldersData =
            BiliHttpApi
                .getAllFavoriteFoldersInfo(
                    mid = mid,
                    type = type.value,
                    rid = rid,
                    accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
                ).getResponseData()
        return userFavoriteFoldersData.list.map {
            FavoriteFolderMetadata.fromHttpUserFavoriteFolder(it)
        }
    }

    suspend fun getFavoriteFolderData(
        mediaId: Long,
        pageSize: Int = 20,
        pageNumber: Int = 1,
        preferApiType: ApiType,
    ): FavoriteFolderData {
        val favoriteFolderListData =
            BiliHttpApi
                .getFavoriteList(
                    mediaId = mediaId,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                    accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
                ).getResponseData()
        return FavoriteFolderData.fromHttpFavoriteFolderInfoListData(favoriteFolderListData)
    }

    /**
     * 获取当前用户[mid]订阅（收藏）的收藏夹/合集列表（分页）。
     */
    suspend fun getCollectedFavoriteFolderList(
        mid: Long,
        pageSize: Int = 20,
        pageNumber: Int = 1,
    ): CollectedFavoriteFolderList {
        val collectedFavoriteFoldersData =
            BiliHttpApi
                .getCollectedFavoriteFolders(
                    mid = mid,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                ).getResponseData()
        return CollectedFavoriteFolderList.fromHttpCollectedFavoriteFoldersData(collectedFavoriteFoldersData)
    }

    /**
     * 获取订阅的合集[seasonId]内容列表。
     */
    suspend fun getCollectedSeasonData(
        seasonId: Long,
        pageSize: Int = 20,
        pageNumber: Int = 1,
    ): FavoriteFolderData {
        val favSeasonContentData =
            BiliHttpApi
                .getFavSeasonContent(
                    seasonId = seasonId,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                ).getResponseData()
        return favSeasonContentData.toFavoriteFolderData(seasonId)
    }
}
