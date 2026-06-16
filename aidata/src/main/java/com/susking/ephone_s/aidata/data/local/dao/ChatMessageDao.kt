package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.AiResponseVersionEntity
import com.susking.ephone_s.aidata.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

data class ChatMessageWithVersions(
    @Embedded
    val chatMessage: ChatMessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "chatMessageId"
    )
    val versions: List<AiResponseVersionEntity>
)

/**
 * ChatMessage 表的数据访问对象 (DAO)。
 * 这个接口定义了所有与聊天消息相关的数据库操作。
 */
@Dao
interface ChatMessageDao {

    /**
     * 根据联系人 ID 获取其所有的聊天记录。
     * 使用 Flow 可以在数据变化时自动获得更新，非常适合在 UI 层观察数据。
     * @param contactId 目标联系人的 ID。
     * @return 一个 Flow，它会发出该联系人的所有聊天记录列表。
     */
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContact(contactId: String): Flow<List<ChatMessageWithVersions>>

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId AND timestamp >= :startTime AND timestamp < :endTime AND isHidden = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesInTimeRange(contactId: String, startTime: Long, endTime: Long): List<ChatMessageWithVersions>

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    suspend fun getMessagesForContactNonFlow(contactId: String): List<ChatMessageWithVersions>

    @Transaction
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesWithVersionsNonFlow(): List<ChatMessageWithVersions>

    /**
     * 【新增】分页加载指定联系人的聊天记录，按时间倒序。
     * @param contactId 目标联系人的 ID。
     * @param limit 每页加载的数量。
     * @param offset 偏移量。
     * @return 一个包含一页聊天记录的列表。
     */
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId ORDER BY timestamp DESC, rowid DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(contactId: String, limit: Int, offset: Int): List<ChatMessageWithVersions>

    /**
     * 监听指定联系人最新一页聊天记录，避免聊天界面进入时加载全部历史。
     * Room 会在对应联系人消息变动时重新发射这一页数据，用于保持新消息刷新能力。
     */
    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId ORDER BY timestamp DESC, rowid DESC LIMIT :limit")
    fun getLatestMessagesPagedFlow(contactId: String, limit: Int): Flow<List<ChatMessageWithVersions>>

    /**
     * 插入一条新的聊天消息。
     * 如果已存在相同 ID 的消息，则会替换它。
     * @param message 要插入的消息实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    suspend fun insertMessageWithVersions(message: ChatMessageEntity, versions: List<AiResponseVersionEntity>) {
        insertMessage(message)
        if (versions.isNotEmpty()) {
            insertAllVersions(versions)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVersions(versions: List<AiResponseVersionEntity>)

    /**
     * 插入一个聊天消息列表。
     * 这是为了一次性迁移或批量插入设计的。
     * @param messages 要插入的消息实体列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    /**
     * 更新一条已存在的聊天消息。
     * @param message 要更新的消息实体。
     */
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * 删除一条指定的聊天消息。
     * @param message 要删除的消息实体。
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    /**
     * 删除一个聊天消息列表。
     * @param messages 要删除的消息实体列表。
     */
    @Delete
    suspend fun deleteMessages(messages: List<ChatMessageEntity>)


    /**
     * 根据联系人 ID 删除其所有的聊天记录。
     * @param contactId 要删除其消息的目标联系人的 ID。
     */
    @Query("DELETE FROM chat_messages WHERE contactId = :contactId")
    suspend fun deleteMessagesForContact(contactId: String)

    @Query("DELETE FROM chat_messages WHERE actionId = :actionId")
    suspend fun deleteMessagesByActionId(actionId: String)

    /**
     * 【新增】清空所有聊天记录。
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
 
    /**
     * 根据聊天消息 ID 删除所有相关的 AI 响应版本。
     * @param chatMessageId 目标聊天消息的 ID。
     */
    @Query("DELETE FROM ai_response_versions WHERE chatMessageId = :chatMessageId")
    suspend fun deleteVersionsForMessage(chatMessageId: String)

    /**
     * 获取所有联系人的所有聊天记录。
     * 这个方法主要用于一次性数据迁移，不应该在常规操作中使用，因为它可能返回大量数据。
     * @return 包含数据库中所有消息的列表。
     */
    @Query("SELECT * FROM chat_messages")
    suspend fun getAllMessagesForMigration(): List<ChatMessageEntity>

    @Query("SELECT id FROM chat_messages WHERE imageUrl LIKE 'data:image%'")
    suspend fun getAllBase64ImageMessageIds(): List<String>

    @Query("UPDATE chat_messages SET imageUrl = :imageUrl WHERE id = :messageId")
    suspend fun updateMessageImageUrl(messageId: String, imageUrl: String?)

    @Query("SELECT LENGTH(imageUrl) FROM chat_messages WHERE id = :messageId")
    suspend fun getImageUrlLength(messageId: String): Int?

    @Query("SELECT SUBSTR(imageUrl, :offset, :size) FROM chat_messages WHERE id = :messageId")
    suspend fun getImageUrlChunk(messageId: String, offset: Int, size: Int): String?

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE chat_messages SET status = :newStatus, notes = notes || :notesSuffix WHERE contactId = :contactId AND timestamp = :timestamp")
    suspend fun updateMessageStatusAndNotesByTimestamp(contactId: String, timestamp: Long, newStatus: String, notesSuffix: String)

    @Query("UPDATE chat_messages SET notes = :notes WHERE id = :messageId")
    suspend fun updateMessageNotes(messageId: String, notes: String)
    
    /**
     * 更新消息的图片描述
     * 当AI分析完图片后，调用此方法保存描述
     * @param messageId 消息ID
     * @param description AI生成的图片描述
     */
    @Query("UPDATE chat_messages SET imageDescription = :description WHERE id = :messageId")
    suspend fun updateImageDescription(messageId: String, description: String?)

    @Transaction
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestamp(contactId: String, timestamp: Long): ChatMessageWithVersions?
    
    /**
     * 获取指定联系人的消息总数
     * 用于自动总结功能判断是否达到最低消息量
     * @param contactId 联系人ID
     * @return 消息总数
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE contactId = :contactId")
    suspend fun getMessageCountForContact(contactId: String): Int

    /**
     * 获取指定联系人在某个时间戳之后新增的消息总数。
     * 用于自动结构化事件提取，确保触发条件基于“上次提取后新增消息数”，而不是历史总消息数取模。
     * @param contactId 联系人ID
     * @param timestamp 上次成功提取结构化事件的时间戳
     * @return 指定时间戳之后的新增消息总数
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE contactId = :contactId AND timestamp > :timestamp")
    suspend fun getMessageCountAfterTimestamp(contactId: String, timestamp: Long): Int

    /**
     * 获取指定联系人所有消息的时间戳
     * 用于在日历上标记有消息的日期
     * @param contactId 联系人ID
     * @return 一个包含所有消息时间戳的Flow列表
     */
    @Query("SELECT timestamp FROM chat_messages WHERE contactId = :contactId")
    fun getTimestampsForContact(contactId: String): Flow<List<Long>>

    /**
     * 查询指定联系人尚未被 AI 看见的用户消息。
     */
    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId AND role = 'user' AND hasBeenSeenByAi = 0 ORDER BY timestamp ASC")
    suspend fun getUnseenUserMessagesForContact(contactId: String): List<ChatMessageEntity>

    /**
     * 查询指定联系人是否存在尚未被 AI 看见的用户消息。
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE contactId = :contactId AND role = 'user' AND hasBeenSeenByAi = 0")
    suspend fun getUnseenUserMessageCountForContact(contactId: String): Int

    /**
     * 将一组消息标记为已被 AI 看见。只更新冻结列表，避免误标请求期间的新消息。
     */
    @Query("UPDATE chat_messages SET hasBeenSeenByAi = 1 WHERE id IN (:messageIds)")
    suspend fun markMessagesAsSeenByAi(messageIds: List<String>)
}