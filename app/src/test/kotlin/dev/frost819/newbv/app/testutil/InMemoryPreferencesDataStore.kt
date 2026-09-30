package dev.frost819.newbv.app.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 内存版 [DataStore<Preferences>]，用于单元测试。
 *
 * 与文件级 DataStore 行为一致（[data] 流 + [updateData] 事务语义），
 * 但完全不触碰文件系统，规避 Windows 上临时文件重命名竞争导致的随机失败。
 */
class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val new = transform(state.value)
            state.value = new
            new
        }
}
