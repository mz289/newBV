package dev.frost819.newbv.core.log

/**
 * 日志格式定义。
 *
 * 统一 new BV 崩溃日志和手动日志的文件头格式。
 */
object LogFormat {
    /**
     * 崩溃日志头部。
     */
    fun crashHeader(
        appVersion: String,
        appVersionCode: Int,
        androidVersion: String,
        androidSdk: Int,
        device: String,
        model: String,
        manufacturer: String,
    ): String =
        buildString {
            appendLine("======== new BV Crash ========")
            appendLine("App Version: $appVersion ($appVersionCode)")
            appendLine("Android Version: $androidVersion ($androidSdk)")
            appendLine("Device: $device")
            appendLine("Model: $model")
            appendLine("Manufacturer: $manufacturer")
            appendLine("================================")
            appendLine("Recent application logs are included below from Logcat:")
        }
}
