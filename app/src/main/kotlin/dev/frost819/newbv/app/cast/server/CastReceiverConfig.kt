package dev.frost819.newbv.app.cast.server

/**
 * 投屏接收端常量。
 *
 * 端口与协议形态对齐官方云视听小电视（社区逆向实现 ATV-Bilibili-demo 验证）：
 * - HTTP/必连通道同端口 9958：SSDP LOCATION 与设备描述指向它，
 *   手机端在 `SETUP /projection` 上完成 HTTP → NVA 二进制帧协议升级；
 * - `SERVER` 头伪装为 B 站电视端使用的 Platinum UPnP 栈，部分手机端
 *   会依据该特征识别官方设备；
 * - `urn:app-bilibili-com:service:NirvanaControl:3` 是官方私有控制服务。
 */
object CastReceiverConfig {
    const val HTTP_PORT = 9958
    const val SSDP_PORT = 1900
    const val SSDP_ADDRESS = "239.255.255.250"

    /** 必连（NVA Socket）升级端点：手机以 HTTP `SETUP` 方法请求该路径。 */
    const val NVA_PROJECTION_PATH = "/projection"
    const val NVA_USER_AGENT = "Linux/3.0.0 UPnP/1.0 Platinum/1.0.5.13"

    /** SSDP/HTTP 响应的 SERVER 头（官方电视端为 Platinum UPnP 栈）。 */
    const val SERVER_TOKEN = "Linux/3.0.0, UPnP/1.0, Platinum/1.0.5.13"
    const val SSDP_MAX_AGE_SECONDS = 30

    const val DEVICE_NAME = "newBV"
    const val MANUFACTURER = "Bilibili Inc."
    const val MODEL_NAME = "BRAVIA 4K 2015"
    const val MODEL_NUMBER = "1024"
    const val OTT_VERSION = "105500"

    /** B 站官方电视端（云视听小电视）包名，GetAppInfo 需上报该值才会被官方客户端信任。 */
    const val OFFICIAL_YST_PACKAGE_NAME = "com.xiaodianshi.tv.yst"
    const val LOG_FILE_NAME = "cast_receiver_requests.log"
    const val LOG_ROTATED_FILE_NAME = "cast_receiver_requests.old.log"
    const val MAX_LOG_BYTES = 512 * 1024L

    const val MEDIA_RENDERER_DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL_SERVICE_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CONNECTION_MANAGER_SERVICE_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
    const val NIRVANA_SERVICE_TYPE = "urn:app-bilibili-com:service:NirvanaControl:3"
}
