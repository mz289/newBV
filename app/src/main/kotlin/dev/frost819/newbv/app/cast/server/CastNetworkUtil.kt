package dev.frost819.newbv.app.cast.server

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** 局域网地址工具：SSDP LOCATION 与 description.xml 需要本机可达的 IPv4。 */
object CastNetworkUtil {
    fun localIpv4Address(): String =
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.hostAddress.orEmpty().startsWith("169.254.") }
            ?.hostAddress
            ?: "127.0.0.1"

    fun localIpv4Addresses(): List<String> =
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.hostAddress.orEmpty().startsWith("169.254.") }
            .mapNotNull { it.hostAddress }
            .toList()
            .ifEmpty { listOf("127.0.0.1") }

    /** 按「到目标地址的路由出口」选择本机地址，避免多网卡环境下 LOCATION 指向不可达网段。 */
    fun localIpv4AddressFor(remoteAddress: InetAddress): String =
        runCatching {
            DatagramSocket().use { socket ->
                socket.connect(remoteAddress, CastReceiverConfig.SSDP_PORT)
                (socket.localAddress as? Inet4Address)
                    ?.takeUnless { it.isLoopbackAddress }
                    ?.hostAddress
            }
        }.getOrNull()
            ?: localIpv4Address()
}
