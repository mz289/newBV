package dev.frost819.newbv.app.viewmodel.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.data.VideoInfoRepository
import dev.frost819.newbv.app.data.VideoSharedState
import dev.frost819.newbv.biliapi.entity.FavoriteFolderMetadata
import dev.frost819.newbv.biliapi.entity.user.Author
import dev.frost819.newbv.biliapi.entity.video.Dimension
import dev.frost819.newbv.biliapi.entity.video.UserActions
import dev.frost819.newbv.biliapi.entity.video.VideoDetail
import dev.frost819.newbv.biliapi.entity.video.VideoPage
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.biliapi.entity.video.season.Section
import dev.frost819.newbv.biliapi.entity.video.season.UgcSeason
import dev.frost819.newbv.biliapi.repositories.FavoriteRepository
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.biliapi.repositories.VideoDetailRepository
import dev.frost819.newbv.data.datastore.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
 * [VideoDetailViewModel] 的单元测试。
 *
 * 验证详情数据加载、点赞/投币/收藏操作、一键三连、超时/错误处理。
 * 使用 MockK mock 所有 Repository；交互状态断言通过
 * [VideoInfoRepository.updateVideoActionState] 的调用与共享状态进行。
 */
class VideoDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var videoDetailRepository: VideoDetailRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var userRepository: UserRepository
    private lateinit var videoInfoRepository: VideoInfoRepository
    private lateinit var sharedStateFlow: MutableStateFlow<VideoSharedState?>
    private lateinit var viewModel: VideoDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        videoDetailRepository = mockk()
        favoriteRepository = mockk()
        userRepository = mockk()
        videoInfoRepository = mockk(relaxed = true)
        sharedStateFlow = MutableStateFlow(null)
        every { videoInfoRepository.videoSharedState } returns sharedStateFlow

        mockkObject(Prefs)
        coEvery { Prefs.isLogin } returns false
    }

    @AfterEach
    fun tearDown() {
        io.mockk.unmockkObject(Prefs)
        Dispatchers.resetMain()
    }

    private fun createViewModel(aid: Long = 1L): VideoDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("aid" to aid))
        return VideoDetailViewModel(
            videoDetailRepository = videoDetailRepository,
            favoriteRepository = favoriteRepository,
            userRepository = userRepository,
            videoInfoRepository = videoInfoRepository,
            savedStateHandle = savedStateHandle,
        )
    }

    private fun fakeVideoDetail(aid: Long = 1L) =
        VideoDetail(
            bvid = "BV$aid",
            aid = aid,
            cid = 100L,
            cover = "https://example.com/cover.jpg",
            title = "测试视频 $aid",
            publishDate = Date(1700000000000L),
            description = "这是测试视频描述",
            stat =
                VideoDetail.Stat(
                    view = 10000,
                    danmaku = 500,
                    reply = 200,
                    favorite = 300,
                    coin = 100,
                    share = 50,
                    like = 800,
                    historyRank = 0,
                ),
            author = Author(mid = 999L, name = "测试UP", face = ""),
            pages =
                listOf(
                    VideoPage(cid = 100L, index = 1, title = "P1", duration = 600, dimension = mockk()),
                ),
            ugcSeason = null,
            relatedVideos = emptyList(),
            redirectToEp = false,
            epid = null,
            argueTip = null,
            tags = emptyList(),
            userActions = UserActions(),
            history = VideoDetail.History(0, 0),
        )

    @Test
    fun `init loads video detail successfully`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.detail).isNotNull()
            assertThat(state.detail?.title).isEqualTo("测试视频 1")
            assertThat(state.loading).isFalse()
            assertThat(state.error).isFalse()
        }

    @Test
    fun `init sets error on network failure`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } throws IOException("network error")

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.detail).isNull()
            assertThat(state.error).isTrue()
            assertThat(state.loading).isFalse()
            assertThat(state.errorTip).contains("network error")
        }

    @Test
    fun `toggleLike delegates to shared repository`() =
        runTest(testDispatcher) {
            val detail =
                fakeVideoDetail().copy(
                    userActions = UserActions(like = false, coin = false, favorite = false),
                )
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            viewModel.toggleLike(true)
            advanceUntilIdle()

            coVerify { videoInfoRepository.setVideoLiked(aid = 1L, like = true, bvid = "BV1") }
        }

    @Test
    fun `toggleLike emits toast on failure`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { videoInfoRepository.setVideoLiked(any(), any(), any()) } throws Exception("already liked")

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleLike(true)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("点赞失败")
            }
        }

    @Test
    fun `sendCoin delegates to shared repository`() =
        runTest(testDispatcher) {
            val detail =
                fakeVideoDetail().copy(
                    userActions = UserActions(coin = false),
                )
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            viewModel.sendCoin()
            advanceUntilIdle()

            coVerify { videoInfoRepository.sendVideoCoin(aid = 1L, bvid = "BV1") }
        }

    @Test
    fun `sendCoin emits toast on failure`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { videoInfoRepository.sendVideoCoin(any(), any()) } throws Exception("no coins")

            viewModel = createViewModel(aid = 1L)
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.sendCoin()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("投币失败")
            }
        }

    @Test
    fun `aid property matches route parameter`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns fakeVideoDetail()

            viewModel = createViewModel(aid = 999L)
            advanceUntilIdle()

            assertThat(viewModel.aid).isEqualTo(999L)
            coVerify { videoDetailRepository.getVideoDetail(aid = 999L, any(), any()) }
        }

    @Test
    fun `toggleFollow follows UP on success`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { userRepository.followUser(any(), any()) } returns true

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify { userRepository.followUser(mid = 999L, any()) }
            assertThat(viewModel.uiState.value.isFollowing).isTrue()
        }

    @Test
    fun `toggleFollow unfollows UP on success`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { Prefs.isLogin } returns true
            coEvery { userRepository.checkIsFollowing(any()) } returns true
            coEvery { userRepository.unfollowUser(any(), any()) } returns true

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isFollowing).isTrue()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify { userRepository.unfollowUser(mid = 999L, any()) }
            assertThat(viewModel.uiState.value.isFollowing).isFalse()
        }

    @Test
    fun `toggleFollow emits toast on failure`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { userRepository.followUser(any(), any()) } throws RuntimeException("already followed")

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleFollow()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("关注失败")
            }
        }

    @Test
    fun `toggleFollow is no-op when detail is null`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } throws RuntimeException("error")

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleFollow()
            advanceUntilIdle()

            coVerify(exactly = 0) { userRepository.followUser(any(), any()) }
            coVerify(exactly = 0) { userRepository.unfollowUser(any(), any()) }
        }

    @Test
    fun `updateFavorite syncs shared state and folder ids on success`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { favoriteRepository.updateVideoToFavoriteFolder(any(), any(), any(), any()) } returns Unit

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateFavorite(listOf(1L, 2L))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.videoFavoriteFolderIds).containsExactly(1L, 2L)
            coVerify { videoInfoRepository.updateVideoActionState(aid = 1L, favorited = true) }
        }

    @Test
    fun `updateFavorite does not sync shared state on failure`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { favoriteRepository.updateVideoToFavoriteFolder(any(), any(), any(), any()) } throws
                RuntimeException("fav error")

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.updateFavorite(listOf(1L))
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("收藏失败")
            }
            coVerify(exactly = 0) { videoInfoRepository.updateVideoActionState(aid = any(), liked = any(), coined = any(), favorited = any()) }
        }

    @Test
    fun `toggleFavorite with default folder adds to favorite`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { Prefs.isLogin } returns true
            coEvery { favoriteRepository.getAllFavoriteFolderMetadataList(any(), any(), any(), any()) } returns
                listOf(
                    FavoriteFolderMetadata(
                        id = 10,
                        fid = 10,
                        mid = 1L,
                        title = "默认收藏夹",
                        cover = null,
                        videoInThisFav = false,
                        mediaCount = 5,
                    ),
                )
            coEvery { favoriteRepository.updateVideoToFavoriteFolder(any(), any(), any(), any()) } returns Unit

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleFavorite()
            advanceUntilIdle()

            coVerify { videoInfoRepository.updateVideoActionState(aid = 1L, favorited = true) }
        }

    @Test
    fun `toggleFavorite without default folder emits toast`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { Prefs.isLogin } returns true
            coEvery { favoriteRepository.getAllFavoriteFolderMetadataList(any(), any(), any(), any()) } returns
                listOf(
                    FavoriteFolderMetadata(
                        id = 10,
                        fid = 10,
                        mid = 1L,
                        title = "其他收藏夹",
                        cover = null,
                        videoInThisFav = false,
                        mediaCount = 5,
                    ),
                )

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleFavorite()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("未找到默认收藏夹")
            }
        }

    @Test
    fun `toggleFavorite unfavorites when already favorited`() =
        runTest(testDispatcher) {
            val detail =
                fakeVideoDetail().copy(
                    userActions = UserActions(favorite = true),
                )
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { Prefs.isLogin } returns true
            coEvery { favoriteRepository.getAllFavoriteFolderMetadataList(any(), any(), any(), any()) } returns
                listOf(
                    FavoriteFolderMetadata(
                        id = 10,
                        fid = 10,
                        mid = 1L,
                        title = "默认收藏夹",
                        cover = null,
                        videoInThisFav = true,
                        mediaCount = 5,
                    ),
                )
            coEvery { favoriteRepository.updateVideoToFavoriteFolder(any(), any(), any(), any()) } returns Unit
            sharedStateFlow.value = VideoSharedState(aid = 1L, favorited = true)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleFavorite()
            advanceUntilIdle()

            coVerify { favoriteRepository.updateVideoToFavoriteFolder(aid = 1L, addMediaIds = emptyList(), any(), any()) }
            coVerify { videoInfoRepository.updateVideoActionState(aid = 1L, favorited = false) }
        }

    @Test
    fun `oneClickTripleAction delegates and toasts on success`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { videoInfoRepository.sendOneClickTriple(any(), any()) } returns
                dev.frost819.newbv.biliapi.http.entity.video.OneClickTripleAction(like = true, coin = true, fav = true)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.oneClickTripleAction()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).isEqualTo("一键三连")
            }
            coVerify { videoInfoRepository.sendOneClickTriple(aid = 1L, bvid = "BV1") }
        }

    @Test
    fun `oneClickTripleAction emits toast on failure`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail
            coEvery { videoInfoRepository.sendOneClickTriple(any(), any()) } throws
                RuntimeException("triple error")

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.oneClickTripleAction()
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(VideoDetailUiEffect.ShowToast::class.java)
                assertThat((effect as VideoDetailUiEffect.ShowToast).message).contains("一键三连失败")
            }
        }

    @Test
    fun `oneClickTripleAction is no-op when detail is null`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } throws RuntimeException("error")

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.oneClickTripleAction()
            advanceUntilIdle()

            coVerify(exactly = 0) { videoInfoRepository.sendOneClickTriple(any(), any()) }
        }
}
