package dev.frost819.newbv.biliapi.entity.user

import bilibili.app.space.v1.BiliSpaceVideo
import dev.frost819.newbv.biliapi.http.util.smartDate
import dev.frost819.newbv.biliapi.http.util.toSmartDate

data class SpaceVideoData(
    val videos: List<SpaceVideo>,
    val page: SpaceVideoPage,
) {
    companion object {
        fun fromWebSpaceVideoData(webSpaceVideoData: dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData) =
            SpaceVideoData(
                videos =
                    webSpaceVideoData.list
                        ?.vlist
                        ?.map { SpaceVideo.fromSpaceVideoItem(it) }
                        ?: emptyList(),
                page =
                    SpaceVideoPage(
                        hasNext =
                            (webSpaceVideoData.page?.count ?: 0)
                                > (
                                    (webSpaceVideoData.page?.pageNumber ?: 0) *
                                        (webSpaceVideoData.page?.pageSize ?: 0)
                                ),
                        nextWebPageSize = webSpaceVideoData.page?.pageSize ?: 0,
                        nextWebPageNumber = (webSpaceVideoData.page?.pageNumber ?: 0) + 1,
                    ),
            )

        fun fromAppSpaceVideoData(appSpaceVideoData: dev.frost819.newbv.biliapi.http.entity.user.AppSpaceVideoData) =
            SpaceVideoData(
                videos =
                    appSpaceVideoData.item
                        .map { SpaceVideo.fromSpaceVideoItem(it) },
                page =
                    SpaceVideoPage(
                        hasNext = appSpaceVideoData.hasNext,
                        lastAvid =
                            appSpaceVideoData.item
                                .lastOrNull()
                                ?.param
                                ?.toLong() ?: 0,
                    ),
            )

        /** 合集内视频列表（polymer seasons_archives_list）转换。 */
        fun fromWebSeasonArchives(webSeasonArchivesData: dev.frost819.newbv.biliapi.http.entity.user.WebSeasonArchivesData) =
            SpaceVideoData(
                videos = webSeasonArchivesData.archives.map { SpaceVideo.fromCollectionArchiveItem(it) },
                page =
                    SpaceVideoPage(
                        hasNext = pageHasNext(webSeasonArchivesData.page?.pageNumber, webSeasonArchivesData.page?.pageSize, webSeasonArchivesData.page?.total),
                        nextWebPageSize = webSeasonArchivesData.page?.pageSize ?: 20,
                        nextWebPageNumber = (webSeasonArchivesData.page?.pageNumber ?: 0) + 1,
                    ),
            )

        /** 系列内视频列表（x/series/archives）转换。 */
        fun fromWebSeriesArchives(webSeriesArchivesData: dev.frost819.newbv.biliapi.http.entity.user.WebSeriesArchivesData) =
            SpaceVideoData(
                videos = webSeriesArchivesData.archives.map { SpaceVideo.fromCollectionArchiveItem(it) },
                page =
                    SpaceVideoPage(
                        hasNext = pageHasNext(webSeriesArchivesData.page?.num, webSeriesArchivesData.page?.size, webSeriesArchivesData.page?.total),
                        nextWebPageSize = webSeriesArchivesData.page?.size ?: 20,
                        nextWebPageNumber = (webSeriesArchivesData.page?.num ?: 0) + 1,
                    ),
            )

        /** 收藏夹内容列表（x/v3/fav/resource/list）转换；过滤非视频（type!=2）与已失效条目。 */
        fun fromWebFavResourceList(
            webFavResourceList: dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList,
            pageNumber: Int,
            pageSize: Int,
        ): SpaceVideoData {
            val videos =
                webFavResourceList.medias
                    .filter { it.type == 2 && !it.bvid.isNullOrEmpty() }
                    .map { SpaceVideo.fromFavResourceItem(it) }
            return SpaceVideoData(
                videos = videos,
                page =
                    SpaceVideoPage(
                        hasNext = webFavResourceList.hasMore == true,
                        nextWebPageSize = pageSize,
                        nextWebPageNumber = pageNumber + 1,
                    ),
            )
        }

        /** 页码×页宽小于总数即还有下一页（两个归档接口的分页对象字段名不同，统一按三要素判断）。 */
        private fun pageHasNext(
            pageNumber: Int?,
            pageSize: Int?,
            total: Int?,
        ): Boolean {
            val num = pageNumber ?: 0
            val size = pageSize ?: 0
            return size > 0 && num * size < (total ?: 0)
        }
    }
}

data class SpaceVideo(
    val aid: Long,
    val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val duration: Int,
    val play: Int,
    val danmaku: Int,
    val pubTime: String?,
    val playbackPosition: Int = 0,
) {
    companion object {
        fun fromSpaceVideoItem(
            spaceVideoItem: dev.frost819.newbv.biliapi.http.entity.user.WebSpaceVideoData.SpaceVideoListItem.VListItem,
        ) = SpaceVideo(
            aid = spaceVideoItem.aid,
            bvid = spaceVideoItem.bvid,
            title = spaceVideoItem.title,
            cover = spaceVideoItem.pic,
            author = spaceVideoItem.author,
            duration = convertMmSsToSeconds(spaceVideoItem.length),
            play = spaceVideoItem.play,
            danmaku = spaceVideoItem.videoReview,
            pubTime = spaceVideoItem.created.toSmartDate(),
            playbackPosition = spaceVideoItem.playbackPosition,
        )

        /** 将 App gRPC 用户空间视频转换为领域模型。 */
        fun fromGrpcSpaceVideoItem(item: BiliSpaceVideo) =
            SpaceVideo(
                aid = item.param.toLongOrNull() ?: 0L,
                bvid = item.bvid,
                title = item.title,
                cover = item.cover,
                author = "",
                duration = item.duration.toInt(),
                play = item.play,
                danmaku = item.danmaku.toInt(),
                pubTime = item.ctime.toInt().smartDate,
            )

        fun fromSpaceVideoItem(
            spaceVideoItem: dev.frost819.newbv.biliapi.http.entity.user.AppSpaceVideoData.SpaceVideoItem,
        ) = SpaceVideo(
            aid = spaceVideoItem.param.toLong(),
            bvid = spaceVideoItem.bvid ?: "",
            title = spaceVideoItem.title,
            cover = spaceVideoItem.cover,
            author = spaceVideoItem.author ?: "",
            duration = spaceVideoItem.duration,
            play = spaceVideoItem.play,
            danmaku = spaceVideoItem.danmaku,
            pubTime = spaceVideoItem.ctime.smartDate,
        )

        /** 收藏夹内视频稿件（x/v3/fav/resource/list 条目）转换。 */
        fun fromFavResourceItem(
            mediaItem: dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList.MediaItem,
        ) = SpaceVideo(
            aid = mediaItem.id,
            bvid = mediaItem.bvid ?: "",
            title = mediaItem.title ?: "",
            cover = mediaItem.cover ?: "",
            author = "",
            duration = mediaItem.duration ?: 0,
            play =
                when (val play = mediaItem.cntInfo?.play) {
                    null -> -1
                    else ->
                        if (play > Int.MAX_VALUE) {
                            Int.MAX_VALUE
                        } else {
                            play.toInt()
                        }
                },
            danmaku = mediaItem.cntInfo?.danmaku ?: -1,
            pubTime = mediaItem.pubtime?.toSmartDate(),
        )

        /** 合集/系列内视频稿件（polymer 归档对象）转换。 */
        fun fromCollectionArchiveItem(
            archiveItem: dev.frost819.newbv.biliapi.http.entity.user.WebCollectionArchiveItem,
        ) = SpaceVideo(
            aid = archiveItem.aid,
            bvid = archiveItem.bvid,
            title = archiveItem.title,
            cover = archiveItem.pic,
            author = "",
            duration = archiveItem.duration,
            play =
                when (val view = archiveItem.stat?.view) {
                    null -> -1
                    else ->
                        if (view > Int.MAX_VALUE) {
                            Int.MAX_VALUE
                        } else {
                            view.toInt()
                        }
                },
            danmaku = archiveItem.stat?.danmaku ?: -1,
            pubTime = archiveItem.pubdate?.toSmartDate(),
            playbackPosition = archiveItem.playbackPosition,
        )
    }
}

private fun convertMmSsToSeconds(time: String): Int {
    val parts = time.split(":")
    val minutes = parts[0].toInt()
    val seconds = parts[1].toInt()
    return (minutes * 60) + seconds
}

enum class SpaceVideoOrder(
    val value: String,
) {
    PubDate("pubdate"),
    Click("click"),
    Stow("stow"),
}

/**
 * UP 主空间合集/系列条目。
 *
 * @param id 合集（seasonId）或系列（seriesId）的 ID
 * @param title 展示标题（合集优先取去掉"合集·"前缀的 title）
 * @param cover 封面 URL
 * @param total 内含视频总数
 * @param isSeason true=合集（season）false=系列（series）
 */
data class UpCollection(
    val id: Long,
    val title: String,
    val cover: String,
    val total: Int,
    val isSeason: Boolean,
)

/**
 * UP 主空间合集/系列列表数据。
 */
data class SpaceCollectionData(
    val collections: List<UpCollection>,
    val page: SpaceCollectionPage,
) {
    companion object {
        /**
         * 合集栏目（仅 seasons）：由 seasons_series_list 响应转换。
         *
         * 合集在混合分页中排在系列之前，某页 seasons 为空即说明合集已翻完，
         * 提前终止避免无谓地拉取系列页。
         */
        fun fromWebSeasonsOnly(
            webSeasonsSeriesData: dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData,
        ): SpaceCollectionData {
            val itemsLists = webSeasonsSeriesData.itemsLists
            val page = itemsLists?.page
            val pageNumber = page?.pageNumber ?: 1
            val pageSize = page?.pageSize ?: 0
            val total = page?.total ?: 0
            val seasons =
                itemsLists?.seasonsList
                    .orEmpty()
                    .mapNotNull { it.meta?.toUpCollection(isSeason = true) }
            return SpaceCollectionData(
                collections = seasons,
                page =
                    SpaceCollectionPage(
                        hasNext = seasons.isNotEmpty() && pageSize > 0 && pageNumber * pageSize < total,
                        nextPageNumber = pageNumber + 1,
                        pageSize = if (pageSize > 0) pageSize else DEFAULT_PAGE_SIZE,
                    ),
            )
        }

        /**
         * 系列栏目（仅 series）：由 home/seasons_series 响应转换。
         *
         * 该端点的 page.total 为总页数语义，hasNext 按 页码 < 总页数 判断。
         */
        fun fromWebSeriesOnly(
            webSeasonsSeriesData: dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData,
        ): SpaceCollectionData {
            val itemsLists = webSeasonsSeriesData.itemsLists
            val page = itemsLists?.page
            val pageNumber = page?.pageNumber ?: 1
            val pageSize = page?.pageSize ?: 0
            val totalPages = page?.total ?: 0
            val series =
                itemsLists?.seriesList
                    .orEmpty()
                    .mapNotNull { it.meta?.toUpCollection(isSeason = false) }
            return SpaceCollectionData(
                collections = series,
                page =
                    SpaceCollectionPage(
                        hasNext =
                            series.isNotEmpty() && (
                                if (totalPages > 0) {
                                    pageNumber < totalPages
                                } else {
                                    true
                                }
                            ),
                        nextPageNumber = pageNumber + 1,
                        pageSize = if (pageSize > 0) pageSize else DEFAULT_PAGE_SIZE,
                    ),
            )
        }

        private fun dev.frost819.newbv.biliapi.http.entity.user.WebCollectionMeta.toUpCollection(
            isSeason: Boolean,
        ): UpCollection? {
            val id = seasonId ?: seriesId ?: return null
            val displayTitle =
                title?.takeIf { it.isNotBlank() }
                    ?: name?.takeIf { it.isNotBlank() }
                    ?: return null
            return UpCollection(
                id = id,
                title = displayTitle,
                cover = cover ?: "",
                total = total ?: 0,
                isSeason = isSeason,
            )
        }

        const val DEFAULT_PAGE_SIZE = 20
    }
}

/**
 * UP 主公开收藏夹条目。
 *
 * @param id 收藏夹 ID（内容页 media_id）
 * @param title 收藏夹标题
 * @param mediaCount 收藏内容数
 */
data class UserFavFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
)

/**
 * UP 主空间合集/系列分页游标。
 *
 * 仅 Web 通道（polymer/x/series 接口均为 Web 专用），按页码分页。
 */
data class SpaceCollectionPage(
    val hasNext: Boolean = true,
    val nextPageNumber: Int = 1,
    val pageSize: Int = SpaceCollectionData.DEFAULT_PAGE_SIZE,
)

data class SpaceVideoPage(
    val hasNext: Boolean = true,
    // web
    val nextWebPageSize: Int = 20,
    val nextWebPageNumber: Int = 1,
    // app
    val lastAvid: Long = 0,
)
