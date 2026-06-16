package com.susking.ephone_s.aidata.domain.model.import_export

/**
 * 数据导入模式枚举
 *
 * 定义了三种导入策略:
 * - OVERWRITE: 覆盖导入(完全清空现有数据,导入新数据)
 * - INCREMENTAL_KEEP_EXISTING: 增量导入,遇到冲突保留现有数据
 * - INCREMENTAL_PREFER_IMPORT: 增量导入,遇到冲突使用导入数据
 */
enum class ImportMode {
    /**
     * 覆盖导入模式
     *
     * 行为:
     * - 清空所有现有数据
     * - 导入全部新数据
     * - 这是原有的默认行为
     */
    OVERWRITE,

    /**
     * 增量导入 - 保留现有数据模式
     *
     * 行为:
     * - 联系人冲突: 保留现有联系人,跳过导入
     * - 关联数据: 智能合并,ID冲突时保留现有记录
     * - 新数据: 直接添加
     */
    INCREMENTAL_KEEP_EXISTING,

    /**
     * 增量导入 - 优先导入数据模式
     *
     * 行为:
     * - 联系人冲突: 使用导入数据覆盖现有联系人
     * - 关联数据: 智能合并,ID冲突时使用导入记录
     * - 新数据: 直接添加
     */
    INCREMENTAL_PREFER_IMPORT;

    /**
     * 判断是否为增量导入模式
     */
    fun isIncremental(): Boolean {
        return this == INCREMENTAL_KEEP_EXISTING || this == INCREMENTAL_PREFER_IMPORT
    }
}