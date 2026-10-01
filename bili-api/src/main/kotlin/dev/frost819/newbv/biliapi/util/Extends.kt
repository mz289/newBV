package dev.frost819.newbv.biliapi.util

fun String.convertStringTimeToSeconds(): Int {
    // 部分稿件可能没有时长，Web 接口返回 NaN:NaN:NaN，App 接口返回空字符串
    if (startsWith("NaN") || isBlank()) return 0

    val parts = this.split(":")
    val hours = if (parts.size == 3) parts[0].toInt() else 0
    val minutes = parts[parts.size - 2].toInt()
    val seconds = parts[parts.size - 1].toInt()
    return (hours * 3600) + (minutes * 60) + seconds
}

fun Long.toBv(): String = AvBvConverter.av2bv(this)

fun String.toAv(): Long = AvBvConverter.bv2av(this)
