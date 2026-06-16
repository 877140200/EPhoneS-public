package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.ChatMessage

/**
 * 数据迁移 Repository 接口
 * 负责处理旧数据格式到新数据格式的迁移
 */
interface DataMigrationRepository {
    
    /**
     * 获取旧消息用于迁移
     * @return Map<联系人ID, 消息列表>
     */
    suspend fun getOldMessagesForMigration(): Map<String, List<ChatMessage>>
    
    /**
     * 迁移后删除旧消息
     */
    suspend fun deleteOldMessagesAfterMigration()
    
    /**
     * 将图片消息从 Base64 迁移到文件
     */
    suspend fun migrateImageMessagesToFiles()
}