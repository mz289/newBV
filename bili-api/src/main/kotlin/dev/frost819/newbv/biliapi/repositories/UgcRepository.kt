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
}
