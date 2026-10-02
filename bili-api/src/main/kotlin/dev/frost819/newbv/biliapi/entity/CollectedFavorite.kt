package dev.frost819.newbv.biliapi.entity

import dev.frost819.newbv.biliapi.http.entity.user.favorite.CollectedFavoriteFoldersData
import dev.frost819.newbv.biliapi.http.entity.user.favorite.FavSeasonContentData
import dev.frost819.newbv.biliapi.http.entity.user.favorite.FavSeasonMedia

/**
 * 订阅内容类型
 *
 * @param value 类型 11：收藏夹 21：视频合集
 */
enum class CollectedFavoriteType(
    val value: Int,
) {
    Folder(11),
    Season(21),
    ;

    companion object {
        fun fromValue(typeId: Int): CollectedFavoriteType = entries.firstOrNull { it.value == typeId } ?: Folder
    }
}

/**
 * 订阅的收藏夹/合集
 *
 * @param id 收藏夹/合集id 收藏夹内容用其查收藏夹详情，合集内容用其查合集内容
 * @param title 标题
 * @param cover 封面图片url
 * @param intro 简介
 * @param upper 创建者信息
 * @param mediaCount 内容数量
 * @param type 类型
 */
data class CollectedFavoriteFolder(
    val id: Long,
    val title: String,
    val cover: String,
    val intro: String,
    val upper: Upper,
    val mediaCount: Int,
    val type: CollectedFavoriteType,
) {
    companion object {
        fun fromHttpCollectedFavoriteFolder(
            httpCollectedFavoriteFolder: dev.frost819.newbv.biliapi.http.entity.user.favorite.CollectedFavoriteFolder,
        ): CollectedFavoriteFolder =
            CollectedFavoriteFolder(
                id = httpCollectedFavoriteFolder.id,
                title = httpCollectedFavoriteFolder.title,
                cover = httpCollectedFavoriteFolder.cover,
                intro = httpCollectedFavoriteFolder.intro,
                upper = Upper.fromHttpUpper(httpCollectedFavoriteFolder.upper),
                mediaCount = httpCollectedFavoriteFolder.mediaCount,
                type = CollectedFavoriteType.fromValue(httpCollectedFavoriteFolder.type),
            )
    }
}

/**
 * 订阅列表分页数据
 *
 * @param folders 订阅列表
 * @param hasMore 是否还有更多
 * @param total 订阅总数
 */
data class CollectedFavoriteFolderList(
    val folders: List<CollectedFavoriteFolder>,
    val hasMore: Boolean,
    val total: Int,
) {
    companion object {
        fun fromHttpCollectedFavoriteFoldersData(
            httpCollectedFavoriteFoldersData: CollectedFavoriteFoldersData,
        ): CollectedFavoriteFolderList =
            CollectedFavoriteFolderList(
                folders =
                    httpCollectedFavoriteFoldersData.list.map {
                        CollectedFavoriteFolder.fromHttpCollectedFavoriteFolder(
                            it,
                        )
                    },
                hasMore = httpCollectedFavoriteFoldersData.hasMore,
                total = httpCollectedFavoriteFoldersData.count,
            )
    }
}

/**
 * 将订阅的合集内容转换为收藏夹数据，便于复用收藏夹视频列表的加载链路。
 *
 * @param seasonId 合集id（info 缺失时兜底使用）
 */
fun FavSeasonContentData.toFavoriteFolderData(seasonId: Long): FavoriteFolderData =
    FavoriteFolderData(
        info =
            FavoriteFolderMetadata(
                id = info?.id ?: seasonId,
                fid = 0,
                mid = info?.upper?.mid ?: 0,
                title = info?.title.orEmpty(),
                cover = info?.cover.orEmpty(),
                videoInThisFav = true,
                mediaCount = info?.mediaCount ?: medias.size,
            ),
        medias = medias.map { it.toFavoriteItem() },
        hasMore = hasMore,
    )

/**
 * 将合集内容视频转换为通用收藏条目，未知类型归入 [FavoriteItemType.All]（展示时会过滤）。
 */
private fun FavSeasonMedia.toFavoriteItem(): FavoriteItem =
    FavoriteItem(
        id = id,
        type = FavoriteItemType.entries.firstOrNull { it.value == type } ?: FavoriteItemType.All,
        title = title,
        cover = cover,
        intro = intro,
        page = page,
        duration = duration,
        upper = Upper.fromHttpUpper(upper),
        link = link,
        pubtime = pubtime,
        bvid = bvid,
    )
