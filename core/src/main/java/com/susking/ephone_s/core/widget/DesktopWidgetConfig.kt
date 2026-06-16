package com.susking.ephone_s.core.widget

/**
 * 桌面卡片的位置与尺寸配置（课程表/时钟/天气共用）。
 *
 * 默认值取 SCHEDULE 类型的历史默认，保证无参构造时与旧版课程表行为一致；
 * 新增卡片应通过 [DesktopWidgetConfig.fromType] 取各自类型的默认配置。
 */
data class DesktopWidgetConfig(
    val leftMarginDp: Int = 18,
    val topMarginDp: Int = 22,
    val widthMode: String = "FULL",
    val heightMode: String = "NORMAL"
) {
    companion object {
        /**
         * 按卡片类型生成默认配置。
         */
        fun fromType(type: DesktopWidgetType): DesktopWidgetConfig {
            return DesktopWidgetConfig(
                leftMarginDp = type.defaultLeftMarginDp,
                topMarginDp = type.defaultTopMarginDp,
                widthMode = type.defaultWidthMode,
                heightMode = type.defaultHeightMode
            )
        }
    }
}
