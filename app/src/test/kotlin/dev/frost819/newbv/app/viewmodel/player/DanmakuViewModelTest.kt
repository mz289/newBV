package dev.frost819.newbv.app.viewmodel.player

import com.google.common.truth.Truth.assertThat
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.frost819.newbv.app.data.DanmakuBlockHitStats
import dev.frost819.newbv.app.data.DanmakuBlockRuleStore
import dev.frost819.newbv.app.ui.action.player.DanmakuSettingAction
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMeta
import dev.frost819.newbv.biliapi.http.entity.danmaku.DanmakuData
import dev.frost819.newbv.biliapi.repositories.VideoPlayRepository
import dev.frost819.newbv.danmaku.config.DanmakuBlockRule
import dev.frost819.newbv.danmaku.config.DanmakuBlockRuleType
import dev.frost819.newbv.danmaku.config.DanmakuMergeMode
import dev.frost819.newbv.danmaku.entity.DanmakuType
import dev.frost819.newbv.data.datastore.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import dev.frost819.newbv.data.datastore.DanmakuType as DataDanmakuType

/**
 * [DanmakuViewModel] 的单元测试。
 *
 * 覆盖两部分：
 * - 弹幕状态更新逻辑：缩放、透明度、区域、速度因子、蒙版开关、类型过滤
 * - 分段加载逻辑：元数据兜底、初始段定位、预取、去重、越界 clamp、
 *   弹幕关闭跳过、失败重试
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DanmakuViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var videoPlayRepository: VideoPlayRepository
    private lateinit var viewModel: DanmakuViewModel

    @BeforeEach
    fun setUp() {
        DanmakuBlockHitStats.reset()
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.d(any(), any(), any()) } returns 0
        mockkObject(Prefs)
        every { Prefs.defaultDanmakuScale } returns 1.75f
        every { Prefs.defaultDanmakuOpacity } returns 0.7f
        every { Prefs.defaultDanmakuArea } returns 0.5f
        every { Prefs.defaultDanmakuSpeedFactor } returns 1f
        every { Prefs.defaultDanmakuMask } returns false
        every { Prefs.defaultDanmakuEnabled } returns true
        every { Prefs.defaultDanmakuTypes } returns
            listOf(
                DataDanmakuType.All,
                DataDanmakuType.Rolling,
                DataDanmakuType.Top,
                DataDanmakuType.Bottom,
            )
        every { Prefs.defaultDanmakuScale = any() } answers {}
        every { Prefs.defaultDanmakuOpacity = any() } answers {}
        every { Prefs.defaultDanmakuArea = any() } answers {}
        every { Prefs.defaultDanmakuSpeedFactor = any() } answers {}
        every { Prefs.defaultDanmakuMask = any() } answers {}
        every { Prefs.defaultDanmakuEnabled = any() } answers {}
        every { Prefs.defaultDanmakuTypes = any() } answers {}
        every { Prefs.apiType } returns ApiType.Web

        videoPlayRepository = mockk()
        viewModel = DanmakuViewModel(videoPlayRepository).apply { preprocessingDispatcher = testDispatcher }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial danmakuState has default values`() {
        val state = viewModel.danmakuState.value
        assertThat(state.enabled).isTrue()
        assertThat(state.scale).isEqualTo(1.75f)
        assertThat(state.opacity).isEqualTo(0.7f)
        assertThat(state.area).isEqualTo(0.5f)
        assertThat(state.speedFactor).isEqualTo(1f)
        assertThat(state.maskEnabled).isFalse()
        assertThat(state.enabledTypes).isNotEmpty()
    }

    @Test
    fun `updateDanmakuState SetScale updates scale`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetScale(2.5f))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.scale).isEqualTo(2.5f)
        }

    @Test
    fun `updateDanmakuState SetOpacity updates opacity`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetOpacity(0.9f))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.opacity).isEqualTo(0.9f)
        }

    @Test
    fun `updateDanmakuState SetArea updates area`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetArea(0.8f))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.area).isEqualTo(0.8f)
        }

    @Test
    fun `updateDanmakuState SetSpeedFactor updates speedFactor`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetSpeedFactor(1.5f))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.speedFactor).isEqualTo(1.5f)
        }

    @Test
    fun `updateDanmakuState SetMaskEnabled updates maskEnabled`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetMaskEnabled(true))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.maskEnabled).isTrue()

            viewModel.updateDanmakuState(DanmakuSettingAction.SetMaskEnabled(false))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.maskEnabled).isFalse()
        }

    @Test
    fun `updateDanmakuState SetEnabledTypes updates enabledTypes`() =
        runTest(testDispatcher) {
            val types = listOf(DanmakuType.Rolling, DanmakuType.Top)
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(types))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.enabledTypes).contains(DanmakuType.Rolling)
            assertThat(viewModel.danmakuState.value.enabledTypes).contains(DanmakuType.Top)
        }

    @Test
    fun `toggleDanmaku flips enabled and preserves enabledTypes`() =
        runTest(testDispatcher) {
            // 回归：总开关不应改动各类型勾选（如仅开顶部弹幕），否则用户设置丢失
            val types = listOf(DanmakuType.Top, DanmakuType.Bottom)
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(types))
            advanceUntilIdle()

            viewModel.toggleDanmaku()
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.enabled).isFalse()
            assertThat(viewModel.danmakuState.value.enabledTypes).containsExactlyElementsIn(types)

            viewModel.toggleDanmaku()
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.enabled).isTrue()
            assertThat(viewModel.danmakuState.value.enabledTypes).containsExactlyElementsIn(types)
        }

    @Test
    fun `updateDanmakuState SetEnabled updates enabled`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabled(false))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.enabled).isFalse()

            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabled(true))
            advanceUntilIdle()
            assertThat(viewModel.danmakuState.value.enabled).isTrue()
        }

    @Test
    fun `updateDanmakuState with no change is no-op`() =
        runTest(testDispatcher) {
            val initialScale = viewModel.danmakuState.value.scale
            viewModel.updateDanmakuState(DanmakuSettingAction.SetScale(initialScale))
            advanceUntilIdle()

            assertThat(viewModel.danmakuState.value.scale).isEqualTo(initialScale)
        }

    @Test
    fun `updateDanmakuState SetScale persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetScale(3.0f))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuScale = 3.0f }
        }

    @Test
    fun `updateDanmakuState SetOpacity persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetOpacity(0.5f))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuOpacity = 0.5f }
        }

    @Test
    fun `updateDanmakuState SetArea persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetArea(0.9f))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuArea = 0.9f }
        }

    @Test
    fun `updateDanmakuState SetSpeedFactor persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetSpeedFactor(2.0f))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuSpeedFactor = 2.0f }
        }

    @Test
    fun `updateDanmakuState SetMaskEnabled persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetMaskEnabled(true))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuMask = true }
        }

    @Test
    fun `updateDanmakuState SetEnabledTypes persists to Prefs`() =
        runTest(testDispatcher) {
            val types = listOf(DanmakuType.Rolling, DanmakuType.Top)
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(types))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuTypes = any() }
        }

    @Test
    fun `updateDanmakuState SetEnabled persists to Prefs`() =
        runTest(testDispatcher) {
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabled(false))
            advanceUntilIdle()

            verify { Prefs.defaultDanmakuEnabled = false }
        }

    @Test
    fun `updateDanmakuState SetEnabled with persist false skips Prefs`() =
        runTest(testDispatcher) {
            // 投屏等临时开关不覆盖用户默认设置
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabled(enabled = false, persist = false))
            advanceUntilIdle()

            assertThat(viewModel.danmakuState.value.enabled).isFalse()
            verify(exactly = 0) { Prefs.defaultDanmakuEnabled = any() }
        }

    @Test
    fun `legacy empty persisted types migrates to disabled master switch with all types`() =
        runTest(testDispatcher) {
            // 旧版本把总开关关闭持久化为空类型列表；升级后应还原类型勾选而不是丢失
            every { Prefs.defaultDanmakuTypes } returns emptyList()
            every { Prefs.defaultDanmakuEnabled } returns true
            val migrated = DanmakuViewModel(videoPlayRepository)

            val state = migrated.danmakuState.value
            assertThat(state.enabled).isFalse()
            assertThat(state.enabledTypes).containsExactlyElementsIn(DanmakuType.entries)
        }

    // === 分段加载 ===

    /** 构造一条 Web 分段接口返回的弹幕数据（time 为秒）。 */
    private fun fakeDanmakuData(
        dmid: Long,
        timeSec: Float,
    ) = DanmakuData(
        time = timeSec,
        type = 1,
        size = 25,
        color = 0xFFFFFF,
        timestamp = 0,
        pool = 0,
        midHash = "hash",
        dmid = dmid,
        level = 0,
        text = "dm-$dmid",
    )

    @Test
    fun `segmentIndexOf uses default 6min segment size`() {
        assertThat(viewModel.segmentIndexOf(0L)).isEqualTo(1)
        assertThat(viewModel.segmentIndexOf(359_999L)).isEqualTo(1)
        assertThat(viewModel.segmentIndexOf(360_000L)).isEqualTo(2)
        assertThat(viewModel.segmentIndexOf(720_001L)).isEqualTo(3)
    }

    @Test
    fun `loadDanmaku fetches initial segment and prefetches next one`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 3, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 1, preferApiType = any())
            }
            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 2, preferApiType = any())
            }
            // 预取窗口只有 1 段
            coVerify(exactly = 0) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 3, preferApiType = any())
            }
        }

    @Test
    fun `loadDanmaku locates initial segment from initialPositionMs`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 5, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2, initialPositionMs = 720_000)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 3, preferApiType = any())
            }
            coVerify(exactly = 0) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 1, preferApiType = any())
            }
        }

    @Test
    fun `loadDanmaku falls back to default segment size when meta fails`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } throws IOException("network down")
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            // 720_000ms 在默认 6 分钟分段下属于第 3 段
            viewModel.loadDanmaku(aid = 1, cid = 2, initialPositionMs = 720_000)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 3, preferApiType = any())
            }
        }

    @Test
    fun `loadDanmaku skips segment loading when danmaku is closed`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns
                DanmakuMeta(360_000, 3, closed = true, 50)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                videoPlayRepository.getDanmakuSegment(any(), any(), any(), any())
            }
        }

    @Test
    fun `segmentTotal clamps requests beyond the end`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 1, false, 10)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()
            // 已加载全部段后，进度推进到下一分段：目标段越界，不发起请求
            viewModel.onVideoPositionChanged(360_000)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 2, preferApiType = any())
            }
        }

    @Test
    fun `onVideoPositionChanged within loaded segment does not refetch`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 3, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()
            val callsAfterInit = 2 // 初始段 + 预取段

            // 同段内多次进度更新（10Hz 喂入），不产生新请求
            repeat(5) { viewModel.onVideoPositionChanged(it * 1_000L) }
            advanceUntilIdle()

            coVerify(exactly = callsAfterInit) {
                videoPlayRepository.getDanmakuSegment(any(), any(), any(), any())
            }
        }

    @Test
    fun `onVideoPositionChanged crossing boundary loads next segment`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 5, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()

            viewModel.onVideoPositionChanged(360_000)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 3, preferApiType = any())
            }
        }

    @Test
    fun `segment fetch retries on failure and succeeds`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 3, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } throws
                IOException("flaky") andThen listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()

            coVerify(exactly = 2) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 1, preferApiType = any())
            }
        }

    @Test
    fun `clearDanmaku resets segment state`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 3, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2)
            advanceUntilIdle()

            viewModel.clearDanmaku()

            assertThat(viewModel.danmakuMask.value).isNull()
        }

    @Test
    fun `second loadDanmaku discards stale meta result of the first`() =
        runTest(testDispatcher) {
            // 第一次元数据请求慢（挂起），第二次立即返回：第一次的结果必须被丢弃
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } coAnswers
                { throw IOException("stale") } andThen DanmakuMeta(360_000, 3, false, 100)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))

            viewModel.loadDanmaku(aid = 1, cid = 2, initialPositionMs = 360_000)
            viewModel.loadDanmaku(aid = 1, cid = 2, initialPositionMs = 0)
            advanceUntilIdle()

            // 第二次加载从第 1 段开始且成功；陈旧结果不会覆盖分段状态
            coVerify(exactly = 1) {
                videoPlayRepository.getDanmakuSegment(aid = 1, cid = 2, segmentIndex = 1, preferApiType = any())
            }
        }

    // === 引擎时钟对齐 ===

    @Test
    fun `分段边界合并并在断点续播时读取前段且切换配置无需重拉`() =
        runTest(testDispatcher) {
            every { Prefs.danmakuMergeMode } returns 2
            every { Prefs.danmakuMergeMode = any() } answers {}
            val vm = DanmakuViewModel(videoPlayRepository).apply { preprocessingDispatcher = testDispatcher }
            val player = mockk<DanmakuPlayer>(relaxed = true)
            val outputs = mutableListOf<List<DanmakuItemData>>()
            every { player.updateData(any()) } answers {
                outputs.add(firstArg())
                emptyList()
            }
            vm.danmakuPlayer = player
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 2, false, 2)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), 1, any()) } returns
                listOf(fakeDanmakuData(1, 359f).copy(text = "边界弹幕"))
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), 2, any()) } returns
                listOf(fakeDanmakuData(2, 361f).copy(text = "边界弹幕"))
            vm.loadDanmaku(1, 2, initialPositionMs = 360_000)
            advanceUntilIdle()
            assertThat(outputs.last().single().content).isEqualTo("边界弹幕 ×2")
            assertThat(outputs.last().single().position).isEqualTo(359_000)
            vm.updateDanmakuState(DanmakuSettingAction.SetMergeMode(DanmakuMergeMode.Off))
            advanceUntilIdle()
            assertThat(outputs.last()).hasSize(2)
            vm.updateDanmakuState(DanmakuSettingAction.SetMergeMode(DanmakuMergeMode.Similar))
            advanceUntilIdle()
            assertThat(outputs.last().single().mergedCount).isEqualTo(2)
            coVerify(exactly = 2) { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) }
            vm.clearDanmaku()
        }

    @Test
    fun `切换显示类型从缓存恢复同文弹幕且保留接口权重`() =
        runTest(testDispatcher) {
            every { Prefs.danmakuMergeMode } returns 2
            val vm = DanmakuViewModel(videoPlayRepository).apply { preprocessingDispatcher = testDispatcher }
            val player = mockk<DanmakuPlayer>(relaxed = true)
            val outputs = mutableListOf<List<DanmakuItemData>>()
            every { player.updateData(any()) } answers {
                outputs.add(firstArg())
                emptyList()
            }
            vm.danmakuPlayer = player
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 1, false, 2)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(
                    fakeDanmakuData(1, 0f).copy(text = "同文", level = 4),
                    fakeDanmakuData(2, 1f).copy(text = "同文", type = 5, level = 8),
                )
            vm.loadDanmaku(1, 2)
            advanceUntilIdle()
            assertThat(outputs.last().single().score).isEqualTo(8)
            vm.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(listOf(DanmakuType.Top)))
            advanceUntilIdle()
            assertThat(outputs.last().single().mode).isEqualTo(5)
            assertThat(outputs.last().single().mergedCount).isEqualTo(1)
            vm.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(listOf(DanmakuType.All)))
            advanceUntilIdle()
            assertThat(outputs.last().single().mergedCount).isEqualTo(2)
            coVerify(exactly = 1) { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) }
            vm.clearDanmaku()
        }

    @Test
    fun `后台处理切视频后旧快照不能提交且新视频不沿用缓存`() =
        runTest(testDispatcher) {
            val pending = ArrayDeque<Runnable>()
            viewModel.preprocessingDispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        pending.add(block)
                    }
                }
            val player = mockk<DanmakuPlayer>(relaxed = true)
            val outputs = mutableListOf<List<DanmakuItemData>>()
            every { player.updateData(any()) } answers {
                outputs.add(firstArg())
                emptyList()
            }
            viewModel.danmakuPlayer = player
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 1, false, 1)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), 2, any(), any()) } returns
                listOf(fakeDanmakuData(1, 0f))
            coEvery { videoPlayRepository.getDanmakuSegment(any(), 3, any(), any()) } returns
                listOf(fakeDanmakuData(2, 0f))
            viewModel.loadDanmaku(1, 2)
            runCurrent()
            assertThat(pending).hasSize(1)
            assertThat(outputs).isEmpty()
            viewModel.clearDanmaku()
            viewModel.loadDanmaku(1, 3)
            pending.removeFirst().run()
            runCurrent()
            assertThat(outputs).isEmpty()
            assertThat(pending).hasSize(1)
            pending.removeFirst().run()
            advanceUntilIdle()
            assertThat(outputs.single().single().danmakuId).isEqualTo(2)
            viewModel.clearDanmaku()
        }

    @Test
    fun `后台处理期间改变类型只提交最新配置且不重复请求`() =
        runTest(testDispatcher) {
            val pending = ArrayDeque<Runnable>()
            viewModel.preprocessingDispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        pending.add(block)
                    }
                }
            val player = mockk<DanmakuPlayer>(relaxed = true)
            val outputs = mutableListOf<List<DanmakuItemData>>()
            every { player.updateData(any()) } answers {
                outputs.add(firstArg())
                emptyList()
            }
            viewModel.danmakuPlayer = player
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 1, false, 2)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(
                    fakeDanmakuData(1, 0f),
                    fakeDanmakuData(2, 1f).copy(type = 5),
                )
            viewModel.loadDanmaku(1, 2)
            runCurrent()
            viewModel.updateDanmakuState(DanmakuSettingAction.SetEnabledTypes(listOf(DanmakuType.Top)))
            pending.removeFirst().run()
            runCurrent()
            assertThat(outputs).isEmpty()
            pending.removeFirst().run()
            advanceUntilIdle()
            assertThat(outputs.single().single().danmakuId).isEqualTo(2)
            coVerify(exactly = 1) { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) }
            viewModel.clearDanmaku()
        }

    @Test
    fun `取消分段请求不留下预取段加载标记`() =
        runTest(testDispatcher) {
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 4, false, 1)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), 1, any()) } coAnswers {
                delay(1000)
                listOf(fakeDanmakuData(1, 0f))
            }
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), 2, any()) } returns
                listOf(fakeDanmakuData(2, 360f))
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), 3, any()) } returns emptyList()
            viewModel.loadDanmaku(1, 2)
            runCurrent()
            viewModel.onVideoPositionChanged(360_000)
            advanceUntilIdle()
            coVerify(exactly = 1) { videoPlayRepository.getDanmakuSegment(any(), any(), 2, any()) }
            viewModel.clearDanmaku()
        }

    @Test
    fun `onVideoPositionChanged seeks engine when position jumps beyond threshold`() {
        // 回归：断点续播/切集使视频位置跳变（如恢复到 20 分钟）而引擎时钟仍在 0，
        // 引擎按自身时钟渲染会长时间无弹幕，喂入位置时必须自动对齐引擎
        val player = mockk<DanmakuPlayer>(relaxed = true)
        every { player.getCurrentTimeMs() } returns 0L
        viewModel.danmakuPlayer = player

        viewModel.onVideoPositionChanged(1_200_000)

        verify(exactly = 1) { player.seekTo(1_200_000) }
    }

    @Test
    fun `onVideoPositionChanged does not seek engine within threshold`() {
        val player = mockk<DanmakuPlayer>(relaxed = true)
        every { player.getCurrentTimeMs() } returns 1_000_000L
        viewModel.danmakuPlayer = player

        // 正常播放的采样抖动（< 500ms）不应触发引擎 seek
        viewModel.onVideoPositionChanged(1_000_300)

        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun `onVideoPositionChanged is safe before engine is initialized`() {
        // 引擎尚未 init（danmakuPlayer 为 null）时喂入位置不应抛异常
        viewModel.onVideoPositionChanged(1_200_000)
    }

    @Test
    fun `onVideoPositionChanged keeps engine paused after jump when not playing`() {
        // DanmakuPlayer.seekTo 会内部解暂停；暂停/缓冲期间喂入跳变位置后必须还原暂停态，
        // 否则弹幕会在视频暂停时继续滚动
        val player = mockk<DanmakuPlayer>(relaxed = true)
        every { player.getCurrentTimeMs() } returns 0L
        viewModel.danmakuPlayer = player

        viewModel.onVideoPositionChanged(1_200_000)

        verify { player.seekTo(1_200_000) }
        verify { player.pause() }
    }

    @Test
    fun `onVideoPositionChanged after jump does not pause engine when playing`() {
        val player = mockk<DanmakuPlayer>(relaxed = true)
        every { player.getCurrentTimeMs() } returns 0L
        viewModel.danmakuPlayer = player
        viewModel.play()

        viewModel.onVideoPositionChanged(1_200_000)

        verify { player.seekTo(1_200_000) }
        verify(exactly = 0) { player.pause() }
    }

    @Test
    fun `共享屏蔽规则变化重载原始数据且关闭屏蔽恢复数量`() =
        runTest(testDispatcher) {
            val blockState =
                MutableStateFlow(
                    DanmakuBlockRuleStore.State(
                        enabled = true,
                        rules = listOf(DanmakuBlockRule(DanmakuBlockRuleType.User, "ab")),
                    ),
                )
            mockkObject(DanmakuBlockRuleStore)
            every { DanmakuBlockRuleStore.state } returns blockState
            every { Prefs.danmakuMergeMode } returns 2
            val vm = DanmakuViewModel(videoPlayRepository).apply { preprocessingDispatcher = testDispatcher }
            val player = mockk<DanmakuPlayer>(relaxed = true)
            val outputs = mutableListOf<List<DanmakuItemData>>()
            every { player.updateData(any()) } answers {
                outputs.add(firstArg())
                emptyList()
            }
            vm.danmakuPlayer = player
            coEvery { videoPlayRepository.getDanmakuMeta(any(), any()) } returns DanmakuMeta(360_000, 1, false, 3)
            coEvery { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) } returns
                listOf(
                    fakeDanmakuData(1, 0f).copy(text = "same", midHash = "ab"),
                    fakeDanmakuData(2, 0f).copy(text = "same", midHash = "cd"),
                    fakeDanmakuData(3, 0f).copy(text = "same", midHash = "cd"),
                )
            vm.loadDanmaku(1, 2)
            advanceUntilIdle()
            assertThat(outputs.last().single().mergedCount).isEqualTo(2)
            assertThat(DanmakuBlockHitStats.totalHits.value).isEqualTo(1)
            blockState.value = blockState.value.copy(enabled = false)
            advanceUntilIdle()
            assertThat(outputs.last().single().mergedCount).isEqualTo(3)
            assertThat(DanmakuBlockHitStats.totalHits.value).isEqualTo(1)
            blockState.value =
                blockState.value.copy(
                    enabled = true,
                    rules =
                        listOf(
                            DanmakuBlockRule(DanmakuBlockRuleType.User, "cd"),
                        ),
                )
            advanceUntilIdle()
            assertThat(outputs.last().single().mergedCount).isEqualTo(1)
            assertThat(DanmakuBlockHitStats.totalHits.value).isEqualTo(3)
            coVerify(exactly = 1) { videoPlayRepository.getDanmakuSegment(any(), any(), any(), any()) }
        }
}
