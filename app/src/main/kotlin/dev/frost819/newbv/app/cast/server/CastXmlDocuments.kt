package dev.frost819.newbv.app.cast.server

/**
 * UPnP 设备/服务描述 XML（description.xml 与各 SCPD）。
 *
 * 除标准 MediaRenderer 三服务（AVTransport/RenderingControl/ConnectionManager）外，
 * 还声明了 B 站官方客户端使用的私有服务 NirvanaControl。
 */
object CastXmlDocuments {
    const val SINK_PROTOCOL_INFO =
        "http-get:*:*:*," +
            "http-get:*:video/mp4:*," +
            "http-get:*:video/x-matroska:*," +
            "http-get:*:video/x-msvideo:*," +
            "http-get:*:video/x-flv:*," +
            "http-get:*:video/octet-stream:*," +
            "http-get:*:video/mpeg:*," +
            "http-get:*:video/quicktime:*," +
            "http-get:*:application/vnd.apple.mpegurl:*," +
            "http-get:*:application/octet-stream:*," +
            "http-get:*:application/x-mpegURL:*," +
            "http-get:*:application/dash+xml:*," +
            "http-get:*:audio/L16:DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
            "http-get:*:audio/L16:*," +
            "http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
            "http-get:*:audio/mp4:DLNA.ORG_PN=AAC_ISO;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
            "http-get:*:audio/aac:*," +
            "http-get:*:audio/aacp:*," +
            "http-get:*:audio/mpeg:*," +
            "http-get:*:audio/mp3:*," +
            "http-get:*:audio/mp4:*," +
            "http-get:*:audio/x-m4a:*," +
            "http-get:*:audio/flac:*," +
            "http-get:*:audio/x-flac:*," +
            "http-get:*:audio/wav:*," +
            "http-get:*:audio/x-wav:*," +
            "http-get:*:audio/ogg:*," +
            "http-get:*:audio/opus:*," +
            "http-get:*:audio/x-ms-wma:*," +
            "http-get:*:image/jpeg:*," +
            "http-get:*:image/png:*"

    /**
     * 设备描述，字段形态对齐官方云视听小电视（社区逆向验证）：
     * - `X_brandName/hostVersion/ottVersion/channelName/capability` 为官方
     *   电视端特征字段，手机端「必连」会据此识别；
     * - 无 URLBase，服务地址为相对路径（相对 description.xml 的 URL 解析）；
     * - 在官方双服务（AVTransport + NirvanaControl）之外附加
     *   RenderingControl/ConnectionManager，兼容通用 DLNA 控制点。
     */
    fun deviceDescription(
        host: String,
        uuid: String,
    ): String {
        return xml(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <root xmlns:dlna="urn:schemas-dlna-org:device-1-0" xmlns="urn:schemas-upnp-org:device-1-0">
              <specVersion>
                <major>1</major>
                <minor>0</minor>
              </specVersion>
              <device>
                <deviceType>${CastReceiverConfig.MEDIA_RENDERER_DEVICE_TYPE}</deviceType>
                <friendlyName>${CastReceiverConfig.DEVICE_NAME}</friendlyName>
                <manufacturer>${CastReceiverConfig.MANUFACTURER}</manufacturer>
                <manufacturerURL>https://bilibili.com/</manufacturerURL>
                <modelDescription>云视听小电视</modelDescription>
                <modelName>${CastReceiverConfig.MODEL_NAME}</modelName>
                <modelNumber>${CastReceiverConfig.MODEL_NUMBER}</modelNumber>
                <modelURL>https://app.bilibili.com/</modelURL>
                <serialNumber>${CastReceiverConfig.MODEL_NUMBER}</serialNumber>
                <UDN>uuid:$uuid</UDN>
                <X_brandName>${CastReceiverConfig.DEVICE_NAME}</X_brandName>
                <hostVersion>25</hostVersion>
                <ottVersion>${CastReceiverConfig.OTT_VERSION}</ottVersion>
                <channelName>master</channelName>
                <capability>255</capability>
                <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
                <dlna:X_DLNACAP xmlns:dlna="urn:schemas-dlna-org:device-1-0">playcontainer-1-0</dlna:X_DLNACAP>
                <serviceList>
                  <service>
                    <serviceType>${CastReceiverConfig.AV_TRANSPORT_SERVICE_TYPE}</serviceType>
                    <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                    <SCPDURL>/dlna/AVTransport.xml</SCPDURL>
                    <controlURL>/AVTransport/action</controlURL>
                    <eventSubURL>/AVTransport/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>${CastReceiverConfig.NIRVANA_SERVICE_TYPE}</serviceType>
                    <serviceId>urn:app-bilibili-com:serviceId:NirvanaControl</serviceId>
                    <SCPDURL>/dlna/NirvanaControl.xml</SCPDURL>
                    <controlURL>/NirvanaControl/action</controlURL>
                    <eventSubURL>/NirvanaControl/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>${CastReceiverConfig.RENDERING_CONTROL_SERVICE_TYPE}</serviceType>
                    <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                    <SCPDURL>/RenderingControl.xml</SCPDURL>
                    <controlURL>/RenderingControl/control</controlURL>
                    <eventSubURL>/RenderingControl/event</eventSubURL>
                  </service>
                  <service>
                    <serviceType>${CastReceiverConfig.CONNECTION_MANAGER_SERVICE_TYPE}</serviceType>
                    <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                    <SCPDURL>/ConnectionManager.xml</SCPDURL>
                    <controlURL>/ConnectionManager/control</controlURL>
                    <eventSubURL>/ConnectionManager/event</eventSubURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """
        )
    }

    fun avTransportScpd(): String =
        serviceScpd(
            actions =
                listOf(
                    action("SetAVTransportURI", "InstanceID", "CurrentURI", "CurrentURIMetaData"),
                    action("Play", "InstanceID", "Speed"),
                    action("Pause", "InstanceID"),
                    action("Stop", "InstanceID"),
                    action("Seek", "InstanceID", "Unit", "Target"),
                    actionSpec(
                        "GetTransportInfo",
                        listOf(
                            inArg("InstanceID"),
                            outArg("CurrentTransportState"),
                            outArg("CurrentTransportStatus"),
                            outArg("CurrentSpeed"),
                        ),
                    ),
                    actionSpec(
                        "GetPositionInfo",
                        listOf(
                            inArg("InstanceID"),
                            outArg("Track"),
                            outArg("TrackDuration"),
                            outArg("TrackMetaData"),
                            outArg("TrackURI"),
                            outArg("RelTime"),
                            outArg("AbsTime"),
                            outArg("RelCount"),
                            outArg("AbsCount"),
                        ),
                    ),
                    actionSpec(
                        "GetMediaInfo",
                        listOf(
                            inArg("InstanceID"),
                            outArg("NrTracks"),
                            outArg("MediaDuration"),
                            outArg("CurrentURI"),
                            outArg("CurrentURIMetaData"),
                            outArg("NextURI"),
                            outArg("NextURIMetaData"),
                            outArg("PlayMedium"),
                            outArg("RecordMedium"),
                            outArg("WriteStatus"),
                        ),
                    ),
                    actionSpec(
                        "GetCurrentTransportActions",
                        listOf(inArg("InstanceID"), outArg("Actions")),
                    ),
                    actionSpec(
                        "GetDeviceCapabilities",
                        listOf(
                            inArg("InstanceID"),
                            outArg("PlayMedia"),
                            outArg("RecMedia"),
                            outArg("RecQualityModes"),
                        ),
                    ),
                    actionSpec(
                        "GetTransportSettings",
                        listOf(inArg("InstanceID"), outArg("PlayMode"), outArg("RecQualityMode")),
                    ),
                ),
        )

    fun renderingControlScpd(): String =
        serviceScpd(
            actions =
                listOf(
                    action("GetMute", "InstanceID", "Channel"),
                    action("SetMute", "InstanceID", "Channel", "DesiredMute"),
                    action("GetVolume", "InstanceID", "Channel"),
                    action("SetVolume", "InstanceID", "Channel", "DesiredVolume"),
                ),
        )

    fun connectionManagerScpd(): String =
        serviceScpd(
            actions =
                listOf(
                    action("GetProtocolInfo"),
                    actionSpec(
                        "PrepareForConnection",
                        listOf(
                            inArg("RemoteProtocolInfo"),
                            inArg("PeerConnectionManager"),
                            inArg("PeerConnectionID"),
                            inArg("Direction"),
                            outArg("ConnectionID"),
                            outArg("AVTransportID"),
                            outArg("RcsID"),
                        ),
                    ),
                    action("GetCurrentConnectionIDs"),
                    action("GetCurrentConnectionInfo", "ConnectionID"),
                ),
        )

    fun nirvanaControlScpd(): String =
        serviceScpd(
            actions =
                listOf(
                    actionSpec(
                        "GetAppInfo",
                        listOf(
                            outArg("PackageName"),
                            outArg("AppKey"),
                            outArg("Signature"),
                            outArg("CurrentSignedIn"),
                        ),
                    ),
                    action("LoginWithCode", "Code"),
                    actionSpec(
                        "PrepareForMirrorProjection",
                        listOf(outArg("ScreenWidth"), outArg("ScreenHeight"), outArg("PushUrl")),
                    ),
                    action("SetDanmakuSwitch", "DesiredSwitch"),
                    action(
                        "AppendDanmaku",
                        "Content",
                        "Size",
                        "Type",
                        "Color",
                        "DanmakuId",
                        "Action",
                    ),
                    actionSpec(
                        "GetPlayInfo",
                        listOf(inArg("Params"), outArg("Content")),
                    ),
                    actionSpec("GetAccountInfo", listOf(outArg("VipInfo"))),
                    action("SwitchQuality", "Qn"),
                    action("Play"),
                    action("Pause"),
                    action("Stop"),
                    action("Seek", "Target"),
                    action("SetSpeed", "Speed"),
                ),
        )

    private fun serviceScpd(actions: List<String>): String =
        xml(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <scpd xmlns="urn:schemas-upnp-org:service-1-0">
              <specVersion>
                <major>1</major>
                <minor>0</minor>
              </specVersion>
              <actionList>
                ${actions.joinToString(separator = "\n")}
              </actionList>
              <serviceStateTable>
                <stateVariable sendEvents="no">
                  <name>A_ARG_TYPE_InstanceID</name>
                  <dataType>ui4</dataType>
                </stateVariable>
                <stateVariable sendEvents="no">
                  <name>A_ARG_TYPE_String</name>
                  <dataType>string</dataType>
                </stateVariable>
              </serviceStateTable>
            </scpd>
            """
        )

    private fun action(
        name: String,
        vararg args: String,
    ): String = actionSpec(name, args.map { inArg(it) })

    private fun actionSpec(
        name: String,
        args: List<ScpdArg>,
    ): String {
        val argumentList =
            args.joinToString(separator = "\n") { arg ->
                """
                <argument>
                  <name>${arg.name}</name>
                  <direction>${arg.direction}</direction>
                  <relatedStateVariable>${arg.relatedStateVariable}</relatedStateVariable>
                </argument>
                """.trimIndent()
            }
        return """
            <action>
              <name>$name</name>
              <argumentList>
                $argumentList
              </argumentList>
            </action>
        """.trimIndent()
    }

    private fun inArg(name: String): ScpdArg = ScpdArg(name = name, direction = "in")

    private fun outArg(name: String): ScpdArg = ScpdArg(name = name, direction = "out")

    private data class ScpdArg(
        val name: String,
        val direction: String,
        val relatedStateVariable: String = "A_ARG_TYPE_String",
    )

    private fun xml(value: String): String =
        value.trimIndent().lineSequence().joinToString("\n") { it.trimEnd() }
}
