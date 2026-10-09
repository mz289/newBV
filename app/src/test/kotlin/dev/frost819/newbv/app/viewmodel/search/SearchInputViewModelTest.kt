package dev.frost819.newbv.app.viewmodel.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.testutil.InMemoryPreferencesDataStore
import dev.frost819.newbv.biliapi.entity.search.Hotword
import dev.frost819.newbv.biliapi.repositories.SearchRepository
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.data.db.entity.SearchHistoryEntity
import dev.frost819.newbv.data.repository.SearchHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Date

/**
 * [SearchInputViewModel] 的单元测试。
 *
 * 验证热搜词加载、搜索建议、搜索历史增删逻辑。
 * 使用 MockK mock [SearchRepository] 和 [SearchHistoryRepository]。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchInputViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var searchRepo: SearchRepository
    private lateinit var historyRepo: SearchHistoryRepository
    private lateinit var viewModel: SearchInputViewModel

    companion object {
        private lateinit var testDataStore: DataStore<Preferences>

        @JvmStatic
        @BeforeAll
        fun initPrefs() {
            Prefs.resetForTesting()
            testDataStore = InMemoryPreferencesDataStore()
            Prefs.init(testDataStore)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            // Leave Prefs initialized
        }
    }

    private fun fakeHotword(keyword: String) =
        Hotword(
            keyword = keyword,
            showName = keyword,
            icon = null,
        )

    private fun fakeHistory(keyword: String) =
        SearchHistoryEntity(
            id = null,
            keyword = keyword,
            searchDate = Date(),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        runBlocking { Prefs.clear() }

        searchRepo = mockk()
        historyRepo = mockk()

        coEvery { searchRepo.getSearchHotwords(any(), any()) } returns
            listOf(fakeHotword("热词1"), fakeHotword("热词2"))
        coEvery { searchRepo.getSearchSuggest(any(), any()) } returns
            listOf("建议1", "建议2")
        coEvery { historyRepo.getHistories(any()) } returns emptyList()
        coEvery { historyRepo.addHistory(any()) } returns Unit
        coEvery { historyRepo.deleteHistory(any()) } returns Unit
        coEvery { historyRepo.clearAll() } returns Unit

        viewModel = SearchInputViewModel(searchRepo, historyRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing keyword cancels old suggestions and clearing keeps them empty`() = runTest(testDispatcher) {
        val old = CompletableDeferred<List<String>>()
        coEvery { searchRepo.getSearchSuggest("old", any()) } coAnswers { old.await() }
        viewModel.updateKeyword("old")
        runCurrent()
        viewModel.updateKeyword("new")
        runCurrent()
        assertThat(viewModel.uiState.value.suggests).containsExactly("建议1", "建议2")
        old.complete(listOf("old suggestion"))
        runCurrent()
        assertThat(viewModel.uiState.value.suggests).doesNotContain("old suggestion")

        viewModel.updateKeyword("   ")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.suggests).isEmpty()
        coVerify(exactly = 0) { searchRepo.getSearchSuggest("   ", any()) }
    }

    @Test
    fun `history write failure still completes search`() = runTest(testDispatcher) {
        coEvery { historyRepo.addHistory(any()) } throws java.io.IOException("disk full")
        var completed = false
        viewModel.commitSearch(" search ") { completed = true }
        advanceUntilIdle()
        assertThat(completed).isTrue()
        coVerify { historyRepo.addHistory("search") }
    }

    @Test
    fun `init loads hotwords and histories`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.hotwords).hasSize(2)
            assertThat(state.hotwords[0].keyword).isEqualTo("热词1")
            assertThat(state.histories).isEmpty()
        }

    @Test
    fun `updateKeyword with non-empty keyword loads suggests`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.updateKeyword("测试")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.keyword).isEqualTo("测试")
            assertThat(state.suggests).hasSize(2)
            assertThat(state.suggests[0]).isEqualTo("建议1")
        }

    @Test
    fun `updateKeyword with empty keyword clears suggests`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.updateKeyword("测试")
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.suggests).isNotEmpty()

            viewModel.updateKeyword("")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.suggests).isEmpty()
        }

    @Test
    fun `commitSearch adds history and reloads`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            coEvery { historyRepo.getHistories(any()) } returns listOf(fakeHistory("搜索词"))
            viewModel.commitSearch("搜索词")
            advanceUntilIdle()

            coVerify { historyRepo.addHistory("搜索词") }
            assertThat(viewModel.uiState.value.histories).hasSize(1)
        }

    @Test
    fun `commitSearch calls completion after history is persisted`() =
        runTest(testDispatcher) {
            var completed = false

            viewModel.commitSearch("搜索词") { completed = true }
            advanceUntilIdle()

            coVerify { historyRepo.addHistory("搜索词") }
            assertThat(completed).isTrue()
        }

    @Test
    fun `commitSearch with blank keyword does nothing`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.commitSearch("")
            advanceUntilIdle()

            coVerify(exactly = 0) { historyRepo.addHistory(any()) }
        }

    @Test
    fun `deleteHistory removes and reloads`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            coEvery { historyRepo.getHistories(any()) } returns listOf(fakeHistory("词2"))
            viewModel.deleteHistory("词1")
            advanceUntilIdle()

            coVerify { historyRepo.deleteHistory("词1") }
            assertThat(viewModel.uiState.value.histories).hasSize(1)
            assertThat(
                viewModel.uiState.value.histories[0]
                    .keyword,
            ).isEqualTo("词2")
        }

    @Test
    fun `clearAllHistories clears and reloads`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            coEvery { historyRepo.getHistories(any()) } returns emptyList()
            viewModel.clearAllHistories()
            advanceUntilIdle()

            coVerify { historyRepo.clearAll() }
            assertThat(viewModel.uiState.value.histories).isEmpty()
        }

    @Test
    fun `hotwords state emits updates`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.uiState.test {
                assertThat(awaitItem().hotwords).isNotEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init with hotwords failure leaves hotwords empty`() =
        runTest(testDispatcher) {
            coEvery { searchRepo.getSearchHotwords(any(), any()) } throws RuntimeException("network error")
            val failingVm = SearchInputViewModel(searchRepo, historyRepo)
            advanceUntilIdle()

            assertThat(failingVm.uiState.value.hotwords).isEmpty()
        }
}
