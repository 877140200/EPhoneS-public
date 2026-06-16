package com.susking.ephone_s.aidata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.susking.ephone_s.aidata.domain.model.PersonProfile

/**
 * PersonProfile 的 Room 实体
 * 用于在数据库中存储角色设定信息
 *
 * 注意: 使用的 List<String> 类型转换器已在 AiDataDatabase 中全局注册
 */
@Entity(tableName = "person_profiles")
data class PersonProfileEntity(
    @PrimaryKey
    val id: String,
    
    // === 基础标识 ===
    val remarkName: String,
    val realName: String,
    val persona: String,
    
    // === 关系信息 ===
    val nicknameForUser: String?,
    
    // === 个人档案 ===
    val signature: String?,
    val description: String?,
    val gender: String?,
    val age: Int?,
    val birthday: String?,
    val zodiacSign: String?,
    val location: String?,
    val companyOrSchool: String?,
    val profession: String?,
    val info_line_text: String?,
    
    // === 外观设置 ===
    val avatarUri: String?,
    val backgroundUri: String?,
    val chatBackgroundUri: String?,
    
    // === 状态信息 ===
    val status: String?,
    val statusText: String?,
    val isBusy: Boolean,
    
    // === 分组与显示 ===
    val group: String?,
    val isPinned: Boolean,
    val unreadMessageCount: Int,
    
    // === AI 行为配置 ===
    val offlineModeEnabled: Boolean,
    val ttsVoiceId: String?,
    val voiceDescription: String?,
    
    // NovelAI 设置
    val naiPromptSource: String,
    val naiPositivePrompt: String?,
    val naiNegativePrompt: String?,
    
    // 自动总结长期记忆
    val autoSummaryEnabled: Boolean,
    val summaryInterval: Int,
    val messagesSinceLastSummary: Int,
    val lastSummaryTimestamp: Long?,
    // 自动结构化提取连续失败次数（指数退避用）。新增列带 SQL 默认值 0，与实体 Kotlin 默认值一致，满足迁移与 schema 校验。
    @ColumnInfo(defaultValue = "0")
    val autoSummaryFailureCount: Int = 0,
    
    // 高级设置
    val injectThoughts: Boolean,
    val backgroundActivityEnabled: Boolean,
    val actionCooldownMinutes: Int,
    val lastBackgroundActionTimestamp: Long?,
    val timeAwarenessEnabled: Boolean,
    
    // 上下文管理
    val shortTermMemoryLimit: Int,
    val attachMemoryLimit: Int,
    val selectedPhotos: List<String>,
    
    // 隐私模式
    val privacyModeEnabled: Boolean,
    
    // 聊天列表显示控制
    val isHiddenFromChatList: Boolean,
    
    // 拉黑功能
    val isBlocked: Boolean,
    val blockTimestamp: Long?,
    val blockCooldownHours: Double,
    val applicationReason: String?,
    val isBlockedByContact: Boolean,
    
    // 群聊相关
    val isGroupChat: Boolean,
    val groupChatRole: String?,
    
    // 世界书绑定配置
    val onlineGlobalWorldBooks: List<Long>,
    val onlineLocalWorldBooks: List<Long>,
    val offlineGlobalWorldBooks: List<Long>,
    val offlineLocalWorldBooks: List<Long>
)

/**
 * 将 PersonProfileEntity 转换为领域模型 PersonProfile
 */
fun PersonProfileEntity.toDomainModel(): PersonProfile {
    return PersonProfile(
        id = id,
        remarkName = remarkName,
        realName = realName,
        persona = persona,
        nicknameForUser = nicknameForUser,
        signature = signature,
        description = description,
        gender = gender,
        age = age,
        birthday = birthday,
        zodiacSign = zodiacSign,
        location = location,
        companyOrSchool = companyOrSchool,
        profession = profession,
        info_line_text = info_line_text,
        avatarUri = avatarUri,
        backgroundUri = backgroundUri,
        chatBackgroundUri = chatBackgroundUri,
        status = status,
        statusText = statusText,
        isBusy = isBusy,
        group = group,
        isPinned = isPinned,
        unreadMessageCount = unreadMessageCount,
        offlineModeEnabled = offlineModeEnabled,
        ttsVoiceId = ttsVoiceId,
        voiceDescription = voiceDescription,
        naiPromptSource = naiPromptSource,
        naiPositivePrompt = naiPositivePrompt,
        naiNegativePrompt = naiNegativePrompt,
        autoSummaryEnabled = autoSummaryEnabled,
        summaryInterval = summaryInterval,
        messagesSinceLastSummary = messagesSinceLastSummary,
        lastSummaryTimestamp = lastSummaryTimestamp,
        autoSummaryFailureCount = autoSummaryFailureCount,
        injectThoughts = injectThoughts,
        backgroundActivityEnabled = backgroundActivityEnabled,
        actionCooldownMinutes = actionCooldownMinutes,
        lastBackgroundActionTimestamp = lastBackgroundActionTimestamp,
        timeAwarenessEnabled = timeAwarenessEnabled,
        shortTermMemoryLimit = shortTermMemoryLimit,
        attachMemoryLimit = attachMemoryLimit,
        selectedPhotos = selectedPhotos,
        isBlocked = isBlocked,
        blockTimestamp = blockTimestamp,
        blockCooldownHours = blockCooldownHours,
        applicationReason = applicationReason,
        isBlockedByContact = isBlockedByContact,
        isGroupChat = isGroupChat,
        groupChatRole = groupChatRole,
        privacyModeEnabled = privacyModeEnabled,
        isHiddenFromChatList = isHiddenFromChatList,
        onlineGlobalWorldBooks = onlineGlobalWorldBooks,
        onlineLocalWorldBooks = onlineLocalWorldBooks,
        offlineGlobalWorldBooks = offlineGlobalWorldBooks,
        offlineLocalWorldBooks = offlineLocalWorldBooks
    )
}

/**
 * 将领域模型 PersonProfile 转换为 PersonProfileEntity
 */
fun PersonProfile.toEntity(): PersonProfileEntity {
    return PersonProfileEntity(
        id = id,
        remarkName = remarkName,
        realName = realName,
        persona = persona,
        nicknameForUser = nicknameForUser,
        signature = signature,
        description = description,
        gender = gender,
        age = age,
        birthday = birthday,
        zodiacSign = zodiacSign,
        location = location,
        companyOrSchool = companyOrSchool,
        profession = profession,
        info_line_text = info_line_text,
        avatarUri = avatarUri,
        backgroundUri = backgroundUri,
        chatBackgroundUri = chatBackgroundUri,
        status = status,
        statusText = statusText,
        isBusy = isBusy,
        group = group,
        isPinned = isPinned,
        unreadMessageCount = unreadMessageCount,
        offlineModeEnabled = offlineModeEnabled,
        ttsVoiceId = ttsVoiceId,
        voiceDescription = voiceDescription,
        naiPromptSource = naiPromptSource,
        naiPositivePrompt = naiPositivePrompt,
        naiNegativePrompt = naiNegativePrompt,
        autoSummaryEnabled = autoSummaryEnabled,
        summaryInterval = summaryInterval,
        messagesSinceLastSummary = messagesSinceLastSummary,
        lastSummaryTimestamp = lastSummaryTimestamp,
        autoSummaryFailureCount = autoSummaryFailureCount,
        injectThoughts = injectThoughts,
        backgroundActivityEnabled = backgroundActivityEnabled,
        actionCooldownMinutes = actionCooldownMinutes,
        lastBackgroundActionTimestamp = lastBackgroundActionTimestamp,
        timeAwarenessEnabled = timeAwarenessEnabled,
        shortTermMemoryLimit = shortTermMemoryLimit,
        attachMemoryLimit = attachMemoryLimit,
        selectedPhotos = selectedPhotos,
        isBlocked = isBlocked,
        blockTimestamp = blockTimestamp,
        blockCooldownHours = blockCooldownHours,
        applicationReason = applicationReason,
        isBlockedByContact = isBlockedByContact,
        isGroupChat = isGroupChat,
        groupChatRole = groupChatRole,
        privacyModeEnabled = privacyModeEnabled,
        isHiddenFromChatList = isHiddenFromChatList,
        onlineGlobalWorldBooks = onlineGlobalWorldBooks,
        onlineLocalWorldBooks = onlineLocalWorldBooks,
        offlineGlobalWorldBooks = offlineGlobalWorldBooks,
        offlineLocalWorldBooks = offlineLocalWorldBooks
    )
}