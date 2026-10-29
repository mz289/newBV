package dev.frost819.newbv.biliapi.entity.ugc.region

import dev.frost819.newbv.biliapi.entity.ugc.UgcItem
import dev.frost819.newbv.biliapi.http.entity.search.SearchVideoResult
import dev.frost819.newbv.biliapi.http.entity.search.SearchResultData

data class UgcFeedData(
    var hasNext: Boolean,
    var nextPage: UgcFeedPage,
    var items: List<UgcItem> = emptyList(),
) {
    companion object {
        fun fromRegionFeedRcmd(data: dev.frost819.newbv.biliapi.http.entity.region.RegionFeedRcmd): UgcFeedData =
            UgcFeedData(
                hasNext = data.archives.isNotEmpty(),
                nextPage = UgcFeedPage(),
                items = data.archives.map { UgcItem.fromRegionRcmdArchive(it) },
            )

        /** web 分类搜索结果 -> 分区数据，是否还有更多按请求页与总页数判断。 */
        fun fromSearchVideoResult(data: SearchResultData): UgcFeedData =
            UgcFeedData(
                hasNext = data.page < data.numPages,
                nextPage = UgcFeedPage(),
                items =
                    data.searchTypeResults
                        .filterIsInstance<SearchVideoResult>()
                        .map { UgcItem.fromSearchVideoResult(it) },
            )
    }
}
