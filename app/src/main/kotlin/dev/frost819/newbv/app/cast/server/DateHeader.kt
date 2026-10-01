package dev.frost819.newbv.app.cast.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** SSDP 响应要求的 RFC 1123 DATE 头。 */
object DateHeader {
    private val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    @Synchronized
    fun now(): String = format.format(Date())
}
