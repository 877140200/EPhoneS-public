package com.susking.ephone_s.aidata.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * 角色设定（整合了原 QqContact 和 UserProfile 的相关字段）
 * 这是 AI 交互中的核心数据模型，包含角色的所有信息
 */
@Parcelize
data class PersonProfile(
    // === 基础标识 ===
    val id: String = UUID.randomUUID().toString(),
    val remarkName: String,              // 用户给AI的备注
    val realName: String,                // 角色本名
    val persona: String,                 // 角色设定
    
    // === 关系信息 ===
    val nicknameForUser: String? = null, // AI对用户的称呼
    
    // === 个人档案 ===
    val signature: String? = null,
    val description: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val zodiacSign: String? = null,
    val location: String? = null,
    val companyOrSchool: String? = null,
    val profession: String? = null,
    val info_line_text: String? = null,  // 保持与 QqContact 一致的命名
    
    // === 外观设置 ===
    val avatarUri: String? = null,
    val backgroundUri: String? = null,
    val chatBackgroundUri: String? = null,
    
    // === 状态信息 ===
    @Deprecated("统一使用statusText和isBusy")
    val status: String? = "在线",
    val statusText: String? = "在线",
    val isBusy: Boolean = false,
    
    // === 分组与显示 ===
    val group: String? = "未分组",
    var isPinned: Boolean = false,
    var unreadMessageCount: Int = 0,
    
    // === AI 行为配置 ===
    val offlineModeEnabled: Boolean = false,
    val ttsVoiceId: String? = null,
    val voiceDescription: String? = null, // voicedesign 模型的音色文本描述
    
    // NovelAI 设置
    val naiPromptSource: String = "system",  // "system" 或 "character"
    val naiPositivePrompt: String? = null,
    val naiNegativePrompt: String? = null,
    
    // 自动总结长期记忆
    val autoSummaryEnabled: Boolean = false,
    val summaryInterval: Int = 100,
    val messagesSinceLastSummary: Int = 0,
    val lastSummaryTimestamp: Long? = null,  // 上次聊天总结的时间戳
    val autoSummaryFailureCount: Int = 0,    // 自动结构化提取连续失败次数，用于指数退避；成功清零
    
    // 高级设置
    val injectThoughts: Boolean = true,
    val backgroundActivityEnabled: Boolean = false,
    val actionCooldownMinutes: Int = 60,
    val lastBackgroundActionTimestamp: Long? = null,  // 上次独立后台行动时间戳
    val timeAwarenessEnabled: Boolean = true,
    
    // 上下文管理
    val shortTermMemoryLimit: Int = 20,
    val attachMemoryLimit: Int = 10,
    val selectedPhotos: List<String> = emptyList(),
    
    // 隐私模式
    val privacyModeEnabled: Boolean = false,  // 开启后对话列表的最新消息显示为马赛克
    
    // 聊天列表显示控制
    val isHiddenFromChatList: Boolean = false,  // 为true时不在聊天列表中显示，但仍在好友列表中
    
    // 拉黑功能
    val isBlocked: Boolean = false,
    val blockTimestamp: Long? = null,
    val blockCooldownHours: Double = 0.00833333,  // 默认30秒
    val applicationReason: String? = null,
    val isBlockedByContact: Boolean = false,
    
    // 群聊相关
    val isGroupChat: Boolean = false,
    val groupChatRole: String? = null,  // "creator", "admin", "member"
    
    // === 时间感知相关 (新增) ===
    val sleepSchedule: SleepSchedule? = null,
    val timeSensitivityConfig: TimeSensitivityConfig = TimeSensitivityConfig(),
    
    // === 世界书绑定配置 (新增) ===
    val onlineGlobalWorldBooks: List<Long> = emptyList(),  // 线上模式-全局世界书ID列表
    val onlineLocalWorldBooks: List<Long> = emptyList(),   // 线上模式-局部世界书ID列表
    val offlineGlobalWorldBooks: List<Long> = emptyList(), // 线下模式-全局世界书ID列表
    val offlineLocalWorldBooks: List<Long> = emptyList()   // 线下模式-局部世界书ID列表
) : Parcelable