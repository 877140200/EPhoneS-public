package com.susking.ephone_s.aidata.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.domain.model.AiAction
import com.susking.ephone_s.aidata.domain.model.AiActionDeserializer
import com.susking.ephone_s.aidata.domain.model.AiActionListDeserializer
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.ActionRepository
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository

/**
 * ActionRepository 实现类
 * 为 ActionExecutor 提供所需的数据操作
 */
class ActionRepositoryImpl(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val shoppingAuthorizedAccountRepository: com.susking.ephone_s.aidata.domain.repository.ShoppingAuthorizedAccountRepository
) : ActionRepository {

    // Gson实例,配置了AiAction的自定义反序列化器
    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(AiAction::class.java, AiActionDeserializer())
            .registerTypeAdapter(object : TypeToken<List<AiAction>>() {}.type, AiActionListDeserializer())
            .create()
    }

    override fun getGsonInstance(): Gson = gson

    override suspend fun getPersonProfile(contactId: String): PersonProfile? {
        return personProfileRepository.getPersonProfileById(contactId)
    }

    override suspend fun updatePersonProfile(profile: PersonProfile) {
        personProfileRepository.updatePersonProfile(profile)
    }

    override suspend fun getMessageByTimestamp(contactId: String, timestamp: Long): ChatMessage? {
        return chatRepository.getMessageByTimestamp(contactId, timestamp)
    }

    override suspend fun updateMessageStatusByTimestamp(
        contactId: String,
        timestamp: Long,
        newStatus: String,
        statusSuffix: String
    ) {
        val message = chatRepository.getMessageByTimestamp(contactId, timestamp)
        if (message != null) {
            val updatedContent = (message.content ?: "") + statusSuffix
            val updatedMessage = message.copy(
                status = newStatus,
                content = updatedContent
            )
            chatRepository.updateMessage(updatedMessage)
        }
    }

    override suspend fun getNewFeedsCount(): Int {
        return settingsRepository.getNewFeedsCount()
    }

    override suspend fun setNewFeedsCount(count: Int) {
        settingsRepository.setNewFeedsCount(count)
    }
    
    override suspend fun updateLatestMessageStatusByType(
        contactId: String,
        messageType: String,
        newStatus: String
    ) {
        // 获取该联系人的所有消息
        val messages = chatRepository.getMessagesForContactNonFlow(contactId)
        // 找到最新的指定类型的消息
        val targetMessage = messages
            .filter { it.type == messageType }
            .maxByOrNull { it.timestamp }
        
        if (targetMessage != null) {
            val updatedMessage = targetMessage.copy(status = newStatus)
            chatRepository.updateMessage(updatedMessage)
        }
    }
    
    override suspend fun addShoppingAuthorizedAccount(contactId: String, note: String?) {
        shoppingAuthorizedAccountRepository.addAuthorizedAccount(contactId, note)
    }
}