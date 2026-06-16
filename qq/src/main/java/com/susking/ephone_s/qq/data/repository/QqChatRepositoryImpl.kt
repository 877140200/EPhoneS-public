package com.susking.ephone_s.qq.data.repository

import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.qq.domain.repository.QqChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * QQ聊天数据仓库实现
 * 
 * 桥接aidata模块的ChatRepository到qq模块
 * 实现依赖倒置和模块解耦
 */
class QqChatRepositoryImpl @Inject constructor(
    private val chatRepository: ChatRepository
) : QqChatRepository {
    
    override fun getMessagesForContact(contactId: String): Flow<List<ChatMessage>> {
        return chatRepository.getMessagesForContact(contactId)
    }
    
    override suspend fun insertMessage(message: ChatMessage) {
        chatRepository.insertMessage(message)
    }
    
    override suspend fun updateMessage(message: ChatMessage) {
        chatRepository.updateMessage(message)
    }
    
    override suspend fun deleteMessage(message: ChatMessage) {
        chatRepository.deleteMessage(message)
    }
    
    override suspend fun deleteMessagesByActionId(actionId: String) {
        chatRepository.deleteMessagesByActionId(actionId)
    }
    
    override suspend fun deleteMessagesForContact(contactId: String) {
        chatRepository.deleteMessagesForContact(contactId)
    }
}