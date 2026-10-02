package dev.frost819.newbv.app.viewmodel.pgc

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.PgcWebPage
import dev.frost819.newbv.biliapi.entity.season.Timeline
import dev.frost819.newbv.biliapi.entity.season.TimelineEp
import dev.frost819.newbv.biliapi.entity.season.TimelineFilter
import dev.frost819.newbv.biliapi.repositories.PgcRepository
import dev.frost819.newbv.biliapi.repositories.SeasonRepository
import dev.frost819.newbv.data.datastore.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Date

/**
 * [PgcViewModel] 的单元测试。
 *
 * 验证分区富布局数据的聚合加载：分区页服务端板块（标题/条目按接口下发）、
 * 索引分组，国创额外聚合放送时间表；以及分区间状态隔离、懒加载去重、刷新、
 * 页面数据与时间表的失败隔离。使用 MockK mock 仓库层。
 *
 * 时间表加载读取 [Prefs.apiType]：用 resetForTesting 重置内存缓存即可
 * 读到默认值，无需初始化 DataStore（避免多测试类共享 JVM 时的实例冲突）。
 */
class PgcViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pgcRepository: PgcRepository
    private lateinit var seasonRepository: SeasonRepository
    private lateinit var viewModel: PgcViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        Prefs.resetForTesting()
        pgcRepository = mockk()
        seasonRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeModuleItem(
        seasonId: Int,
        title: String = "影视 $seasonId",
    ) = PgcWebPage.ModuleItem(
        seasonId = seasonId,
        episodeId = null,
        avid = null,
        title = title,
        subTitle = "副标题 $seasonId",
        cover = "https://example.com/cover$seasonId.jpg",
        rating = "9.0",
        rank = null,
    )

    private fun fakeModule(
        moduleId: Int,
        title: String,
        style: String,
        items: List<PgcWebPage.ModuleItem>,
    ) = PgcWebPage.WebModule(
        moduleId = moduleId,
        title = title,
        style = style,
        items = items,
    )

    /** 服务端下发的分区页数据：板块标题与顺序模拟接口返回。 */
    private fun fakeWebPage(moduleTitle: String = "电影热播榜"): PgcWebPage =
        PgcWebPage(
            banner =
                listOf(
                    CarouselData.CarouselItem(
                        cover = "https://example.com/banner.jpg",
                        title = "轮播标题",
                        seasonId = 1,
                        episodeId = 100,
                    ),
                ),
            indexGroups =
                listOf(
                    PgcWebPage.IndexGroup(
                        field = "style_id",
                        name = "风格",
                        values =
                            listOf(
                                PgcWebPage.IndexGroup.Value(keyword = "-1", name = "全部"),
                                PgcWebPage.IndexGroup.Value(keyword = "10051", name = "喜剧"),
                            ),
                    ),
                ),
            modules =
                listOf(
                    fakeModule(
                        moduleId = 1,
                        title = "推荐模块",
                        style = "web_hot_v2",
                        items = listOf(fakeModuleItem(1), fakeModuleItem(2)),
                    ),
                    fakeModule(
                        moduleId = 2,
                        title = moduleTitle,
                        style = "web_rank_v2",
                        items =
                            listOf(
                                fakeModuleItem(3).copy(rank = 1, rating = "9.8"),
                                fakeModuleItem(4).copy(rank = 2),
                            ),
                    ),
                ),
        )

    private fun fakeTimeline() =
        listOf(
            Timeline(
                dateString = "2026-10-02",
                date = Date(),
                dayOfWeek = 5,
                isToday = true,
                episodes =
                    listOf(
                        TimelineEp(
                            cover = "https://example.com/tl.jpg",
                            title = "时间表国创",
                            seasonId = 900,
                            publishIndex = "第7话",
                            publishTime = "10:00",
                            publishDate = Date(),
                        ),
                    ),
            ),
        )

    private fun stubAll() {
        coEvery { pgcRepository.getPgcWebPage(any()) } returns fakeWebPage()
        coEvery { seasonRepository.getTimeline(any(), any()) } returns fakeTimeline()
    }

    @Test
    fun `loadIfNeeded loads server modules index and carousel`() =
        runTest(testDispatcher) {
            stubAll()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()

            val state = viewModel.uiStateFor(PgcType.Movie)
            assertThat(state.loaded).isTrue()
            assertThat(state.error).isFalse()
            // 板块标题与顺序保持接口下发
            assertThat(state.modules.map { it.title }).containsExactly("推荐模块", "电影热播榜").inOrder()
            assertThat(state.modules[1].items).hasSize(2)
            assertThat(state.carouselItems).hasSize(1)
            assertThat(state.indexGroups.single().name).isEqualTo("风格")
        }

    @Test
    fun `loadIfNeeded is no-op when already loaded`() =
        runTest(testDispatcher) {
            stubAll()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()

            coVerify(exactly = 1) { pgcRepository.getPgcWebPage(PgcType.Movie) }
        }

    @Test
    fun `guochuang loads timeline with guochuang filter`() =
        runTest(testDispatcher) {
            stubAll()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.GuoChuang)
            advanceUntilIdle()

            val state = viewModel.uiStateFor(PgcType.GuoChuang)
            assertThat(state.timeline).hasSize(1)
            assertThat(state.timelineError).isFalse()
            coVerify { seasonRepository.getTimeline(TimelineFilter.GuoChuang, any()) }
        }

    @Test
    fun `non guochuang does not load timeline`() =
        runTest(testDispatcher) {
            stubAll()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Tv)
            advanceUntilIdle()

            coVerify(exactly = 0) { seasonRepository.getTimeline(any(), any()) }
        }

    @Test
    fun `states are isolated between types`() =
        runTest(testDispatcher) {
            stubAll()
            coEvery {
                pgcRepository.getPgcWebPage(PgcType.Tv)
            } returns fakeWebPage(moduleTitle = "电视剧热播榜")

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()
            viewModel.loadIfNeeded(PgcType.Tv)
            advanceUntilIdle()

            // 切到电视剧后电影状态保留，互不清空
            assertThat(viewModel.uiStateFor(PgcType.Movie).modules.map { it.title }).contains("电影热播榜")
            assertThat(viewModel.uiStateFor(PgcType.Tv).modules.map { it.title }).contains("电视剧热播榜")
            coVerify(exactly = 1) { pgcRepository.getPgcWebPage(PgcType.Movie) }
        }

    @Test
    fun `refresh reloads page data`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { pgcRepository.getPgcWebPage(any()) } answers {
                callCount++
                fakeWebPage(moduleTitle = "热播榜第$callCount 次加载")
            }
            coEvery { seasonRepository.getTimeline(any(), any()) } returns fakeTimeline()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()
            viewModel.refresh(PgcType.Movie)
            advanceUntilIdle()

            assertThat(callCount).isEqualTo(2)
            assertThat(viewModel.uiStateFor(PgcType.Movie).modules.map { it.title })
                .contains("热播榜第2 次加载")
            assertThat(viewModel.uiStateFor(PgcType.Movie).loaded).isTrue()
        }

    @Test
    fun `web page failure sets error`() =
        runTest(testDispatcher) {
            coEvery { pgcRepository.getPgcWebPage(any()) } throws IOException("page error")
            coEvery { seasonRepository.getTimeline(any(), any()) } returns fakeTimeline()

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.Movie)
            advanceUntilIdle()

            val state = viewModel.uiStateFor(PgcType.Movie)
            assertThat(state.error).isTrue()
            assertThat(state.modules).isEmpty()
            assertThat(state.carouselItems).isEmpty()
        }

    @Test
    fun `timeline failure only sets timelineError`() =
        runTest(testDispatcher) {
            stubAll()
            coEvery { seasonRepository.getTimeline(any(), any()) } throws IOException("timeline error")

            viewModel = PgcViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded(PgcType.GuoChuang)
            advanceUntilIdle()

            val state = viewModel.uiStateFor(PgcType.GuoChuang)
            assertThat(state.timelineError).isTrue()
            assertThat(state.timeline).isEmpty()
            assertThat(state.error).isFalse()
            assertThat(state.modules).isNotEmpty()
        }
}
