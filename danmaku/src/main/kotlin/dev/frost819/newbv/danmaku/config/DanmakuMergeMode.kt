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
     * 相同或有序编辑距离、拼音、词频向量匹配的弹幕也并入同簇。
     */
    Similar("开启", 2),
    ;

    companion object {
        /** 相似文本（2）→ Similar，其余（含历史遗留编码 1，已在 Prefs.init 迁移为 2）→ Off。 */
        fun fromPreference(value: Int): DanmakuMergeMode = if (value == 2) Similar else Off
    }
}
