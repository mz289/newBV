package dev.frost819.newbv.app.cast

/**
 * 投屏接收端对当前播放的快照描述。
 *
 * 由播放器 UI 层注册的 [CastPlaybackSession] 上报，供 AVTransport
 * `GetPositionInfo`/`GetTransportInfo` 与 Nirvana `GetPlayInfo` 返回给投屏端。
 */
data class CastPlaybackSnapshot(
    val state: CastTransportState = CastTransportState.STOPPED,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val epid: Int? = null,
    val seasonId: Int = 0,
    val roomId: Long = 0L,
    val title: String = "",
    val qualityId: Int = 0,
    val availableQuality: Map<Int, String> = emptyMap(),
    val danmakuEnabled: Boolean = false,
)

/** DLNA AVTransport 传输状态（Nirvana 播放状态在格式化时另行映射）。 */
enum class CastTransportState(val dlnaName: String) {
    STOPPED("STOPPED"),
    PLAYING("PLAYING"),
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),
    TRANSITIONING("TRANSITIONING"),
}

/**
 * 播放器对投屏控制命令的实现。
 *
 * 由当前播放界面（视频/直播/外部媒体）注册，HTTP 服务线程将
 * Play/Pause/Stop/Seek/SetSpeed/SwitchQuality/SetDanmakuSwitch 转发到此接口。
 */
interface CastPlaybackSession {
    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun setQuality(qualityId: Int)

    fun setDanmakuEnabled(enabled: Boolean)

    fun snapshot(): CastPlaybackSnapshot
}

/**
 * 当前投屏会话注册表。
 *
 * HTTP 服务与播放器界面解耦：界面注册/注销会话，
 * 服务按需读取当前会话执行控制命令或上报快照。
 */
object CastPlaybackSessionRegistry {
    @Volatile
    private var session: CastPlaybackSession? = null

    fun register(session: CastPlaybackSession) {
        this.session = session
    }

    fun unregister(session: CastPlaybackSession) {
        if (this.session === session) {
            this.session = null
        }
    }

    fun current(): CastPlaybackSession? = session

    fun pauseCurrent() {
        session?.pause()
    }
}
