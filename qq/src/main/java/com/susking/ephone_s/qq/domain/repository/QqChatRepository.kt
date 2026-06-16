package com.susking.ephone_s.qq.domain.repository

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * QQ聊天数据仓库接口
 * 
 * 定义QQ聊天相关的数据访问接口
 * 实现依赖倒置,domain层定义接口,data层提供实现
 */
interface QqChatRepository {
    
    /**
     * 获取指定联系人的聊天消息Flow
     */
    fun getMessagesForContact(contactId: String): Flow<List<ChatMessage>>
    
    /**
     * 插入消息
     */
    suspend fun insertMessage(message: ChatMessage)
    
    /**
     * 更新消息
     */
    suspend fun updateMessage(message: ChatMessage)
    
    /**
     * 删除消息
     */
    suspend fun deleteMessage(message: ChatMessage)
    
    /**
     * 根据actionId删除消息组
     */
    suspend fun deleteMessagesByActionId(actionId: String)
    
    /**
     * 删除联系人的所有消息
     */
    suspend fun deleteMessagesForContact(contactId: String)
}