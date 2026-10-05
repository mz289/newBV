package dev.frost819.newbv.app.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.user.Author
import dev.frost819.newbv.biliapi.entity.video.Dimension
import dev.frost819.newbv.biliapi.entity.video.RelatedVideo
import dev.frost819.newbv.biliapi.entity.video.UserActions
import dev.frost819.newbv.biliapi.entity.video.VideoDetail
import dev.frost819.newbv.biliapi.entity.video.VideoPage
import dev.frost819.newbv.biliapi.http.entity.video.OneClickTripleAction
import dev.frost819.newbv.biliapi.repositories.CoinRepository
import dev.frost819.newbv.biliapi.repositories.LikeRepository
import dev.frost819.newbv.biliapi.repositories.OneClickTripleActionRepository
import dev.frost819.newbv.biliapi.repositories.VideoDetailRepository
import dev.frost819.newbv.data.datastore.Prefs
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import java.util.Date

/**
 * [VideoInfoRepository] 的单元测试。
 *
 * 验证 StateFlow 传播：视频列表、详情、相关视频、历史进度的更新与重置。
 */
class VideoInfoRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var videoDetailRepository: VideoDetailRepository
    private lateinit var likeRepository: LikeRepository
    private lateinit var coinRepository: CoinRepository
    private lateinit var oneClickTripleActionRepository: OneClickTripleActionRepository
    private lateinit var repository: VideoInfoRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        videoDetailRepository = mockk()
        likeRepository = mockk()
        coinRepository = mockk()
        oneClickTripleActionRepository = mockk()
        repository =
            VideoInfoRepository(
                videoDetailRepository = videoDetailRepository,
                likeRepository = likeRepository,
                coinRepository = coinRepository,
                oneClickTripleActionRepository = oneClickTripleActionRepository,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeVideoDetail(
        relatedVideos: List<RelatedVideo> = emptyList(),
        history: VideoDetail.History = VideoDetail.History(0, 0),
    ): VideoDetail =
        VideoDetail(
            bvid = "BV1xx",
            aid = 1L,
            cid = 10L,
            cover = "",
            title = "test",
            publishDate = Date(),
            description = "",
            stat = VideoDetail.Stat(0, 0, 0, 0, 0, 0, 0, 0),
            author = Author(0, "", ""),
            pages = emptyList(),
            ugcSeason = null,
            relatedVideos = relatedVideos,
            redirectToEp = false,
            epid = null,
            argueTip = null,
            tags = emptyList(),
            userActions = UserActions(),
            history = history,
        )

    private fun fakeRelatedVideo(aid: Long = 1L): RelatedVideo =
        RelatedVideo(
            aid = aid,
            cid = 10L,
            cover = "",
            title = "related",
            duration = 60,
            author = null,
            jumpToSeason = false,
            epid = null,
            view = 0,
            danmaku = 0,
        )

    // ── updateVideoList ──────────────────────────────────────────────

    @Test
    fun `updateVideoList propagates to StateFlow`() =
        runTest(testDispatcher) {
            val items =
                listOf(
                    VideoListItem(aid = 1, cid = 10, title = "视频1"),
                    VideoListItem(aid = 2, cid = 20, title = "视频2"),
                )

            repository.updateVideoList(items)
            advanceUntilIdle()

            assertThat(repository.videoList.value).hasSize(2)
            assertThat(repository.videoList.value[0].title).isEqualTo("视频1")
        }

    @Test
    fun `videoList StateFlow emits on update`() =
        runTest(testDispatcher) {
            repository.videoList.test {
                assertThat(awaitItem()).isEmpty()

                val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
                repository.updateVideoList(items)
                advanceUntilIdle()

                val emitted = awaitItem()
                assertThat(emitted).hasSize(1)
                assertThat(emitted[0].aid).isEqualTo(1)
            }
        }

    // ── updateVideoDetail ────────────────────────────────────────────

    @Test
    fun `updateVideoDetail sets videoDetail`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isEqualTo(detail)
        }

    @Test
    fun `updateVideoDetail sets relatedVideos from detail`() =
        runTest(testDispatcher) {
            val related = listOf(fakeRelatedVideo(1), fakeRelatedVideo(2))
            val detail = fakeVideoDetail(relatedVideos = related)

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            assertThat(repository.relatedVideos.value).hasSize(2)
            assertThat(repository.relatedVideos.value[0].aid).isEqualTo(1)
        }

    @Test
    fun `updateVideoDetail sets lastPlayedCid and lastPlayedTime in sharedState`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail(history = VideoDetail.History(progress = 300, lastPlayedCid = 999L))

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            val shared = repository.videoSharedState.value
            assertThat(shared?.lastPlayedCid).isEqualTo(999L)
            assertThat(shared?.lastPlayedTime).isEqualTo(300)
        }

    @Test
    fun `videoDetail StateFlow emits on updateVideoDetail`() =
        runTest(testDispatcher) {
            repository.videoDetail.test {
                assertThat(awaitItem()).isNull()

                val detail = fakeVideoDetail()
                repository.updateVideoDetail(detail)
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(detail)
            }
        }

    // ── loadVideoDetail ──────────────────────────────────────────────

    @Test
    fun `loadVideoDetail success updates all state`() =
        runTest(testDispatcher) {
            val related = listOf(fakeRelatedVideo(10))
            val detail = fakeVideoDetail(relatedVideos = related, history = VideoDetail.History(500, 50L))
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isEqualTo(detail)
            assertThat(repository.relatedVideos.value).hasSize(1)
            assertThat(repository.videoSharedState.value?.lastPlayedCid).isEqualTo(50L)
            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(500)
        }

    @Test
    fun `loadVideoDetail failure does not crash and leaves state unchanged`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } throws
                RuntimeException("network error")

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isNull()
            assertThat(repository.relatedVideos.value).isEmpty()
        }

    @Test
    fun `loadVideoDetail uses ApiType Web by default`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            coVerify { videoDetailRepository.getVideoDetail(1L, ApiType.Web, "") }
        }

    // ── updateUgcPages ───────────────────────────────────────────────

    @Test
    fun `updateUgcPages adds pages to videos with multiple pages`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            val pages =
                listOf(
                    VideoPage(cid = 10L, index = 1, title = "P1", duration = 60, dimension = Dimension(1920, 1080)),
                    VideoPage(cid = 20L, index = 2, title = "P2", duration = 120, dimension = Dimension(1920, 1080)),
                )
            coEvery { videoDetailRepository.getUgcPages(any(), any()) } returns pages

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            val updated = repository.videoList.value
            assertThat(updated[0].ugcPages).isNotNull()
            assertThat(updated[0].ugcPages).hasSize(2)
        }

    @Test
    fun `updateUgcPages does not modify single-page videos`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            val singlePage =
                listOf(
                    VideoPage(cid = 10L, index = 1, title = "P1", duration = 60, dimension = Dimension(1920, 1080)),
                )
            coEvery { videoDetailRepository.getUgcPages(any(), any()) } returns singlePage

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoList.value[0].ugcPages).isNull()
        }

    @Test
    fun `updateUgcPages failure leaves item unchanged`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            coEvery { videoDetailRepository.getUgcPages(any(), any()) } throws RuntimeException("error")

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoList.value[0].ugcPages).isNull()
            assertThat(repository.videoList.value[0].title).isEqualTo("视频1")
        }

    // ── updateHistory ────────────────────────────────────────────────

    @Test
    fun `updateHistory before detail load is a no-op`() =
        runTest(testDispatcher) {
            repository.updateHistory(progress = 600, lastPlayedCid = 200L)
            advanceUntilIdle()

            assertThat(repository.videoSharedState.value).isNull()
        }

    @Test
    fun `updateHistory sets lastPlayedCid and lastPlayedTime in sharedState`() =
        runTest(testDispatcher) {
            repository.updateVideoDetail(fakeVideoDetail())

            repository.updateHistory(progress = 600, lastPlayedCid = 200L)
            advanceUntilIdle()

            assertThat(repository.videoSharedState.value?.aid).isEqualTo(1L)
            assertThat(repository.videoSharedState.value?.lastPlayedCid).isEqualTo(200L)
            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(600)
        }

    @Test
    fun `updateHistory with negative progress sets negative value`() =
        runTest(testDispatcher) {
            repository.updateVideoDetail(fakeVideoDetail())

            repository.updateHistory(progress = -1, lastPlayedCid = 0L)
            advanceUntilIdle()

            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(-1)
        }

    // ── reset ────────────────────────────────────────────────────────

    @Test
    fun `setVideoLiked writes state only after api success`() =
        runTest(testDispatcher) {
            mockkObject(Prefs)
            coEvery { Prefs.apiType } returns ApiType.Web
            coEvery { likeRepository.updateVideoLiked(any(), any(), any(), any()) } returns Unit

            repository.updateVideoDetail(fakeVideoDetail())
            repository.setVideoLiked(aid = 1L, like = true)

            assertThat(repository.videoSharedState.value?.liked).isTrue()

            coEvery { likeRepository.updateVideoLiked(any(), any(), any(), any()) } throws RuntimeException("fail")
            val result = runCatching { repository.setVideoLiked(aid = 1L, like = false) }

            assertThat(result.isFailure).isTrue()
            assertThat(repository.videoSharedState.value?.liked).isTrue()
            unmockkObject(Prefs)
        }

    @Test
    fun `sendVideoCoin writes coined state after api success`() =
        runTest(testDispatcher) {
            mockkObject(Prefs)
            coEvery { Prefs.apiType } returns ApiType.Web
            coEvery { coinRepository.sendVideoCoin(any(), any(), any(), any()) } returns Unit

            repository.updateVideoDetail(fakeVideoDetail())
            repository.sendVideoCoin(aid = 1L)

            assertThat(repository.videoSharedState.value?.coined).isTrue()
            unmockkObject(Prefs)
        }

    @Test
    fun `sendOneClickTriple syncs state when api returns result`() =
        runTest(testDispatcher) {
            mockkObject(Prefs)
            coEvery { Prefs.apiType } returns ApiType.Web
            coEvery { oneClickTripleActionRepository.sendVideoOneClickTripleAction(any(), any(), any()) } returns
                OneClickTripleAction(like = true, coin = true, fav = true)

            repository.updateVideoDetail(fakeVideoDetail())
            val result = repository.sendOneClickTriple(aid = 1L)

            assertThat(result).isEqualTo(OneClickTripleAction(like = true, coin = true, fav = true))
            assertThat(repository.videoSharedState.value).isEqualTo(
                VideoSharedState(
                    aid = 1L,
                    liked = true,
                    coined = true,
                    favorited = true,
                    lastPlayedCid = 0L,
                    lastPlayedTime = 0,
                ),
            )
            unmockkObject(Prefs)
        }
}
