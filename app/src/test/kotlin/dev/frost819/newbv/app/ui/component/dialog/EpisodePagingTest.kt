package dev.frost819.newbv.app.ui.component.dialog

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 选集分页模型（[episodePages] / [pageIndexOfOrdinal]）的单元测试。
 *
 * 覆盖正序/倒序切页、末页不满、空列表，以及「切排序后仍停在同一批集数」
 * 的锚定语义——弹窗的定位与页签文案都建立在这套序号上。
 */
class EpisodePagingTest {
    @Test
    fun `正序按原始顺序切页且页签为集数区间`() {
        val pages = episodePages(count = 180, pageSize = 20, order = EpisodeOrder.Ascending)

        assertThat(pages).hasSize(9)
        assertThat(pages.first().ordinals).isEqualTo((1..20).toList())
        assertThat(pages.first().label).isEqualTo("1–20")
        assertThat(pages.last().ordinals).isEqualTo((161..180).toList())
        assertThat(pages.last().label).isEqualTo("161–180")
    }

    @Test
    fun `倒序第一页是最新集数且页签仍从大到小可读`() {
        val pages = episodePages(count = 180, pageSize = 20, order = EpisodeOrder.Descending)

        assertThat(pages).hasSize(9)
        assertThat(pages.first().ordinals).isEqualTo((180 downTo 161).toList())
        // 页签文案始终表达原始集数区间
        assertThat(pages.first().label).isEqualTo("161–180")
        assertThat(pages[1].label).isEqualTo("141–160")
        assertThat(pages.last().ordinals).isEqualTo((20 downTo 1).toList())
        assertThat(pages.last().label).isEqualTo("1–20")
    }

    @Test
    fun `末页不满时按实际条目数切分且两种排序页数一致`() {
        val ascending = episodePages(count = 25, pageSize = 20, order = EpisodeOrder.Ascending)
        val descending = episodePages(count = 25, pageSize = 20, order = EpisodeOrder.Descending)

        assertThat(ascending).hasSize(2)
        assertThat(ascending.last().ordinals).isEqualTo((21..25).toList())
        assertThat(ascending.last().label).isEqualTo("21–25")
        assertThat(descending).hasSize(2)
        assertThat(descending.last().ordinals).isEqualTo((5 downTo 1).toList())
        assertThat(descending.last().label).isEqualTo("1–5")
    }

    @Test
    fun `空列表与非法参数返回空页集合`() {
        assertThat(episodePages(count = 0, pageSize = 20, order = EpisodeOrder.Ascending)).isEmpty()
        assertThat(episodePages(count = 10, pageSize = 0, order = EpisodeOrder.Descending)).isEmpty()
        assertThat(emptyList<EpisodePage>().pageIndexOfOrdinal(1)).isEqualTo(-1)
        assertThat(episodePages(10, 20, EpisodeOrder.Ascending).pageIndexOfOrdinal(null)).isEqualTo(-1)
    }

    @Test
    fun `定位当前集在两种排序下指向同一批集数`() {
        val ascending = episodePages(count = 180, pageSize = 20, order = EpisodeOrder.Ascending)
        val descending = episodePages(count = 180, pageSize = 20, order = EpisodeOrder.Descending)

        // 第 175 集：正序在第 9 页，倒序在第 1 页
        assertThat(ascending.pageIndexOfOrdinal(175)).isEqualTo(8)
        assertThat(descending.pageIndexOfOrdinal(175)).isEqualTo(0)

        // 切换排序的锚定语义：正序第 1 页（1–20）在倒序下落在最后一页
        val anchor = ascending.first().ordinals.first()
        assertThat(descending.pageIndexOfOrdinal(anchor)).isEqualTo(descending.lastIndex)
    }

    @Test
    fun `越界序号不落在任何页`() {
        val pages = episodePages(count = 180, pageSize = 20, order = EpisodeOrder.Ascending)

        assertThat(pages.pageIndexOfOrdinal(0)).isEqualTo(-1)
        assertThat(pages.pageIndexOfOrdinal(181)).isEqualTo(-1)
    }
}
