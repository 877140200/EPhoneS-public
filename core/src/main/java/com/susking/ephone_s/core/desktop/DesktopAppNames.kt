package com.susking.ephone_s.core.desktop

/**
 * 桌面应用名称的单一常量源。
 *
 * 主题模块的图标编辑列表与默认图标映射、桌面模块的图标读取都应引用这里，
 * 避免在多个文件里手写同一份应用名清单导致新增应用时漏改。
 */
object DesktopAppNames {

    const val QQ: String = "QQ"
    const val WORLD_BOOK: String = "世界书集"
    const val THEME: String = "主题"
    const val ALBUM: String = "相册"
    const val SHOPPING: String = "商城"
    const val ALIPAY: String = "支付宝"
    const val EVENT_GRAPH: String = "关系图"
    const val SCHEDULE: String = "课程表"
    const val PRESET: String = "预设"
    const val X: String = "X"
    const val SETTINGS: String = "设置"
    const val UNKNOWN: String = "???"
    const val CPHONE: String = "CPhone"
    const val TAVERN_RECORD: String = "酒馆记录"
    const val HEALTH: String = "健康"

    /**
     * 桌面要求展示的全部应用名称，顺序即主题编辑器中图标行的展示顺序。
     */
    val ALL: List<String> = listOf(
        QQ,
        WORLD_BOOK,
        THEME,
        ALBUM,
        SHOPPING,
        ALIPAY,
        EVENT_GRAPH,
        SCHEDULE,
        PRESET,
        X,
        SETTINGS,
        UNKNOWN,
        CPHONE,
        TAVERN_RECORD,
        HEALTH
    )
}
