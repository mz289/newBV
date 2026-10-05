package dev.frost819.newbv.biliapi.entity.pgc

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.SeasonIndexType
import org.junit.jupiter.api.Test

/**
 * [PgcItem] 实体 HTTP→Domain 转换方法的单元测试。
 */
class PgcEntityTest {
    // ------------------------------------------------------------------
    // PgcItem.fromIndexResultItem
    // ------------------------------------------------------------------

    @Test
    fun `PgcItem fromIndexResultItem maps all fields`() {
        val httpItem =
            dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem(
                badge = "会员",
                badgeInfo =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.BadgeInfo(
                        bgColor = "#FB7299",
                        bgColorNight = "#BB5C7B",
                        text = "会员",
                    ),
                badgeType = 1,
                cover = "http://cover.test",
                firstEp =
                    dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem.FirstEp(
                        cover = "http://ep.test",
                        epId = 500,
                    ),
                indexShow = "全24话",
                isFinish = 1,
                link = "http://link.test",
                mediaId = 100,
                order = "1",
                orderType = "click",
                score = "9.5",
                seasonId = 400,
                seasonStatus = 1,
                seasonType = 1,
                subTitle = "测试副标题",
                title = "测试番剧",
                titleIcon = "",
            )

        val item = PgcItem.fromIndexResultItem(httpItem)

        assertThat(item.cover).isEqualTo("http://cover.test")
        assertThat(item.title).isEqualTo("测试番剧")
        assertThat(item.subTitle).isEqualTo("测试副标题")
        assertThat(item.seasonId).isEqualTo(400)
        assertThat(item.episodeId).isEqualTo(500)
        assertThat(item.seasonType).isEqualTo(SeasonIndexType.Anime)
        assertThat(item.rating).isEqualTo("9.5")
    }
}
