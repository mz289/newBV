package dev.frost819.newbv.app.cast.server

/**
 * 投屏接收端常量。
 *
 * HTTP 端口 9837 与 SSDP 1900/239.255.255.250 需与 B 站官方客户端的
 * 探测约定一致；`urn:app-bilibili-com:service:NirvanaControl:3` 是官方
 * 客户端在 DLNA 之外使用的私有控制服务，用于进度/画质/弹幕同步。
 */
object CastReceiverConfig {
    const val HTTP_PORT = 9837
    const val SSDP_PORT = 1900
    const val SSDP_ADDRESS = "239.255.255.250"
    const val DEVICE_NAME = "newBV"
    const val MANUFACTURER = "Bilibili Inc."
    const val MODEL_NAME = "newBV Cast Receiver"
    const val MODEL_NUMBER = "1"

    /** B 站官方电视端（云视听小电视）包名，GetAppInfo 需上报该值才会被官方客户端信任。 */
    const val OFFICIAL_YST_PACKAGE_NAME = "com.xiaodianshi.tv.yst"
    const val LOG_FILE_NAME = "cast_receiver_requests.log"
    const val MAX_LOG_BYTES = 512 * 1024L

    const val MEDIA_RENDERER_DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL_SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CONNECTION_MANAGER_SERVICE_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
    const val NIRVANA_SERVICE_TYPE = "urn:app-bilibili-com:service:NirvanaControl:3"
}
