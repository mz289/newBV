package dev.frost819.newbv.app.cast.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NvaExtDecoderTest {
    private val json = """{"aid":123,"cid":456,"title":"测试😀"}"""

    @Test
    fun `decodes standard and URL safe base64 with or without padding`() {
        val encrypted = encrypt(json.toByteArray())
        val encoders =
            listOf(
                Base64.getEncoder(),
                Base64.getEncoder().withoutPadding(),
                Base64.getUrlEncoder(),
                Base64.getUrlEncoder().withoutPadding(),
            )
        encoders.forEach { encoder ->
            assertThat(NvaExtDecoder.decode(encoder.encodeToString(encrypted))).isEqualTo(json)
        }
    }

    @Test
    fun `decodes raw deflated encrypted metadata`() {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        val compressed =
            try {
                ByteArrayOutputStream().use { output ->
                    DeflaterOutputStream(output, deflater).use { it.write(json.toByteArray()) }
                    output.toByteArray()
                }
            } finally {
                deflater.end()
            }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypt(compressed))
        assertThat(NvaExtDecoder.decode(encoded)).isEqualTo(json)
        val truncated = Base64.getUrlEncoder().encodeToString(encrypt(compressed.copyOf(compressed.size / 2)))
        assertThat(NvaExtDecoder.decode(truncated)).isNull()
    }

    @Test
    fun `preserves whitespace padding suffix and incomplete trailing group compatibility`() {
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(encrypt(json.toByteArray()))
        assertThat(encoded.length % 4).isEqualTo(0)
        val spaced = encoded.chunked(5).joinToString("\n\t\u2003")
        assertThat(NvaExtDecoder.decode("  $spaced=ignored suffix  ")).isEqualTo(json)
        assertThat(NvaExtDecoder.decode(encoded + "A")).isEqualTo(json)
        assertThat(NvaExtDecoder.decode(encoded + "!")).isNull()
    }

    @Test
    fun `keeps hex fallback and rejects malformed or non JSON metadata`() {
        val hex = encrypt(json.toByteArray()).joinToString("") { "%02x".format(it) }
        assertThat(NvaExtDecoder.decode(hex)).isEqualTo(json)
        assertThat(NvaExtDecoder.decode(hex.uppercase())).isEqualTo(json)
        listOf("", " \n ", "!invalid!", "0", "00", "not metadata").forEach {
            assertThat(NvaExtDecoder.decode(it)).isNull()
        }
        val encoded = Base64.getEncoder().encodeToString(encrypt("plain text".toByteArray()))
        assertThat(NvaExtDecoder.decode(encoded)).isNull()
    }

    private fun encrypt(bytes: ByteArray): ByteArray {
        val key = "1pzhA828t4i.6Oq@".toByteArray()
        return Cipher
            .getInstance("AES/CBC/PKCS5Padding")
            .apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
            }.doFinal(bytes)
    }
}
