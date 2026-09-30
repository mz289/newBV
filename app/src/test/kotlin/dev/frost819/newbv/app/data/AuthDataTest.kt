package dev.frost819.newbv.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.testutil.InMemoryPreferencesDataStore
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [AuthData] 的单元测试。
 *
 * 验证 JSON 序列化/反序列化与 Prefs 双向同步。
 * Prefs 初始化一次，每个测试前 clear 重置。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthDataTest {
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
            // Leave Prefs initialized to avoid async write exceptions
        }
    }

    @BeforeEach
    fun clearPrefs() {
        runBlocking { Prefs.clear() }
    }

    @Test
    fun `toJson and fromJson roundtrip preserves all fields`() {
        val original =
            AuthData(
                uid = 12345L,
                uidCkMd5 = "ckmd5",
                sid = "sid-123",
                biliJct = "jct-token",
                sessData = "sess-data",
                tokenExpiredDate = 1700000000000L,
                accessToken = "access-token",
                refreshToken = "refresh-token",
            )

        val json = original.toJson()
        val restored = AuthData.fromJson(json)

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `toJson produces valid JSON with all fields`() {
        val authData =
            AuthData(
                uid = 100L,
                uidCkMd5 = "md5",
                sid = "sid",
                biliJct = "jct",
                sessData = "sess",
                tokenExpiredDate = 1700000000000L,
            )

        val json = authData.toJson()

        assertThat(json).contains("\"uid\":100")
        assertThat(json).contains("\"sessData\":\"sess\"")
        assertThat(json).contains("\"biliJct\":\"jct\"")
    }

    @Test
    fun `fromJson handles default empty accessToken and refreshToken`() {
        val json =
            """
            {"uid":1,"uidCkMd5":"m","sid":"s","biliJct":"j","sessData":"d","tokenExpiredDate":1700000000000}
            """.trimIndent()

        val authData = AuthData.fromJson(json)

        assertThat(authData.accessToken).isEmpty()
        assertThat(authData.refreshToken).isEmpty()
    }

    @Test
    fun `saveToPrefs writes all fields to Prefs`() {
        val authData =
            AuthData(
                uid = 999L,
                uidCkMd5 = "ckmd5",
                sid = "mysid",
                biliJct = "myjct",
                sessData = "mysess",
                tokenExpiredDate = 1700000000000L,
                accessToken = "mytoken",
                refreshToken = "myrefresh",
            )

        authData.saveToPrefs()

        assertThat(Prefs.isLogin).isTrue()
        assertThat(Prefs.uid).isEqualTo(999L)
        assertThat(Prefs.uidCkMd5).isEqualTo("ckmd5")
        assertThat(Prefs.sid).isEqualTo("mysid")
        assertThat(Prefs.biliJct).isEqualTo("myjct")
        assertThat(Prefs.sessData).isEqualTo("mysess")
        assertThat(Prefs.accessToken).isEqualTo("mytoken")
        assertThat(Prefs.refreshToken).isEqualTo("myrefresh")
        assertThat(Prefs.tokenExpiredDate.time).isEqualTo(1700000000000L)
    }
}
