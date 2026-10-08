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
                trimWidth = false,
                trimSpace = false,
                trimEnding = false,
                crossMode = false,
                skipSubtitle = false,
                skipBottom = true,
                markPosition = DanmakuCountMark.Prefix,
                scrollThreshold = 640,
                dropThreshold = 75,
            )
        assertThat(DanmakuMergeConfigStore.decode(DanmakuMergeConfigStore.encode(value))).isEqualTo(value)
    }

    @Test
    fun `删除的显示配置不会恢复或再次写入且保留有效设置`() {
        val config =
            DanmakuMergeConfigStore.decode(
                """{"windowSeconds":42,"markPosition":"Prefix","markThreshold":1000,"enlarge":true,"representativePercent":100,"preferFixedMode":true}""",
            )
        assertThat(config).isEqualTo(DanmakuMergeConfig(windowSeconds = 42, markPosition = DanmakuCountMark.Prefix))
        val encoded = DanmakuMergeConfigStore.encode(config)
        for (key in listOf("markThreshold", "enlarge", "representativePercent", "preferFixedMode")) {
            assertThat(encoded).doesNotContain(key)
        }
    }

    @Test
    fun `忽略旧版合并前屏蔽开关且不再保存该字段`() {
        for (value in listOf(true, false)) {
            val config = DanmakuMergeConfigStore.decode("""{"filterBeforeMerge":$value,"windowSeconds":42}""")
            assertThat(config.windowSeconds).isEqualTo(42)
            assertThat(DanmakuMergeConfigStore.encode(config)).doesNotContain("filterBeforeMerge")
        }
    }

    @Test
    fun `忽略旧版高级弹幕豁免且保留其他设置`() {
        for (value in listOf(true, false)) {
            val config =
                DanmakuMergeConfigStore.decode(
                    """{"skipAdvanced":$value,"skipSubtitle":false,"windowSeconds":42}""",
                )
            assertThat(config).isEqualTo(DanmakuMergeConfig(skipSubtitle = false, windowSeconds = 42))
            assertThat(DanmakuMergeConfigStore.encode(config)).doesNotContain("skipAdvanced")
        }
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
