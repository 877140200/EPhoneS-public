package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.entity.AiResponseVersionEntity
import com.susking.ephone_s.aidata.data.local.entity.toEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.FavoriteMessageRepository
import com.susking.ephone_s.aidata.util.ImageFileHelper
import com.susking.ephone_s.core.util.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 聊天记录 Repository 实现
 * 负责管理聊天消息的完整生命周期，包括图片文件处理和收藏状态
 */
class ChatRepositoryImpl(
    private val database: AiDataDatabase,
    private val context: Context,
    private val favoriteMessageRepository: FavoriteMessageRepository,
    private val personProfileRepository: com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository,
    private val activeContactTracker: com.susking.ephone_s.aidata.domain.tracker.ActiveContactTracker?,
    private val scheduledGreetingService: com.susking.ephone_s.aidata.service.ScheduledGreetingService? = null
) : ChatRepository {
    
    private companion object {
        private const val TAG = "ChatRepository"
    }
    
    private val chatDao = database.chatMessageDao()
    private val versionDao = database.aiResponseVersionDao()

    override fun getMessagesForContact(contactId: String): Flow<List<ChatMessage>> {
        val messagesFlow = chatDao.getMessagesForContact(contactId)
        val favoriteIdsFlow = favoriteMessageRepository.getFavoriteMessageIdsForContact(contactId)
        
        return messagesFlow.combine(favoriteIdsFlow) { messagesWithVersions, favoriteIds ->
            mapMessagesWithVersions(messagesWithVersions, favoriteIds.toSet())
        }
    }

    override fun getLatestMessagesPagedFlow(contactId: String, limit: Int): Flow<List<ChatMessage>> {
        val messagesFlow = chatDao.getLatestMessagesPagedFlow(contactId, limit)
        val favoriteIdsFlow = favoriteMessageRepository.getFavoriteMessageIdsForContact(contactId)

        return messagesFlow.combine(favoriteIdsFlow) { messagesWithVersions, favoriteIds ->
            mapMessagesWithVersions(messagesWithVersions, favoriteIds.toSet()).reversed()
        }
    }

    override suspend fun getMessagesForContactNonFlow(contactId: String): List<ChatMessage> {
        return chatDao.getMessagesForContactNonFlow(contactId).map { it.chatMessage.toDomainModel() }
    }

    override suspend fun getMessagesPaged(contactId: String, limit: Int, offset: Int): List<ChatMessage> {
        val messagesWithVersions = chatDao.getMessagesPaged(contactId, limit, offset)
        val favoriteIds = favoriteMessageRepository.getFavoriteMessageIdsForContactNonFlow(contactId).toSet()
        
        return mapMessagesWithVersions(messagesWithVersions, favoriteIds).reversed() // 因为DAO层是DESC排序，这里反转以保持UI的ASC顺序
    }

    private fun mapMessagesWithVersions(
        messagesWithVersions: List<com.susking.ephone_s.aidata.data.local.dao.ChatMessageWithVersions>,
        favoriteIds: Set<String>
    ): List<ChatMessage> {
        return messagesWithVersions.map { messageWithVersions ->
            val versions: List<String> = messageWithVersions.versions.map { it.versionContent }
            val domainModel: ChatMessage = messageWithVersions.chatMessage.toDomainModel()
            domainModel.copy(
                aiResponseVersions = versions,
                isFavorited = domainModel.id in favoriteIds
            )
        }
    }

    override suspend fun insertMessage(message: ChatMessage) {
        // 创建一个消息的可变副本以进行修改。通过常规发送链路产生的用户新消息默认未被 AI 看见。
        var messageToSave: ChatMessage = message.copy(
            hasBeenSeenByAi = message.role != "user"
        )
        
        // 检查 imageUrl 是否为需要存储的 Base64 字符串
        message.imageUrl?.let {
            // 判断它是否是 Base64（简单检查）
            if (it.startsWith("data:image")) {
                // 保存 Base64 为文件并获取路径
                val imagePath = ImageFileHelper.saveImageFromBase64(context, it)
                if (imagePath != null) {
                    // 如果成功，更新副本的 imageUrl 为文件路径，同时保留自动回复可见性标记。
                    messageToSave = messageToSave.copy(imageUrl = imagePath)
                    Log.d(TAG, "Image Base64 converted to file at: $imagePath")
                } else {
                    Log.e(TAG, "Failed to save Base64 image to file.")
                    // 可选：在这里处理保存失败的逻辑，例如不保存图片，同时保留自动回复可见性标记。
                    messageToSave = messageToSave.copy(imageUrl = null)
                }
            }
        }
        
        // 【修复】保存版本历史,截断base64防止数据库行过大
        if (messageToSave.aiResponseVersions.isNotEmpty()) {
            val versionEntities = messageToSave.aiResponseVersions.map { versionContent ->
                AiResponseVersionEntity(
                    chatMessageId = messageToSave.id,
                    versionContent = truncateBase64InContent(versionContent)
                )
            }
            chatDao.insertMessageWithVersions(messageToSave.toEntity(), versionEntities)
            Log.d(TAG, "Saved message with ${versionEntities.size} versions for ${messageToSave.contactId}")
        } else {
            chatDao.insertMessage(messageToSave.toEntity())
            Log.d(TAG, "Saved a new message for ${messageToSave.contactId} to the database.")
        }
        
        // 【新增】检查是否需要生成节日祝福
        scheduledGreetingService?.let { service ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "---------- 触发节日祝福检查 ----------")
                    Log.d(TAG, "【触发检查】联系人ID: ${messageToSave.contactId}")
                    Log.d(TAG, "【触发检查】消息角色: ${messageToSave.role}")
                    
                    // 获取该联系人的消息总数
                    val totalCount = chatDao.getMessageCountForContact(messageToSave.contactId)
                    Log.d(TAG, "【触发检查】消息总数: $totalCount")
                    
                    if (totalCount >= 200) {
                        Log.d(TAG, "【触发检查】✓ 消息数达标，开始检查节日祝福")
                    } else {
                        Log.d(TAG, "【触发检查】消息数未达200层，跳过检查")
                    }
                    
                    // 检查并预请求节日祝福
                    service.checkAndPrepareGreeting(messageToSave.contactId, totalCount)
                    Log.d(TAG, "--------------------------------------")
                } catch (e: Exception) {
                    Log.e(TAG, "【触发检查】✗ 节日祝福检查失败: ${e.message}", e)
                }
            }
        } ?: run {
            Log.d(TAG, "【触发检查】⚠ ScheduledGreetingService未注入，跳过节日祝福检查")
        }
        
        // 【修复未读计数】如果是AI发送的消息(role == "assistant"),增加联系人的未读计数
        // 但如果用户当前正在查看该联系人的聊天界面,则不增加未读计数
        if (messageToSave.role == "assistant") {
            try {
                // 检查用户是否正在查看该联系人的聊天界面
                val isViewingChat = activeContactTracker?.isActiveContact(messageToSave.contactId) ?: false
                
                val contact = personProfileRepository.getPersonProfileById(messageToSave.contactId)
                contact?.let {
                    val updatedContact = if (!isViewingChat) {
                        // 用户不在该聊天界面,增加未读计数
                        it.copy(
                            unreadMessageCount = it.unreadMessageCount + 1,
                            isHiddenFromChatList = false  // 接收消息时自动取消隐藏
                        )
                    } else {
                        // 用户正在查看聊天,只取消隐藏,不增加未读计数
                        it.copy(isHiddenFromChatList = false)
                    }
                    
                    personProfileRepository.updatePersonProfile(updatedContact)
                    if (!isViewingChat) {
                        Log.d(TAG, "Updated unread count for contact ${it.remarkName}: ${updatedContact.unreadMessageCount}")
                    }
                    Log.d(TAG, "Unhidden contact ${it.remarkName} from chat list")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update contact ${messageToSave.contactId}", e)
            }
        }
    }

    override suspend fun insertMessageWithVersions(message: ChatMessage, versions: List<String>) {
        val versionEntities = versions.map { versionContent ->
            AiResponseVersionEntity(
                chatMessageId = message.id,
                versionContent = truncateBase64InContent(versionContent)
            )
        }
        chatDao.insertMessageWithVersions(message.toEntity(), versionEntities)
    }
    
    /**
     * 截断内容中的base64图片数据,只保留前50位
     * 防止数据库行过大导致SQLiteBlobTooBigException
     */
    private fun truncateBase64InContent(content: String): String {
        // 匹配 data:image/...;base64, 后面的base64数据
        val base64Pattern = Regex("""(data:image/[^;]+;base64,)([A-Za-z0-9+/=]{50})[A-Za-z0-9+/=]+""")
        return base64Pattern.replace(content) { matchResult ->
            "${matchResult.groupValues[1]}${matchResult.groupValues[2]}...[已截断]"
        }
    }

    override suspend fun updateMessage(message: ChatMessage) {
        var messageToUpdate = message
        // 检查并处理 Base64 图片
        message.imageUrl?.let {
            if (it.startsWith("data:image")) {
                val imagePath = ImageFileHelper.saveImageFromBase64(context, it)
                if (imagePath != null) {
                    messageToUpdate = message.copy(imageUrl = imagePath)
                    Log.d(TAG, "Update: Converted Base64 image to file at: $imagePath")
                } else {
                    Log.e(TAG, "Update: Failed to save Base64 image to file for message ${message.id}")
                    messageToUpdate = message.copy(imageUrl = null)
                }
            }
        }
        
        database.withTransaction {
            chatDao.updateMessage(messageToUpdate.toEntity())
            val versions = messageToUpdate.aiResponseVersions.map {
                AiResponseVersionEntity(chatMessageId = messageToUpdate.id, versionContent = truncateBase64InContent(it))
            }
            chatDao.deleteVersionsForMessage(messageToUpdate.id)
            if (versions.isNotEmpty()) {
                chatDao.insertAllVersions(versions)
            }
        }
        Log.d(TAG, "Updated message with ID: ${messageToUpdate.id} in the database using a transaction.")
    }

    override suspend fun deleteMessage(message: ChatMessage) {
        // 在删除数据库条目之前，先删除关联的图片文件
        message.imageUrl?.let {
            // 假设 imageUrl 现在是文件路径
            if (!it.startsWith("data:image")) { // 确保它不是旧的base64数据
                ImageFileHelper.deleteImage(it)
                Log.d(TAG, "Deleted associated image file: $it")
            }
        }
        chatDao.deleteMessage(message.toEntity())
        Log.d(TAG, "Deleted message with ID: ${message.id} from the database.")
    }

    override suspend fun deleteMessagesForContact(contactId: String) {
        chatDao.deleteMessagesForContact(contactId)
    }

    override suspend fun deleteMessagesByActionId(actionId: String) {
        chatDao.deleteMessagesByActionId(actionId)
    }

    override suspend fun clearAll() {
        chatDao.clearAll()
    }

    override suspend fun getMessageByTimestamp(contactId: String, timestamp: Long): ChatMessage? {
        return chatDao.getMessageByTimestamp(contactId, timestamp)?.chatMessage?.toDomainModel()
    }

    override suspend fun updateMessageStatus(messageId: String, status: String) {
        chatDao.updateMessageStatus(messageId, status)
    }

    override suspend fun updateMessageNotes(messageId: String, notes: String) {
        chatDao.updateMessageNotes(messageId, notes)
    }

    override suspend fun getAllMessages(): List<ChatMessage> {
        return chatDao.getAllMessagesWithVersionsNonFlow().map { it.chatMessage.toDomainModel() }
    }

    override suspend fun insertMessages(messages: List<ChatMessage>) {
        messages.forEach { message ->
            // 批量导入链路保留消息自身标记；旧导入数据因模型默认值为 true，不会触发历史重复回复。
            val messageToInsert: ChatMessage = message.copy(
                hasBeenSeenByAi = if (message.role == "user") message.hasBeenSeenByAi else true
            )
            val versionEntities = messageToInsert.aiResponseVersions.map { versionContent ->
                AiResponseVersionEntity(
                    chatMessageId = messageToInsert.id,
                    versionContent = truncateBase64InContent(versionContent)
                )
            }
            chatDao.insertMessageWithVersions(messageToInsert.toEntity(), versionEntities)
        }
    }

    override suspend fun getUnseenUserMessagesForContact(contactId: String): List<ChatMessage> {
        return chatDao.getUnseenUserMessagesForContact(contactId).map { entity -> entity.toDomainModel() }
    }

    override suspend fun hasUnseenUserMessagesForContact(contactId: String): Boolean {
        return chatDao.getUnseenUserMessageCountForContact(contactId) > 0
    }

    override suspend fun markMessagesAsSeenByAi(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        chatDao.markMessagesAsSeenByAi(messageIds)
    }
    
    override suspend fun deleteMessages(messages: List<ChatMessage>) {
        // 遍历消息列表，删除每个消息关联的图片
        messages.forEach { message ->
            message.imageUrl?.let {
                if (!it.startsWith("data:image")) {
                    ImageFileHelper.deleteImage(it)
                    Log.d(TAG, "Deleted associated image file for message ${message.id}: $it")
                }
            }
        }
        chatDao.deleteMessages(messages.map { it.toEntity() })
        Log.d(TAG, "Deleted ${messages.size} messages from the database.")
    }
    
    override suspend fun updateMessageStatusAndNotesByTimestamp(
        contactId: String,
        timestamp: Long,
        newStatus: String,
        notesSuffix: String
    ) {
        chatDao.updateMessageStatusAndNotesByTimestamp(contactId, timestamp, newStatus, notesSuffix)
        Log.d(TAG, "Updated message status by timestamp for contact ID: $contactId, timestamp: $timestamp to $newStatus")
    }
    
    /**
     * 联系人变化事件
     * 用于通知QQ消息列表刷新
     */
    object ContactsChangedEvent
    
    override suspend fun sendShoppingAccessRequest(contactId: String): Long {
        val timestamp = System.currentTimeMillis()
        val message = ChatMessage(
            id = "shopping_req_${contactId}_$timestamp",
            contactId = contactId,
            content = "申请查看购物app",
            role = "user",
            timestamp = timestamp,
            type = "shopping_access_request",
            status = "pending"
        )
        insertMessage(message)
        
        // 发送事件通知UI刷新消息列表
        // 使用通用事件对象，避免依赖qq模块
        EventBus.post(ContactsChangedEvent)
        
        Log.d(TAG, "Sent shopping access request to contact: $contactId")
        return timestamp
    }
    
    override suspend fun updateImageDescription(messageId: String, description: String?) {
        chatDao.updateImageDescription(messageId, description)
        Log.d(TAG, "Updated image description for message: $messageId")
    }

    override fun getDatesWithMessages(contactId: String): Flow<Set<Long>> {
        return chatDao.getTimestampsForContact(contactId).map { timestamps ->
            timestamps.map {
                // 将时间戳转换为当天的开始时间
                java.util.Calendar.getInstance().apply {
                    timeInMillis = it
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            }.toSet()
        }
    }
}