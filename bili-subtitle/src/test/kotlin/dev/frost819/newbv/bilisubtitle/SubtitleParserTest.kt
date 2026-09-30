package dev.frost819.newbv.bilisubtitle

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [SubtitleParser] 的单元测试。
 *
 * 验证 BCC 和 SRT 格式字幕的解析逻辑，包括正常输入、空输入、
 * 多条目输入、异常 JSON 处理等边界情况。
 */
class SubtitleParserTest {
    @Test
    fun `parse bcc returns correct number of items`() {
        val bccJson =
            """
            {
                "font_size": 0.4,
                "font_color": "#FFFFFF",
                "background_alpha": 0.5,
                "background_color": "#9C27B0",
                "stroke": "none",
                "body": [
                    {"from": 0.5, "to": 2.0, "location": 2, "content": "Hello"},
                    {"from": 2.5, "to": 4.0, "location": 2, "content": "World"}
                ]
            }
            """.trimIndent()

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `parse bcc maps content correctly`() {
        val bccJson =
            """
            {"body": [{"from": 0.0, "to": 1.0, "content": "Test content"}]}
            """.trimIndent()

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result[0].content).isEqualTo("Test content")
    }

    @Test
    fun `parse bcc maps timestamps correctly`() {
        val bccJson =
            """
            {"body": [{"from": 1.5, "to": 3.5, "content": "X"}]}
            """.trimIndent()

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result[0].from.totalMills).isEqualTo(1500)
        assertThat(result[0].to.totalMills).isEqualTo(3500)
    }

    @Test
    fun `parse bcc with empty body returns empty list`() {
        val bccJson = """{"body": []}"""

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result).isEmpty()
    }

    @Test
    fun `parse bcc with invalid json returns empty list`() {
        val result = SubtitleParser.fromBccString("not valid json")

        assertThat(result).isEmpty()
    }

    @Test
    fun `parse bcc with null body returns empty list`() {
        val bccJson = """{"body": null}"""

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result).isEmpty()
    }

    @Test
    fun `parse bcc with missing body returns empty list`() {
        val bccJson = """{"font_size": 0.4}"""

        val result = SubtitleParser.fromBccString(bccJson)

        assertThat(result).isEmpty()
    }

    @Test
    fun `parse bcc from resource file`() {
        val fileContent = this::class.java.getResource("/example.bcc")?.readText()!!
        val result = SubtitleParser.fromBccString(fileContent)

        assertThat(result).isNotEmpty()
        assertThat(result[0].content).isNotEmpty()
    }
}
