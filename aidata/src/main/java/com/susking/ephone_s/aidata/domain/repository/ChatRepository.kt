package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 聊天记录 Repository 接口
 */
interface ChatRepository {
    
    /**
     * 获取指定联系人的所有聊天记录（Flow）
     */
    fun getMessagesForContact(contactId: String): Flow<List<ChatMessage>>
    
    /**
     * 获取指定联系人的所有聊天记录（非Flow）
     */
    suspend fun getMessagesForContactNonFlow(contactId: String): List<ChatMessage>
    
    /**
     * 分页获取聊天记录
     */
    suspend fun getMessagesPaged(contactId: String, limit: Int, offset: Int): List<ChatMessage>

    /**
     * 监听指定联系人最新一页聊天记录
     */
    fun getLatestMessagesPagedFlow(contactId: String, limit: Int): Flow<List<ChatMessage>>
    
    /**
     * 插入一条聊天消息
     */
    suspend fun insertMessage(message: ChatMessage)
    
    /**
     * 插入聊天消息及其版本
     */
    suspend fun insertMessageWithVersions(message: ChatMessage, versions: List<String>)
    
    /**
     * 更新聊天消息
     */
    suspend fun updateMessage(message: ChatMessage)
    
    /**
     * 删除聊天消息
     */
    suspend fun deleteMessage(message: ChatMessage)
    
    /**
     * 删除指定联系人的所有聊天记录
     */
    suspend fun deleteMessagesForContact(contactId: String)
    
    /**
     * 根据 actionId 删除消息
     */
    suspend fun deleteMessagesByActionId(actionId: String)
    
    /**
     * 清空所有聊天记录
     */
    suspend fun clearAll()
    
    /**
     * 根据时间戳获取消息
     */
    suspend fun getMessageByTimestamp(contactId: String, timestamp: Long): ChatMessage?
    
    /**
     * 更新消息状态
     */
    suspend fun updateMessageStatus(messageId: String, status: String)
    
    /**
     * 更新消息备注
     */
    suspend fun updateMessageNotes(messageId: String, notes: String)
    
    /**
     * 获取所有聊天记录（非Flow,用于导出）
     */
    suspend fun getAllMessages(): List<ChatMessage>
    
    /**
     * 批量插入消息（用于导入）
     */
    suspend fun insertMessages(messages: List<ChatMessage>)

    /**
     * 查询指定联系人尚未被 AI 看见的用户消息。
     */
    suspend fun getUnseenUserMessagesForContact(contactId: String): List<ChatMessage>

    /**
     * 查询指定联系人是否存在尚未被 AI 看见的用户消息。
     */
    suspend fun hasUnseenUserMessagesForContact(contactId: String): Boolean

    /**
     * 将冻结列表中的消息标记为已被 AI 看见。
     */
    suspend fun markMessagesAsSeenByAi(messageIds: List<String>)
    
    /**
     * 批量删除消息
     */
    suspend fun deleteMessages(messages: List<ChatMessage>)
    
    /**
     * 按时间戳更新消息状态和备注
     */
    suspend fun updateMessageStatusAndNotesByTimestamp(
        contactId: String,
        timestamp: Long,
        newStatus: String,
        notesSuffix: String
    )
    
    /**
     * 发送购物访问申请消息
     * @param contactId 联系人ID
     * @return 消息的timestamp
     */
    suspend fun sendShoppingAccessRequest(contactId: String): Long
    
    /**
     * 更新图片消息的描述
     * @param messageId 消息ID
     * @param description 图片描述
     */
    suspend fun updateImageDescription(messageId: String, description: String?)

    /**
     * 获取指定联系人有聊天记录的日期
     */
    fun getDatesWithMessages(contactId: String): Flow<Set<Long>>
}