package com.susking.ephone_s.qq.domain.use_case.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.di.ApplicationScope
import com.susking.ephone_s.aidata.domain.model.AiActionParser
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.api.TtsSynthesisRequest
import com.susking.ephone_s.aidata.api.TtsSynthesisResult
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.use_case.TriggerAutoSummarizeUseCase
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.brain.service.ActionExecutor
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicy
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.brain.service.NovelAiService
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.qq.domain.manager.QqEvent
import com.susking.ephone_s.qq.domain.model.OfflineVersionData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * 执行AI响应的UseCase
 * 这是最复杂的UseCase,处理线上和线下两种模式的AI响应
 */
class ExecuteAiResponseUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRequestService: AiRequestService,
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val actionExecutor: ActionExecutor,
    private val followUpPolicyStore: FollowUpPolicyStore,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson,
    private val chatMessageDao: ChatMessageDao,
    private val triggerAutoSummarizeUseCase: TriggerAutoSummarizeUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    data class ExecutionResult(
        val messages: List<ChatMessage>,
        val rawJson: String,
        val incomingCallContactId: String? = null,
        // 打字延迟完成信号：最后一条气泡落库后 complete。调用方可 await 它，
        // 以便在所有气泡逐条冒出完毕后才关闭"正在输入"提示（方案 A）。
        // 不启用打字延迟时为已完成状态，await 立即返回。
        val typingCompletion: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    )

    suspend operator fun invoke(
        prompt: AiPromptRequest,
        contactId: String,
        isOfflineMode: Boolean,
        originalMessageIdToUpdate: String? = null
    ): Result<ExecutionResult> = withContext(Dispatchers.IO) {
        try {
            val aiResponseJson = aiRequestService.getChatCompletion(context, prompt)
            if (aiResponseJson.isNullOrBlank()) {
                return@withContext Result.failure(Exception("AI返回空响应"))
            }
            val result = if (isOfflineMode) {
                executeOfflineMode(aiResponseJson, contactId, originalMessageIdToUpdate)
            } else {
                executeOnlineMode(aiResponseJson, contactId, originalMessageIdToUpdate)
            }

            val hasValidExtractableResult: Boolean = hasValidExtractableResult(result)
            if (originalMessageIdToUpdate == null && prompt.aiVisibleMessageIds.isNotEmpty() && hasValidExtractableResult) {
                chatRepository.markMessagesAsSeenByAi(prompt.aiVisibleMessageIds)
            }
            if (hasValidExtractableResult) {
                saveFollowUpPolicyIfPresent(contactId, aiResponseJson, result.messages.lastOrNull()?.id)
            }
            if (hasValidExtractableResult && originalMessageIdToUpdate == null) {
                triggerAutoSummarizeAfterSuccessfulAiResponse(contactId, result)
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun triggerAutoSummarizeAfterSuccessfulAiResponse(contactId: String, result: ExecutionResult): Unit {
        runCatching {
            val totalMessageCount: Int = chatMessageDao.getMessageCountForContact(contactId)
            val contact = personProfileRepository.getPersonProfileById(contactId) ?: return
            val newMessageCountSinceLastSummary: Int = contact.lastSummaryTimestamp
                ?.let { lastSummaryTimestamp: Long ->
                    chatMessageDao.getMessageCountAfterTimestamp(contactId, lastSummaryTimestamp)
                }
                ?: totalMessageCount
            val newMessageTimestamp: Long = result.messages.maxOfOrNull { message: ChatMessage -> message.timestamp }
                ?: System.currentTimeMillis()
            val wasTriggered: Boolean = triggerAutoSummarizeUseCase.shouldTrigger(
                contactId = contactId,
                totalMessageCount = totalMessageCount,
                newMessageCountSinceLastSummary = newMessageCountSinceLastSummary,
                newMessageTimestamp = newMessageTimestamp
            )
            Log.d(
                "ExecuteAiResponse",
                "AI回复成功后自动提取检查完成: contactId=$contactId, totalMessageCount=$totalMessageCount, newMessageCountSinceLastSummary=$newMessageCountSinceLastSummary, wasTriggered=$wasTriggered"
            )
        }.onFailure { error: Throwable ->
            Log.e("ExecuteAiResponse", "AI回复成功后自动提取检查失败: ${error.message}", error)
        }
    }

    private suspend fun executeOfflineMode(
        aiResponseJson: String,
        contactId: String,
        originalMessageIdToUpdate: String?
    ): ExecutionResult {
        val turnId = determineTurnId(contactId, originalMessageIdToUpdate)

        // 清理旧轮次数据
        if (originalMessageIdToUpdate != null) {
            heartbeatRepository.deleteHeartbeatsByAiTurnId(turnId)
            jottingRepository.deleteJottingsByAiTurnId(turnId)
        }

        // 先解析并返回带占位图的消息(立即显示文本)
        val messages = parseOfflineResponseWithPlaceholder(aiResponseJson, contactId, turnId)

        // 线下模式不启用打字延迟：通常只有单条文本气泡，且图片走 generateImageAsync 独立异步流程，
        // 因此这里保持原有的一次性写库行为（useTypingDelay=false，无内联 asyncTasks）。
        if (originalMessageIdToUpdate != null) {
            handleRegenerate(contactId, originalMessageIdToUpdate, messages, aiResponseJson, turnId, useTypingDelay = false, asyncTasks = emptyList())
        } else {
            handleNewMessages(messages, aiResponseJson, turnId, useTypingDelay = false, asyncTasks = emptyList())
        }

        // 异步生成图片(不阻塞返回)
        generateImageAsync(aiResponseJson, contactId, turnId)

        return ExecutionResult(messages, aiResponseJson)
    }

    private suspend fun executeOnlineMode(
        aiResponseJson: String,
        contactId: String,
        originalMessageIdToUpdate: String?
    ): ExecutionResult {
        val turnId = determineTurnId(contactId, originalMessageIdToUpdate)

        if (originalMessageIdToUpdate != null) {
            heartbeatRepository.deleteHeartbeatsByAiTurnId(turnId)
            jottingRepository.deleteJottingsByAiTurnId(turnId)
        }

        val actions = AiActionParser.parseAiActions(aiResponseJson)
        val executionResult = actionExecutor.executeActions(context, actions, contactId, turnId)
        val messages = executionResult.messages

        // 线上模式启用打字延迟：多条气泡逐条延迟落库，模拟真人打字节奏。
        // 异步任务（生图等）随消息一并传入，由写库环节在所有气泡落库完成后再启动，
        // 避免生图任务在占位图气泡尚未写入数据库时去更新（导致更新 0 行、图片丢失）。
        // handle 返回的 typingCompletion 在最后一条气泡落库后 complete，透传给调用方用于关闭打字提示。
        val typingCompletion: CompletableDeferred<Unit> = if (originalMessageIdToUpdate != null) {
            handleRegenerate(contactId, originalMessageIdToUpdate, messages, aiResponseJson, turnId, useTypingDelay = true, asyncTasks = executionResult.asyncTasks)
        } else {
            handleNewMessages(messages, aiResponseJson, turnId, useTypingDelay = true, asyncTasks = executionResult.asyncTasks)
        }

        return ExecutionResult(messages, aiResponseJson, executionResult.incomingCallContactId, typingCompletion)
    }

    private suspend fun determineTurnId(contactId: String, originalMessageIdToUpdate: String?): String {
        if (originalMessageIdToUpdate != null) {
            val chatHistory = chatRepository.getMessagesForContact(contactId).first()
            val originalMessage = chatHistory.find { it.id == originalMessageIdToUpdate }
            if (originalMessage?.aiTurnId != null) {
                return originalMessage.aiTurnId!!
            }
        }
        return UUID.randomUUID().toString()
    }

    private suspend fun parseOfflineResponse(
        json: String,
        contactId: String,
        turnId: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val cleanedJson = json.lines()
                .dropWhile { it.trim().startsWith("```") }
                .takeWhile { !it.trim().startsWith("```") }
                .joinToString("\n").trim()
            val finalJson = if (cleanedJson.isBlank()) json else cleanedJson

            val type = object : TypeToken<List<Map<String, String>>>() {}.type
            val responseArray = gson.fromJson<List<Map<String, String>>>(finalJson, type)

            val textPart = responseArray.find { it["type"] == "offline_text" || it["type"] == "text" }
            val imagePart = responseArray.find { it["type"] == "naiimag" }
            
            Log.d("ExecuteAiResponse", "解析离线响应: 找到${responseArray.size}个部分")
            Log.d("ExecuteAiResponse", "文本部分: ${textPart != null}, 图片部分: ${imagePart != null}")

            textPart?.get("content")?.let { text ->
                messages.add(ChatMessage(
                    contactId = contactId,
                    type = "offline_text",
                    role = "assistant",
                    content = text
                ))
            }

            imagePart?.get("prompt")?.let { prompt ->
                // 真正生成图片而不是占位
                val personProfile = personProfileRepository.getPersonProfileById(contactId)
                if (personProfile != null) {
                    try {
                        val imageBase64 = NovelAiService.generateImage(prompt, personProfile, gson)
                        messages.add(ChatMessage(
                            contactId = contactId,
                            type = "naiimag",
                            role = "assistant",
                            content = prompt, // 存储提示词以便后续编辑
                            imageUrl = imageBase64 // 真实图片数据,会在ChatRepository中转换为文件
                        ))
                        Log.i("ExecuteAiResponse", "离线模式图片生成成功")
                    } catch (e: Exception) {
                        // 生成失败,显示占位图
                        messages.add(ChatMessage(
                            contactId = contactId,
                            type = "naiimag",
                            role = "assistant",
                            content = prompt, // 存储提示词以便后续编辑
                            imageUrl = "error_placeholder"
                        ))
                        Log.e("ExecuteAiResponse", "离线模式图片生成失败", e)
                    }
                } else {
                    // 联系人不存在,显示占位图
                    messages.add(ChatMessage(
                        contactId = contactId,
                        type = "naiimag",
                        role = "assistant",
                        content = prompt, // 存储提示词以便后续编辑
                        imageUrl = "error_placeholder"
                    ))
                    Log.e("ExecuteAiResponse", "离线模式图片生成失败: 联系人不存在")
                }
            }
        } catch (e: Exception) {
            messages.add(ChatMessage(
                contactId = contactId,
                type = "error",
                role = "assistant",
                content = "[AI响应格式错误]"
            ))
        }
        return messages
    }
    
    /**
     * 解析离线响应,图片使用占位符(立即返回,不等待生成)
     */
    private suspend fun parseOfflineResponseWithPlaceholder(
        json: String,
        contactId: String,
        turnId: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val finalJson = extractOfflineJsonArray(json)
            val responseArray = JsonParser.parseString(finalJson).asJsonArray

            val textPart = responseArray.firstOrNull { element ->
                element.asJsonObjectOrNull()?.getStringOrNull("type") in listOf("offline_text", "text")
            }?.asJsonObjectOrNull()
            val voicePart = responseArray.firstOrNull { element ->
                element.asJsonObjectOrNull()?.getStringOrNull("type") == "voice_message"
            }?.asJsonObjectOrNull()
            val imagePart = responseArray.firstOrNull { element ->
                element.asJsonObjectOrNull()?.getStringOrNull("type") == "naiimag"
            }?.asJsonObjectOrNull()

            // 立即添加文本消息。优先显示普通文本,若只有语音消息则回退显示语音转写内容,避免聊天框空白。
            val visibleText = textPart?.getStringOrNull("content") ?: voicePart?.getStringOrNull("content")
            visibleText?.let { text ->
                messages.add(ChatMessage(
                    contactId = contactId,
                    type = "offline_text",
                    role = "assistant",
                    content = text
                ))
            }

            // 添加占位图消息(后台生成)
            imagePart?.getStringOrNull("prompt")?.let { prompt ->
                messages.add(ChatMessage(
                    contactId = contactId,
                    type = "naiimag",
                    role = "assistant",
                    content = prompt, // 存储提示词以便后续编辑
                    imageUrl = "generating_placeholder" // 占位符,表示正在生成
                ))
            }
        } catch (e: Exception) {
            messages.add(ChatMessage(
                contactId = contactId,
                type = "error",
                role = "assistant",
                content = "[AI响应格式错误]"
            ))
            Log.e("ExecuteAiResponse", "解析离线响应失败", e)
        }
        return messages
    }
    
    /**
     * 异步生成图片并更新数据库
     */
    private suspend fun generateImageAsync(
        json: String,
        contactId: String,
        turnId: String
    ) {
        try {
            val finalJson = extractOfflineJsonArray(json)
            val responseArray = JsonParser.parseString(finalJson).asJsonArray

            val imagePart = responseArray.firstOrNull { element ->
                element.asJsonObjectOrNull()?.getStringOrNull("type") == "naiimag"
            }?.asJsonObjectOrNull()
            val prompt = imagePart?.getStringOrNull("prompt") ?: return

            val personProfile = personProfileRepository.getPersonProfileById(contactId) ?: return
            
            // 生成图片
            val imageBase64 = try {
                NovelAiService.generateImage(prompt, personProfile, gson)
            } catch (e: Exception) {
                Log.e("ExecuteAiResponse", "异步图片生成失败", e)
                "error_placeholder"
            }

            // 查找并更新对应的消息
            val messages = chatRepository.getMessagesForContact(contactId).first()
            val imageMessage = messages.findLast {
                it.aiTurnId == turnId && it.type == "naiimag" && it.imageUrl == "generating_placeholder"
            }
            
            if (imageMessage != null) {
                // 【修复】更新图片时保留原有的content（提示词），这样用户可以编辑生图提示词
                val updatedMessage = imageMessage.copy(
                    imageUrl = imageBase64,
                    content = imageMessage.content // 保留提示词
                )
                chatRepository.updateMessage(updatedMessage)
                Log.i("ExecuteAiResponse", "异步图片生成完成并更新")
            }
        } catch (e: Exception) {
            Log.e("ExecuteAiResponse", "异步生成图片时出错", e)
        }
    }

    private suspend fun handleRegenerate(
        contactId: String,
        originalMessageIdToUpdate: String,
        newMessages: List<ChatMessage>,
        rawJson: String,
        turnId: String,
        useTypingDelay: Boolean,
        asyncTasks: List<suspend () -> Unit>
    ): CompletableDeferred<Unit> {
        val chatHistory = chatRepository.getMessagesForContact(contactId).first()
        val historyHolder = chatHistory.findLast { it.aiTurnId == turnId }
        val inheritedVersions = historyHolder?.aiResponseVersions ?: emptyList()

        // 调试日志:检查版本历史继承情况
        android.util.Log.d("ExecuteAiResponse", "重说功能 - turnId: $turnId")
        android.util.Log.d("ExecuteAiResponse", "找到的历史持有者: ${historyHolder?.id}")
        android.util.Log.d("ExecuteAiResponse", "继承的版本数量: ${inheritedVersions.size}")

        // 【修复】只存储精简的版本数据,不包含base64图片
        val newVersionJson = createVersionData(newMessages)

        val finalVersions = inheritedVersions + newVersionJson

        // 调试日志:检查最终版本列表
        android.util.Log.d("ExecuteAiResponse", "新版本JSON长度: ${newVersionJson.length}")
        android.util.Log.d("ExecuteAiResponse", "最终版本数量: ${finalVersions.size}")
        android.util.Log.d("ExecuteAiResponse", "显示版本索引: ${finalVersions.size - 1}")

        val orderedMessages: List<ChatMessage> = assignSequentialTimestamps(newMessages)
        val lastMessage = orderedMessages.last().copy(
            aiResponseVersions = finalVersions,
            displayedResponseIndex = finalVersions.size - 1,
            aiTurnId = turnId
        )
        val otherMessages = orderedMessages.dropLast(1).map { it.copy(aiTurnId = turnId) }
        val finalMessages = otherMessages + lastMessage

        // 重说会先删除旧轮次气泡，再写入新气泡。删除必须在写入前同步完成，避免新旧气泡同时出现。
        val messagesToDelete = chatHistory.filter { it.aiTurnId == turnId }
        chatRepository.deleteMessages(messagesToDelete)

        return insertMessagesWithOptionalTypingDelay(
            contactId = contactId,
            finalMessages = finalMessages,
            useTypingDelay = useTypingDelay,
            asyncTasks = asyncTasks
        )
    }

    private suspend fun handleNewMessages(
        messages: List<ChatMessage>,
        rawJson: String,
        turnId: String,
        useTypingDelay: Boolean,
        asyncTasks: List<suspend () -> Unit>
    ): CompletableDeferred<Unit> {
        if (messages.isEmpty()) return CompletableDeferred(Unit)

        // 【修复】只存储精简的版本数据,不包含base64图片
        val versionJson = createVersionData(messages)

        val orderedMessages: List<ChatMessage> = assignSequentialTimestamps(messages)
        val lastMessage = orderedMessages.last().copy(
            aiResponseVersions = listOf(versionJson),
            aiTurnId = turnId
        )
        val otherMessages = orderedMessages.dropLast(1).map { it.copy(aiTurnId = turnId) }
        val finalMessages = otherMessages + lastMessage

        return insertMessagesWithOptionalTypingDelay(
            contactId = messages.first().contactId,
            finalMessages = finalMessages,
            useTypingDelay = useTypingDelay,
            asyncTasks = asyncTasks
        )
    }

    /**
     * 按需带打字延迟地逐条写入气泡，模拟真人打字节奏。
     *
     * useTypingDelay = false（线下模式/重说不需要节奏）时，行为与旧逻辑完全一致：
     * 同步逐条写库 + 触发自动 TTS + 顺序执行异步任务，全部在当前协程内完成后返回。
     *
     * useTypingDelay = true（线上模式）时：
     * - 第一条气泡立即同步写入，保证打字提示结束时聊天框已有内容，不会闪空。
     * - 其余气泡在 applicationScope 中按内容长度计算的延迟逐条写入。该作用域为 Application 级，
     *   不随聊天页销毁取消，也不在 withTimeout 的子协程内，因此长回复的累积延迟不会触发请求超时。
     * - 异步任务（生图等）与自动 TTS 均在所有气泡落库完成后才启动，避免占位图气泡尚未入库时
     *   就被异步更新（导致更新 0 行、图片丢失）。
     *
     * 返回值：打字延迟完成信号（方案 A）。当"最后一条气泡落库"后 complete，
     * 调用方据此推迟关闭"正在输入"提示，使提示一直亮到最后一条气泡冒出。
     * 注意只等气泡写完，不等 TTS/异步任务——后者继续后台运行不影响提示。
     * 不启用打字延迟时返回的 deferred 在本函数返回前即已完成，await 立即返回。
     */
    private suspend fun insertMessagesWithOptionalTypingDelay(
        contactId: String,
        finalMessages: List<ChatMessage>,
        useTypingDelay: Boolean,
        asyncTasks: List<suspend () -> Unit>
    ): CompletableDeferred<Unit> {
        val typingCompletion: CompletableDeferred<Unit> = CompletableDeferred()
        if (finalMessages.isEmpty()) {
            typingCompletion.complete(Unit)
            return typingCompletion
        }

        if (!useTypingDelay) {
            finalMessages.forEach { message: ChatMessage -> chatRepository.insertMessage(message) }
            typingCompletion.complete(Unit)
            enqueueAutoTtsGeneration(contactId, finalMessages)
            runAsyncTasks(asyncTasks)
            return typingCompletion
        }

        // 第一条立即写入，保证 UI 不会在打字提示结束后短暂空白。
        chatRepository.insertMessage(finalMessages.first())

        val remainingMessages: List<ChatMessage> = finalMessages.drop(1)
        if (remainingMessages.isEmpty()) {
            typingCompletion.complete(Unit)
            enqueueAutoTtsGeneration(contactId, finalMessages)
            runAsyncTasks(asyncTasks)
            return typingCompletion
        }

        // 其余气泡放到 Application 作用域后台逐条延迟写入，当前协程（受 withTimeout 约束）立即返回。
        applicationScope.launch(Dispatchers.IO) {
            try {
                remainingMessages.forEach { message: ChatMessage ->
                    kotlinx.coroutines.delay(computeTypingDelayMillis(message))
                    chatRepository.insertMessage(message)
                }
            } finally {
                // 用 try/finally 保证即便写库中途异常，提示也不会永久卡在"正在输入"。
                typingCompletion.complete(Unit)
            }
            // 全部气泡落库后再触发 TTS 与异步任务，确保占位图等已存在于数据库中。
            enqueueAutoTtsGeneration(contactId, finalMessages)
            runAsyncTasks(asyncTasks)
        }
        return typingCompletion
    }

    /**
     * 顺序执行异步任务（生图、心声等），单个任务失败不影响其余任务。
     */
    private suspend fun runAsyncTasks(asyncTasks: List<suspend () -> Unit>) {
        asyncTasks.forEach { task: suspend () -> Unit ->
            try {
                task()
            } catch (e: Exception) {
                Log.e("ExecuteAiResponse", "执行异步任务出错", e)
            }
        }
    }

    /**
     * 根据气泡内容计算模拟真人打字所需的延迟。
     * 文本/语音气泡按字数线性增长；图片、表情等无文本气泡使用基础延迟。
     * 每字符延迟由用户在全局设置中可调（getChatTypingDelayPerCharMillis），数值越大越像真人慢打字；
     * 基础延迟与上下限仍为内置常量。最终结果钳制在 [MIN_TYPING_DELAY_MILLIS, MAX_TYPING_DELAY_MILLIS] 之间。
     */
    private fun computeTypingDelayMillis(message: ChatMessage): Long {
        val characterCount: Int = message.content?.length ?: 0
        val perCharMillis: Long = settingsRepository.getChatTypingDelayPerCharMillis().toLong()
        val rawDelay: Long = TYPING_BASE_DELAY_MILLIS + characterCount.toLong() * perCharMillis
        return rawDelay.coerceIn(MIN_TYPING_DELAY_MILLIS, MAX_TYPING_DELAY_MILLIS)
    }

    private suspend fun enqueueAutoTtsGeneration(contactId: String, messages: List<ChatMessage>): Unit {
        if (!settingsRepository.isAiReplyAutoTtsEnabled()) return
        val targetMessages: List<ChatMessage> = messages.filter { message: ChatMessage ->
            message.role == "assistant" && message.type == "voice_message" && !message.content.isNullOrBlank()
        }
        if (targetMessages.isEmpty()) return

        val model: String = settingsRepository.getTtsModel().trim()
        val apiKey: String = settingsRepository.getTtsApiKey().trim()
        if (model.isBlank() || apiKey.isBlank()) return

        val contact = personProfileRepository.getPersonProfileById(contactId)
        val voiceId: String = contact?.ttsVoiceId?.takeIf { configuredVoiceId: String -> configuredVoiceId.isNotBlank() }
            ?: settingsRepository.getTtsVoiceId()
        val voiceDescription: String = contact?.voiceDescription.orEmpty()
        val isStreaming: Boolean = settingsRepository.isTtsStreamingEnabled()

        targetMessages.forEach { message: ChatMessage ->
            applicationScope.launch(Dispatchers.IO) {
                val generatingMessage: ChatMessage = message.copy(
                    ttsGenerationStatus = "generating",
                    ttsModelId = model,
                    ttsVoiceId = voiceId,
                    ttsErrorMessage = null,
                    ttsIsStreaming = isStreaming
                )
                chatRepository.updateMessage(generatingMessage)
                val firstAttemptMessage: ChatMessage = synthesizeAndCacheVoiceMessage(
                    message = generatingMessage,
                    model = model,
                    voiceId = voiceId,
                    voiceDescription = voiceDescription,
                    isStreaming = isStreaming
                )
                if (firstAttemptMessage.ttsGenerationStatus == "failed") {
                    kotlinx.coroutines.delay(AUTO_TTS_RETRY_DELAY_MILLIS)
                    synthesizeAndCacheVoiceMessage(
                        message = firstAttemptMessage.copy(
                            ttsGenerationStatus = "generating",
                            ttsErrorMessage = null
                        ),
                        model = model,
                        voiceId = voiceId,
                        voiceDescription = voiceDescription,
                        isStreaming = isStreaming
                    )
                }
            }
        }
    }

    private suspend fun synthesizeAndCacheVoiceMessage(
        message: ChatMessage,
        model: String,
        voiceId: String,
        voiceDescription: String,
        isStreaming: Boolean
    ): ChatMessage {
        val isVoiceDesign: Boolean = model.contains("voicedesign", ignoreCase = true)
        val request: TtsSynthesisRequest = TtsSynthesisRequest(
            text = message.content.orEmpty(),
            model = model,
            voiceId = voiceId,
            isStreaming = false,
            description = if (isVoiceDesign) voiceDescription else "QQ 自动语音气泡：${message.contactId}"
        )
        val result: TtsSynthesisResult = aiRequestService.synthesizeSpeechWithLogging(request)
        val audioFile: java.io.File? = result.audioFile
        val updatedMessage: ChatMessage = if (audioFile != null && result.errorMessage == null) {
            message.copy(
                voiceAudioPath = audioFile.absolutePath,
                voiceDurationMillis = result.durationMillis,
                ttsGenerationStatus = "success",
                ttsModelId = result.model,
                ttsVoiceId = result.voiceId,
                ttsGeneratedAt = System.currentTimeMillis(),
                ttsErrorMessage = null,
                ttsIsStreaming = result.isStreaming
            )
        } else {
            message.copy(
                ttsGenerationStatus = "failed",
                ttsModelId = result.model,
                ttsVoiceId = result.voiceId,
                ttsGeneratedAt = System.currentTimeMillis(),
                ttsErrorMessage = result.errorMessage ?: "语音合成失败",
                ttsIsStreaming = result.isStreaming
            )
        }
        chatRepository.updateMessage(updatedMessage)
        return updatedMessage
    }

    private fun assignSequentialTimestamps(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) {
            return emptyList()
        }
        val baseTimestamp: Long = messages.minOf { it.timestamp }
        return messages.mapIndexed { index: Int, message: ChatMessage ->
            message.copy(timestamp = baseTimestamp + index.toLong())
        }
    }
    
    private fun hasValidExtractableResult(result: ExecutionResult): Boolean {
        return result.incomingCallContactId != null || result.messages.any { message: ChatMessage ->
            val hasVisibleContent: Boolean = !message.content.isNullOrBlank() || !message.imageUrl.isNullOrBlank() || !message.voiceAudioPath.isNullOrBlank()
            val isErrorLikeMessage: Boolean = message.type == "error" || isFailedAiResponseText(message.content) || message.content?.startsWith("[AI响应格式错误]") == true
            message.role == "assistant" && hasVisibleContent && !isErrorLikeMessage
        }
    }

    private fun isFailedAiResponse(aiResponseJson: String): Boolean {
        return extractFailedAiResponseTexts(aiResponseJson).any { text: String ->
            isFailedAiResponseText(text)
        }
    }

    private fun extractFailedAiResponseMessage(aiResponseJson: String): String {
        return extractFailedAiResponseTexts(aiResponseJson)
            .firstOrNull { text: String -> isFailedAiResponseText(text) }
            ?: "AI回复失败"
    }

    private fun extractFailedAiResponseTexts(aiResponseJson: String): List<String> {
        val fallbackText: String = aiResponseJson.trim()
        return try {
            val rootElement: JsonElement = JsonParser.parseString(fallbackText)
            when {
                rootElement.isJsonArray -> rootElement.asJsonArray.mapNotNull { element: JsonElement ->
                    element.asJsonObjectOrNull()?.getStringOrNull("content")
                }
                rootElement.isJsonObject -> {
                    val rootObject: JsonObject = rootElement.asJsonObject
                    val choicesArray: JsonArray? = rootObject.getAsJsonArrayOrNull("choices")
                    val choiceContent: String? = choicesArray
                        ?.firstOrNull()
                        ?.asJsonObjectOrNull()
                        ?.getAsJsonObjectOrNull("message")
                        ?.getStringOrNull("content")
                    val directContent: String? = rootObject.getStringOrNull("content")
                    listOfNotNull(choiceContent, directContent, fallbackText)
                }
                else -> listOf(fallbackText)
            }
        } catch (e: Exception) {
            listOf(fallbackText)
        }
    }

    private fun isFailedAiResponseText(text: String?): Boolean {
        val normalizedText: String = text?.trim().orEmpty()
        if (normalizedText.isBlank()) {
            return false
        }
        val hasWrappedRequestFailure: Boolean = normalizedText.contains("获取AI回复失败", ignoreCase = true) &&
            normalizedText.contains("请求失败", ignoreCase = true)
        val hasUpstreamFailure: Boolean = listOf(
            "system_disk_overloaded",
            "system disk overloaded",
            "upstream_error",
            "bad_response_status_code",
            "来自上游渠道的报错"
        ).any { keyword: String ->
            normalizedText.contains(keyword, ignoreCase = true)
        }
        val hasHttpFailureStatus: Boolean = Regex("请求失败:\\s*(5\\d{2}|429)").containsMatchIn(normalizedText)
        return hasWrappedRequestFailure || hasUpstreamFailure || hasHttpFailureStatus
    }

    private fun saveFollowUpPolicyIfPresent(contactId: String, aiResponseJson: String, anchorMessageId: String?) {
        val policy: FollowUpPolicy = extractFollowUpPolicy(aiResponseJson) ?: return
        followUpPolicyStore.savePolicy(contactId, anchorMessageId, policy)
        EventBus.post(QqEvent.FollowUpPolicyChanged(contactId))
    }

    private fun extractFollowUpPolicy(aiResponseJson: String): FollowUpPolicy? {
        val content: String = extractResponseContent(aiResponseJson)
        return try {
            val rootElement: JsonElement = JsonParser.parseString(content)
            if (!rootElement.isJsonObject) return null
            val rootObject: JsonObject = rootElement.asJsonObject
            val policyObject: JsonObject = rootObject.getAsJsonObjectOrNull("followUpPolicy") ?: return null
            val shouldFollowUp: Boolean = policyObject.getBooleanOrNull("shouldFollowUpIfUserSilentTooLong") ?: false
            val hint: String? = policyObject.getStringOrNull("followUpHint")
            FollowUpPolicy(shouldFollowUpIfUserSilentTooLong = shouldFollowUp, followUpHint = hint)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractResponseContent(aiResponseJson: String): String {
        var contentString: String = aiResponseJson.trim()
        try {
            val parsedResponse: JsonElement = JsonParser.parseString(contentString)
            if (parsedResponse.isJsonObject) {
                val responseObject: JsonObject = parsedResponse.asJsonObject
                val choicesArray: JsonArray? = responseObject.getAsJsonArrayOrNull("choices")
                val firstChoice: JsonObject? = choicesArray?.firstOrNull()?.asJsonObjectOrNull()
                val messageObject: JsonObject? = firstChoice?.getAsJsonObjectOrNull("message")
                val extractedContent: String? = messageObject?.getStringOrNull("content")
                if (!extractedContent.isNullOrBlank()) {
                    contentString = extractedContent.trim()
                }
            }
        } catch (e: Exception) {
            Log.d("ExecuteAiResponse", "追问策略解析：响应不是完整接口响应")
        }
        return contentString
    }

    /**
     * 提取离线模式可解析的 JSON 数组。
     * 兼容完整接口响应、Markdown 代码块、数组前导废话以及数组尾部重复的括号残留。
     */
    private fun extractOfflineJsonArray(json: String): String {
        var contentString = json.trim()

        try {
            val parsedResponse = JsonParser.parseString(contentString)
            if (parsedResponse.isJsonObject) {
                val responseObject = parsedResponse.asJsonObject
                val choicesArray = responseObject.getAsJsonArrayOrNull("choices")
                val firstChoice = choicesArray?.firstOrNull()?.asJsonObjectOrNull()
                val messageObject = firstChoice?.getAsJsonObjectOrNull("message")
                val extractedContent = messageObject?.getStringOrNull("content")
                if (!extractedContent.isNullOrBlank()) {
                    contentString = extractedContent.trim()
                }
            }
        } catch (e: Exception) {
            Log.d("ExecuteAiResponse", "离线响应不是完整接口响应,继续按内容解析")
        }

        if (contentString.startsWith("```")) {
            contentString = contentString.substringAfter("```").substringBeforeLast("```").trim()
            if (contentString.startsWith("json")) {
                contentString = contentString.substringAfter("json").trim()
            }
        }

        val arrayStartIndex = contentString.indexOf('[')
        if (arrayStartIndex < 0) {
            throw IllegalArgumentException("离线响应中未找到JSON数组")
        }

        val arrayEndIndex = findBalancedJsonArrayEnd(contentString, arrayStartIndex)
        if (arrayEndIndex < 0) {
            throw IllegalArgumentException("离线响应中的JSON数组未闭合")
        }

        val jsonArrayString = contentString.substring(arrayStartIndex, arrayEndIndex + 1).trim()
        val parsedArray = JsonParser.parseString(jsonArrayString)
        if (!parsedArray.isJsonArray) {
            throw IllegalArgumentException("离线响应提取结果不是JSON数组")
        }
        return jsonArrayString
    }

    /**
     * 查找完整 JSON 数组的结束位置。
     * 该方法会跳过字符串内部的括号,因此可以安全裁掉数组后面的重复 ]} 残留。
     */
    private fun findBalancedJsonArrayEnd(text: String, startIndex: Int): Int {
        var depth = 0
        var isInsideString = false
        var isEscaped = false

        for (index in startIndex until text.length) {
            val currentChar = text[index]
            if (isInsideString) {
                when {
                    isEscaped -> isEscaped = false
                    currentChar == '\\' -> isEscaped = true
                    currentChar == '"' -> isInsideString = false
                }
                continue
            }

            when (currentChar) {
                '"' -> isInsideString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }

        return -1
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return if (isJsonObject) asJsonObject else null
    }

    private fun JsonObject.getAsJsonArrayOrNull(memberName: String): JsonArray? {
        val element = get(memberName) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.getAsJsonObjectOrNull(memberName: String): JsonObject? {
        val element = get(memberName) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.getStringOrNull(memberName: String): String? {
        val element = get(memberName) ?: return null
        return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else null
    }

    private fun JsonObject.getBooleanOrNull(memberName: String): Boolean? {
        val element = get(memberName) ?: return null
        return if (element.isJsonPrimitive) element.asBoolean else null
    }
    
    /**
     * 创建版本数据JSON,只包含必要信息,不包含base64图片
     * 这样可以避免数据库行过大的问题
     */
    private companion object {
        private const val AUTO_TTS_RETRY_DELAY_MILLIS: Long = 3000L

        // 打字延迟参数：模拟真人打字节奏，按气泡字数线性增长并钳制在合理区间。
        // 每字符延迟（TYPING_PER_CHAR_MILLIS）已迁移到全局设置，由用户编辑，见 SettingsRepository.getChatTypingDelayPerCharMillis()。
        private const val TYPING_BASE_DELAY_MILLIS: Long = 400L
        private const val MIN_TYPING_DELAY_MILLIS: Long = 300L
        private const val MAX_TYPING_DELAY_MILLIS: Long = 4000L
    }

    private fun createVersionData(messages: List<ChatMessage>): String {
        // 提取文本内容和图片URL(文件路径,不是base64)
        val textMessage = messages.find { it.role == "assistant" && !it.content.isNullOrBlank() }
        val imageMessage = messages.find { it.imageUrl != null }
        
        val versionData = OfflineVersionData(
            textContent = textMessage?.content,
            imageUrl = imageMessage?.imageUrl?.let { url ->
                // 确保只存储文件路径,不存储base64
                if (url.startsWith("data:image")) null else url
            }
        )
        return gson.toJson(versionData)
    }
}
