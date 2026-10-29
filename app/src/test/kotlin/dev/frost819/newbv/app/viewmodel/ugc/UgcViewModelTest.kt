package dev.frost819.newbv.app.viewmodel.ugc

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ugc.UgcItem
import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedData
import dev.frost819.newbv.biliapi.entity.ugc.region.UgcFeedPage
import dev.frost819.newbv.biliapi.repositories.SearchFilterDuration
import dev.frost819.newbv.biliapi.repositories.SearchFilterOrderType
import dev.frost819.newbv.biliapi.repositories.UgcRepository
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

/**
 * [UgcViewModel] 的单元测试。
 *
 * 验证分区数据加载、切换分区、刷新、筛选数据源切换与超时/错误处理。
 * 使用 MockK mock [UgcRepository]，用 answers + callCount 区分多次调用。
 */
class UgcViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var ugcRepository: UgcRepository
    private lateinit var viewModel: UgcViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ugcRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeUgcItem(aid: Long) =
        UgcItem(
            aid = aid,
            bvid = "BV$aid",
            title = "视频 $aid",
            cover = "https://example.com/cover$aid.jpg",
            author = "UP$aid",
            authorMid = aid,
            play = 1000,
            danmaku = 100,
            duration = 600,
        )

    private fun fakeFeedData(
        items: List<UgcItem>,
        hasNext: Boolean,
        nextPage: Int,
    ) = UgcFeedData(
        hasNext = hasNext,
        nextPage = UgcFeedPage(nextPage),
        items = items,
    )

    @Test
    fun `init loads first page`() =
        runTest(testDispatcher) {
            val items = listOf(fakeUgcItem(1), fakeUgcItem(2))
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } returns
                fakeFeedData(items, hasNext = true, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(2)
            assertThat(viewModel.uiState.value.loading).isFalse()
            assertThat(viewModel.uiState.value.hasMore).isTrue()
            assertThat(viewModel.uiState.value.error).isFalse()
        }

    @Test
    fun `loadMore appends items and stops when hasNext is false`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    fakeFeedData(listOf(fakeUgcItem(1), fakeUgcItem(2)), hasNext = true, nextPage = 2)
                } else {
                    fakeFeedData(listOf(fakeUgcItem(3), fakeUgcItem(4)), hasNext = false, nextPage = 3)
                }
            }
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()
            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(4)
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `switchType clears and loads new region`() =
        runTest(testDispatcher) {
            coEvery {
                ugcRepository.getRegionFeedRcmd(UgcTypeV2.Douga, any())
            } returns fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
            coEvery {
                ugcRepository.getRegionFeedRcmd(UgcTypeV2.Music, any())
            } returns fakeFeedData(listOf(fakeUgcItem(100)), hasNext = false, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()
            viewModel.switchType(UgcTabItem.Music)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(100)
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `error sets error flag and preserves existing items`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
                } else {
                    throw IOException("network error")
                }
            }
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()
            assertThat(viewModel.uiState.value.items).hasSize(1)

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(viewModel.uiState.value.error).isTrue()
            assertThat(viewModel.uiState.value.loading).isFalse()
        }

    @Test
    fun `refresh clears items and reloads`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
                } else {
                    fakeFeedData(listOf(fakeUgcItem(99)), hasNext = false, nextPage = 2)
                }
            }
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()
            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(1)

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(99)
        }

    @Test
    fun `loadMore is no-op when hasMore is false`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                fakeFeedData(listOf(fakeUgcItem(callCount.toLong())), hasNext = false, nextPage = 2)
            }
            viewModel = UgcViewModel(ugcRepository)

            advanceUntilIdle()
            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(viewModel.uiState.value.hasMore).isFalse()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(callCount).isEqualTo(1)
            assertThat(viewModel.uiState.value.items).hasSize(1)
        }

    @Test
    fun `switchType to same type with existing items is no-op`() =
        runTest(testDispatcher) {
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } returns
                fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.switchType(UgcTabItem.Douga)
            advanceUntilIdle()

            coVerify(exactly = 1) { ugcRepository.getRegionFeedRcmd(any(), any()) }
        }

    @Test
    fun `switchType to different type loads new data`() =
        runTest(testDispatcher) {
            coEvery { ugcRepository.getRegionFeedRcmd(UgcTypeV2.Douga, any()) } returns
                fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
            coEvery { ugcRepository.getRegionFeedRcmd(UgcTypeV2.Game, any()) } returns
                fakeFeedData(listOf(fakeUgcItem(100), fakeUgcItem(101)), hasNext = false, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.switchType(UgcTabItem.Game)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(2)
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(100)
        }

    @Test
    fun `init error on first load sets error with empty items`() =
        runTest(testDispatcher) {
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } throws IOException("init error")
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).isEmpty()
            assertThat(viewModel.uiState.value.error).isTrue()
            assertThat(viewModel.uiState.value.loading).isFalse()
        }

    @Test
    fun `refresh clears error on reload`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                if (callCount == 1) throw IOException("error")
                fakeFeedData(listOf(fakeUgcItem(99)), hasNext = false, nextPage = 2)
            }
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.error).isTrue()

            viewModel.refresh()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isFalse()
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(99)
        }

    @Test
    fun `loadMore appends to existing items`() =
        runTest(testDispatcher) {
            var callCount = 0
            coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    fakeFeedData(listOf(fakeUgcItem(1), fakeUgcItem(2)), hasNext = true, nextPage = 2)
                } else {
                    fakeFeedData(listOf(fakeUgcItem(3)), hasNext = false, nextPage = 3)
                }
            }
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items).hasSize(3)
            assertThat(
                viewModel.uiState.value.items[2]
                    .aid,
            ).isEqualTo(3)
        }

    // ------------------------------------------------------------------
    // 筛选（排序/时长 -> 分区搜索数据源）
    // ------------------------------------------------------------------

    private fun stubRegionFeed() {
        coEvery { ugcRepository.getRegionFeedRcmd(any(), any()) } returns
            fakeFeedData(listOf(fakeUgcItem(1)), hasNext = true, nextPage = 2)
    }

    @Test
    fun `updateFilter switches to search data source with tab params`() =
        runTest(testDispatcher) {
            stubRegionFeed()
            coEvery { ugcRepository.getRegionFeedBySearch(any(), any(), any(), any(), any()) } returns
                fakeFeedData(listOf(fakeUgcItem(10)), hasNext = true, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.switchType(UgcTabItem.Music)
            advanceUntilIdle()
            viewModel.updateFilter(SearchFilterOrderType.MostClicks, SearchFilterDuration.Between10And30Minutes)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFiltered).isTrue()
            assertThat(viewModel.uiState.value.items).hasSize(1)
            assertThat(
                viewModel.uiState.value.items[0]
                    .aid,
            ).isEqualTo(10)
            coVerify {
                ugcRepository.getRegionFeedBySearch(
                    searchTid = eq(UgcTabItem.Music.searchTid),
                    keyword = eq(UgcTabItem.Music.displayName),
                    order = eq(SearchFilterOrderType.MostClicks),
                    duration = eq(SearchFilterDuration.Between10And30Minutes),
                    page = eq(UgcFeedPage(1)),
                )
            }
        }

    @Test
    fun `updateFilter back to default returns to region feed`() =
        runTest(testDispatcher) {
            stubRegionFeed()
            coEvery { ugcRepository.getRegionFeedBySearch(any(), any(), any(), any(), any()) } returns
                fakeFeedData(listOf(fakeUgcItem(10)), hasNext = true, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.updateFilter(SearchFilterOrderType.MostDanmaku, SearchFilterDuration.All)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isFiltered).isTrue()

            viewModel.updateFilter(SearchFilterOrderType.ComprehensiveSort, SearchFilterDuration.All)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFiltered).isFalse()
            coVerify(exactly = 2) { ugcRepository.getRegionFeedRcmd(any(), any()) }
        }

    @Test
    fun `updateFilter with unchanged values does not reload`() =
        runTest(testDispatcher) {
            stubRegionFeed()
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.updateFilter(SearchFilterOrderType.ComprehensiveSort, SearchFilterDuration.All)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showFilter).isFalse()
            coVerify(exactly = 1) { ugcRepository.getRegionFeedRcmd(any(), any()) }
        }

    @Test
    fun `search load dedupes items by aid and stops when page fully duplicated`() =
        runTest(testDispatcher) {
            stubRegionFeed()
            coEvery { ugcRepository.getRegionFeedBySearch(any(), any(), any(), any(), any()) } returns
                fakeFeedData(
                    listOf(fakeUgcItem(1), fakeUgcItem(1), fakeUgcItem(2)),
                    hasNext = true,
                    nextPage = 2,
                )
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.updateFilter(SearchFilterOrderType.LatestPublish, SearchFilterDuration.All)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.items.map { it.aid }).containsExactly(1L, 2L).inOrder()
            assertThat(viewModel.uiState.value.hasMore).isTrue()

            // 第二页与第一页完全重复 -> hasMore 变 false
            coEvery { ugcRepository.getRegionFeedBySearch(any(), any(), any(), any(), any()) } returns
                fakeFeedData(listOf(fakeUgcItem(1), fakeUgcItem(2)), hasNext = true, nextPage = 3)
            viewModel.loadMore()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.items.map { it.aid }).containsExactly(1L, 2L).inOrder()
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `switchType keeps filter and loads via search`() =
        runTest(testDispatcher) {
            stubRegionFeed()
            coEvery { ugcRepository.getRegionFeedBySearch(any(), any(), any(), any(), any()) } returns
                fakeFeedData(listOf(fakeUgcItem(100)), hasNext = true, nextPage = 2)
            viewModel = UgcViewModel(ugcRepository)
            advanceUntilIdle()

            viewModel.updateFilter(SearchFilterOrderType.MostFavorites, SearchFilterDuration.All)
            advanceUntilIdle()
            viewModel.switchType(UgcTabItem.Game)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFiltered).isTrue()
            coVerify {
                ugcRepository.getRegionFeedBySearch(
                    searchTid = eq(UgcTabItem.Game.searchTid),
                    keyword = eq(UgcTabItem.Game.displayName),
                    order = eq(SearchFilterOrderType.MostFavorites),
                    duration = eq(SearchFilterDuration.All),
                    page = eq(UgcFeedPage(1)),
                )
            }
        }
}
