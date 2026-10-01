package dev.frost819.newbv.app.cast.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * B 站官方客户端投屏元数据（`nva_ext` / `_nva_ext_` 字段）解码器。
 *
 * 官方客户端把视频身份 JSON（aid/cid/seekts/qn/speed/弹幕开关等）用
 * AES-128-CBC（密钥与 IV 同为固定 16 字节）加密，再经 raw deflate 压缩，
 * 以 base64url 或 hex 文本附在 DIDL 元数据的 `upnp:longDescription` 中。
 */
internal object NvaExtDecoder {
    private val aesKey = "1pzhA828t4i.6Oq@".toByteArray(Charsets.UTF_8)
    private val keySpec = SecretKeySpec(aesKey, "AES")
    private val ivSpec = IvParameterSpec(aesKey)

    fun decode(value: String): String? {
        val input = value.trim()
        if (input.isBlank()) return null
        return decodeBase64Deflated(input) ?: decodeHex(input)
    }

    private fun decodeBase64Deflated(input: String): String? =
        runCatching {
            val encrypted = decodeBase64Url(input) ?: return null
            val decrypted = decrypt(encrypted)
            val plain = inflateRaw(decrypted) ?: decrypted
            plain.toString(Charsets.UTF_8).trim().takeIf { it.startsWith("{") }
        }.getOrNull()

    private fun decodeHex(input: String): String? =
        runCatching {
            val encrypted = decodeHexBytes(input) ?: return null
            decrypt(encrypted).toString(Charsets.UTF_8).trim().takeIf { it.startsWith("{") }
        }.getOrNull()

    private fun decrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(bytes)
    }

    private fun inflateRaw(data: ByteArray): ByteArray? =
        runCatching {
            val inflater = Inflater(true)
            try {
                inflater.setInput(data)
                val output = ByteArrayOutputStream(data.size)
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count > 0) {
                        output.write(buffer, 0, count)
                    } else if (inflater.needsInput() || inflater.needsDictionary()) {
                        break
                    }
                }
                output.toByteArray().takeIf { it.isNotEmpty() && inflater.finished() }
            } finally {
                inflater.end()
            }
        }.getOrNull()

    private fun decodeHexBytes(input: String): ByteArray? {
        if (input.length % 2 != 0 || input.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(input.length / 2) { index ->
            val offset = index * 2
            ((input[offset].digitToInt(16) shl 4) or input[offset + 1].digitToInt(16)).toByte()
        }
    }

    private fun decodeBase64Url(input: String): ByteArray? {
        val output = ByteArrayOutputStream(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (char in input) {
            if (char == '=') break
            if (char.isWhitespace()) continue
            val value = base64Value(char) ?: return null
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xff)
            }
        }
        return output.toByteArray()
    }

    private fun base64Value(char: Char): Int? =
        when (char) {
            in 'A'..'Z' -> char - 'A'
            in 'a'..'z' -> char - 'a' + 26
            in '0'..'9' -> char - '0' + 52
            '+', '-' -> 62
            '/', '_' -> 63
            else -> null
        }
}
