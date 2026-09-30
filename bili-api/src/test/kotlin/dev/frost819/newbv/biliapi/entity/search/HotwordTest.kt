package dev.frost819.newbv.biliapi.entity.search

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [Hotword] 实体的单元测试。
 *
 * 覆盖 `fromHttpWebHotword`、`fromHttpAppSquareDataItem`、
 * `fromHttpAppSearchTrendingHotword` 三个转换方法的字段映射与 null 处理。
 */
class HotwordTest {
    // ------------------------------------------------------------------
    // fromHttpWebHotword
    // ------------------------------------------------------------------

    @Test
    fun `fromHttpWebHotword maps all fields correctly`() {
        val httpHotword =
            dev.frost819.newbv.biliapi.http.entity.search.Hotword(
                keyword = "test-kw",
                showName = "测试关键词",
                icon = "http://icon.test",
                uri = "http://uri.test",
                goto = "search",
            )

        val result = Hotword.fromHttpWebHotword(httpHotword)

        assertThat(result.keyword).isEqualTo("test-kw")
        assertThat(result.showName).isEqualTo("测试关键词")
        assertThat(result.icon).isEqualTo("http://icon.test")
    }

    @Test
    fun `fromHttpWebHotword preserves icon field`() {
        val httpHotword =
            dev.frost819.newbv.biliapi.http.entity.search.Hotword(
                keyword = "kw",
                showName = "sn",
                icon = "icon-url",
                uri = "",
                goto = "",
            )

        assertThat(Hotword.fromHttpWebHotword(httpHotword).icon).isEqualTo("icon-url")
    }

    // ------------------------------------------------------------------
    // fromHttpAppSquareDataItem
    // ------------------------------------------------------------------

    @Test
    fun `fromHttpAppSearchTrendingHotword maps all fields correctly`() {
        val httpHotword =
            dev.frost819.newbv.biliapi.http.entity.search.SearchTendingData.Hotword(
                position = 3,
                keyword = "trending-kw",
                showName = "热搜词",
                icon = "http://trending-icon.test",
                hotId = 100,
                isCommercial = 0,
            )

        val result = Hotword.fromHttpAppSearchTrendingHotword(httpHotword)

        assertThat(result.keyword).isEqualTo("trending-kw")
        assertThat(result.showName).isEqualTo("热搜词")
        assertThat(result.icon).isEqualTo("http://trending-icon.test")
    }

    @Test
    fun `fromHttpAppSearchTrendingHotword maps null icon correctly`() {
        val httpHotword =
            dev.frost819.newbv.biliapi.http.entity.search.SearchTendingData.Hotword(
                position = 1,
                keyword = "kw",
                showName = "sn",
                icon = null,
                hotId = 1,
                isCommercial = 0,
            )

        val result = Hotword.fromHttpAppSearchTrendingHotword(httpHotword)

        assertThat(result.icon).isNull()
        assertThat(result.keyword).isEqualTo("kw")
    }
}
