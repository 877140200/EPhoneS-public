package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.domain.model.ChatMessage

/**
 * AI 提示词服务接口
 * 负责根据不同场景构建完整的 AI 请求数据
 * 
 * 核心职责:
 * 1. 从 aidata 各个 Repository 获取数据
 * 2. 使用 OnlinePromptBuilder 组装提示词
 * 3. 返回完整的 AiPromptRequest 供 brain 模块使用
 */
interface AiPromptService {
    
    /**
     * 构建对话提示词
     * @param contactId 联系人ID
     * @param isPropel 是否推进模式
     * @param customHistory 自定义历史记录(用于重说功能),如果为null则从数据库获取
     * @return AI 提示词请求数据
     */
    suspend fun buildConversationalPrompt(
        contactId: String,
        isPropel: Boolean = false,
        customHistory: List<ChatMessage>? = null
    ): AiPromptRequest
    
    /**
     * 构建视频通话决策提示词
     * @param contactId 联系人ID
     * @return AI 提示词请求数据
     */
    suspend fun buildVideoCallDecisionPrompt(
        contactId: String
    ): AiPromptRequest
    
    /**
     * 构建重拨决策提示词
     * @param contactId 联系人ID
     * @param lastCallFailureReason 上次通话失败原因
     * @return AI 提示词请求数据
     */
    suspend fun buildRedialDecisionPrompt(
        contactId: String,
        lastCallFailureReason: String
    ): AiPromptRequest
    
    /**
     * 构建通话中对话提示词
     * @param contactId 联系人ID
     * @param callHistory 通话历史记录
     * @param isReroll 是否重新生成
     * @return AI 提示词请求数据
     */
    suspend fun buildInCallPrompt(
        contactId: String,
        callHistory: List<ChatMessage>,
        isReroll: Boolean
    ): AiPromptRequest
    
    /**
     * 构建视频通话结构化记忆抽取提示词。
     *
     * 方法名保留为 buildCallSummaryPrompt，用于兼容既有调用链；实际输出已改为结构化事件、节点和关系。
     * @param contactId 联系人ID
     * @param transcript 通话记录文本
     * @param lastMessageTimestamp 最后一句话的时间戳
     * @param hangupTimestamp 挂断电话的时间戳
     * @param isUserHangup 是否是用户挂断
     * @return AI 提示词请求数据
     */
    suspend fun buildCallSummaryPrompt(
        contactId: String,
        transcript: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean
    ): AiPromptRequest
    
    /**
     * 构建通话后提示词
     * @param contactId 联系人ID
     * @param lastMessageTimestamp 最后一句话的时间戳
     * @param hangupTimestamp 挂断电话的时间戳
     * @param isUserHangup 是否是用户挂断
     * @return AI 提示词请求数据
     */
    suspend fun buildPostCallPrompt(
        contactId: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean
    ): AiPromptRequest
    
    /**
     * 构建拒接后提示词
     * @param contactId 联系人ID
     * @param reason 拒接原因
     * @return AI 提示词请求数据
     */
    suspend fun buildDeclinedCallPrompt(
        contactId: String,
        reason: String
    ): AiPromptRequest
    
    /**
     * 构建线下剧情提示词
     * @param contactId 联系人ID
     * @param enableNovelAi 是否启用 NovelAI
     * @param customHistory 自定义历史记录(用于重说功能),如果为null则从数据库获取
     * @return AI 提示词请求数据
     */
    suspend fun buildOfflinePrompt(
        contactId: String,
        enableNovelAi: Boolean,
        customHistory: List<ChatMessage>? = null
    ): AiPromptRequest
    
    /**
     * 构建后台独立行动提示词
     * @param systemPrompt 系统提示词
     * @return AI 提示词请求数据
     */
    suspend fun buildBackgroundActionPrompt(
        systemPrompt: String
    ): AiPromptRequest
    
    /**
     * 构建好友申请理由提示词
     * @param contactId 联系人ID
     * @return AI 提示词请求数据
     */
    suspend fun buildFriendRequestPrompt(
        contactId: String
    ): AiPromptRequest
    
    /**
     * 构建查手机数据生成提示词
     * @param contactId 角色ID
     * @param appType App类型（album/browser/taobao/memo/diary/amap/appUsage/music/qq）
     * @return AI 提示词请求数据
     */
    suspend fun buildCPhoneDataPrompt(
        contactId: String,
        appType: String
    ): AiPromptRequest

    /**
     * 构建 CPhone 每日自动日记联合提示词。
     * 该提示词一次请求同时返回日记和每日分层摘要。
     */
    suspend fun buildAutomaticDailyDiaryPrompt(
        contactId: String,
        windowStart: Long,
        windowEnd: Long,
        windowLabel: String
    ): AiPromptRequest

    /**
     * 构建人设分析提示词
     * 让AI自动分析角色人设并推断时间感知配置
     * @param personaDescription 角色人设描述
     * @return AI 提示词请求数据
     */
    suspend fun buildPersonaAnalysisPrompt(
        personaDescription: String
    ): AiPromptRequest
}

/**
 * AI 提示词请求数据
 * 包含发送给 AI 的完整请求信息
 */
data class AiPromptRequest(
    val request: ChatCompletionRequest,
    val url: String,
    val displayPromptJson: String,
    val timestamp: Long,
    val contactName: String? = null,
    val activityType: String = "聊天消息",
    val aiVisibleMessageIds: List<String> = emptyList()
)