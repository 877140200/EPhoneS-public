package com.susking.ephone_s.core.widget

// 宽高模式字符串常量下沉到文件顶层：Kotlin 枚举条目的初始化早于 companion object，
// 若枚举条目直接引用 companion 里的常量会触发「companion 未初始化」编译错误。
// 放到包级 const 后，枚举条目与 companion 可同时安全引用，且值保持不变。
private const val MODE_WIDTH_COMPACT: String = "COMPACT"
private const val MODE_WIDTH_FULL: String = "FULL"
private const val MODE_HEIGHT_NORMAL: String = "NORMAL"
private const val MODE_HEIGHT_LARGE: String = "LARGE"

/**
 * 桌面小组件类型。
 *
 * 作为「桌面卡片」拖拽与持久化的单一数据源：每种类型自带默认位置、默认宽高模式，
 * 以及不同宽高模式对应的网格跨度。新增卡片（如时钟、天气）只需在此登记，
 * 拖拽与存储逻辑无需重复编写。
 *
 * 下沉到 core 模块后，desktop（桌面拖拽）与 schedule（课表设置改尺寸）共用同一份定义，
 * 杜绝两端配置结构不一致导致的不同步问题。
 */
enum class DesktopWidgetType(
    val storageKey: String,
    val defaultLeftMarginDp: Int,
    val defaultTopMarginDp: Int,
    val defaultWidthMode: String,
    val defaultHeightMode: String
) {
    // 课程表：沿用历史默认值（18,22,FULL,NORMAL），保证老用户布局不变
    SCHEDULE("SCHEDULE", 18, 22, MODE_WIDTH_FULL, MODE_HEIGHT_NORMAL),

    // 时钟：紧凑卡片，默认放在课程表下方一段距离，避免初始重叠
    CLOCK("CLOCK", 18, 150, MODE_WIDTH_COMPACT, MODE_HEIGHT_NORMAL),

    // 天气：紧凑卡片，默认放在时钟右侧
    WEATHER("WEATHER", 200, 150, MODE_WIDTH_COMPACT, MODE_HEIGHT_NORMAL);

    /**
     * 根据宽度模式返回占用的网格列数。
     */
    fun resolveColumnSpan(widthMode: String): Int {
        return if (widthMode == WIDTH_COMPACT) COMPACT_COLUMN_SPAN else FULL_COLUMN_SPAN
    }

    /**
     * 根据高度模式返回占用的网格行数。
     */
    fun resolveRowSpan(heightMode: String): Int {
        return if (heightMode == HEIGHT_LARGE) LARGE_ROW_SPAN else NORMAL_ROW_SPAN
    }

    companion object {
        // 对外暴露的 public 常量，引用顶层私有常量保持单一来源，值不变
        const val WIDTH_COMPACT: String = MODE_WIDTH_COMPACT
        const val WIDTH_FULL: String = MODE_WIDTH_FULL
        const val HEIGHT_NORMAL: String = MODE_HEIGHT_NORMAL
        const val HEIGHT_LARGE: String = MODE_HEIGHT_LARGE

        // 紧凑卡片占 2 列，全宽卡片占 4 列（桌面网格共 4 列）
        const val COMPACT_COLUMN_SPAN: Int = 2
        const val FULL_COLUMN_SPAN: Int = 4

        // 普通高度占 1 行，加高占 2 行
        const val NORMAL_ROW_SPAN: Int = 1
        const val LARGE_ROW_SPAN: Int = 2
    }
}
