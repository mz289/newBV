package dev.frost819.newbv.app.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * [SearchRemoteFeature] 的单元测试。
 *
 * 验证关键词提交分发：非空关键词回调并回传确认消息，
 * 空白关键词拒绝提交并提示。
 */
class SearchRemoteFeatureTest {
    @Test
    fun `submit forwards keyword to callback and returns confirmation`() =
        runBlocking {
            val received = mutableListOf<String>()
            val feature = SearchRemoteFeature.create { keyword -> received.add(keyword) }

            val result = feature.dispatch("submit", "  火影忍者\n")

            assertThat(received).containsExactly("火影忍者")
            assertThat(result).contains("火影忍者")
        }

    @Test
    fun `blank keyword is rejected without callback`() =
        runBlocking {
            var called = false
            val feature = SearchRemoteFeature.create { called = true }

            val result = feature.dispatch("submit", "   ")

            assertThat(called).isFalse()
            assertThat(result).contains("请输入搜索关键词")
        }

    @Test
    fun `unknown action returns null from dispatch`() =
        runBlocking {
            val feature = SearchRemoteFeature.create { }

            assertThat(feature.dispatch("nope", "x")).isNull()
        }
}
