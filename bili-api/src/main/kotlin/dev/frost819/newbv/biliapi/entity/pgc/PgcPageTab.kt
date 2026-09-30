package dev.frost819.newbv.biliapi.entity.pgc

import dev.frost819.newbv.biliapi.http.entity.pgc.PgcPageTabData

/**
 * 番剧页模块化数据（/pgc/page/pc/bangumi/tab，TV/PC 端番剧 Tab）。
 *
 * 页面按 [Module.style] 数据驱动渲染（对齐 wiliwili 用法）：
 * `follow` 我的追番、`double_feed` 猜你喜欢（横版卡片，[nextCursor] 翻页）、
 * `v_card` 等其余样式为运营推荐（竖版卡片）。
 *
 * @property modules 模块列表，保持接口返回顺序。
 * @property nextCursor 下一页游标，传回 [dev.frost819.newbv.biliapi.repositories.PgcRepository.getPgcPageTab] 翻页。
 * @property hasNext 是否还有下一页。
 */
data class PgcPageTab(
    val modules: List<Module>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class Module(
        val moduleId: Int,
        val style: String,
        val title: String,
        val items: List<Card>,
    )

    /**
     * 模块条目（对齐 wiliwili PGCItemResult 的字段语义）。
     *
     * @property desc 进度或描述：progress 优先，其次 desc。
     * @property bottomBadge 右下角标：bottom_right_badge.text 优先，其次 new_ep.index_show。
     * @property badge 左上角标：badge_info.text（出品/大会员等）。
     */
    data class Card(
        val seasonId: Int,
        val episodeId: Long?,
        val title: String,
        val subTitle: String?,
        val desc: String?,
        val cover: String,
        val bottomBadge: String?,
        val badge: String?,
    )

    companion object {
        fun fromPgcPageTabData(data: PgcPageTabData): PgcPageTab =
            PgcPageTab(
                modules =
                    data.modules.map { module ->
                        Module(
                            moduleId = module.moduleId,
                            style = module.style,
                            title = module.title,
                            items =
                                module.items.map { item ->
                                    Card(
                                        seasonId = item.seasonId,
                                        episodeId = item.episodeId,
                                        title = item.title,
                                        subTitle = item.subTitle,
                                        desc = item.progress?.takeIf { it.isNotEmpty() } ?: item.desc,
                                        cover = item.cover,
                                        bottomBadge = item.bottomRightBadge?.text ?: item.newEp?.indexShow,
                                        badge = item.badgeInfo?.text,
                                    )
                                },
                        )
                    },
                nextCursor = data.nextCursor,
                hasNext = data.hasNext == 1,
            )
    }
}
