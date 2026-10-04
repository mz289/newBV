package dev.frost819.newbv.danmaku.config

/**
 * 重复弹幕合并模式。
 *
 * @property displayName 弹幕设置菜单展示名
 */
enum class DanmakuMergeMode(
    val displayName: String,
    val preferenceValue: Int,
) {
    /** 不合并。 */
    Off("关闭", 0),

    /**
     * 相似合并：在精确合并基础上，归一化（全角转半角/去尾标点/压缩空白）后
     * 相同或编辑距离足够近（相似度 >= 0.8）的弹幕也并入同簇。
     */
    Similar("开启", 2),
    ;

    companion object {
        /** 旧版相同文本（1）和相似文本（2）均迁移到相似文本。 */
        fun fromPreference(value: Int): DanmakuMergeMode = if (value == 1 || value == 2) Similar else Off
    }
}
