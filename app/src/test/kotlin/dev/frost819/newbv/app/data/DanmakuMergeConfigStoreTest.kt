package dev.frost819.newbv.app.data

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.danmaku.config.DanmakuCountMark
import dev.frost819.newbv.danmaku.config.DanmakuMergeConfig
import org.junit.jupiter.api.Test

class DanmakuMergeConfigStoreTest {
    @Test
    fun `旧百分比配置迁移且严格匹配不会额外启用其他算法`() {
        val strict = DanmakuMergeConfigStore.decode("""{"similarityPercent":100,"windowSeconds":42}""")
        assertThat(strict.windowSeconds).isEqualTo(42)
        assertThat(strict.editDistanceThreshold).isEqualTo(0)
        assertThat(strict.cosineThreshold).isEqualTo(101)
        assertThat(strict.recognizePinyin).isFalse()
        assertThat(DanmakuMergeConfigStore.decode("""{"similarityPercent":80}""").editDistanceThreshold).isEqualTo(5)
    }

    @Test
    fun `所有自定义字段保存后可恢复`() {
        val value =
            DanmakuMergeConfig(
                windowSeconds = 42,
                editDistanceThreshold = 8,
                cosineThreshold = 30,
                recognizePinyin = false,
                representativePercent = 20,
                preferFixedMode = true,
                trimWidth = false,
                trimSpace = false,
                trimEnding = false,
                crossMode = false,
                skipSubtitle = false,
                skipAdvanced = false,
                skipBottom = true,
                markPosition = DanmakuCountMark.Prefix,
                markThreshold = 8,
                enlarge = true,
                scrollThreshold = 640,
                dropThreshold = 75,
                filterBeforeMerge = true,
            )
        assertThat(DanmakuMergeConfigStore.decode(DanmakuMergeConfigStore.encode(value))).isEqualTo(value)
    }

    @Test
    fun `损坏或空配置恢复默认值`() {
        listOf("", "broken", "[]", "{}", "null").forEach {
            assertThat(DanmakuMergeConfigStore.decode(it)).isEqualTo(DanmakuMergeConfig())
        }
    }

    @Test
    fun `缺失未知和错误类型字段不影响其他参数`() {
        val result =
            DanmakuMergeConfigStore.decode(
                """{"windowSeconds":42,"trimWidth":false,"markPosition":"unknown","similarityPercent":{},"future":true}""",
            )
        assertThat(result).isEqualTo(DanmakuMergeConfig(windowSeconds = 42, trimWidth = false))
    }
}
