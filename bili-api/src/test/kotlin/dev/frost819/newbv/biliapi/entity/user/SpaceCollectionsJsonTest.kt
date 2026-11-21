package dev.frost819.newbv.biliapi.entity.user

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.entity.user.WebFavResourceList
import dev.frost819.newbv.biliapi.http.entity.user.WebSeasonsSeriesData
import dev.frost819.newbv.biliapi.http.entity.user.WebUserFavFolders
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * UP 空间合集/系列/收藏夹实体的 JSON 反序列化测试。
 *
 * 接口返回 snake_case 键，实体属性为 camelCase——多词字段漏加 @SerialName
 * 时字段会静默落到默认值（合集全空/播放数丢失），必须用真实 JSON 做防回归。
 */
class SpaceCollectionsJsonTest {
    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
        }

    @Test
    fun `seasons_series_list parses seasons and series and page`() {
        val raw =
            """
            {"code":0,"message":"OK","ttl":1,"data":{"items_lists":{
              "page":{"page_num":1,"page_size":20,"total":11},
              "seasons_list":[
                {"archives":[],"recent_aids":[116539525171199],
                 "meta":{"category":0,"cover":"https://archive.biliimg.com/bfs/archive/x.jpg",
                         "mid":3546784896912196,"name":"合集·水族&爬宠&其他","title":"水族&爬宠&其他",
                         "ptime":1795274593,"season_id":8087292,"total":16}},
                {"archives":[],"recent_aids":[],
                 "meta":{"category":0,"cover":"https://archive.biliimg.com/bfs/archive/y.jpg",
                         "mid":3546784896912196,"name":"合集·人生副本","title":"人生副本",
                         "ptime":1795270000,"season_id":7419296,"total":89}}
              ],
              "series_list":[
                {"archives":[],"recent_aids":[],
                 "meta":{"category":1,"cover":"","mid":3546784896912196,
                         "name":"我的系列","series_id":370368,"total":19}}
              ]
            }}}
            """.trimIndent()

        val data = json.decodeFromString<Wrapper>(raw).data

        val itemsLists = data!!.itemsLists!!
        // 合集列表必须解析出来（seasons_list 的 @SerialName 防回归）
        assertThat(itemsLists.seasonsList).hasSize(2)
        assertThat(itemsLists.seriesList).hasSize(1)
        assertThat(itemsLists.seasonsList[0].meta?.seasonId).isEqualTo(8087292L)
        assertThat(itemsLists.seasonsList[0].meta?.title).isEqualTo("水族&爬宠&其他")
        assertThat(itemsLists.seasonsList[0].meta?.total).isEqualTo(16)
        assertThat(itemsLists.seriesList[0].meta?.seriesId).isEqualTo(370368L)
        assertThat(itemsLists.page?.pageNumber).isEqualTo(1)
        assertThat(itemsLists.page?.pageSize).isEqualTo(20)
        assertThat(itemsLists.page?.total).isEqualTo(11)
    }

    @Test
    fun `fav resource list parses cnt_info play and danmaku`() {
        val raw =
            """
            {"code":0,"message":"0","ttl":1,
             "data":{"info":{"id":4058080750,"title":"AI调用","media_count":129,"cover":"http://i1.hdslb.com/x.jpg"},
             "medias":[
               {"id":117618300161145,"bvid":"BV19jsE66EKh","title":"靠三桶油分红","cover":"http://i1.hdslb.com/y.jpg",
                "duration":198,"type":2,"pubtime":1794713239,
                "cnt_info":{"collect":5947,"play":80432,"danmaku":9}}
             ],
             "has_more":true}}
            """.trimIndent()

        val body = json.decodeFromString<FavWrapper>(raw)

        val media = body.data!!.medias.single()
        // cnt_info 的 @SerialName 防回归：播放数/弹幕数解析
        assertThat(media.cntInfo?.play).isEqualTo(80432L)
        assertThat(media.cntInfo?.danmaku).isEqualTo(9)
        assertThat(body.data?.hasMore).isTrue()
    }

    @Test
    fun `fav folder list parses cover and has_more`() {
        val raw =
            """
            {"code":0,"message":"OK","ttl":1,"data":{"count":2,"has_more":false,"list":[
              {"id":4058080750,"title":"AI调用","media_count":129,
               "cover":"http://i1.hdslb.com/bfs/archive/z.jpg","attr":22,"state":0,"type":0}
            ]}}
            """.trimIndent()

        val body = json.decodeFromString<FavoriteFoldersWrapper>(raw)

        val folder = body.data!!.list.single()
        assertThat(folder.id).isEqualTo(4058080750L)
        assertThat(folder.mediaCount).isEqualTo(129)
        assertThat(folder.cover).isEqualTo("http://i1.hdslb.com/bfs/archive/z.jpg")
        assertThat(body.data?.hasMore).isFalse()
    }

    /** 包装 code/message 以贴近真实响应，验证 data 层级解析。 */
    @kotlinx.serialization.Serializable
    private data class Wrapper(
        val data: WebSeasonsSeriesData? = null,
    )

    @kotlinx.serialization.Serializable
    private data class FavoriteFoldersWrapper(
        val data: WebUserFavFolders? = null,
    )

    @kotlinx.serialization.Serializable
    private data class FavWrapper(
        val data: WebFavResourceList? = null,
    )
}
