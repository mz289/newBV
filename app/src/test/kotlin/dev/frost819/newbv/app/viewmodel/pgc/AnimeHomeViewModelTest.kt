package dev.frost819.newbv.app.viewmodel.pgc

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.pgc.PgcPageTab
import dev.frost819.newbv.biliapi.entity.pgc.PgcRankData
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
import java.time.LocalDate
import java.util.Date

/**
 * [AnimeHomeViewModel] 的单元测试。
 *
 * 验证番剧页模块化接口（我的追番/推荐/猜你喜欢）、热播榜与时间表的聚合加载、
 * 懒加载去重、空模块剔除、猜你喜欢游标翻页与板块间失败隔离。
 * 使用 MockK mock 仓库层。
 *
 * 时间表加载读取 [Prefs.apiType]：用 resetForTesting 重置内存缓存即可
 * 读到默认值，无需初始化 DataStore（避免多测试类共享 JVM 时的实例冲突）。
 */
class AnimeHomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pgcRepository: PgcRepository
    private lateinit var seasonRepository: SeasonRepository
    private lateinit var viewModel: AnimeHomeViewModel

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

    private fun fakeCard(seasonId: Int) =
        PgcPageTab.Card(
            seasonId = seasonId,
            episodeId = seasonId * 10L,
            title = "模块番剧 $seasonId",
            subTitle = "推荐理由",
            desc = "339万评论热议中",
            cover = "https://example.com/card$seasonId.jpg",
            bottomBadge = "更新至第100话",
            badge = "大会员",
        )

    private fun fakeRankItem(rank: Int) =
        PgcRankData.Item(
            rank = rank,
            seasonId = rank * 100,
            title = "热播 $rank",
            cover = "https://example.com/rank$rank.jpg",
            rating = "9.$rank",
            newEpIndexShow = "更新至第100话",
            badge = "",
        )

    private fun fakeTimeline() =
        Timeline(
            dateString = "2026-04-23",
            date = Date(),
            dayOfWeek = 4,
            isToday = true,
            episodes =
                listOf(
                    TimelineEp(
                        cover = "https://example.com/tl.jpg",
                        title = "时间表番剧",
                        seasonId = 900,
                        publishIndex = "第7话",
                        publishTime = "09:00",
                        publishDate = Date(),
                    ),
                ),
        )

    /** 首页模块：我的追番（登录场景）+ 番剧推荐 + 猜你喜欢，游标 16 可翻页。 */
    private fun stubCommon(
        pageTab: PgcPageTab =
            PgcPageTab(
                modules =
                    listOf(
                        PgcPageTab.Module(1741, "follow", "我的追番", (1..6).map { fakeCard(it) }),
                        PgcPageTab.Module(1742, "v_card", "番剧推荐", (7..16).map { fakeCard(it) }),
                        PgcPageTab.Module(1744, "double_feed", "猜你喜欢", (100..111).map { fakeCard(it) }),
                    ),
                nextCursor = "16",
                hasNext = true,
            ),
        rankItems: List<PgcRankData.Item> = (1..15).map { fakeRankItem(it) },
        timeline: List<Timeline> = listOf(fakeTimeline()),
    ) {
        coEvery { pgcRepository.getPgcPageTab(any(), any()) } returns pageTab
        coEvery { pgcRepository.getPgcRankList(any()) } returns PgcRankData(items = rankItems)
        coEvery { seasonRepository.getTimeline(any(), any()) } returns timeline
    }

    @Test
    fun `loadIfNeeded loads modules rank and timeline`() =
        runTest(testDispatcher) {
            stubCommon()

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.loaded).isTrue()
            assertThat(state.error).isFalse()
            assertThat(state.loading).isFalse()
            // 模块按接口返回顺序保留
            assertThat(state.pageModules.map { it.style }).containsExactly("follow", "v_card", "double_feed").inOrder()
            // 热播榜：截取前 10 名
            assertThat(state.rankItems).hasSize(10)
            assertThat(state.rankItems.first().rank).isEqualTo(1)
            assertThat(state.timeline).hasSize(1)
        }

    @Test
    fun `empty modules are skipped`() =
        runTest(testDispatcher) {
            // 未登录场景：我的追番模块为空，应被剔除
            val pageTab =
                PgcPageTab(
                    modules =
                        listOf(
                            PgcPageTab.Module(1741, "follow", "我的追番", emptyList()),
                            PgcPageTab.Module(1742, "v_card", "番剧推荐", (7..16).map { fakeCard(it) }),
                        ),
                    nextCursor = null,
                    hasNext = false,
                )
            stubCommon(pageTab = pageTab)

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isFalse()
            assertThat(state.pageModules.map { it.style }).containsExactly("v_card")
        }

    @Test
    fun `loadMoreGuess appends next page into double_feed module`() =
        runTest(testDispatcher) {
            stubCommon()
            // 第二页：与首页重叠 2 条（100/101），新增 10 条
            val nextPage =
                PgcPageTab(
                    modules =
                        listOf(
                            PgcPageTab.Module(1744, "double_feed", "猜你喜欢", (110..121).map { fakeCard(it) }),
                        ),
                    nextCursor = "32",
                    hasNext = true,
                )
            coEvery { pgcRepository.getPgcPageTab("16", any()) } returns nextPage

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            viewModel.loadMoreGuess()
            advanceUntilIdle()

            val guessModule =
                viewModel.uiState.value.pageModules
                    .first { it.style == "double_feed" }
            // 首页 12 条 + 第二页去重后 10 条
            assertThat(guessModule.items).hasSize(22)
        }

    @Test
    fun `loadMoreGuess is no-op without more pages`() =
        runTest(testDispatcher) {
            val pageTab =
                PgcPageTab(
                    modules = listOf(PgcPageTab.Module(1744, "double_feed", "猜你喜欢", (1..12).map { fakeCard(it) })),
                    nextCursor = null,
                    hasNext = false,
                )
            stubCommon(pageTab = pageTab)

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            viewModel.loadMoreGuess()
            advanceUntilIdle()

            coVerify(exactly = 1) { pgcRepository.getPgcPageTab(any(), any()) }
        }

    @Test
    fun `loadIfNeeded is no-op when already loaded`() =
        runTest(testDispatcher) {
            stubCommon()

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            coVerify(exactly = 1) { pgcRepository.getPgcPageTab(any(), any()) }
        }

    @Test
    fun `pageTab failure sets error but timeline still loads`() =
        runTest(testDispatcher) {
            coEvery { pgcRepository.getPgcPageTab(any(), any()) } throws IOException("page error")
            coEvery { pgcRepository.getPgcRankList(any()) } returns
                PgcRankData(items = (1..12).map { fakeRankItem(it) })
            coEvery { seasonRepository.getTimeline(any(), any()) } returns listOf(fakeTimeline())

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isTrue()
            assertThat(state.pageModules).isEmpty()
            assertThat(state.rankItems).isNotEmpty()
            assertThat(state.timeline).hasSize(1)
        }

    @Test
    fun `rank failure sets error but modules still load`() =
        runTest(testDispatcher) {
            stubCommon()
            coEvery { pgcRepository.getPgcRankList(any()) } throws IOException("rank error")

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isTrue()
            assertThat(state.pageModules).isNotEmpty()
            assertThat(state.rankItems).isEmpty()
        }

    @Test
    fun `timeline uses anime filter`() =
        runTest(testDispatcher) {
            stubCommon()

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            coVerify { seasonRepository.getTimeline(TimelineFilter.Anime, any()) }
        }

    @Test
    fun `timeline preserves all days returned by api`() =
        runTest(testDispatcher) {
            // API 返回今天（周四 10/1）前后各 7 天共 15 天，应原样保留不做周裁剪
            val start = LocalDate.of(2026, 9, 22) // 周二
            val days =
                (0 until 15).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    Timeline(
                        dateString = date.toString(),
                        date = Date(),
                        dayOfWeek = date.dayOfWeek.value,
                        isToday = offset == 9, // 10/1 为周四
                        episodes = emptyList(),
                    )
                }
            stubCommon(timeline = days)

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()

            val timeline = viewModel.uiState.value.timeline
            assertThat(timeline).hasSize(15)
            assertThat(timeline.first().dateString).isEqualTo("2026-09-22")
            assertThat(timeline[9].isToday).isTrue()
            assertThat(timeline.last().dateString).isEqualTo("2026-10-06")
        }

    @Test
    fun `refreshAll reloads with refresh flag`() =
        runTest(testDispatcher) {
            stubCommon()

            viewModel = AnimeHomeViewModel(pgcRepository, seasonRepository)
            viewModel.loadIfNeeded()
            advanceUntilIdle()
            viewModel.refreshAll()
            advanceUntilIdle()

            coVerify(exactly = 1) { pgcRepository.getPgcPageTab(any(), 1) }
            coVerify(exactly = 2) { pgcRepository.getPgcPageTab(any(), any()) }
        }
}
