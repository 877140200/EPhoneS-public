package com.susking.ephone_s.aidata.domain.model.import_export

/**
 * 导入结果统计
 * 记录增量导入过程中各类数据的导入情况
 */
data class ImportResult(
    /** 联系人资料统计 */
    val personProfileStats: DataTypeStats = DataTypeStats(),
    
    /** 聊天消息统计 */
    val chatMessageStats: DataTypeStats = DataTypeStats(),
    
    /** 长期记忆统计 */
    val longTermMemoryStats: DataTypeStats = DataTypeStats(),
    
    /** 随笔统计 */
    val jottingStats: DataTypeStats = DataTypeStats(),
    
    /** 心声统计 */
    val heartbeatStats: DataTypeStats = DataTypeStats(),
    
    /** 世界书统计 */
    val worldBookStats: DataTypeStats = DataTypeStats(),
    
    /** 世界书条目统计 */
    val worldBookEntryStats: DataTypeStats = DataTypeStats(),
    
    /** 收藏消息统计 */
    val favoriteMessageStats: DataTypeStats = DataTypeStats(),
    
    /** 动态统计 */
    val feedStats: DataTypeStats = DataTypeStats(),
    
    /** 钱包交易统计 */
    val walletTransactionStats: DataTypeStats = DataTypeStats(),
    
    /** 预约统计 */
    val appointmentStats: DataTypeStats = DataTypeStats(),
    
    /** 表情分类统计 */
    val stickerCategoryStats: DataTypeStats = DataTypeStats(),
    
    /** 表情统计 */
    val stickerStats: DataTypeStats = DataTypeStats(),
    
    /** 联系人字段冲突详情 */
    val personProfileFieldConflicts: MutableMap<String, PersonProfileFieldConflictStats> = mutableMapOf()
) {
    /**
     * 单一数据类型的统计信息
     */
    data class DataTypeStats(
        /** 新增的数量 */
        var newCount: Int = 0,
        
        /** 冲突的数量 */
        var conflictCount: Int = 0,
        
        /** 冲突中保留现有的数量 */
        var conflictKeepExistingCount: Int = 0,
        
        /** 冲突中使用导入的数量 */
        var conflictUseImportCount: Int = 0,
        
        /** 内容完全相同跳过的数量 */
        var identicalSkippedCount: Int = 0
    ) {
        /**
         * 记录新增项
         */
        fun recordNew() {
            newCount++
        }
        
        /**
         * 记录冲突并保留现有
         */
        fun recordConflictKeepExisting() {
            conflictCount++
            conflictKeepExistingCount++
        }
        
        /**
         * 记录冲突并使用导入
         */
        fun recordConflictUseImport() {
            conflictCount++
            conflictUseImportCount++
        }
        
        /**
         * 记录内容相同跳过
         */
        fun recordIdenticalSkipped() {
            identicalSkippedCount++
        }
        
        /**
         * 判断是否有数据
         */
        fun hasData(): Boolean {
            return newCount > 0 || conflictCount > 0 || identicalSkippedCount > 0
        }
    }
    
    /**
     * 联系人字段冲突统计
     * 用于统计某个联系人有多少个字段发生了冲突
     */
    data class PersonProfileFieldConflictStats(
        /** 联系人ID */
        val contactId: String,
        
        /** 联系人真名 */
        val contactRealName: String,
        
        /** 冲突字段总数 */
        var totalConflictFields: Int = 0,
        
        /** 保留现有的字段数 */
        var keepExistingFields: Int = 0,
        
        /** 使用导入的字段数 */
        var useImportFields: Int = 0
    ) {
        /**
         * 记录字段冲突解决
         */
        fun recordFieldConflict(resolution: ConflictResolution) {
            totalConflictFields++
            when (resolution) {
                ConflictResolution.KEEP_EXISTING -> keepExistingFields++
                ConflictResolution.USE_IMPORT -> useImportFields++
            }
        }
    }
    
    /**
     * 格式化为可读的统计报告
     */
    fun formatReport(): String {
        val report = StringBuilder()
        report.appendLine("导入完成!")
        report.appendLine()
        
        // 联系人资料统计
        if (personProfileStats.hasData() || personProfileFieldConflicts.isNotEmpty()) {
            report.appendLine("联系人资料:")
            if (personProfileStats.newCount > 0) {
                report.appendLine("• 新增联系人: ${personProfileStats.newCount}个")
            }
            if (personProfileFieldConflicts.isNotEmpty()) {
                personProfileFieldConflicts.values.forEach { stats ->
                    report.appendLine("• 资料冲突: 联系人${stats.contactRealName}(${stats.contactId})有${stats.totalConflictFields}个字段冲突")
                    report.appendLine("  (保留现有${stats.keepExistingFields}个, 使用导入${stats.useImportFields}个)")
                }
            }
            if (personProfileStats.identicalSkippedCount > 0) {
                report.appendLine("• 内容相同: ${personProfileStats.identicalSkippedCount}个联系人")
            }
            report.appendLine()
        }
        
        // 聊天消息统计
        if (chatMessageStats.hasData()) {
            report.appendLine("聊天消息:")
            appendDataTypeStats(report, chatMessageStats, "条")
            report.appendLine()
        }
        
        // 长期记忆统计
        if (longTermMemoryStats.hasData()) {
            report.appendLine("长期记忆:")
            appendDataTypeStats(report, longTermMemoryStats, "条")
            report.appendLine()
        }
        
        // 随笔统计
        if (jottingStats.hasData()) {
            report.appendLine("随笔(Jotting):")
            appendDataTypeStats(report, jottingStats, "条")
            report.appendLine()
        }
        
        // 心声统计
        if (heartbeatStats.hasData()) {
            report.appendLine("心声(Heartbeat):")
            appendDataTypeStats(report, heartbeatStats, "条")
            report.appendLine()
        }
        
        // 世界书统计
        if (worldBookStats.hasData()) {
            report.appendLine("世界书:")
            appendDataTypeStats(report, worldBookStats, "个")
            report.appendLine()
        }
        
        // 世界书条目统计
        if (worldBookEntryStats.hasData()) {
            report.appendLine("世界书条目:")
            appendDataTypeStats(report, worldBookEntryStats, "个")
            report.appendLine()
        }
        
        // 收藏消息统计
        if (favoriteMessageStats.hasData()) {
            report.appendLine("收藏消息:")
            appendDataTypeStats(report, favoriteMessageStats, "条")
            report.appendLine()
        }
        
        // 动态统计
        if (feedStats.hasData()) {
            report.appendLine("动态:")
            appendDataTypeStats(report, feedStats, "条")
            report.appendLine()
        }
        
        // 钱包交易统计
        if (walletTransactionStats.hasData()) {
            report.appendLine("钱包交易:")
            appendDataTypeStats(report, walletTransactionStats, "笔")
            report.appendLine()
        }
        
        // 预约统计
        if (appointmentStats.hasData()) {
            report.appendLine("预约:")
            appendDataTypeStats(report, appointmentStats, "个")
            report.appendLine()
        }
        
        // 表情分类统计
        if (stickerCategoryStats.hasData()) {
            report.appendLine("表情分类:")
            appendDataTypeStats(report, stickerCategoryStats, "个")
            report.appendLine()
        }
        
        // 表情统计
        if (stickerStats.hasData()) {
            report.appendLine("表情:")
            appendDataTypeStats(report, stickerStats, "个")
            report.appendLine()
        }
        
        return report.toString()
    }
    
    /**
     * 辅助方法:格式化单个数据类型的统计信息
     */
    private fun appendDataTypeStats(report: StringBuilder, stats: DataTypeStats, unit: String) {
        if (stats.newCount > 0) {
            report.appendLine("• 新增: ${stats.newCount}${unit}")
        }
        if (stats.conflictCount > 0) {
            report.appendLine("• 冲突: ${stats.conflictCount}${unit}(保留现有${stats.conflictKeepExistingCount}${unit}, 使用导入${stats.conflictUseImportCount}${unit})")
        }
        if (stats.identicalSkippedCount > 0) {
            report.appendLine("• 内容相同: ${stats.identicalSkippedCount}${unit}")
        }
    }
}