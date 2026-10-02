package dev.frost819.newbv.biliapi.entity.pgc

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebInitialStateData
import dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebInitialStateData.Modules.Index.IndexGroup.IndexValue
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Test

/**
 * [PgcWebPage] 实体转换方法的单元测试。
 *
 * 覆盖 `fromPgcWebInitialStateData`（分区页服务端板块/索引分组/轮播）转换路径：
 * 板块标题与条目顺序保持接口原样、协议相对封面补全、猜你喜欢分组条目拍平、
 * 无封面条目剔除、索引分组扁平化。
 */
class PgcWebPageTest {
    @Test
    fun `modules keep server title style and item order`() {
        val state =
            fakeState(
                ext =
                    listOf(
                        fakeModule(
                            moduleId = 1,
                            title = "推荐模块",
                            style = "web_hot_v2",
                            items =
                                listOf(
                                    fakeExtItem(title = "甲", seasonId = 10),
                                    fakeExtItem(title = "乙", seasonId = 11),
                                ),
                        ),
                        fakeModule(
                            moduleId = 2,
                            title = "电影热播榜",
                            style = "web_rank_v2",
                            items =
                                listOf(
                                    fakeExtItem(title = "榜一", seasonId = 20, rating = "9.8", rank = 1),
                                    fakeExtItem(title = "榜二", seasonId = 21, rank = 2),
                                ),
                        ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        assertThat(page.modules.map { it.title }).containsExactly("推荐模块", "电影热播榜").inOrder()
        assertThat(page.modules.map { it.style }).containsExactly("web_hot_v2", "web_rank_v2").inOrder()
        val rank = page.modules[1]
        assertThat(rank.items[0].rank).isEqualTo(1)
        assertThat(rank.items[0].rating).isEqualTo("9.8")
        assertThat(rank.items[1].rating).isNull()
    }

    @Test
    fun `protocol relative cover is completed and empty subtitle dropped`() {
        val state =
            fakeState(
                ext =
                    listOf(
                        fakeModule(
                            moduleId = 1,
                            title = "独家热播",
                            style = "web_operation_v",
                            items =
                                listOf(
                                    fakeExtItem(
                                        title = "剧",
                                        seasonId = 30,
                                        cover = "//i0.hdslb.com/x.png",
                                        subTitle = "",
                                    ),
                                ),
                        ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        val item = page.modules[0].items[0]
        assertThat(item.cover).isEqualTo("https://i0.hdslb.com/x.png")
        assertThat(item.subTitle).isNull()
    }

    @Test
    fun `feed v3 group items are flattened`() {
        // web_feed_v3 的条目是分组结构：卡片挂在 sub_items 里
        val state =
            fakeState(
                ext =
                    listOf(
                        fakeModule(
                            moduleId = 1,
                            title = "猜你喜欢",
                            style = "web_feed_v3",
                            items =
                                listOf(
                                    fakeExtItem(
                                        title = "分组头",
                                        cover = null,
                                        subItems =
                                            listOf(
                                                fakeExtItem(title = "卡1", seasonId = 40),
                                                fakeExtItem(title = "卡2", seasonId = 41),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        val titles = page.modules[0].items.map { it.title }
        assertThat(titles).containsExactly("卡1", "卡2").inOrder()
    }

    @Test
    fun `items without cover are dropped`() {
        val state =
            fakeState(
                ext =
                    listOf(
                        fakeModule(
                            moduleId = 1,
                            title = "专题模块",
                            style = "web_column",
                            items =
                                listOf(
                                    fakeExtItem(title = "活动卡", seasonId = null, cover = null),
                                    fakeExtItem(title = "正常卡", seasonId = 50),
                                ),
                        ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        assertThat(page.modules[0].items.map { it.title }).containsExactly("正常卡")
    }

    @Test
    fun `index groups flatten all and values`() {
        val state =
            fakeState(
                index =
                    PgcWebInitialStateData.Modules.Index(
                        items =
                            listOf(
                                PgcWebInitialStateData.Modules.Index.IndexGroup(
                                    field = "style_id",
                                    name = "风格",
                                    all =
                                        PgcWebInitialStateData.Modules.Index.IndexGroup.IndexValue(
                                            keyword = "-1",
                                            name = "全部",
                                        ),
                                    values =
                                        listOf(
                                            listOf(
                                                value(keyword = "10051", name = "喜剧"),
                                                value(keyword = "10052", name = "爱情"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        val group = page.indexGroups.single()
        assertThat(group.field).isEqualTo("style_id")
        assertThat(group.name).isEqualTo("风格")
        assertThat(group.values.map { it.keyword }).containsExactly("-1", "10051", "10052").inOrder()
        assertThat(group.values.map { it.name }).containsExactly("全部", "喜剧", "爱情").inOrder()
    }

    @Test
    fun `avid only items are kept for ugc navigation`() {
        val state =
            fakeState(
                ext =
                    listOf(
                        fakeModule(
                            moduleId = 1,
                            title = "编辑精选",
                            style = "web_archive",
                            items = listOf(fakeExtItem(title = "视频", avid = 1128)),
                        ),
                    ),
            )

        val page = PgcWebPage.fromPgcWebInitialStateData(state)

        val item = page.modules[0].items[0]
        assertThat(item.avid).isEqualTo(1128)
        assertThat(item.seasonId).isNull()
        assertThat(item.isNavigable).isTrue()
    }

    private fun fakeState(
        ext: List<PgcWebInitialStateData.Modules.ExtModule> = emptyList(),
        index: PgcWebInitialStateData.Modules.Index? = null,
    ) = PgcWebInitialStateData(
        modules =
            PgcWebInitialStateData.Modules(
                banner =
                    PgcWebInitialStateData.Modules.Banner(
                        title = "banner",
                        spmid = "",
                        size = 0,
                        style = "",
                        headers = JsonArray(emptyList()),
                        items = emptyList(),
                        wids = JsonArray(emptyList()),
                        moduleId = 1668,
                    ),
                index = index,
                ext = ext,
            ),
    )

    private fun fakeModule(
        moduleId: Int,
        title: String,
        style: String,
        items: List<PgcWebInitialStateData.Modules.ExtModule.ExtItem>,
    ) = PgcWebInitialStateData.Modules.ExtModule(
        title = title,
        style = style,
        moduleId = moduleId,
        items = items,
    )

    private fun fakeExtItem(
        title: String,
        seasonId: Int? = null,
        cover: String? = "//i0.hdslb.com/cover.png",
        rating: String? = null,
        rank: Int? = null,
        subTitle: String? = null,
        avid: Long? = null,
        subItems: List<PgcWebInitialStateData.Modules.ExtModule.ExtItem>? = null,
    ) = PgcWebInitialStateData.Modules.ExtModule.ExtItem(
        title = title,
        cover = cover,
        seasonId = seasonId,
        rating = rating,
        rank = rank,
        subTitle = subTitle,
        avid = avid,
        subItems = subItems,
    )

    private fun value(
        keyword: String,
        name: String,
    ) = IndexValue(keyword = keyword, name = name)
}
