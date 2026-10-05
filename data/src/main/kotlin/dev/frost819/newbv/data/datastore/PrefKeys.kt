package dev.frost819.newbv.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore 偏好设置键定义。
 *
 * 集中管理所有偏好项的 [Preferences.Key]。
 *
 * 键名沿用自原版 BV 的缩写（如 `dds2`、`prefer_enable_webmark`），应用包名不同、
 * 无跨应用兼容需求，但键名已随 v1.0.0 发布，不可再改名（会丢用户设置）。
 */
internal object PrefKeys {
    // ===== 账号 & 认证 =====
    val isLogin = booleanPreferencesKey("il")
    val uid = longPreferencesKey("uid")
    val sid = stringPreferencesKey("sid")
    val sessData = stringPreferencesKey("sd")
    val biliJct = stringPreferencesKey("bj")
    val uidCkMd5 = stringPreferencesKey("ucm")
    val tokenExpiredDate = longPreferencesKey("ted")
    val accessToken = stringPreferencesKey("access_token")
    val refreshToken = stringPreferencesKey("refresh_token")
    val buvid = stringPreferencesKey("random_buvid")
    val buvid3 = stringPreferencesKey("random_buvid3")
    val buvid3FromSpi = booleanPreferencesKey("buvid3_from_spi")
    val deviceCookies = stringPreferencesKey("device_cookies")
    val incognitoMode = booleanPreferencesKey("im")

    // ===== 网络 & API =====
    val apiType = intPreferencesKey("api_type")
    val crashReportEnabled = booleanPreferencesKey("crash_report_enabled")
    val autoSelectCdn = booleanPreferencesKey("auto_select_cdn")
    val enableCastReceiver = booleanPreferencesKey("enable_cast_receiver")
    val castReceiverUuid = stringPreferencesKey("cast_receiver_uuid")

    // ===== 更新 =====
    val acceptPrerelease = booleanPreferencesKey("accept_prerelease")

    // ===== 播放器 - 视频 =====
    val defaultQuality = intPreferencesKey("dq")
    val defaultVideoCodec = intPreferencesKey("dvc")
    val enableSoftwareVideoDecoder = booleanPreferencesKey("enable_software_video_decoder")
    val actionAfterPlay = intPreferencesKey("action_after_play")
    val playerCustomShortcuts = stringPreferencesKey("player_custom_shortcuts")

    // ===== 播放器 - 音频 =====
    val defaultAudio = intPreferencesKey("da")
    val enableFfmpegAudioRenderer = booleanPreferencesKey("enable_ffmpeg_audio_renderer")

    // ===== 播放器 - 弹幕 =====
    val defaultDanmakuEnabled = booleanPreferencesKey("dden")
    val defaultDanmakuTypes = stringPreferencesKey("ddts")
    val defaultDanmakuScale = floatPreferencesKey("dds2")
    val defaultDanmakuOpacity = floatPreferencesKey("ddo")
    val defaultDanmakuSpeedFactor = floatPreferencesKey("ddsf")
    val defaultDanmakuArea = floatPreferencesKey("dda")
    val defaultDanmakuMask = booleanPreferencesKey("prefer_enable_webmark")
    val danmakuBlockEnabled = booleanPreferencesKey("danmaku_block_enabled")
    val danmakuBlockRules = stringPreferencesKey("danmaku_block_rules")

    /** 合并模式固定编码：0=关闭，2=相似（历史编码 1 由 Prefs.init 一次性迁移为 2）。 */
    val danmakuMergeMode = intPreferencesKey("danmaku_merge_mode")
    val danmakuMergeConfig = stringPreferencesKey("danmaku_merge_config")

    // ===== 播放器 - 字幕 =====
    val defaultSubtitleFontSize = intPreferencesKey("dsfs")
    val defaultSubtitleBackgroundOpacity = floatPreferencesKey("dsbo")
    val defaultSubtitleBottomPadding = intPreferencesKey("dsbp")

    // ===== 播放器 - SponsorBlock =====
    val sponsorBlockEnabled = booleanPreferencesKey("sponsor_block_enabled")
    val sponsorBlockPolicies = stringPreferencesKey("sponsor_block_policies")

    // ===== 播放器 - 界面 =====
    val defaultPlaySpeed = intPreferencesKey("dps")
    val showVideoInfo = booleanPreferencesKey("show_video_info")
    val showPersistentSeek = booleanPreferencesKey("show_persistent_seek")
    val showPlayerDebugInfo = booleanPreferencesKey("show_player_debug_info")

    // ===== 应用界面 =====
    val density = floatPreferencesKey("density")

    /** 视频卡片宽度上限（dp），列数按屏宽自适应。 */
    val videoCardWidth = intPreferencesKey("video_card_width")

    /** 已废弃：旧"视频卡片列数"设置，仅在 [Prefs] 迁移为卡宽时读取。 */
    val videoColumns = intPreferencesKey("video_columns")
    val homeLeftNavItem = intPreferencesKey("home_left_nav")
    val firstHomeTopNavItem = intPreferencesKey("first_home_top_nav")
    val firstPersonalTopNavItem = intPreferencesKey("first_personal_top_nav")
    val showHotword = booleanPreferencesKey("shw")
    val themeMode = intPreferencesKey("theme_mode")
    val accentColor = stringPreferencesKey("accent_color")

    // ===== 存储设置 =====
    val cacheThreshold = intPreferencesKey("cache_threshold")
    val cacheAutoClean = booleanPreferencesKey("cache_auto_clean")
}
