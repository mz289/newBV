package dev.frost819.newbv.biliapi.entity.danmaku

/**
 * 弹幕显示类型。
 *
 * 持久化、设置菜单和渲染过滤共用此模型。枚举顺序用于保存用户选择，不可重排。
 *
 * @property modeValue 对应的弹幕协议 mode 值，-1 表示显示全部。
 */
enum class DanmakuType(
    val modeValue: Int,
) {
    /**
     * 显示所有弹幕类型
     */
    All(-1),

    /**
     * 顶部居中弹幕
     */
    Top(5),

    /**
     * 滚动弹幕（从右向左移动）
     */
    Rolling(1),

    /**
     * 底部居中弹幕
     */
    Bottom(4),

    /**
     * 高级弹幕（mode 7，自定义位置与动画轨迹）
     *
     * 注意：追加在末尾，持久化按 ordinal 序号存储，插入中间会破坏已有用户设置
     */
    Advanced(7),
}
