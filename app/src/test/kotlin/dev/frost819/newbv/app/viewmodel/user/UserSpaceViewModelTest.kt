package dev.frost819.newbv.app.viewmodel.user

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.user.SpaceCollectionData
import dev.frost819.newbv.biliapi.entity.user.SpaceCollectionPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoData
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder
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
import kotlinx.coroutines.delay
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
 * [UserSpaceViewModel] 的单元测试。
 *
 * 验证用户投稿视频列表分页加载、错误与超时处理。
 * 用户名和头像由路由参数传入，不调用 getUserInfo API。
 * 使用 MockK mock [UserRepository]，mockkObject mock [Prefs]。
 */
class UserSpaceViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: UserSpaceViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()

        mockkObject(Prefs)
        every { Prefs.apiType } returns ApiType.Web

        // 关注状态查询是 init 的独立请求，默认返回未关注，避免 strict mock 抛异常
        coEvery { userRepository.checkIsFollowing(any()) } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Prefs)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UserSpaceViewModel = UserSpaceViewModel(userRepository = userRepository)

    private fun fakeVideo(aid: Long) =
        SpaceVideo(
            aid = aid,
            bvid = "BV$aid",
            title = "视频$aid",
            cover = "https://example.com/cover$aid.jpg",
            author = "测试UP",
            duration = 600,
            play = 10000,
            danmaku = 500,
            pubTime = "2024-01-01",
        )

    private fun fakeVideoData(
        videos: List<SpaceVideo> = listOf(fakeVideo(1), fakeVideo(2)),
        hasNext: Boolean = true,
    ) = SpaceVideoData(
        videos = videos,
        page = SpaceVideoPage(hasNext = hasNext),
    )

    private fun fakeCollection(
        id: Long,
        isSeason: Boolean = true,
    ) = UpCollection(
        id = id,
        title = "合集$id",
        cover = "https://example.com/c$id.jpg",
        total = 3,
        isSeason = isSeason,
    )

    private fun fakeCollectionData(
        collections: List<UpCollection>,
        hasNext: Boolean = true,
    ) = SpaceCollectionData(
        collections = collections,
        page = SpaceCollectionPage(hasNext = hasNext, nextPageNumber = 2, pageSize = 20),
    )

    // ------------------------------------------------------------------
    // init
    // ------------------------------------------------------------------

    @Test
    fun `init loads videos successfully`() =
        runTest(testDispatcher) {
            val videoData = fakeVideoData(videos = listOf(fakeVideo(1), fakeVideo(2)), hasNext = true)

            coEvery {
                userRepository.getSpaceVideos(
                    mid = 1L,
                    order = SpaceVideoOrder.PubDate,
                    page = any(),
                    preferApiType = BiliApiType.Web,
                )
            } returns videoData

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP", "https://example.com/face.jpg")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.mid).isEqualTo(1L)
            assertThat(state.name).isEqualTo("测试UP")
            assertThat(state.face).isEqualTo("https://example.com/face.jpg")
            assertThat(state.videos).hasSize(2)
            assertThat(state.loading).isFalse()
            assertThat(state.error).isFalse()
            assertThat(state.hasMore).isTrue()
        }

    @Test
    fun `init is idempotent when same mid and videos already loaded`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getSpaceVideos(any(), any(), any(), any()) }
        }

    @Test
    fun `init reloads when mid changes`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()

            viewModel = createViewModel()
            viewModel.init(1L, "UP1")
            advanceUntilIdle()

            viewModel.init(2L, "UP2")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                userRepository.getSpaceVideos(mid = 1L, order = any(), page = any(), preferApiType = any())
            }
            coVerify(exactly = 1) {
                userRepository.getSpaceVideos(mid = 2L, order = any(), page = any(), preferApiType = any())
            }
        }

    // ------------------------------------------------------------------
    // loadVideos pagination
    // ------------------------------------------------------------------

    @Test
    fun `loadVideos loads next page when hasMore is true`() =
        runTest(testDispatcher) {
            val firstPage =
                fakeVideoData(
                    videos = listOf(fakeVideo(1)),
                    hasNext = true,
                )
            val secondPage =
                fakeVideoData(
                    videos = listOf(fakeVideo(2)),
                    hasNext = false,
                )

            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returnsMany
                listOf(
                    firstPage,
                    secondPage,
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos).hasSize(1)
            assertThat(viewModel.uiState.value.hasMore).isTrue()

            viewModel.loadVideos(1L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos).hasSize(2)
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `loadVideos does not load when hasMore is false`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns
                fakeVideoData(
                    videos = listOf(fakeVideo(1)),
                    hasNext = false,
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.loadVideos(1L)
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getSpaceVideos(any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.videos).hasSize(1)
        }

    @Test
    fun `loadVideos does not load when already loading`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns
                fakeVideoData(
                    videos = listOf(fakeVideo(1)),
                    hasNext = true,
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            // loading is true (set synchronously), don't advance yet
            assertThat(viewModel.uiState.value.loading).isTrue()

            viewModel.loadVideos(1L)
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getSpaceVideos(any(), any(), any(), any()) }
        }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Test
    fun `refresh clears videos and reloads`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns
                fakeVideoData(
                    videos = listOf(fakeVideo(1), fakeVideo(2)),
                    hasNext = true,
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos).hasSize(2)

            viewModel.refresh(1L)
            advanceUntilIdle()

            coVerify(exactly = 2) { userRepository.getSpaceVideos(any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.videos).hasSize(2)
            assertThat(viewModel.uiState.value.error).isFalse()
        }

    // ------------------------------------------------------------------
    // selectTab
    // ------------------------------------------------------------------

    @Test
    fun `selectTab to MostPlayed clears videos and reloads with click order`() =
        runTest(testDispatcher) {
            coEvery {
                userRepository.getSpaceVideos(mid = 1L, order = SpaceVideoOrder.PubDate, page = any(), preferApiType = BiliApiType.Web)
            } returns fakeVideoData(videos = listOf(fakeVideo(1)))
            coEvery {
                userRepository.getSpaceVideos(mid = 1L, order = SpaceVideoOrder.Click, page = any(), preferApiType = BiliApiType.Web)
            } returns fakeVideoData(videos = listOf(fakeVideo(2)))

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.tab).isEqualTo(UserSpaceTab.Latest)
            assertThat(viewModel.uiState.value.videos.map { it.aid }).containsExactly(1L)

            viewModel.selectTab(1L, UserSpaceTab.MostPlayed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tab).isEqualTo(UserSpaceTab.MostPlayed)
            // 旧排序的视频已清空，只剩新排序内容
            assertThat(state.videos.map { it.aid }).containsExactly(2L)
            coVerify(exactly = 1) {
                userRepository.getSpaceVideos(mid = 1L, order = SpaceVideoOrder.Click, page = any(), preferApiType = any())
            }
        }

    @Test
    fun `selectTab same tab is no-op`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.selectTab(1L, UserSpaceTab.Latest)
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getSpaceVideos(any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.videos).hasSize(2)
        }

    @Test
    fun `selectTab filters duplicate aids on overlapping pages`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returnsMany
                listOf(
                    fakeVideoData(videos = listOf(fakeVideo(1), fakeVideo(2)), hasNext = true),
                    fakeVideoData(videos = listOf(fakeVideo(2), fakeVideo(3)), hasNext = false),
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.loadVideos(1L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videos.map { it.aid }).containsExactly(1L, 2L, 3L).inOrder()
        }

    // ------------------------------------------------------------------
    // collections（合集/系列）
    // ------------------------------------------------------------------

    @Test
    fun `selectTab to Seasons loads collections`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery {
                userRepository.getSpaceCollections(mid = 1L, page = any(), preferApiType = BiliApiType.Web)
            } returns fakeCollectionData(
                collections = listOf(fakeCollection(10), fakeCollection(11, isSeason = false)),
                hasNext = false,
            )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.selectTab(1L, UserSpaceTab.Seasons)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tab).isEqualTo(UserSpaceTab.Seasons)
            assertThat(state.videos).isEmpty()
            assertThat(state.collections).hasSize(2)
            assertThat(state.collections[0].isSeason).isTrue()
            assertThat(state.collections[1].isSeason).isFalse()
            assertThat(state.hasMore).isFalse()
            assertThat(state.error).isFalse()
        }

    @Test
    fun `loadMore appends collections pages on Seasons tab`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.getSpaceCollections(any(), any(), any()) } returnsMany
                listOf(
                    fakeCollectionData(collections = listOf(fakeCollection(10)), hasNext = true),
                    fakeCollectionData(collections = listOf(fakeCollection(12)), hasNext = false),
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.selectTab(1L, UserSpaceTab.Seasons)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.collections).hasSize(1)
            assertThat(viewModel.uiState.value.hasMore).isTrue()

            viewModel.loadMore(1L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.collections.map { it.id }).containsExactly(10L, 12L).inOrder()
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `selectTab back to Latest reloads videos`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData(videos = listOf(fakeVideo(1)))
            coEvery { userRepository.getSpaceCollections(any(), any(), any()) } returns
                fakeCollectionData(collections = listOf(fakeCollection(10)), hasNext = false)

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.selectTab(1L, UserSpaceTab.Seasons)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.collections).isNotEmpty()

            viewModel.selectTab(1L, UserSpaceTab.Latest)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tab).isEqualTo(UserSpaceTab.Latest)
            assertThat(state.collections).isEmpty()
            assertThat(state.videos).isNotEmpty()
        }

    @Test
    fun `selectTab to Series loads series collections`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.getSpaceCollections(any(), any(), any()) } returns
                fakeCollectionData(
                    collections = listOf(fakeCollection(370368, isSeason = false)),
                    hasNext = false,
                )

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.selectTab(1L, UserSpaceTab.Series)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.tab).isEqualTo(UserSpaceTab.Series)
            assertThat(state.collections).hasSize(1)
            assertThat(state.collections[0].isSeason).isFalse()
        }

    @Test
    fun `selectTab resets error state before reload`() =
        runTest(testDispatcher) {
            coEvery {
                userRepository.getSpaceVideos(mid = 1L, order = SpaceVideoOrder.PubDate, page = any(), preferApiType = BiliApiType.Web)
            } throws IOException("network error")
            coEvery { userRepository.getSpaceCollections(any(), any(), any()) } returns
                fakeCollectionData(collections = listOf(fakeCollection(10)), hasNext = false)

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.error).isTrue()

            viewModel.selectTab(1L, UserSpaceTab.Seasons)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isFalse()
            assertThat(viewModel.uiState.value.collections).isNotEmpty()
        }

    // ------------------------------------------------------------------
    // error states
    // ------------------------------------------------------------------

    @Test
    fun `loadVideos error sets error`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } throws IOException("network error")

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isTrue()
            assertThat(state.loading).isFalse()
            assertThat(state.videos).isEmpty()
        }

    // ------------------------------------------------------------------
    // timeout handling
    // ------------------------------------------------------------------

    @Test
    fun `loadVideos timeout sets error`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } coAnswers {
                delay(11_000)
                fakeVideoData()
            }

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.error).isTrue()
            assertThat(state.loading).isFalse()
        }

    // ------------------------------------------------------------------
    // App apiType mapping
    // ------------------------------------------------------------------

    @Test
    fun `uses App apiType when Prefs apiType is App`() =
        runTest(testDispatcher) {
            every { Prefs.apiType } returns ApiType.App

            coEvery {
                userRepository.getSpaceVideos(
                    mid = any(),
                    order = any(),
                    page = any(),
                    preferApiType = BiliApiType.App,
                )
            } returns fakeVideoData()

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            coVerify {
                userRepository.getSpaceVideos(
                    mid = any(),
                    order = any(),
                    page = any(),
                    preferApiType = BiliApiType.App,
                )
            }
        }

    // ------------------------------------------------------------------
    // follow status
    // ------------------------------------------------------------------

    @Test
    fun `init loads following status`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.checkIsFollowing(1L) } returns true

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFollowing).isTrue()
        }

    @Test
    fun `init keeps not following when status check fails`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.checkIsFollowing(any()) } throws IOException("network error")

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isFollowing).isFalse()
            // 关注状态查询失败不应影响投稿列表加载
            assertThat(state.videos).hasSize(2)
            assertThat(state.error).isFalse()
        }

    @Test
    fun `toggleFollow follows UP when not following`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.followUser(mid = 1L, preferApiType = BiliApiType.Web) } returns true

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify { userRepository.followUser(mid = 1L, preferApiType = BiliApiType.Web) }
            assertThat(viewModel.uiState.value.isFollowing).isTrue()
            assertThat(viewModel.uiState.value.followLoading).isFalse()
        }

    @Test
    fun `toggleFollow unfollows UP when already following`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.checkIsFollowing(1L) } returns true
            coEvery { userRepository.unfollowUser(mid = 1L, preferApiType = BiliApiType.Web) } returns true

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isFollowing).isTrue()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify { userRepository.unfollowUser(mid = 1L, preferApiType = BiliApiType.Web) }
            assertThat(viewModel.uiState.value.isFollowing).isFalse()
        }

    @Test
    fun `toggleFollow failure emits toast and keeps state`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.followUser(any(), any()) } throws IOException("network error")

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleFollow()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(UserSpaceUiEffect.ShowToast::class.java)
                assertThat((effect as UserSpaceUiEffect.ShowToast).message).contains("关注失败")
            }

            assertThat(viewModel.uiState.value.isFollowing).isFalse()
            assertThat(viewModel.uiState.value.followLoading).isFalse()
        }

    @Test
    fun `toggleFollow non-zero response emits toast`() =
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceVideos(any(), any(), any(), any()) } returns fakeVideoData()
            coEvery { userRepository.followUser(any(), any()) } returns false

            viewModel = createViewModel()
            viewModel.init(1L, "测试UP")
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleFollow()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(UserSpaceUiEffect.ShowToast::class.java)
                assertThat((effect as UserSpaceUiEffect.ShowToast).message).isEqualTo("关注失败")
            }

            assertThat(viewModel.uiState.value.isFollowing).isFalse()
        }

    @Test
    fun `toggleFollow is no-op when mid is zero`() =
        runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify(exactly = 0) { userRepository.followUser(any(), any()) }
            coVerify(exactly = 0) { userRepository.unfollowUser(any(), any()) }
        }
}
