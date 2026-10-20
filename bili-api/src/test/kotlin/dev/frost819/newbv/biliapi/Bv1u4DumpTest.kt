package dev.frost819.newbv.biliapi

import bilibili.community.service.dm.v1.DMGrpcKt
import bilibili.community.service.dm.v1.dmSegMobileReq
import bilibili.metadata.device.device
import bilibili.metadata.locale.locale
import bilibili.metadata.metadata
import bilibili.metadata.network.NetworkType
import bilibili.metadata.network.network
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import io.grpc.Metadata as GrpcMetadata

/** 导出 BV1u4HVzZEno（弹幕还原 緋色月下，cid 32812567027，3857s）全量弹幕为测试语料。 */
class Bv1u4DumpTest {
    private class MetaInterceptor(private val buvid: String) : ClientInterceptor {
        override fun <ReqT, RespT> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> =
            object : SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                override fun start(listener: Listener<RespT>, headers: GrpcMetadata) {
                    val accessKey = ""
                    headers.put(GrpcMetadata.Key.of("authorization", GrpcMetadata.ASCII_STRING_MARSHALLER), "identify_v1 $accessKey")
                    headers.put(GrpcMetadata.Key.of("x-bili-metadata-bin", GrpcMetadata.BINARY_BYTE_MARSHALLER), metadata {
                        this.accessKey = accessKey; mobiApp = "android_hd"; device = "Pad"; build = 1680000
                        channel = "master"; this.buvid = buvid; platform = "android"
                    }.toByteArray())
                    headers.put(GrpcMetadata.Key.of("x-bili-device-bin", GrpcMetadata.BINARY_BYTE_MARSHALLER), device {
                        appId = 1; mobiApp = "android_hd"; device = "Pad"; build = 1680000; channel = "master"; platform = "android"
                    }.toByteArray())
                    headers.put(GrpcMetadata.Key.of("x-bili-local-bin", GrpcMetadata.BINARY_BYTE_MARSHALLER), locale { timezone = "Asia/Shanghai" }.toByteArray())
                    headers.put(GrpcMetadata.Key.of("x-bili-network-bin", GrpcMetadata.BINARY_BYTE_MARSHALLER), network { type = NetworkType.WIFI }.toByteArray())
                    super.start(listener, headers)
                }
            }
    }

    @Test
    fun dump() {
        runBlocking {
            val channel = ManagedChannelBuilder.forAddress("grpc.biliapi.net", 443)
                .useTransportSecurity().maxInboundMessageSize(64 * 1024 * 1024).build()
            val stub = DMGrpcKt.DMCoroutineStub(channel).withInterceptors(MetaInterceptor("VVV${System.nanoTime()}"))
            val out = StringBuilder()
            out.append("{\"aid\":115309453248536,\"bvid\":\"BV1u4HVzZEno\",\"cid\":32812567027,\"entries\":[")
            var first = true
            var mode7 = 0
            var total = 0
            for (segment in 1L..11L) {
                val reply =
                    try {
                        stub.dmSegMobile(
                            dmSegMobileReq { pid = 115309453248536; oid = 32812567027; type = 1; segmentIndex = segment },
                        )
                    } catch (e: Exception) {
                        println("segment=$segment failed: $e")
                        break
                    }
                println("segment=$segment elems=${reply.elemsCount}")
                reply.elemsList.forEach { e ->
                    total++
                    if (e.mode != 7 || e.content.isBlank()) return@forEach
                    mode7++
                    val sb = StringBuilder(e.content.length + 16)
                    e.content.forEach { ch ->
                        when (ch) {
                            '"' -> sb.append("\\\"")
                            '\\' -> sb.append("\\\\")
                            '\n' -> sb.append("\\n")
                            '\r' -> sb.append("\\r")
                            '\t' -> sb.append("\\t")
                            else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                        }
                    }
                    val row =
                        "{\"id\":${e.id},\"t\":${e.progress / 1000.0},\"mode\":${e.mode},\"size\":${e.fontsize}," +
                            "\"color\":${e.color},\"level\":${e.weight},\"attr\":${e.attr},\"payload\":\"$sb\"}"
                    if (!first) out.append(",\n")
                    out.append(row)
                    first = false
                }
            }
            out.append("\n]}")
            val file = File("../danmaku-engine/src/test/resources/bv1u4_m7.json")
            file.parentFile.mkdirs()
            file.writeText(out.toString())
            println("RESULT total=$total mode7=$mode7 -> ${file.absolutePath} (${file.length()} bytes)")
            channel.shutdownNow()
        }
    }
}
