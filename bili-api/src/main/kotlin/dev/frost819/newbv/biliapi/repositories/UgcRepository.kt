package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedData
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedPage
import dev.frost819.newbv.biliapi.http.BiliHttpApi

class UgcRepository {
    suspend fun getRegionFeedRcmd(
        ugcType: UgcTypeV2,
        page: UgcFeedPage,
    ): UgcFeedData {
        val responseData =
            BiliHttpApi
                .getRegionFeedRcmd(
                    displayId = page.nextPage,
                    fromRegion = ugcType.tid,
                ).getResponseData()
        val ugcFeedData = UgcFeedData.fromRegionFeedRcmd(responseData)
        ugcFeedData.nextPage = UgcFeedPage(page.nextPage + 1)
        return ugcFeedData
    }

    /**
     * 按筛选条件获取分区视频（web 分类搜索接口）。
     *
     * 参考 TVBox 哔哩合辑站源（csp_Bili）的筛选方案：排序 + 时长经
     * `x/web-interface/wbi/search/type` 检索，并以分区旧版 tid（[searchTid]）限定范围、
     * 分区名（[keyword]）作为检索词。搜索接口不识别 [UgcTypeV2.tid] 的新版分区号。
     */
    suspend fun getRegionFeedBySearch(
        searchTid: Int,
        keyword: String,
        order: SearchFilterOrderType,
        duration: SearchFilterDuration,
        page: UgcFeedPage,
    ): UgcFeedData {
        val responseData =
            BiliHttpApi
                .searchType(
                    keyword = keyword,
                    type = "video",
                    page = page.nextPage,
                    tid = searchTid,
                    order = order.httpOrderParam,
                    duration = duration.httpDurationParam,
                ).getResponseData()
        val ugcFeedData = UgcFeedData.fromSearchVideoResult(responseData)
        ugcFeedData.nextPage = UgcFeedPage(page.nextPage + 1)
        return ugcFeedData
    }
}
