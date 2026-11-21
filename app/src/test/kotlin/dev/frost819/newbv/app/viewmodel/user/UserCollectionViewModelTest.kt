package dev.frost819.newbv.app.viewmodel.user

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoData
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoPage
import dev.frost819.newbv.biliapi.entity.user.UpCollection
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.data.datastore.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import dev.frost819.newbv.biliapi.entity.ApiType as BiliApiType

/**
 * [UserCollectionViewModel] 的单元测试。
 *
 * 验证合集（season）/系列（series）视频列表的分页加载、去重与错误处理。
 */
class UserCollectionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: UserCollectionViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()

        mockkObject(Prefs)
        every { Prefs.apiType } returns ApiType.Web
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Prefs)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UserCollectionViewModel =
        UserCollectionViewModel(userRepository = userRepository)

    private fun fakeVideo(aid: Long) =
        SpaceVideo(
            aid = aid,
            bvid = "BV$aid",
            title = "视频$aid",
            cover = "https://example.com/cover$aid.jpg",
            author = "",
            duration = 600,
            play = 10000,
            danmaku = 500,
            pubTime = "2024-01-01",
        )

    private fun fakeVideoData(
        videos: List<SpaceVideo>,
        hasNext: Boolean = true,
    ) = SpaceVideoData(
        videos = videos,
        page = SpaceVideoPage(hasNext = hasNext),
    )

    private fun initSeason(viewModel: UserCollectionViewModel) {
        viewModel.init(mid = 1L, collectionId = 2046621L, isSeason = true, title = "样片日记", cover = "")
    }

    // ------------------------------------------------------------------
    // init
    // ------------------------------------------------------------------

    @Test
    fun `init loads season videos`() =
        runTest(testDispatcher) {
            coEvery {
                userRepository.getCollectionVideos(
                    mid = 1L,
                    collection = any(),
                    page = any(),
                    preferApiType = BiliApiType.Web,
                )
            } returns fakeVideoData(listOf(fakeVideo(1), fakeVideo(2)))

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.collection?.id).isEqualTo(2046621L)
            assertThat(state.collection?.isSeason).isTrue()
            assertThat(state.videos).hasSize(2)
            assertThat(state.loading).isFalse()
            assertThat(state.hasMore).isTrue()
        }

    @Test
    fun `init routes series by isSeason flag`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } returns
                fakeVideoData(listOf(fakeVideo(1)))

            viewModel = createViewModel()
            viewModel.init(mid = 1L, collectionId = 370368L, isSeason = false, title = "系列", cover = "")
            advanceUntilIdle()

            coVerify {
                userRepository.getCollectionVideos(
                    mid = 1L,
                    collection = match { it.id == 370368L && !it.isSeason },
                    page = any(),
                    preferApiType = any(),
                )
            }
        }

    @Test
    fun `init is idempotent for same collection`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } returns
                fakeVideoData(listOf(fakeVideo(1)))

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()

            initSeason(viewModel)
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getCollectionVideos(any(), any(), any(), any()) }
        }

    // ------------------------------------------------------------------
    // pagination
    // ------------------------------------------------------------------

    @Test
    fun `loadMore appends next page and stops when hasMore is false`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } returnsMany
                listOf(
                    fakeVideoData(listOf(fakeVideo(1)), hasNext = true),
                    fakeVideoData(listOf(fakeVideo(2)), hasNext = false),
                )

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.videos).hasSize(1)

            viewModel.loadMore(1L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos.map { it.aid }).containsExactly(1L, 2L).inOrder()
            assertThat(viewModel.uiState.value.hasMore).isFalse()

            viewModel.loadMore(1L)
            advanceUntilIdle()
            coVerify(exactly = 2) { userRepository.getCollectionVideos(any(), any(), any(), any()) }
        }

    @Test
    fun `loadMore filters duplicate aids on overlapping pages`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } returnsMany
                listOf(
                    fakeVideoData(listOf(fakeVideo(1), fakeVideo(2)), hasNext = true),
                    fakeVideoData(listOf(fakeVideo(2), fakeVideo(3)), hasNext = false),
                )

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()

            viewModel.loadMore(1L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos.map { it.aid }).containsExactly(1L, 2L, 3L).inOrder()
        }

    @Test
    fun `refresh clears videos and reloads`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } returns
                fakeVideoData(listOf(fakeVideo(1)))

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()

            viewModel.refresh(1L)
            advanceUntilIdle()

            coVerify(exactly = 2) { userRepository.getCollectionVideos(any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.videos).hasSize(1)
            assertThat(viewModel.uiState.value.error).isFalse()
        }

    // ------------------------------------------------------------------
    // error states
    // ------------------------------------------------------------------

    @Test
    fun `loadVideos error sets error state`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getCollectionVideos(any(), any(), any(), any()) } throws IOException("network error")

            viewModel = createViewModel()
            initSeason(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isTrue()
            assertThat(state.loading).isFalse()
            assertThat(state.videos).isEmpty()
        }
}
