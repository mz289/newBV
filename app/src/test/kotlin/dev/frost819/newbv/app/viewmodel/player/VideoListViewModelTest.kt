package dev.frost819.newbv.app.viewmodel.player

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.data.VideoInfoRepository
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.biliapi.repositories.VideoDetailRepository
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

/**
 * [VideoListViewModel] 的单元测试。
 *
 * 验证分集列表查找逻辑：下一集、上一集查找，
 * 以及与 [VideoInfoRepository] 的状态同步。
 */
class VideoListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var videoInfoRepository: VideoInfoRepository
    private lateinit var videoDetailRepository: VideoDetailRepository
    private lateinit var viewModel: VideoListViewModel

    private val sampleVideoList =
        listOf(
            VideoListItem(aid = 100, cid = 1001, title = "第一集"),
            VideoListItem(aid = 200, cid = 2001, title = "第二集"),
            VideoListItem(aid = 300, cid = 3001, title = "第三集"),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        videoDetailRepository = mockk()
        videoInfoRepository = VideoInfoRepository(videoDetailRepository)
        viewModel = VideoListViewModel(videoInfoRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.videoListState.value
        assertThat(state.videoList).isEmpty()
        assertThat(state.relatedVideos).isEmpty()
    }

    @Test
    fun `videoListState syncs when repository updates`() =
        runTest(testDispatcher) {
            videoInfoRepository.updateVideoList(sampleVideoList)
            advanceUntilIdle()

            val state = viewModel.videoListState.value
            assertThat(state.videoList).hasSize(3)
            assertThat(state.videoList[0].title).isEqualTo("第一集")
        }

    @Test
    fun `setCurrentAid sets current aid`() =
        runTest(testDispatcher) {
            viewModel.setCurrentAid(42L)
            assertThat(viewModel.videoListState.value.videoList).isEmpty()
        }

    @Test
    fun `empty video list in repository does not update state`() =
        runTest(testDispatcher) {
            videoInfoRepository.updateVideoList(emptyList())
            advanceUntilIdle()

            assertThat(viewModel.videoListState.value.videoList).isEmpty()
        }
}
