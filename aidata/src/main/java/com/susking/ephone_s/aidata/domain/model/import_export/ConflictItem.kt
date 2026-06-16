package com.susking.ephone_s.aidata.domain.model.import_export

/**
 * 冲突类型枚举
 */
enum class ConflictType {
    /** 联系人资料字段冲突 */
    PERSON_PROFILE_FIELD,
    
    /** 聊天消息冲突 */
    CHAT_MESSAGE,
    
    /** 长期记忆冲突 */
    LONG_TERM_MEMORY,
    
    /** 随笔冲突 */
    JOTTING,
    
    /** 心声冲突 */
    HEARTBEAT,
    
    /** 世界书冲突 */
    WORLD_BOOK,
    
    /** 世界书条目冲突 */
    WORLD_BOOK_ENTRY,
    
    /** 收藏消息冲突 */
    FAVORITE_MESSAGE,
    
    /** 动态冲突 */
    FEED,
    
    /** 钱包交易冲突 */
    WALLET_TRANSACTION,
    
    /** 预约冲突 */
    APPOINTMENT,
    
    /** 表情分类冲突 */
    STICKER_CATEGORY,
    
    /** 表情冲突 */
    STICKER
}

/**
 * 冲突解决选项
 */
enum class ConflictResolution {
    /** 保留现有数据 */
    KEEP_EXISTING,
    
    /** 使用导入数据 */
    USE_IMPORT
}

/**
 * 冲突项数据类
 * 表示导入过程中发现的单个冲突
 */
sealed class ConflictItem {
    abstract val conflictType: ConflictType
    abstract val itemId: String
    
    /**
     * 联系人资料字段冲突
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param fieldName 冲突的字段名称(中文显示名)
     * @param fieldKey 冲突的字段键名(英文属性名)
     * @param existingValue 现有字段值
     * @param importValue 导入字段值
     * @param selectedFields 已经选择的字段Map(用于显示上下文)
     */
    data class PersonProfileFieldConflict(
        val contactId: String,
        val contactRealName: String,
        val fieldName: String,
        val fieldKey: String,
        val existingValue: Any?,
        val importValue: Any?,
        val selectedFields: Map<String, Any?> = emptyMap()
    ) : ConflictItem() {
        override val conflictType = ConflictType.PERSON_PROFILE_FIELD
        override val itemId = contactId
    }
    
    /**
     * 聊天消息冲突
     * @param messageId 消息ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 消息时间戳
     * @param existingMessage 现有消息的所有字段
     * @param importMessage 导入消息的所有字段
     */
    data class ChatMessageConflict(
        val messageId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingMessage: Map<String, Any?>,
        val importMessage: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.CHAT_MESSAGE
        override val itemId = messageId
    }
    
    /**
     * 长期记忆冲突
     * @param memoryId 记忆ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 记忆时间戳
     * @param existingMemory 现有记忆的所有字段
     * @param importMemory 导入记忆的所有字段
     */
    data class LongTermMemoryConflict(
        val memoryId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingMemory: Map<String, Any?>,
        val importMemory: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.LONG_TERM_MEMORY
        override val itemId = memoryId
    }
    
    /**
     * 随笔冲突
     * @param jottingId 随笔ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 随笔时间戳
     * @param existingJotting 现有随笔的所有字段
     * @param importJotting 导入随笔的所有字段
     */
    data class JottingConflict(
        val jottingId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingJotting: Map<String, Any?>,
        val importJotting: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.JOTTING
        override val itemId = jottingId
    }
    
    /**
     * 心声冲突
     * @param heartbeatId 心声ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 心声时间戳
     * @param existingHeartbeat 现有心声的所有字段
     * @param importHeartbeat 导入心声的所有字段
     */
    data class HeartbeatConflict(
        val heartbeatId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingHeartbeat: Map<String, Any?>,
        val importHeartbeat: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.HEARTBEAT
        override val itemId = heartbeatId
    }
    
    /**
     * 世界书冲突
     * @param worldBookId 世界书ID
     * @param timestamp 时间戳
     * @param existingWorldBook 现有世界书的所有字段
     * @param importWorldBook 导入世界书的所有字段
     */
    data class WorldBookConflict(
        val worldBookId: String,
        val timestamp: Long,
        val existingWorldBook: Map<String, Any?>,
        val importWorldBook: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.WORLD_BOOK
        override val itemId = worldBookId
    }
    
    /**
     * 世界书条目冲突
     * @param entryId 条目ID
     * @param worldBookId 所属世界书ID
     * @param timestamp 时间戳
     * @param existingEntry 现有条目的所有字段
     * @param importEntry 导入条目的所有字段
     */
    data class WorldBookEntryConflict(
        val entryId: String,
        val worldBookId: String,
        val timestamp: Long,
        val existingEntry: Map<String, Any?>,
        val importEntry: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.WORLD_BOOK_ENTRY
        override val itemId = entryId
    }
    
    /**
     * 收藏消息冲突
     * @param messageId 消息ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 时间戳
     * @param existingFavorite 现有收藏的所有字段
     * @param importFavorite 导入收藏的所有字段
     */
    data class FavoriteMessageConflict(
        val messageId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingFavorite: Map<String, Any?>,
        val importFavorite: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.FAVORITE_MESSAGE
        override val itemId = messageId
    }
    
    /**
     * 动态冲突
     * @param feedId 动态ID
     * @param contactId 联系人ID
     * @param contactRealName 联系人真名
     * @param timestamp 时间戳
     * @param existingFeed 现有动态的所有字段
     * @param importFeed 导入动态的所有字段
     */
    data class FeedConflict(
        val feedId: String,
        val contactId: String,
        val contactRealName: String,
        val timestamp: Long,
        val existingFeed: Map<String, Any?>,
        val importFeed: Map<String, Any?>
    ) : ConflictItem() {
        override val conflictType = ConflictType.FEED
        override val itemId = feedId
    }
}
