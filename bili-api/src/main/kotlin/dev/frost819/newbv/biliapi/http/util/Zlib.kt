package dev.frost819.newbv.biliapi.http.util

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater()
    return ByteArrayOutputStream().use { output ->
        try {
            inflater.setInput(this)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                // 数据截断时 inflate 会一直返回 0 且 needsInput 恒真，直接终止而非死循环
                if (inflater.needsInput()) break
                val count = inflater.inflate(buffer)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }
}

/**
 * Brotli 解压。
 *
 * B 站 WebSocket 直播弹幕协议 version=3 使用 brotli 压缩，
 * 解压后与 zlib 解压结果相同：一个或多个带头部的弹幕帧。
 */
fun ByteArray.brotliDecompress(): ByteArray =
    BrotliInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
