package com.susking.ephone_s.qq.domain.manager

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.QuotedMessage
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.aidata.prompt.MessageGroupAnalysis
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.aidata.prompt.PromptComponentBuilder
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.susking.ephone_s.core.util.CallBusyState
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.IncomingCallEvent
import com.susking.ephone_s.qq.domain.use_case.ai.ExecuteAiResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.RegenerateResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.RequestAiResponseUseCase
import com.susking.ephone_s.qq.domain.use_case.ai.SwitchResponseVersionUseCase
import com.susking.ephone_s.qq.domain.use_case.chat.ClearChatHistoryUseCase
import com.susking.ephone_s.qq.domain.use_case.chat.SendMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.message.DeleteMessageUseCase
import com.susking.ephone_s.qq.domain.use_case.message.EditMessageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 聊天管理器
 * 
 * 合并了以下Manager的功能:
 * - ChatManager: 消息CRUD、历史管理
 * - AiManager: AI对话、响应生成、版本管理
 * - FeedManager: 动态管理(轻量,已集成)
 * 
 * 职责:
 * 1. 消息发送、编辑、删除
 * 2. AI对话请求和响应处理
 * 3. 聊天历史管理
 * 4. 打字状态管理
 * 
 * 通信:
 * - 发送 ContactsChanged 事件,由 QqContactManager 监听并刷新列表
 */
@Singleton
class QqChatManager @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val editMessageUseCase: EditMessageUseCase,
    private val clearChatHistoryUseCase: ClearChatHistoryUseCase,
    private val requestAiResponseUseCase: RequestAiResponseUseCase,
    private val regenerateResponseUseCase: RegenerateResponseUseCase,
    private val executeAiResponseUseCase: ExecuteAiResponseUseCase,
    private val switchResponseVersionUseCase: SwitchResponseVersionUseCase,
    private val followUpPolicyStore: FollowUpPolicyStore,
    private val settingsRepository: SettingsRepository,
    private val performPatActionUseCase: com.susking.ephone_s.qq.domain.use_case.contact.PerformPatActionUseCase,
    private val coroutineScope: CoroutineScope
) {

    // ==================== 消息管理状态 ====================
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // ==================== AI管理状态 ====================
    
    private val _isAiTyping = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isAiTyping: StateFlow<Map<String, Boolean>> = _isAiTyping.asStateFlow()

    // 消息分组分析事件（在提示词确认之前触发）
    private val _messageGroupsEvent = MutableLiveData<Event<MessageGroupAnalysis>>()
    val messageGroupsEvent: LiveData<Event<MessageGroupAnalysis>> = _messageGroupsEvent
    
    private val _promptConfirmationEvent = MutableLiveData<Event<AiPromptRequest>>()
    val promptConfirmationEvent: LiveData<Event<AiPromptRequest>> = _promptConfirmationEvent

    // 视频通话请求事件
    private val _incomingCallEvent = MutableLiveData<Event<String>>()
    val incomingCallEvent: LiveData<Event<String>> = _incomingCallEvent

    // ==================== 通用状态 ====================
    
    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> = _toastEvent

    // ==================== AI内部状态 ====================
    
    private val lastRawAiResponses = mutableMapOf<String, String>()
    private val runningAiRequestContacts: MutableSet<String> = mutableSetOf()
    private var pendingPrompt: AiPromptRequest? = null
    private var pendingContactId: String? = null
    private var pendingIsOffline: Boolean = false
    private var pendingMessageIdToUpdate: String? = null

    // ==================== 消息管理功能 ====================

    /**
     * 获取指定联系人的聊天消息Flow
     */
    fun getMessagesForContact(contactId: String): Flow<List<ChatMessage>> {
        return chatRepository.getMessagesForContact(contactId)
    }

    /**
     * 监听指定联系人最新一页聊天记录，避免进入聊天时加载全量历史。
     */
    fun getLatestMessagesPagedFlow(contactId: String, limit: Int): Flow<List<ChatMessage>> {
        return chatRepository.getLatestMessagesPagedFlow(contactId, limit)
    }

    /**
     * 分页获取指定联系人的聊天记录，用于上滑加载旧消息。
     */
    suspend fun getMessagesPaged(contactId: String, limit: Int, offset: Int): List<ChatMessage> {
        return chatRepository.getMessagesPaged(contactId, limit, offset)
    }

    /**
     * 发送消息
     */
    fun sendMessage(
        contactId: String,
        text: String? = null,
        imageUrl: String? = null,
        stickerUrl: String? = null,
        stickerName: String? = null,
        type: String? = null,
        amount: Double? = null,
        notes: String? = null,
        productInfo: String? = null,
        voiceAudioPath: String? = null,
        voiceDurationMillis: Long? = null,
        quotedMessage: QuotedMessage? = null
    ) {
        coroutineScope.launch {
            sendMessageUseCase(
                contactId = contactId,
                text = text,
                imageUrl = imageUrl,
                stickerUrl = stickerUrl,
                stickerName = stickerName,
                type = type,
                amount = amount,
                notes = notes,
                productInfo = productInfo,
                voiceAudioPath = voiceAudioPath,
                voiceDurationMillis = voiceDurationMillis,
                quotedMessage = quotedMessage
            ).onSuccess {
                // 成功,UI通过Flow自动更新
                // 通知联系人列表刷新
                EventBus.post(QqEvent.MessageSent(contactId))
                EventBus.post(QqEvent.ContactsChanged)
            }.onFailure { error ->
                _toastEvent.postValue(Event(error.message ?: "发送失败"))
            }
        }
    }

    /**
     * 删除消息
     */
    fun deleteMessage(message: ChatMessage) {
        coroutineScope.launch {
            deleteMessageUseCase(message)
                .onSuccess {
                    // 成功,通知联系人列表刷新最新消息
                    EventBus.post(QqEvent.ContactsChanged)
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("删除失败: ${error.message}"))
                }
        }
    }

    /**
     * 批量删除消息
     */
    fun deleteMessages(messages: List<ChatMessage>) {
        coroutineScope.launch {
            messages.forEach { message ->
                deleteMessageUseCase(message)
            }
            // 批量删除完成后,通知联系人列表刷新
            EventBus.post(QqEvent.ContactsChanged)
        }
    }

    /**
     * 编辑消息
     */
    fun editMessage(messageId: String, contactId: String, newText: String) {
        coroutineScope.launch {
            editMessageUseCase(messageId, contactId, newText)
                .onSuccess {
                    // 成功
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("编辑失败: ${error.message}"))
                }
        }
    }

    /**
     * 更新表情消息URL
     */
    fun updateStickerUrl(messageId: String, contactId: String, newUrl: String, newName: String) {
        coroutineScope.launch {
            editMessageUseCase.updateStickerUrl(messageId, contactId, newUrl, newName)
                .onSuccess {
                    // 成功
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("更新失败: ${error.message}"))
                }
        }
    }

    /**
     * 更新图片描述
     */
    fun updateImageDescription(messageId: String, contactId: String, newDescription: String?) {
        coroutineScope.launch {
            try {
                chatRepository.updateImageDescription(messageId, newDescription)
                // 成功，UI通过Flow自动更新
            } catch (e: Exception) {
                _toastEvent.postValue(Event("更新图片描述失败: ${e.message}"))
            }
        }
    }

    /**
     * 更新消息
     */
    fun updateMessage(message: ChatMessage) {
        coroutineScope.launch {
            chatRepository.updateMessage(message)
        }
    }

    /**
     * 清空聊天记录
     */
    fun clearHistory(contactId: String) {
        coroutineScope.launch {
            clearChatHistoryUseCase(contactId)
                .onSuccess {
                    _toastEvent.postValue(Event("已清空聊天记录"))
                    EventBus.post(QqEvent.ContactsChanged)
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("清空失败: ${error.message}"))
                }
        }
    }

    /**
     * 发送表情
     */
    fun sendSticker(contactId: String, sticker: com.susking.ephone_s.aidata.data.local.entity.StickerEntity) {
        sendMessage(
            contactId = contactId,
            text = null,
            imageUrl = null,
            stickerUrl = sticker.url,
            stickerName = sticker.name,
            type = "sticker"
        )
    }

    /**
     * 执行拍一拍动作
     * 委托给PerformPatActionUseCase处理
     */
    fun performPatAction(
        contactId: String,
        userNickname: String,
        displayNameForUI: String,
        characterOriginalName: String,
        suffix: String
    ) {
        coroutineScope.launch {
            performPatActionUseCase(
                contactId = contactId,
                userNickname = userNickname,
                displayNameForUI = displayNameForUI,
                suffix = suffix
            ).onSuccess {
                // 通知联系人列表刷新
                EventBus.post(QqEvent.MessageSent(contactId))
                EventBus.post(QqEvent.ContactsChanged)
            }.onFailure { error ->
                _toastEvent.postValue(Event("拍一拍失败: ${error.message}"))
            }
        }
    }

    /**
     * 发送礼物消息
     * @param contactId 联系人ID
     * @param giftItemId 礼物物品ID
     * @param giftName 礼物名称
     * @param giftImageUrl 礼物图片URL
     * @param giftValue 礼物价值
     * @param giftNote 礼物备注
     */
    fun sendGiftMessage(
        contactId: String,
        giftItemId: Long,
        giftName: String,
        giftImageUrl: String,
        giftValue: Double,
        giftNote: String? = null
    ) {
        coroutineScope.launch {
            val message = ChatMessage(
                contactId = contactId,
                type = "gift",
                role = "user",
                content = "赠送了礼物",
                giftItemId = giftItemId,
                giftName = giftName,
                giftImageUrl = giftImageUrl,
                giftValue = giftValue,
                giftNote = giftNote,
                timestamp = System.currentTimeMillis()
            )
            
            chatRepository.insertMessage(message)
            
            // 通知联系人列表刷新
            EventBus.post(QqEvent.MessageSent(contactId))
            EventBus.post(QqEvent.ContactsChanged)
        }
    }

    // ==================== AI对话功能 ====================

    /**
     * 请求AI响应
     */
    fun requestAiResponse(contactId: String) {
        coroutineScope.launch {
            if (!tryStartAiRequest(contactId)) return@launch
            setAiTypingState(contactId, true)
            
            try {
                // 1. 先进行消息分组分析
                val allMessages = chatRepository.getMessagesForContact(contactId).first()
                val analysis = PromptComponentBuilder.analyzeRecentMessageGroups(allMessages)
                
                // 2. 获取AI提示词
                requestAiResponseUseCase(contactId)
                    .onSuccess { prompt ->
                        pendingPrompt = prompt
                        pendingContactId = contactId
                        
                        // 3. 先发出消息分组事件
                        _messageGroupsEvent.postValue(Event(analysis))
                    }
                    .onFailure { error ->
                        _toastEvent.postValue(Event("请求失败: ${error.message}"))
                    }
            } catch (e: Exception) {
                _toastEvent.postValue(Event("分析失败: ${e.message}"))
            }
            
            setAiTypingState(contactId, false)
            finishAiRequest(contactId)
        }
    }

    /**
     * 重试AI响应（错误气泡的“重试”按钮触发）。
     * 与 requestAiResponse 不同：不弹出消息分组/提示词确认对话框，
     * 组合提示词后直接经由 brain 链路执行请求，符合“请求必须走 brain”的规范。
     *
     * @param discardedAiTurnIds 本次重试要丢弃的 AI 回复轮次 ID 集合。若被丢弃的轮在同一轮里
     *        既报了错又写过语义账本，需要先把这部分语义更新回退一步，否则新提示词会读到被丢弃轮
     *        污染后的语义状态（当前互动语义/锚点/线索/关键词）。空集合时回退为安全空操作。
     */
    fun retryAiResponse(contactId: String, discardedAiTurnIds: Set<String> = emptySet()) {
        coroutineScope.launch {
            if (!tryStartAiRequest(contactId)) return@launch
            setAiTypingState(contactId, true)
            try {
                // 必须在 requestAiResponseUseCase 组合提示词(读取语义账本)之前完成回退。
                if (discardedAiTurnIds.isNotEmpty()) {
                    val reverted: Boolean = AiDataApi.getContactSemanticStateRepository()
                        .revertSemanticStateForTurns(contactId, discardedAiTurnIds)
                    Log.d("QqChatManager", "重试语义回退: 被丢弃轮=${discardedAiTurnIds.size}, 是否回退=$reverted")
                }
                requestAiResponseUseCase(contactId)
                    .onSuccess { prompt ->
                        executeAiResponseWithConfiguredTimeout(prompt, contactId, false, null)
                            .onSuccess { result ->
                                lastRawAiResponses[contactId] = result.rawJson
                                result.incomingCallContactId?.let { callContactId ->
                                    _incomingCallEvent.postValue(Event(callContactId))
                                    // 同步发送全局来电事件,使非聊天页(桌面等)也能由 MainActivity 拉起来电界面
                                    EventBus.post(IncomingCallEvent(callContactId))
                                }
                                EventBus.post(QqEvent.AiResponseCompleted(contactId))
                                EventBus.post(QqEvent.ContactsChanged)
                            }
                            .onFailure { error ->
                                _toastEvent.postValue(Event("重试失败: ${error.message}"))
                            }
                    }
                    .onFailure { error ->
                        _toastEvent.postValue(Event("重试请求失败: ${error.message}"))
                    }
            } finally {
                finishAiRequest(contactId)
                setAiTypingState(contactId, false)
            }
        }
    }

    fun requestAutoAiResponse(contactId: String, extraInstruction: String? = null) {
        coroutineScope.launch {
            if (CallBusyState.isBusyWith(contactId)) {
                Log.d("QqChatManager", "自动回复诊断: 自动请求被拦截，原因=正在与该联系人通话中 contactId=$contactId")
                return@launch
            }
            val hasUnseenMessage: Boolean = chatRepository.hasUnseenUserMessagesForContact(contactId)
            Log.d("QqChatManager", "自动回复诊断: requestAutoAiResponse 入口 contactId=$contactId hasUnseen=$hasUnseenMessage hasExtra=${!extraInstruction.isNullOrBlank()}")
            if (!hasUnseenMessage && extraInstruction.isNullOrBlank()) {
                Log.d("QqChatManager", "自动回复诊断: 自动请求被拦截，原因=数据库没有未看见用户消息且没有额外指令")
                return@launch
            }
            if (!tryStartAiRequest(contactId)) {
                Log.d("QqChatManager", "自动回复诊断: 自动请求被拦截，原因=同联系人已有运行中请求或待确认提示词")
                return@launch
            }
            setAiTypingState(contactId, true)
            try {
                requestAiResponseUseCase(contactId, extraInstruction)
                    .onSuccess { prompt ->
                        Log.d("QqChatManager", "自动回复诊断: 自动请求提示词生成成功 contactId=$contactId")
                        executeAiResponseWithConfiguredTimeout(prompt, contactId, false, null)
                            .onSuccess { result ->
                                lastRawAiResponses[contactId] = result.rawJson
                                result.incomingCallContactId?.let { callContactId ->
                                    _incomingCallEvent.postValue(Event(callContactId))
                                    // 同步发送全局来电事件,使非聊天页(桌面等)也能由 MainActivity 拉起来电界面
                                    EventBus.post(IncomingCallEvent(callContactId))
                                }
                                EventBus.post(QqEvent.AiResponseCompleted(contactId))
                                EventBus.post(QqEvent.ContactsChanged)
                            }
                            .onFailure { error ->
                                Log.e("QqChatManager", "自动回复诊断: 自动回复执行失败 contactId=$contactId", error)
                                _toastEvent.postValue(Event("自动回复失败: ${error.message}"))
                            }
                    }
                    .onFailure { error ->
                        Log.e("QqChatManager", "自动回复诊断: 自动回复提示词生成失败 contactId=$contactId", error)
                        _toastEvent.postValue(Event("自动回复请求失败: ${error.message}"))
                    }
            } finally {
                finishAiRequest(contactId)
                setAiTypingState(contactId, false)
                if (chatRepository.hasUnseenUserMessagesForContact(contactId)) {
                    EventBus.post(QqEvent.MessageSent(contactId))
                }
            }
        }
    }

    fun requestSilentFollowUpIfAllowed(contactId: String) {
        coroutineScope.launch {
            if (CallBusyState.isBusyWith(contactId)) {
                android.util.Log.d("QqChatManager", "自动回复诊断: 静默追问被拦截，原因=正在与该联系人通话中 contactId=$contactId")
                return@launch
            }
            if (!followUpPolicyStore.canFollowUp(contactId)) {
                android.util.Log.d("QqChatManager", "自动回复诊断: 静默追问被拦截，原因=追问策略不允许 contactId=$contactId")
                return@launch
            }
            if (!tryStartAiRequest(contactId)) {
                android.util.Log.d("QqChatManager", "自动回复诊断: 静默追问被拦截，原因=同联系人已有运行中请求或待确认提示词 contactId=$contactId")
                return@launch
            }
            val followUpCount: Int = followUpPolicyStore.getFollowUpCountAfterLastUserMessage(contactId)
            val hint: String = followUpPolicyStore.getFollowUpHint(contactId)
            val finalChanceInstruction: String = if (followUpCount >= 1) "这是你最后一次追问机会。" else ""
            val extraInstruction: String = "用户已经较长时间没有回复。请结合你上一轮回复和以下追问建议，自然地追问一次；不要显得压迫或催促。$finalChanceInstruction\n追问建议：$hint"
            setAiTypingState(contactId, true)
            try {
                requestAiResponseUseCase(contactId, extraInstruction)
                    .onSuccess { prompt ->
                        executeAiResponseWithConfiguredTimeout(prompt, contactId, false, null)
                            .onSuccess { result ->
                                followUpPolicyStore.markFollowUpSent(contactId)
                                lastRawAiResponses[contactId] = result.rawJson
                                result.incomingCallContactId?.let { callContactId ->
                                    _incomingCallEvent.postValue(Event(callContactId))
                                    // 同步发送全局来电事件,使非聊天页(桌面等)也能由 MainActivity 拉起来电界面
                                    EventBus.post(IncomingCallEvent(callContactId))
                                }
                                EventBus.post(QqEvent.AiResponseCompleted(contactId))
                                EventBus.post(QqEvent.ContactsChanged)
                            }
                            .onFailure { error ->
                                _toastEvent.postValue(Event("自动追问失败: ${error.message}"))
                            }
                    }
                    .onFailure { error ->
                        _toastEvent.postValue(Event("自动追问请求失败: ${error.message}"))
                    }
            } finally {
                finishAiRequest(contactId)
                setAiTypingState(contactId, false)
            }
        }
    }
    
    /**
     * 确认消息分组后，继续显示提示词确认对话框
     */
    fun proceedToPromptConfirmation() {
        val prompt = pendingPrompt ?: return
        _promptConfirmationEvent.postValue(Event(prompt))
    }

    /**
     * 重说AI响应
     */
    fun regenerateResponse(contactId: String) {
        coroutineScope.launch {
            if (!tryStartAiRequest(contactId)) return@launch
            setAiTypingState(contactId, true)
            regenerateResponseUseCase(contactId)
                .onSuccess { request ->
                    pendingPrompt = request.prompt
                    pendingContactId = request.contactId
                    pendingIsOffline = request.isOfflineMode
                    pendingMessageIdToUpdate = request.messageIdToUpdate
                    _promptConfirmationEvent.postValue(Event(request.prompt))
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("重说失败: ${error.message}"))
                }
            setAiTypingState(contactId, false)
            finishAiRequest(contactId)
        }
    }

    /**
     * 执行已确认的AI响应
     */
    fun executeConfirmedResponse() {
        val prompt = pendingPrompt ?: return
        val contactId = pendingContactId ?: return

        coroutineScope.launch {
            if (!tryStartAiRequest(contactId, allowExistingPending = true)) return@launch
            setAiTypingState(contactId, true)
            try {
                executeAiResponseWithConfiguredTimeout(
                    prompt,
                    contactId,
                    pendingIsOffline,
                    pendingMessageIdToUpdate
                ).onSuccess { result ->
                    lastRawAiResponses[contactId] = result.rawJson
                    
                    // 处理视频通话请求
                    result.incomingCallContactId?.let { callContactId ->
                        _incomingCallEvent.postValue(Event(callContactId))
                        // 同步发送全局来电事件,使非聊天页(桌面等)也能由 MainActivity 拉起来电界面
                        EventBus.post(IncomingCallEvent(callContactId))
                    }
                    
                    // 通知联系人列表刷新最新消息
                    EventBus.post(QqEvent.AiResponseCompleted(contactId))
                    EventBus.post(QqEvent.ContactsChanged)
                }.onFailure { error ->
                    _toastEvent.postValue(Event("AI响应失败: ${error.message}"))
                }
            } finally {
                finishAiRequest(contactId)
                setAiTypingState(contactId, false)
                clearPendingAction()
            }
        }
    }

    /**
     * 取消AI请求
     */
    fun cancelRequest() {
        clearPendingAction()
    }

    /**
     * 按用户设置的请求超时时间执行AI响应。
     */
    private suspend fun executeAiResponseWithConfiguredTimeout(
        prompt: AiPromptRequest,
        contactId: String,
        isOfflineMode: Boolean,
        originalMessageIdToUpdate: String?
    ): Result<ExecuteAiResponseUseCase.ExecutionResult> {
        val timeoutMillis: Long = settingsRepository.getChatRequestTimeoutSeconds().coerceAtLeast(1).toLong() * MILLIS_PER_SECOND
        // 网络请求 + 写库受超时约束；但打字延迟（typingCompletion）必须在 withTimeout 之外等待，
        // 否则长回复逐条落库的累积延迟会触发请求超时。await 完成后再返回，调用方的 finally
        // 才会关闭"正在输入"提示，使提示一直亮到最后一条气泡冒出（方案 A）。
        val result: Result<ExecuteAiResponseUseCase.ExecutionResult> = try {
            withTimeout(timeoutMillis) {
                executeAiResponseUseCase(prompt, contactId, isOfflineMode, originalMessageIdToUpdate)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return Result.failure(Exception("AI请求超时，已自动取消本次请求"))
        }
        result.getOrNull()?.typingCompletion?.await()
        return result
    }

    fun switchResponseVersion(messageId: String, contactId: String, newIndex: Int) {
        coroutineScope.launch {
            switchResponseVersionUseCase(messageId, contactId, newIndex)
                .onSuccess {
                    // 成功
                }
                .onFailure { error ->
                    _toastEvent.postValue(Event("切换版本失败: ${error.message}"))
                }
        }
    }

    /**
     * 获取最后一次AI原始响应
     */
    fun getLastRawResponse(contactId: String): String? {
        return lastRawAiResponses[contactId]
    }

    /**
     * 设置打字状态
     */
    fun setTypingState(contactId: String, isTyping: Boolean) {
        val currentMap = _isAiTyping.value.toMutableMap()
        currentMap[contactId] = isTyping
        _isAiTyping.value = currentMap
    }

    // ==================== 私有辅助方法 ====================

    private fun tryStartAiRequest(contactId: String, allowExistingPending: Boolean = false): Boolean {
        val hasPendingPromptForContact: Boolean = pendingContactId == contactId
        if (runningAiRequestContacts.contains(contactId) || (!allowExistingPending && hasPendingPromptForContact)) return false
        runningAiRequestContacts.add(contactId)
        return true
    }

    private fun finishAiRequest(contactId: String) {
        runningAiRequestContacts.remove(contactId)
    }

    private fun setAiTypingState(contactId: String, isTyping: Boolean) {
        setTypingState(contactId, isTyping)
    }

    private fun clearPendingAction() {
        pendingPrompt = null
        pendingContactId = null
        pendingIsOffline = false
        pendingMessageIdToUpdate = null
    }
}

/**
 * QQ事件定义
 * 用于Manager间解耦通信
 */
private const val MILLIS_PER_SECOND: Long = 1000L

sealed class QqEvent {
    /** 联系人数据变化(需要刷新列表) */
    object ContactsChanged : QqEvent()
    
    /** 消息发送完成 */
    data class MessageSent(val contactId: String) : QqEvent()
    
    /** AI响应完成 */
    data class AiResponseCompleted(val contactId: String) : QqEvent()

    /** 追问策略已更新，需要重新调度静默追问 */
    data class FollowUpPolicyChanged(val contactId: String) : QqEvent()
}