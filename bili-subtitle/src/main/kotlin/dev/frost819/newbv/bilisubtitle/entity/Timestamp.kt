package dev.frost819.newbv.bilisubtitle.entity

data class Timestamp(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val milliSeconds: Int,
    var totalMills: Long = 0,
) {
    companion object {
        fun fromBccString(bccTime: Float): Timestamp {
            val mils = (bccTime * 1000).toInt()
            val hours = mils / (1000 * 60 * 60)
            val minutes = (mils % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (mils % (1000 * 60)) / (1000)
            val milliSeconds = mils % 1000
            return Timestamp(hours, minutes, seconds, milliSeconds)
        }
    }

    init {
        totalMills = hours * 60 * 60 * 1000L + minutes * 60 * 1000L + seconds * 1000L + milliSeconds
    }

    fun getBccTime(): Float = hours * 60 * 60 + minutes * 60 + seconds + milliSeconds / 1000f
}
