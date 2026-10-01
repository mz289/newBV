package dev.frost819.newbv.biliapi.http.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.zip.Deflater

/**
 * [Zlib] 扩展函数的单元测试。
 *
 * 测试数据用 [Deflater] 现场构造，验证 zlib 解压缩行为。
 */
class ZlibTest {
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater().apply {
            setInput(data)
            finish()
        }
        val output = ByteArray(data.size * 4 + 16)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOfRange(0, length)
    }

    @Test
    fun `zlibDecompress restores deflated data`() {
        val original = "Hello, World! This is a test string.".toByteArray()
        val decompressed = compress(original).zlibDecompress()
        assertThat(decompressed).isEqualTo(original)
    }

    @Test
    fun `zlibDecompress returns empty for empty input`() {
        val original = ByteArray(0)
        val decompressed = compress(original).zlibDecompress()
        assertThat(decompressed).isEqualTo(original)
    }

    @Test
    fun `zlibDecompress handles large data`() {
        val original = (1..1000).joinToString("").toByteArray()
        val decompressed = compress(original).zlibDecompress()
        assertThat(decompressed).isEqualTo(original)
    }

    @Test
    fun `zlibDecompress handles binary data`() {
        val original = ByteArray(256) { it.toByte() }
        val decompressed = compress(original).zlibDecompress()
        assertThat(decompressed).isEqualTo(original)
    }

    @Test
    fun `zlibDecompress terminates on truncated data instead of looping forever`() {
        val full = compress((1..500).joinToString("").toByteArray())
        val truncated = full.copyOfRange(0, full.size / 2)
        val decompressed = assertDoesNotThrow { truncated.zlibDecompress() }
        assertThat(decompressed).isNotEmpty()
    }
}
