package dev.frost819.newbv.biliapi.entity.pgc

import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebInitialStateData

/**
 * PGC 分区页数据（www.bilibili.com/<分区> 页面 `__INITIAL_STATE__`）。
 *
 * 板块（[modules]）完全由服务端下发——标题、样式与条目均取自接口，
 * 页面按板块数据驱动渲染、非空才显示；v2（电影/纪录片/电视剧/综艺）与
 * v3（番剧/国创）分区页的 `modules.ext` 结构同构。
 *
 * @property banner 轮播图。
 * @property indexGroups 索引快捷筛选分组（排序/风格/地区…，keyword 传回索引接口）。
 */
data class PgcWebPage(
    val banner: List<CarouselData.CarouselItem>,
    val indexGroups: List<IndexGroup>,
    val modules: List<WebModule>,
) {
    data class IndexGroup(
        val field: String,
        val name: String,
        val values: List<Value>,
    ) {
        data class Value(
            val keyword: String,
            val name: String,
        )
    }

    /**
     * 分区板块。
     *
     * @property moduleId 服务端模块 id。
     * @property title 板块名（接口下发）。
     * @property style 服务端样式标识（web_hot_v2 / web_rank_v3 / web_timeline_v3 /
     *   web_archive / web_feed_v3 等）。
     */
    data class WebModule(
        val moduleId: Int,
        val title: String,
        val style: String,
        val items: List<ModuleItem>,
    )

    /**
     * 板块条目。
     *
     * @property seasonId 站内剧集 id（有则跳剧集详情）。
     * @property avid UGC 稿件 id（编辑精选等模块，有则跳视频详情）。
     * @property rank 榜单名次（榜单板块条目）。
     */
    data class ModuleItem(
        val seasonId: Int?,
        val episodeId: Long?,
        val avid: Long?,
        val title: String,
        val subTitle: String?,
        val cover: String,
        val rating: String?,
        val rank: Int?,
    ) {
        /** 是否有可跳转的站内目标。 */
        val isNavigable: Boolean
            get() = seasonId != null || avid != null
    }

    companion object {
        fun fromPgcWebInitialStateData(data: PgcWebInitialStateData): PgcWebPage =
            PgcWebPage(
                banner = CarouselData.fromPgcWebInitialStateData(data).items,
                indexGroups =
                    data.modules.index?.items.orEmpty().mapNotNull { group ->
                        val name = group.name?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        val values =
                            buildList {
                                group.all?.keyword?.let { keyword ->
                                    add(IndexGroup.Value(keyword, group.all.name ?: "全部"))
                                }
                                group.values.forEach { row ->
                                    row.forEach { value ->
                                        val keyword = value.keyword ?: return@forEach
                                        val valueName = value.name ?: return@forEach
                                        add(IndexGroup.Value(keyword, valueName))
                                    }
                                }
                            }
                        PgcWebPage.IndexGroup(
                            field = group.field.orEmpty(),
                            name = name,
                            values = values,
                        )
                    },
                modules =
                    data.modules.ext.map { module ->
                        // 猜你喜欢（web_feed_v3）条目是分组结构，卡片在 sub_items 里，拍平
                        val rawItems =
                            module.items.flatMap { item ->
                                if (item.subItems.isNullOrEmpty()) listOf(item) else item.subItems
                            }
                        WebModule(
                            moduleId = module.moduleId ?: 0,
                            title = module.title.orEmpty(),
                            style = module.style.orEmpty(),
                            items = rawItems.mapNotNull { it.toModuleItem() },
                        )
                    },
            )

        private fun PgcWebInitialStateData.Modules.ExtModule.ExtItem.toModuleItem(): PgcWebPage.ModuleItem? {
            val rawCover = cover ?: return null
            return PgcWebPage.ModuleItem(
                seasonId = seasonId,
                episodeId = episodeId,
                avid = avid,
                title = title.orEmpty(),
                subTitle = subTitle?.takeIf { it.isNotEmpty() },
                cover = if (rawCover.startsWith("//")) "https:$rawCover" else rawCover,
                rating = rating?.takeIf { it.isNotEmpty() },
                rank = rank,
            )
        }
    }
}
