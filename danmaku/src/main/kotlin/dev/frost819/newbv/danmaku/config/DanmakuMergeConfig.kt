package dev.frost819.newbv.danmaku.config

/** 相似文本合并参数；时间窗口从每组第一条弹幕起算。 */
data class DanmakuMergeConfig(
    val windowSeconds: Int = 20,
    /** 有序编辑预算（替换计 2，增删计 1）；0 禁用，短文本会自动收紧。 */
    val editDistanceThreshold: Int = 5,
    /** 2-Gram 词频向量余弦相似度的平方百分比；101 禁用，越低越宽松。 */
    val cosineThreshold: Int = 45,
    val recognizePinyin: Boolean = true,
    val trimWidth: Boolean = true,
    val trimSpace: Boolean = true,
    val trimEnding: Boolean = true,
    val crossMode: Boolean = true,
    val skipSubtitle: Boolean = true,
    val skipBottom: Boolean = false,
    val markPosition: DanmakuCountMark = DanmakuCountMark.Suffix,
    /** 实际文字宽度超过此像素数时，固定弹幕改为滚动；0 禁用。 */
    val scrollThreshold: Int = 0,
    /** 5 秒滑动窗口内的文字密度上限；0 禁用自动优选。 */
    val dropThreshold: Int = 0,
) {
    fun sanitized(): DanmakuMergeConfig =
        copy(
            windowSeconds = windowSeconds.coerceIn(1, 120),
            editDistanceThreshold = editDistanceThreshold.coerceIn(0, 20),
            cosineThreshold = cosineThreshold.coerceIn(0, 101),
            scrollThreshold = scrollThreshold.coerceIn(0, 9999),
            dropThreshold = dropThreshold.coerceIn(0, 9999),
        )
}

enum class DanmakuCountMark(
    val displayName: String,
) {
    Off("隐藏"),
    Prefix("显示在开头"),
    Suffix("显示在结尾"),
}
