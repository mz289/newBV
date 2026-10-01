package dev.frost819.newbv.biliapi.entity.season

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [IndexResultData]、[IndexResultItem] 实体 HTTP→Domain 转换方法的单元测试。
 */
class IndexResultEntityTest {
    @Test
    fun `fromIndexResultData maps list and computes hasNext correctly`() {
        val httpItem =
            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem(
                badge = "会员",
                badgeInfo =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.BadgeInfo(
                        bgColor = "#FF0000",
                        bgColorNight = "#CC0000",
                        text = "会员",
                    ),
                badgeType = 1,
                cover = "http://cover.test",
                firstEp =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.FirstEp(
                        cover = "http://ep-cover.test",
                        epId = 100,
                    ),
                indexShow = "全12话",
                isFinish = 1,
                link = "http://link.test",
                mediaId = 200,
                order = "1",
                orderType = "click",
                score = "9.5",
                seasonId = 300,
                seasonStatus = 1,
                seasonType = 1,
                subTitle = "副标题",
                title = "测试番剧",
                titleIcon = "",
            )
        val httpData =
            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData(
                hasNext = 1,
                list = listOf(httpItem),
                num = 1,
                size = 20,
                total = 100,
            )

        val result = IndexResultData.fromIndexResultData(httpData)

        assertThat(result.list).hasSize(1)
        assertThat(result.list[0].title).isEqualTo("测试番剧")
        assertThat(result.list[0].subTitle).isEqualTo("副标题")
        assertThat(result.list[0].cover).isEqualTo("http://cover.test")
        assertThat(result.list[0].score).isEqualTo("9.5")
        assertThat(result.list[0].indexShow).isEqualTo("全12话")
        assertThat(result.list[0].seasonId).isEqualTo(300)
        assertThat(result.list[0].badge!!.text).isEqualTo("会员")
        assertThat(result.list[0].badge!!.bgColor).isEqualTo("#FF0000")
        assertThat(result.nextPage.hasNext).isTrue()
        assertThat(result.nextPage.nextPage).isEqualTo(2)
    }

    @Test
    fun `fromIndexResultData with hasNext 0 sets nextPage to -1`() {
        val httpData =
            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData(
                hasNext = 0,
                list = emptyList(),
                num = 5,
                size = 20,
                total = 100,
            )

        val result = IndexResultData.fromIndexResultData(httpData)

        assertThat(result.nextPage.hasNext).isFalse()
        assertThat(result.nextPage.nextPage).isEqualTo(-1)
    }

    @Test
    fun `fromIndexResultItem with empty badge text returns null badge`() {
        val httpItem =
            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem(
                badge = "",
                badgeInfo =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.BadgeInfo(
                        bgColor = "",
                        bgColorNight = "",
                        text = "",
                    ),
                badgeType = 0,
                cover = "",
                firstEp =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.FirstEp(
                        cover = "",
                        epId = 0,
                    ),
                indexShow = "",
                isFinish = 0,
                link = "",
                mediaId = 0,
                order = "",
                orderType = "",
                score = "",
                seasonId = 0,
                seasonStatus = 0,
                seasonType = 0,
                subTitle = "",
                title = "",
                titleIcon = "",
            )

        val item = IndexResultItem.fromIndexResultItem(httpItem)

        assertThat(item.badge).isNull()
    }
}
