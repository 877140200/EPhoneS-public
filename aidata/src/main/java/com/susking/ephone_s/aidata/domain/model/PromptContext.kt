package com.susking.ephone_s.aidata.domain.model

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.FeedEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext

/**
 * 提示词上下文
 * brain 模块每次发起 AI 请求时，从 aidata 获取此对象
 * 包含所有 AI 需要的数据
 */
data class PromptContext(
    // 角色设定
    val personProfile: PersonProfile,
    
    // 用户设定
    val userProfile: UserProfile,
    
    // 聊天记录
    val chatHistory: List<ChatMessage>,
    
    // 世界观（多个世界书的提示词列表）
    val worldBookPrompts: List<String>,
    
    // 原子事件兼容列表
    val longTermMemories: List<LongTermMemory>,

    // 结构化记忆召回上下文
    val memoryRecallContext: MemoryRecallContext? = null,
    
    // 可用表情包
    val availableStickers: List<StickerEntity>,
    
    // 最近动态
    val recentFeeds: List<FeedEntity>,
    
    // 约定倒计时
    val appointments: List<AppointmentEntity>? = null,
    
    // 珍藏回忆
    val generalMemories: List<GeneralMemoryEntity>? = null,
    
    // 额外内容
    val breakLimitContent: String = "",
    val writingStyleContent: String = "",
    
    // 模式标识
    val isPropel: Boolean = false,              // 是否推进模式
    val lastCallFailureReason: String? = null,  // 视频通话拒绝理由
    val enableNovelAi: Boolean = false,          // 是否启用NovelAI
    val schedulePromptSummary: String = ""       // 用户校园状态摘要
)