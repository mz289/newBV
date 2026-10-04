package dev.frost819.newbv.danmaku.config

/**
 * 重复弹幕合并模式。
 *
 * @property displayName 弹幕设置菜单展示名
 */
enum class DanmakuMergeMode(
    val displayName: String,
) {
    /** 不合并。 */
    Off("关闭"),

    /** 精确合并：时间窗口内文本完全相同才合并。 */
    Exact("相同文本"),

    /**
     * 相似合并：在精确合并基础上，归一化（全角转半角/去尾标点/压缩空白）后
     * 相同或编辑距离足够近（相似度 >= 0.8）的弹幕也并入同簇。
     */
    Similar("相似文本"),
}
