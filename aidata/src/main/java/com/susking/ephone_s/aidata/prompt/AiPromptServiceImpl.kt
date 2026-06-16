package com.susking.ephone_s.aidata.prompt

import android.util.Log
import com.google.gson.GsonBuilder
import com.susking.ephone_s.aidata.data.local.entity.ContactSemanticStateEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.ContactSemanticStateRepository
import com.susking.ephone_s.aidata.domain.repository.FeedRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.StickerRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.aidata.domain.repository.WeatherRepository
import com.susking.ephone_s.aidata.domain.repository.WorldSettingRepository
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.use_case.BuildSchedulePromptSummaryUseCase
import kotlinx.coroutines.flow.first

/**
 * AiPromptService 的实现
 * 从各个 Repository 获取数据,使用 OnlinePromptBuilder 组装提示词
 * 
 * 核心原则:
 * - 无状态: 每次调用都从 Repository 获取最新数据
 * - 单一职责: 只负责组装提示词,不执行网络请求
 * - 并发安全: 不保存任何可变状态
 */
class AiPromptServiceImpl(
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val worldSettingRepository: WorldSettingRepository,
    private val stickerRepository: StickerRepository,
    private val feedRepository: FeedRepository,
    private val settingsRepository: SettingsRepository,
    private val worldBookRepository: WorldBookRepository,
    private val worldBookEntryRepository: WorldBookEntryRepository,
    private val cphonePromptBuilder: CPhonePromptBuilder,
    private val memoriesRepository: com.susking.ephone_s.aidata.domain.repository.MemoriesRepository,
    private val memoryRecallService: MemoryRecallService? = null,
    private val contactSemanticStateRepository: ContactSemanticStateRepository? = null,
    private val buildSchedulePromptSummaryUseCase: BuildSchedulePromptSummaryUseCase? = null,
    private val weatherRepository: com.susking.ephone_s.aidata.domain.repository.WeatherRepository? = null
) : AiPromptService {
    
    private val prettyPrintGson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 构建当前天气摘要，供在线提示词注入。
     *
     * 从 [weatherRepository] 读取一次缓存的天气数据，格式化为「位置 · 天气 · 温度」摘要。
     * 天气仓库未注入或无缓存时返回空串，调用方会因此跳过天气 section（向后兼容）。
     *
     * @return 天气摘要文本，无数据时为空串
     */
    private suspend fun buildWeatherSummary(): String {
        val weatherInfo = weatherRepository?.getWeatherSync() ?: return ""
        val temperatureText: String = "${weatherInfo.temperatureCelsius.toInt()}℃"
        return "${weatherInfo.locationName} · ${weatherInfo.weatherText} · $temperatureText"
    }

    private suspend fun getMemoryRecallContext(
        contactId: String,
        query: String,
        recentMessagesText: String = "",
        semanticStateText: String = ""
    ): MemoryRecallContext? {
        val recallService: MemoryRecallService = memoryRecallService ?: return null
        return runCatching {
            recallService.recallMemoryContext(
                MemoryRecallQuery(
                    contactId = contactId,
                    currentMessage = query,
                    recentMessagesText = recentMessagesText,
                    semanticStateText = semanticStateText,
                    topK = PROMPT_MEMORY_LIMIT
                )
            )
        }.getOrNull()
    }


    private suspend fun buildChatEmbeddingRecallQuery(
        history: List<ChatMessage>,
        contact: PersonProfile,
        userProfile: UserProfile
    ): ChatEmbeddingRecallQuery {
        val semanticState: ContactSemanticStateEntity? = contactSemanticStateRepository
            ?.getSemanticStateSnapshotForContact(contact.id)
        val latestUserMessages: List<ChatMessage> = collectLatestContinuousUserMessages(history)
        val latestUserSummary: String = PromptComponentBuilder.buildSimplifiedHistorySummary(
            history = latestUserMessages,
            contact = contact,
            userProfile = userProfile
        )
        val semanticStateText: String = buildSemanticStateRecallSection(semanticState)
        val latestUserText: String = latestUserSummary.takeIf { summary: String -> summary.isNotBlank() }?.let { summary: String ->
            "最新用户连续消息：\n$summary"
        }.orEmpty()
        val queryText: String = listOf(latestUserText, semanticStateText)
            .filter { section: String -> section.isNotBlank() }
            .joinToString(separator = "\n\n")
            .ifBlank { contact.realName }
        return ChatEmbeddingRecallQuery(
            queryText = queryText,
            latestUserText = latestUserText,
            semanticStateText = semanticStateText
        )
    }

    private fun buildMemoryRecallQuery(history: List<ChatMessage>, fallbackText: String): String {
        val latestMessages: List<ChatMessage> = history.takeLast(RECENT_MESSAGE_LIMIT)
        val recentMessagesText: String = latestMessages.joinToString(separator = "\n") { message: ChatMessage ->
            val speaker: String = if (message.role == "user") "用户" else "角色"
            val contentText: String = when {
                !message.content.isNullOrBlank() -> message.content.orEmpty()
                !message.imageUrl.isNullOrBlank() -> "[图片]"
                !message.stickerUrl.isNullOrBlank() -> "[表情包:${message.stickerName.orEmpty()}]"
                else -> "[${message.type}]"
            }
            "$speaker: $contentText"
        }
        return recentMessagesText.ifBlank { fallbackText }
    }

    private fun collectLatestContinuousUserMessages(history: List<ChatMessage>): List<ChatMessage> {
        val latestUserMessages: List<ChatMessage> = history
            .asReversed()
            .takeWhile { message: ChatMessage -> message.role == "user" }
            .asReversed()
        return latestUserMessages.takeLast(RECENT_MESSAGE_LIMIT)
    }

    private fun buildSemanticStateRecallSection(semanticState: ContactSemanticStateEntity?): String {
        if (semanticState == null) return ""
        val activeContext: String = semanticState.activeSemanticContext.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            "当前互动语义：${content.takeSemanticLines(SEMANTIC_ACTIVE_QUERY_LINES)}"
        }.orEmpty()
        val keywords: String = semanticState.semanticKeywords.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            "语义关键词：${content.takeSemanticKeywords(SEMANTIC_KEYWORD_QUERY_COUNT)}"
        }.orEmpty()
        val historicalAnchors: String = semanticState.historicalRecallAnchors.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            "历史召回锚点：${content.takeSemanticLines(SEMANTIC_ANCHOR_QUERY_LINES)}"
        }.orEmpty()
        val resolvedAnchors: String = semanticState.resolvedEventAnchors.takeIf { content: String -> content.isNotBlank() }?.let { content: String ->
            "已结束事件线索：${content.takeSemanticLines(SEMANTIC_RESOLVED_QUERY_LINES)}"
        }.orEmpty()
        val lines: List<String> = listOf(activeContext, keywords, historicalAnchors, resolvedAnchors)
            .filter { content: String -> content.isNotBlank() }
        if (lines.isEmpty()) return ""
        return "语义状态召回查询增强：\n${lines.joinToString(separator = "\n")}"
    }

    private fun String.takeSemanticLines(maxLines: Int): String {
        return lines()
            .map { line: String -> line.trim() }
            .filter { line: String -> line.isNotBlank() }
            .takeLast(maxLines)
            .joinToString(separator = "；")
    }

    private fun String.takeSemanticKeywords(maxCount: Int): String {
        return split('、', ',', '，', ';', '；', '\n')
            .map { keyword: String -> keyword.trim() }
            .filter { keyword: String -> keyword.isNotBlank() }
            .takeLast(maxCount)
            .joinToString(separator = "、")
    }

    private fun logMemoryRecallContext(contactId: String, context: MemoryRecallContext?): Unit {
        if (context == null) {
            Log.d(TAG, "聊天提示词结构化记忆召回为空 contactId=$contactId")
            return
        }
        Log.d(
            TAG,
            "聊天提示词结构化记忆召回 contactId=$contactId, relatedEvents=${context.relevantEvents.size}, activeFacts=${context.activeFacts.size}, pendingCommitments=${context.pendingCommitments.size}, relationshipTimelines=${context.relationshipTimelines.size}, timelineSummaries=${context.timelineSummaries.size}, estimatedTokens=${context.estimatedTokenCount}"
        )
    }
    
    override suspend fun buildConversationalPrompt(
        contactId: String,
        isPropel: Boolean,
        customHistory: List<ChatMessage>?
    ): AiPromptRequest {
        // 1. 获取所有需要的数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        
        val unseenUserMessages: List<ChatMessage> = if (customHistory == null && !isPropel) {
            chatRepository.getUnseenUserMessagesForContact(contactId)
        } else {
            emptyList()
        }

        // 【修复】如果提供了customHistory,使用它;否则从数据库获取
        val allHistory: List<ChatMessage> = customHistory ?: chatRepository.getMessagesForContactNonFlow(contactId)
        val history: List<ChatMessage> = allHistory.takeLast(contact.shortTermMemoryLimit)
        val promptHistory: List<ChatMessage> = buildHistoryWithUnseenMessagesAtEnd(history, unseenUserMessages)
        val promptAllHistory: List<ChatMessage> = buildHistoryWithUnseenMessagesAtEnd(allHistory, unseenUserMessages)
        val worldBookPrompts = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallQuery = buildChatEmbeddingRecallQuery(
            history = history,
            contact = contact,
            userProfile = userProfile
        )
        val memoryRecallContext = getMemoryRecallContext(
            contactId = contactId,
            query = memoryRecallQuery.queryText,
            recentMessagesText = memoryRecallQuery.latestUserText,
            semanticStateText = memoryRecallQuery.semanticStateText
        )
        logMemoryRecallContext(contactId, memoryRecallContext)
        val longTermMemories = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        val schedulePromptSummary = buildSchedulePromptSummaryUseCase?.execute()?.content.orEmpty()
        // 当前位置天气摘要，注入在线提示词供 AI 感知用户所处天气
        val weatherSummary = buildWeatherSummary()

        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        val novelaiEnabled = settingsRepository.isNovelAiEnabled()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 OnlinePromptBuilder 组装消息
        val messages = mutableListOf<ChatMessagePayload>()
        
        // 添加破限内容
        if (breakLimitContent.isNotBlank()) {
            messages.add(ChatMessagePayload(role = "system", content = breakLimitContent))
        }
        
        // 构建历史记录 - 根据时间间隔选择构建方法
        val currentTime = System.currentTimeMillis()
        val lastMessageTimestamp = history.lastOrNull()?.timestamp ?: 0L
        val isLongTimeSinceLastMessage = PromptComponentBuilder.isLongTimeSinceLastMessage(
            lastMessageTimestamp,
            currentTime
        )
        
        val historyBuildResult = if (isLongTimeSinceLastMessage && promptHistory.isNotEmpty()) {
            // 超过20分钟,使用时间感知的历史记录构建。这里只调整提示词历史顺序，不改动数据库与界面展示顺序。
            PromptComponentBuilder.buildTimeAwareHistoryPayloads(
                allMessages = promptAllHistory,
                contact = contact,
                userProfile = userProfile,
                currentTimeMillis = currentTime,
                longTermMemories = longTermMemories,
                isPropel = isPropel
            )
        } else {
            // 20分钟以内,使用普通构建方法。未读用户消息会作为普通历史消息集中追加到末尾。
            PromptComponentBuilder.buildHistoryPayloads(
                promptHistory,
                contact,
                userProfile,
                longTermMemories,
                isPropel
            )
        }
        
        // 提取历史记录载荷和未分析图片标记
        val historyPayloads = historyBuildResult.payloads
        val hasUnanalyzedImages = historyBuildResult.hasUnanalyzedImages
        
        // 构建初始提示词（传入未分析图片标记）
        val initialPrompt = OnlinePromptBuilder.buildInitialPrompt(
            contact,
            userProfile,
            worldBookPrompts,
            longTermMemories,
            memoryRecallContext,
            availableStickers,
            "", // 在线模式取消文风模块
            allRecentFeeds,
            lastMessageTimestamp,
            appointments,
            generalMemories,
            hasUnanalyzedImages, // 传递未分析图片标记
            schedulePromptSummary,
            weatherSummary
        )
        
        // 构建指令
        val commandListPrompt = OnlinePromptBuilder.buildCommandListPrompt(contact, novelaiEnabled)
        val instructionPrompt = OnlinePromptBuilder.buildInstructionPrompt(isPropel)
        
        // 根据 isPropel 决定提示词的最终组装方式
        if (isPropel) {
            // 推进模式: { initial + commands } -> history -> { instruction }
            val combinedInitialPrompt = "$initialPrompt\n\n$commandListPrompt"
            messages.add(ChatMessagePayload(role = "user", content = combinedInitialPrompt))
            messages.addAll(historyPayloads)
            messages.add(ChatMessagePayload(role = "user", content = instructionPrompt))
        } else {
            // 普通模式: { initial + commands + instruction } -> history
            val combinedUserPrompt = listOf(initialPrompt, commandListPrompt, instructionPrompt)
                .filter { promptPart: String -> promptPart.isNotBlank() }
                .joinToString(separator = "\n\n")
            messages.add(ChatMessagePayload(role = "user", content = combinedUserPrompt))
            messages.addAll(historyPayloads)
        }
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        
        // 6. 为显示创建一个"安全"的版本,其中图片被替换为占位符
        val displayMessages = messages.map { message ->
            if (message.content is List<*>) {
                val newContent = (message.content as List<*>).map { part ->
                    when (part) {
                        is ImageContentPart -> TextContentPart(text = "[图片完整信息过大不予展示]")
                        else -> part
                    }
                }
                message.copy(content = newContent)
            } else {
                message
            }
        }
        val displayRequestBody = requestBody.copy(messages = displayMessages)
        val displayJson = prettyPrintGson.toJson(displayRequestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "聊天消息",
            aiVisibleMessageIds = unseenUserMessages.map { message: ChatMessage -> message.id }
        )
    }

    private fun buildHistoryWithUnseenMessagesAtEnd(
        history: List<ChatMessage>,
        unseenUserMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (unseenUserMessages.isEmpty()) return history
        if (history.isEmpty()) return unseenUserMessages
        val unseenMessageIds: Set<String> = unseenUserMessages.map { message: ChatMessage -> message.id }.toSet()
        val historyWithoutUnseenMessages: List<ChatMessage> = history.filterNot { message: ChatMessage ->
            message.id in unseenMessageIds
        }
        return historyWithoutUnseenMessages + unseenUserMessages
    }
    
    override suspend fun buildVideoCallDecisionPrompt(
        contactId: String
    ): AiPromptRequest {
        // 1. 获取数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val history = allHistory.takeLast(contact.shortTermMemoryLimit)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 VideoCallPromptBuilder 构建消息
        val messages = VideoCallPromptBuilder.buildVideoCallDecisionPrompt(contact, userProfile, history, breakLimitContent)
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "视频通话决策"
        )
    }
    
    override suspend fun buildRedialDecisionPrompt(
        contactId: String,
        lastCallFailureReason: String
    ): AiPromptRequest {
        // 1. 获取数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val history = allHistory.takeLast(contact.shortTermMemoryLimit)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 VideoCallPromptBuilder 构建消息
        val messages = VideoCallPromptBuilder.buildRedialVideoCallDecisionPrompt(
            contact,
            userProfile,
            history,
            lastCallFailureReason,
            breakLimitContent
        )
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "视频通话决策 (重拨)"
        )
    }
    
    override suspend fun buildInCallPrompt(
        contactId: String,
        callHistory: List<ChatMessage>,
        isReroll: Boolean
    ): AiPromptRequest {
        // 1. 获取数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allChatHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val chatHistory = allChatHistory.takeLast(contact.shortTermMemoryLimit)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 VideoCallPromptBuilder 构建消息
        val messages = VideoCallPromptBuilder.buildInCallConversationPrompt(
            contact = contact,
            userProfile = userProfile,
            chatHistory = chatHistory,
            callHistory = callHistory,
            isReroll = isReroll,
            breakLimitContent = breakLimitContent
        )
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = null // 通话中AI应返回纯文本
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "视频通话对话"
        )
    }
    
    override suspend fun buildCallSummaryPrompt(
        contactId: String,
        transcript: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean
    ): AiPromptRequest {
        // 1. 获取数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = 0.2f // 对于分析任务,使用较低的温度
        
        // 3. 使用 VideoCallPromptBuilder 构建视频通话结构化抽取消息
        val messages = VideoCallPromptBuilder.buildCallSummaryPrompt(
            contact,
            userProfile,
            transcript,
            lastMessageTimestamp,
            hangupTimestamp,
            isUserHangup
        )
        
        // 4. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "视频通话结构化记忆抽取"
        )
    }
    
    override suspend fun buildPostCallPrompt(
        contactId: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean
    ): AiPromptRequest {
        // 1. 获取所有需要的数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val history = allHistory.takeLast(contact.shortTermMemoryLimit)
        val worldBookPrompts = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext = getMemoryRecallContext(contactId, buildMemoryRecallQuery(history, contact.realName))
        val longTermMemories = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        val novelaiEnabled = settingsRepository.isNovelAiEnabled()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 VideoCallPromptBuilder 构建消息（正常结束场景，isInterrupted = false）
        val messages = VideoCallPromptBuilder.buildPostVideoCallPromptPayloads(
            contact = contact,
            userProfile = userProfile,
            history = history,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            availableStickers = availableStickers,
            breakLimitContent = breakLimitContent,
            allRecentFeeds = allRecentFeeds,
            lastMessageTimestamp = lastMessageTimestamp,
            hangupTimestamp = hangupTimestamp,
            isUserHangup = isUserHangup,
            novelaiEnabled = novelaiEnabled,
            isInterrupted = false,  // 正常结束场景
            currentTimestamp = System.currentTimeMillis(),  // 当前时间
            appointments = appointments,
            generalMemories = generalMemories
        )
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "视频通话后回应"
        )
    }
    
    override suspend fun buildDeclinedCallPrompt(
        contactId: String,
        reason: String
    ): AiPromptRequest {
        // 1. 获取所有需要的数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val history = allHistory.takeLast(contact.shortTermMemoryLimit)
        val worldBookPrompts = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext = getMemoryRecallContext(contactId, buildMemoryRecallQuery(history, reason))
        val longTermMemories = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        val novelaiEnabled = settingsRepository.isNovelAiEnabled()
        
        // 3. 获取破限内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val breakLimitContent = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
                .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        } ?: ""
        
        // 4. 使用 VideoCallPromptBuilder 构建消息
        val messages = VideoCallPromptBuilder.buildDeclinedVideoCallPromptPayloads(
            contact,
            userProfile,
            history,
            reason,
            worldBookPrompts,
            longTermMemories,
            availableStickers,
            breakLimitContent,
            allRecentFeeds,
            novelaiEnabled,
            appointments,
            generalMemories
        )
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "拒接通话后反应"
        )
    }
    
    override suspend fun buildOfflinePrompt(
        contactId: String,
        enableNovelAi: Boolean,
        customHistory: List<ChatMessage>?
    ): AiPromptRequest {
        // 1. 获取所有需要的数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        
        val unseenUserMessages: List<ChatMessage> = if (customHistory == null) {
            chatRepository.getUnseenUserMessagesForContact(contactId)
        } else {
            emptyList()
        }

        // 【修复】如果提供了customHistory,使用它;否则从数据库获取
        val allHistory: List<ChatMessage> = customHistory ?: chatRepository.getMessagesForContactNonFlow(contactId)
        val history: List<ChatMessage> = allHistory.takeLast(contact.shortTermMemoryLimit)
        val promptHistory: List<ChatMessage> = buildHistoryWithUnseenMessagesAtEnd(history, unseenUserMessages)
        val worldBookPrompts = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext = getMemoryRecallContext(contactId, buildMemoryRecallQuery(history, contact.realName))
        val longTermMemories = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = settingsRepository.getApiTemperature()
        
        // 3. 获取破限和文风内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val entries = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
        } ?: emptyList()
        
        val breakLimitContent = entries
            .find { entry -> entry.name == "破限" && entry.isEnabled }?.content
        val writingStyleContent = entries
            .find { entry -> entry.name == "文风" && entry.isEnabled }?.content
        
        // 4. 使用 OfflinePromptBuilder 构建消息。这里只调整提示词历史顺序，不改动数据库与界面展示顺序。
        val messagePayloads = OfflinePromptBuilder.buildOfflineNarrativePrompt(
            contact = contact,
            userProfile = userProfile,
            history = promptHistory,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            novelaiEnabled = enableNovelAi,
            systemPrompt = breakLimitContent,
            writingStylePrompt = writingStyleContent,
            appointments = appointments,
            generalMemories = generalMemories
        )
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messagePayloads,
            temperature = temperature,
            responseFormat = null // 线下模式返回纯文本
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        
        // 6. 为显示创建安全版本
        val displayMessages = messagePayloads.map { message ->
            val content = message.content
            if (content is List<*>) {
                val newContent = content.filterIsInstance<ContentPart>().map { part ->
                    if (part is ImageContentPart) {
                        TextContentPart(text = "[用户发送了一张图片]")
                    } else {
                        part
                    }
                }
                message.copy(content = newContent)
            } else {
                message
            }
        }
        val displayRequestBody = requestBody.copy(messages = displayMessages)
        val displayJson = prettyPrintGson.toJson(displayRequestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contact.remarkName,
            activityType = "线下剧情",
            aiVisibleMessageIds = unseenUserMessages.map { message: ChatMessage -> message.id }
        )
    }
    
    override suspend fun buildBackgroundActionPrompt(
        systemPrompt: String
    ): AiPromptRequest {
        // 1. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = (settingsRepository.getApiTemperature() + 0.15f).coerceAtMost(1.2f)
        
        // 2. 构建消息
        val messagePayloads = listOf(
            ChatMessagePayload(role = "user", content = systemPrompt)
        )
        
        // 3. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messagePayloads,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = "System prompt for decideNextActions",
            timestamp = System.currentTimeMillis(),
            contactName = null,
            activityType = "独立行动决策"
        )
    }
    
    override suspend fun buildFriendRequestPrompt(
        contactId: String
    ): AiPromptRequest {
        // 1. 获取数据
        val contact = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("联系人不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        
        // 2. 获取 API 配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = (settingsRepository.getApiTemperature() + 0.2f).coerceAtMost(1.0f)
        
        // 3. 使用 OnlinePromptBuilder 构建提示词
        val systemPrompt = OnlinePromptBuilder.buildFriendApplicationPrompt(contact, userProfile)
        
        // 4. 构建消息
        val messagePayloads = listOf(ChatMessagePayload(role = "user", content = systemPrompt))
        
        // 5. 构建请求体
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messagePayloads,
            temperature = temperature,
            responseFormat = null // 期望纯文本回复
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = systemPrompt,
            timestamp = System.currentTimeMillis(),
            contactName = contact.realName,
            activityType = "好友申请理由生成"
        )
    }

    override suspend fun buildCPhoneDataPrompt(
        contactId: String,
        appType: String
    ): AiPromptRequest {
        // 1. 获取所有需要的数据,构建PromptContext
        val profile = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("角色不存在: $contactId")
        val userProfile = personProfileRepository.getUserProfile()
        val allHistory = chatRepository.getMessagesForContactNonFlow(contactId)
        val history = allHistory.takeLast(profile.shortTermMemoryLimit)
        val worldBookPrompts = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext = getMemoryRecallContext(contactId, buildMemoryRecallQuery(history, appType))
        val longTermMemories = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        
        // 获取破限和文风内容
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val entries = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
        } ?: emptyList()
        
        val breakLimitContent = entries
            .find { entry -> entry.name == "破限" && entry.isEnabled }?.content ?: ""
        val writingStyleContent = entries
            .find { entry -> entry.name == "文风" && entry.isEnabled }?.content ?: ""
        
        // 构建PromptContext
        val context = com.susking.ephone_s.aidata.domain.model.PromptContext(
            personProfile = profile,
            userProfile = userProfile,
            chatHistory = history,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            availableStickers = availableStickers,
            recentFeeds = allRecentFeeds,
            appointments = appointments,
            generalMemories = generalMemories,
            breakLimitContent = breakLimitContent,
            writingStyleContent = writingStyleContent,
            isPropel = false,
            lastCallFailureReason = null,
            enableNovelAi = false,
            schedulePromptSummary = buildSchedulePromptSummaryUseCase?.execute()?.content.orEmpty()
        )

        // 2. 根据appType调用对应的Prompt构建方法
        val systemPrompt = when (appType) {
            "album" -> cphonePromptBuilder.buildAlbumPrompt(context)
            "browser" -> cphonePromptBuilder.buildBrowserPrompt(context)
            "taobao" -> cphonePromptBuilder.buildTaobaoPrompt(context)
            "memo" -> cphonePromptBuilder.buildMemoPrompt(context)
            "diary" -> cphonePromptBuilder.buildDiaryPrompt(context)
            "amap" -> cphonePromptBuilder.buildAmapPrompt(context)
            "appUsage" -> cphonePromptBuilder.buildAppUsagePrompt(context)
            "music" -> cphonePromptBuilder.buildMusicPrompt(context)
            "qq" -> cphonePromptBuilder.buildQQPrompt(context)
            else -> throw IllegalArgumentException("不支持的App类型: $appType")
        }

        // 3. 获取API配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = 0.9f // 创造性数据生成使用较高温度

        // 4. 构建消息
        val messages = listOf(
            ChatMessagePayload(role = "user", content = systemPrompt)
        )

        // 5. 构建请求体（强制JSON输出）
        // maxTokens设置为9000，为AI推理预留充足空间，避免JSON被截断
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )

        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)

        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = profile.realName,
            activityType = "偷偷摸摸地查ta手机-$appType"
        )
    }

    override suspend fun buildAutomaticDailyDiaryPrompt(
        contactId: String,
        windowStart: Long,
        windowEnd: Long,
        windowLabel: String
    ): AiPromptRequest {
        val context: com.susking.ephone_s.aidata.domain.model.PromptContext = buildCPhonePromptContextForWindow(
            contactId = contactId,
            recallQueryText = "automatic_daily_diary_$windowLabel",
            windowStart = windowStart,
            windowEnd = windowEnd
        )
        val systemPrompt: String = cphonePromptBuilder.buildAutomaticDailyDiaryPrompt(context, windowLabel)
        return buildCPhonePromptRequest(
            contactName = context.personProfile.realName,
            systemPrompt = systemPrompt,
            activityType = "自动生成CPhone每日日记和摘要"
        )
    }

    private suspend fun buildCPhonePromptContextForWindow(
        contactId: String,
        recallQueryText: String,
        windowStart: Long,
        windowEnd: Long
    ): com.susking.ephone_s.aidata.domain.model.PromptContext {
        val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("角色不存在: $contactId")
        val userProfile: UserProfile = personProfileRepository.getUserProfile()
        val allHistory: List<ChatMessage> = chatRepository.getMessagesForContactNonFlow(contactId)
        val windowHistory: List<ChatMessage> = allHistory.filter { message: ChatMessage ->
            message.timestamp >= windowStart && message.timestamp < windowEnd
        }
        val worldBookPrompts: List<String> = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext: MemoryRecallContext? = getMemoryRecallContext(contactId, buildMemoryRecallQuery(windowHistory, recallQueryText))
        val longTermMemories: List<LongTermMemory> = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val entries = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
        } ?: emptyList()
        val breakLimitContent: String = entries
            .find { entry -> entry.name == "破限" && entry.isEnabled }?.content ?: ""
        val writingStyleContent: String = entries
            .find { entry -> entry.name == "文风" && entry.isEnabled }?.content ?: ""
        return com.susking.ephone_s.aidata.domain.model.PromptContext(
            personProfile = profile,
            userProfile = userProfile,
            chatHistory = windowHistory,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            availableStickers = availableStickers,
            recentFeeds = allRecentFeeds,
            appointments = appointments,
            generalMemories = generalMemories,
            breakLimitContent = breakLimitContent,
            writingStyleContent = writingStyleContent,
            isPropel = false,
            lastCallFailureReason = null,
            enableNovelAi = false,
            schedulePromptSummary = buildSchedulePromptSummaryUseCase?.execute()?.content.orEmpty()
        )
    }

    private suspend fun buildCPhonePromptContext(
        contactId: String,
        recallQueryText: String
    ): com.susking.ephone_s.aidata.domain.model.PromptContext {
        val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("角色不存在: $contactId")
        val userProfile: UserProfile = personProfileRepository.getUserProfile()
        val allHistory: List<ChatMessage> = chatRepository.getMessagesForContactNonFlow(contactId)
        val history: List<ChatMessage> = allHistory.takeLast(profile.shortTermMemoryLimit)
        val worldBookPrompts: List<String> = worldSettingRepository.getEnabledWorldBookPrompts()
        val memoryRecallContext: MemoryRecallContext? = getMemoryRecallContext(contactId, buildMemoryRecallQuery(history, recallQueryText))
        val longTermMemories: List<LongTermMemory> = memoryRecallContext?.toCompatibleMemories() ?: emptyList()
        val availableStickers = stickerRepository.getAllStickersSuspend()
        val allRecentFeeds = feedRepository.getAllFeeds().first()
        val appointments = memoriesRepository.getAppointmentsByContactIdSuspend(contactId)
        val generalMemories = memoriesRepository.getMemoriesByContactIdSuspend(contactId)
        val systemWorldBook = worldBookRepository.getSystemWorldBook()
        val entries = systemWorldBook?.let {
            worldBookEntryRepository.getEntriesForWorldBook(it.worldBookId).first()
        } ?: emptyList()
        val breakLimitContent: String = entries
            .find { entry -> entry.name == "破限" && entry.isEnabled }?.content ?: ""
        val writingStyleContent: String = entries
            .find { entry -> entry.name == "文风" && entry.isEnabled }?.content ?: ""
        return com.susking.ephone_s.aidata.domain.model.PromptContext(
            personProfile = profile,
            userProfile = userProfile,
            chatHistory = history,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            availableStickers = availableStickers,
            recentFeeds = allRecentFeeds,
            appointments = appointments,
            generalMemories = generalMemories,
            breakLimitContent = breakLimitContent,
            writingStyleContent = writingStyleContent,
            isPropel = false,
            lastCallFailureReason = null,
            enableNovelAi = false,
            schedulePromptSummary = buildSchedulePromptSummaryUseCase?.execute()?.content.orEmpty()
        )
    }

    private fun buildCPhonePromptRequest(
        contactName: String,
        systemPrompt: String,
        activityType: String
    ): AiPromptRequest {
        val apiUrl: String = settingsRepository.getMainApiUrl()
        val model: String = settingsRepository.getMainModel()
        val messages: List<ChatMessagePayload> = listOf(
            ChatMessagePayload(role = "user", content = systemPrompt)
        )
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = CPHONE_GENERATION_TEMPERATURE,
            responseFormat = ResponseFormat("json_object")
        )
        val fullUrl: String = "$apiUrl/v1/chat/completions"
        val displayJson: String = prettyPrintGson.toJson(requestBody)
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = contactName,
            activityType = activityType
        )
    }

    override suspend fun buildPersonaAnalysisPrompt(
        personaDescription: String
    ): AiPromptRequest {
        // 1. 获取API配置
        val apiUrl = settingsRepository.getMainApiUrl()
        val model = settingsRepository.getMainModel()
        val temperature = 0.3f // 分析任务使用较低温度确保稳定输出
        
        // 2. 使用PersonaAnalyzerPromptBuilder构建提示词
        val systemPrompt = PersonaAnalyzerPromptBuilder.buildAnalyzePrompt(personaDescription)
        
        // 3. 构建消息
        val messages = listOf(
            ChatMessagePayload(role = "user", content = systemPrompt)
        )
        
        // 4. 构建请求体(强制JSON输出)
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = ResponseFormat("json_object")
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        val displayJson = prettyPrintGson.toJson(requestBody)
        
        return AiPromptRequest(
            request = requestBody,
            url = fullUrl,
            displayPromptJson = displayJson,
            timestamp = System.currentTimeMillis(),
            contactName = null,
            activityType = "人设分析"
        )
    }

    private data class ChatEmbeddingRecallQuery(
        val queryText: String,
        val latestUserText: String,
        val semanticStateText: String
    )

    private companion object {
        private const val TAG: String = "AiPromptServiceImpl"
        private const val PROMPT_MEMORY_LIMIT: Int = 50
        private const val RECENT_MESSAGE_LIMIT: Int = 8
        private const val CPHONE_GENERATION_TEMPERATURE: Float = 0.9f
        private const val SEMANTIC_ACTIVE_QUERY_LINES: Int = 3
        private const val SEMANTIC_ANCHOR_QUERY_LINES: Int = 3
        private const val SEMANTIC_RESOLVED_QUERY_LINES: Int = 2
        private const val SEMANTIC_KEYWORD_QUERY_COUNT: Int = 8
    }
}