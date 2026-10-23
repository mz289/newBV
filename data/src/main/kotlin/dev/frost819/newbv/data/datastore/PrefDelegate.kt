package dev.frost819.newbv.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** 同步读写业务值；仅在加载和持久化边界转换 DataStore 编码。 */
class PrefDelegate<T, P>(
    private val key: Preferences.Key<P>,
    private val defaultValue: T,
    private val save: (T) -> P,
    private val restore: (P) -> T,
) : ReadWriteProperty<Any?, T> {
    private val state = MutableStateFlow(defaultValue)
    val flow = state.asStateFlow()

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T {
        Prefs.checkInitialized()
        return state.value
    }

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        Prefs.checkInitialized()
        val persisted = save(value)
        state.value = restore(persisted)
        val dataStore = Prefs.dataStore
        Prefs.launchPersist {
            dataStore.edit { it[key] = persisted }
        }
    }

    internal fun load(preferences: Preferences) {
        state.value = preferences[key]?.let(restore) ?: defaultValue
    }

    internal fun resetToDefault() {
        state.value = defaultValue
    }
}

/** 基本类型无需编解码。 */
fun <T> PrefDelegate(
    key: Preferences.Key<T>,
    defaultValue: T,
): PrefDelegate<T, T> = PrefDelegate(key, defaultValue, save = { it }, restore = { it })
