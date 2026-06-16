package com.susking.ephone_s.qq.ui.chat.videoCall

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity
import com.susking.ephone_s.aidata.domain.model.AiAction
import com.susking.ephone_s.aidata.domain.model.AiActionParser
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
import com.susking.ephone_s.aidata.domain.use_case.SummarizeCallTranscriptUseCase
import com.susking.ephone_s.brain.service.AiRequestService
import com.susking.ephone_s.core.util.CallBusyState
import com.susking.ephone_s.core.util.Event
import com.susking.ephone_s.qq.domain.followup.FollowUpPolicyStore
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.domain.use_case.ai.ExecuteAiResponseUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定义视频通话的各种状态，用于UI展示和逻辑控制。
 */
sealed class VideoCallState {
    object Idle : VideoCallState() // 空闲状态，未进行任何通话
    data class Outgoing(val contact: PersonProfile) : VideoCallState() // 正在呼叫对方
    data class Incoming(val contact: PersonProfile) : VideoCallState() // 正在接收对方来电
    data class Connecting(val contact: PersonProfile) : VideoCallState() // 正在连接中（等待AI第一句话）
    data class InProgress(val contact: PersonProfile) : VideoCallState() // 通话进行中
    // 通话已最小化为悬浮窗。wasConnected 记录最小化前是否已接通(InProgress/Connecting):
    // 用于恢复时回到正确状态,以及挂断时正确判定是否计入通话时长/记忆提取。
    data class Minimized(val contact: PersonProfile, val wasConnected: Boolean) : VideoCallState()
    data class Terminated(val reason: String, val byUser: Boolean) : VideoCallState() // 通话已结束
    data class TerminatedByAi(val reason: String) : VideoCallState() // AI挂断了通话，但UI保留
}

/**
 * 视频通话管理器
 * 负责管理视频通话的状态和逻辑
 */
@Singleton
class VideoCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val videoCallHistoryRepository: VideoCallHistoryRepository,
    private val aiRequestService: AiRequestService,
    private val recoveryManager: VideoCallRecoveryManager,
    private val coroutineScope: CoroutineScope,
    private val qqContactManager: QqContactManager,
    private val qqChatManager: QqChatManager,
    private val followUpPolicyStore: FollowUpPolicyStore
) {
    private val gson = Gson()

    private val _videoCallState = MutableLiveData<VideoCallState>(VideoCallState.Idle)
    val videoCallState: LiveData<VideoCallState> = _videoCallState

    private val _inCallHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    val inCallHistory: LiveData<List<ChatMessage>> = _inCallHistory

    // 是否正在等待AI回复（用于禁用说话按钮）
    private val _isWaitingForAiResponse = MutableLiveData<Boolean>(false)
    val isWaitingForAiResponse: LiveData<Boolean> = _isWaitingForAiResponse

    private var videoCallSetupJob: Job? = null
    private var videoCallJob: Job? = null
    private var videoCallStartTime: Long? = null
    private var wasCallInitiatedByUser: Boolean = false

    // 来电响铃超时计时任务。AI 发起来电(Incoming)后启动,超过 INCOMING_CALL_TIMEOUT_MILLIS
    // 用户仍未接听/拒接,则自动记为"未接来电(未响应)"并结束。接听(acceptVideoCall)、
    // 拒接(declineVideoCall)、或来电被其他流程结束时,必须取消此任务,避免误触发。
    private var incomingCallTimeoutJob: Job? = null

    // 通话结束流程的幂等标志：防止"用户挂断"与"AI主动挂断(EndCall)"几乎同时到达时,
    // endVideoCall 被重入两次,导致重复落库、重复触发记忆抽取和通话后回应。
    // 每次开始新通话(startVideoCall / acceptVideoCall)时复位。
    @Volatile
    private var isEndingCall: Boolean = false

    // 当前通话的数据库记录ID（用于实时更新）
    var currentCallHistoryId: Long? = null
        private set

    // 当前/最近一次通话的联系人。
    // 用途：Terminated / TerminatedByAi 状态不携带 contact,UI 重拨或弹窗时需要拿到联系人。
    // 旧逻辑靠 contactNameTextView 的备注名反查 contacts,备注名重名或被修改时会匹配错对象。
    // 此处在通话开始时缓存联系人对象,供 UI 直接取用,避免脆弱的字符串匹配。
    var currentCallContact: PersonProfile? = null
        private set

    private val _showCallEndDialogEvent = MutableLiveData<Event<String>>()
    val showCallEndDialogEvent: LiveData<Event<String>> = _showCallEndDialogEvent
    
    // 从最小化恢复的事件(用于通知UI重新打开VideoCallFragment)
    private val _restoreFromMinimizedEvent = MutableLiveData<Event<PersonProfile>>()
    val restoreFromMinimizedEvent: LiveData<Event<PersonProfile>> = _restoreFromMinimizedEvent

    init {
        // 把通话状态镜像到全局忙线状态 [CallBusyState],供追问/自动回复/后台行动作闸门判断。
        // 用 observeForever 一处捕获所有写入路径——包括 VideoCallRecoveryManager 崩溃恢复时
        // 直接对 _videoCallState postValue 的路径——避免逐个改写各 postValue 调用点而遗漏。
        // observeForever 必须在主线程注册;Singleton 首次创建所在线程不确定,故 post 到主线程。
        Handler(Looper.getMainLooper()).post {
            _videoCallState.observeForever { state: VideoCallState? ->
                mirrorCallBusyState(state)
            }
        }
    }

    /**
     * 将视频通话状态镜像为全局忙线状态。
     * 任一通话进行态(来电/呼出/接通中/通话中/最小化)标记为忙线;
     * 空闲与结束态(Idle/Terminated/TerminatedByAi)清除忙线。
     */
    private fun mirrorCallBusyState(state: VideoCallState?) {
        when (state) {
            is VideoCallState.Incoming -> CallBusyState.setBusy(state.contact.id)
            is VideoCallState.Outgoing -> CallBusyState.setBusy(state.contact.id)
            is VideoCallState.Connecting -> CallBusyState.setBusy(state.contact.id)
            is VideoCallState.InProgress -> CallBusyState.setBusy(state.contact.id)
            is VideoCallState.Minimized -> CallBusyState.setBusy(state.contact.id)
            else -> CallBusyState.clearBusy()
        }
    }

    fun startVideoCall(contactId: String, lastCallFailureReason: String? = null) {
        wasCallInitiatedByUser = true // 标记是用户发起的
        isEndingCall = false // 复位结束幂等标志,允许本通新通话正常收尾
        _inCallHistory.postValue(emptyList()) // 开始新通话时清空历史记录
        videoCallSetupJob?.cancel() // 取消之前的任务
        videoCallSetupJob = coroutineScope.launch {
            val contact = repository.getPersonProfileById(contactId) ?: return@launch

            currentCallContact = contact // 缓存当前通话联系人,供UI重拨/弹窗直接取用
            _videoCallState.postValue(VideoCallState.Outgoing(contact))
            videoCallStartTime = System.currentTimeMillis()

            // 注意：此处不再提前创建数据库记录。
            // 旧逻辑在 Outgoing(还在等AI决定是否接听)阶段就插入 in_progress 记录,
            // 会导致：1) 用户取消呼出后残留脏记录;2) 等待决策期间崩溃,重启被误判为"可恢复的通话"。
            // 改为在对方 accept(真正接通)后再创建记录。

            val userProfile = qqContactManager.getCurrentUserProfile() ?: return@launch
            val chatHistory = qqChatManager.messages.value.filter { it.contactId == contactId }

            val aiPromptService = AiDataApi.getAiPromptService()
            val promptRequest = if (lastCallFailureReason != null) {
                aiPromptService.buildRedialDecisionPrompt(
                    contactId = contactId,
                    lastCallFailureReason = lastCallFailureReason
                )
            } else {
                aiPromptService.buildVideoCallDecisionPrompt(
                    contactId = contactId
                )
            }

            qqChatManager.setTypingState(contactId, true)
            val aiResponseJson = try {
                aiRequestService.getChatCompletion(context, promptRequest)
            } finally {
                qqChatManager.setTypingState(contactId, false)
            }
            if (aiResponseJson != null) {
                try {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val decisionObject: Map<String, String>? = parseDecisionObject(aiResponseJson, type)

                    if (decisionObject != null) {
                        val decision = decisionObject["decision"]?.lowercase()
                        if (decision == "accept") {
                            // 通话接通即作废通话前设的旧追问策略:通话是比文字更强的互动,
                            // "用户没回我文字所以追问"的前提已被打破,避免挂断后又冒出过期的文字追问。
                            followUpPolicyStore.cancelPolicy(contactId)
                            // 检查当前是否处于最小化状态，如果是则保持最小化
                            val currentState = _videoCallState.value
                            if (currentState !is VideoCallState.Minimized) {
                                _videoCallState.postValue(VideoCallState.Connecting(contact))
                            }
                            // 对方接通后才创建数据库记录(标记为进行中)
                            val initialRecord = VideoCallHistoryEntity(
                                contactId = contactId,
                                timestamp = videoCallStartTime ?: System.currentTimeMillis(),
                                duration = 0L,
                                messages = emptyList(),
                                wasInitiatedByUser = true,
                                terminationReason = null,
                                callStatus = "in_progress",
                                lastUpdateTime = System.currentTimeMillis()
                            )
                            currentCallHistoryId = videoCallHistoryRepository.insertVideoCallHistory(initialRecord)
                            Log.d("VideoCallManager", "对方接通,创建通话记录: id=$currentCallHistoryId")
                            triggerAiInCallResponse(contact, emptyList())
                        } else {
                            val reason = decisionObject["reason"] ?: "无"
                            endVideoCall("对方拒绝了通话： $reason", isAiInitiated = true)
                        }
                    } else {
                        endVideoCall("对方未响应", isAiInitiated = true)
                    }
                } catch (e: Exception) {
                    Log.e("VideoCallManager", "解析AI通话决策失败: $aiResponseJson", e)
                    endVideoCall("对方未响应", isAiInitiated = true)
                }
            } else {
                endVideoCall("对方网络不稳定", isAiInitiated = true)
            }
        }
    }

    /**
     * 解析视频通话决策响应。
     * 决策请求使用 responseFormat=json_object,模型返回单个对象 {"type":"video_call_response","decision":"accept"}。
     * 同时兼容旧的数组格式 [{"type":"video_call_response",...}],提升不同服务商的鲁棒性。
     */
    private fun parseDecisionObject(json: String, mapType: java.lang.reflect.Type): Map<String, String>? {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            // 兼容旧的数组格式
            val arrayType = object : TypeToken<List<Map<String, String>>>() {}.type
            val responseArray: List<Map<String, String>> = gson.fromJson(json, arrayType)
            responseArray.firstOrNull { it["type"] == "video_call_response" }
        } else {
            // 标准单对象格式
            val decisionObject: Map<String, String>? = gson.fromJson(json, mapType)
            decisionObject?.takeIf { it["type"] == "video_call_response" }
        }
    }

    fun acceptVideoCall() {
        val currentState = _videoCallState.value
        if (currentState is VideoCallState.Incoming) {
            cancelIncomingCallTimeout() // 用户已接听,取消响铃超时计时
            // 通话接通即作废旧追问策略(理由同 startVideoCall 接通分支)
            followUpPolicyStore.cancelPolicy(currentState.contact.id)
            isEndingCall = false // 复位结束幂等标志,允许本通新通话正常收尾
            _videoCallState.postValue(VideoCallState.Connecting(currentState.contact))
            videoCallStartTime = System.currentTimeMillis()
            
            // 创建数据库记录（来电接听）
            coroutineScope.launch {
                val initialRecord = VideoCallHistoryEntity(
                    contactId = currentState.contact.id,
                    timestamp = videoCallStartTime!!,
                    duration = 0L,
                    messages = emptyList(),
                    wasInitiatedByUser = false,
                    terminationReason = null,
                    callStatus = "in_progress",
                    lastUpdateTime = System.currentTimeMillis()
                )
                currentCallHistoryId = videoCallHistoryRepository.insertVideoCallHistory(initialRecord)
                Log.d("VideoCallManager", "创建来电通话记录: id=$currentCallHistoryId")
            }
            
            triggerAiInCallResponse(currentState.contact, emptyList())
        }
    }

    fun cancelOutgoingCall() {
        val currentState = _videoCallState.value
        if (currentState is VideoCallState.Outgoing) {
            videoCallSetupJob?.cancel()
            videoCallJob?.cancel()
            _videoCallState.postValue(VideoCallState.Terminated("已取消", byUser = true))
        }
    }

    fun declineVideoCall(reason: String) {
        val currentState = _videoCallState.value
        if (currentState is VideoCallState.Incoming) {
            cancelIncomingCallTimeout() // 用户已拒接,取消响铃超时计时,避免超时逻辑重复记录未接来电
            _videoCallState.postValue(VideoCallState.Terminated("已拒接：$reason", byUser = true))
            // 用户主动拒接,记一条"已拒接"的未接来电:落 history 表 + 插聊天卡片,
            // 与"未响应(超时)"区分原因,使来电历史完整可追溯。
            recordMissedCall(currentState.contact.id, reason = "已拒接")
            // 使用拒接专属提示词触发 AI 角色化反应(内部已处理去重,避免与通用自动回复重复触发)
            recoveryManager.prepareDeclinedCallResponse(currentState.contact.id, reason)
        }
    }

    fun endVideoCall(reason: String? = null, isAiInitiated: Boolean = false, lastMessageFromAi: String? = null) {
        val currentState = _videoCallState.value
        if (currentState is VideoCallState.InProgress || currentState is VideoCallState.Connecting || currentState is VideoCallState.Outgoing || currentState is VideoCallState.Incoming || currentState is VideoCallState.Minimized) {
            // 幂等守卫：防止"用户挂断"与"AI主动挂断(EndCall)"几乎同时到达导致重入,
            // 否则会重复落库、重复触发记忆抽取和通话后回应。
            if (isEndingCall) {
                Log.d("VideoCallManager", "endVideoCall 已在进行中,忽略重复调用")
                return
            }
            isEndingCall = true
            cancelIncomingCallTimeout() // 来电被任意路径结束时,兜底取消响铃超时计时,避免残留任务误触发

            // 是否为"已真正接通"的通话(进行中/连接中/最小化)。
            // 最小化也算接通：用户把通话挂后台时被AI挂断,同样要算时长、抽记忆、触发通话后回应。
            val wasConnected = isConnectedCall(currentState)
            val callHistoryForExtraction = if (wasConnected) _inCallHistory.value.orEmpty() else null

            videoCallSetupJob?.cancel()
            videoCallJob?.cancel()

            // 判断是否为用户主动挂断：AI没有发起挂断 = 用户挂断
            val isUserHangup = !isAiInitiated

            val finalReason = reason ?: if (wasConnected) {
                if (isAiInitiated) "对方已挂断" else "我挂断了电话"
            } else {
                "通话结束"
            }
            var recordText = finalReason

            // 计算通话时长（秒）
            var durationSeconds: Long = 0
            if (wasConnected && videoCallStartTime != null) {
                val durationMillis = System.currentTimeMillis() - videoCallStartTime!!
                if (durationMillis > 1000) {
                    durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
                    val minutes = durationSeconds / 60
                    val seconds = durationSeconds % 60
                    val durationString = String.format("%02d:%02d", minutes, seconds)
                    recordText = "通话结束，时长 $durationString"
                }
            }

            val contactId = when (currentState) {
                is VideoCallState.InProgress -> currentState.contact.id
                is VideoCallState.Connecting -> currentState.contact.id
                is VideoCallState.Outgoing -> currentState.contact.id
                is VideoCallState.Incoming -> currentState.contact.id
                is VideoCallState.Minimized -> currentState.contact.id
                else -> null
            }

            if (contactId != null) {
                coroutineScope.launch {
                    if (!lastMessageFromAi.isNullOrBlank()) {
                        val aiLastWordsMessage = ChatMessage(
                            contactId = contactId,
                            type = "text",
                            content = lastMessageFromAi,
                            role = "assistant"
                        )
                        val updatedHistory = _inCallHistory.value.orEmpty() + aiLastWordsMessage
                        _inCallHistory.postValue(updatedHistory)
                    }
                    
                    // 使用currentCallHistoryId或保存新记录
                    val videoCallId = currentCallHistoryId ?: recoveryManager.saveVideoCallHistory(
                        contactId = contactId,
                        durationSeconds = durationSeconds,
                        terminationReason = finalReason,
                        callHistory = _inCallHistory.value.orEmpty(),
                        videoCallStartTime = videoCallStartTime,
                        wasInitiatedByUser = wasCallInitiatedByUser
                    )
                    
                    // 标记通话为已完成
                    if (videoCallId != null) {
                        videoCallHistoryRepository.updateCallStatus(
                            videoCallId,
                            callStatus = "completed",
                            terminationReason = finalReason,
                            duration = durationSeconds
                        )
                    }
                    
                    // 清空当前通话ID
                    currentCallHistoryId = null
                    
                    // 创建通话记录消息，包含videoCallId（如果有的话）
                    // AI挂断时也显示通话时长
                    val displayText = if (isAiInitiated && durationSeconds > 0) {
                        val minutes = durationSeconds / 60
                        val seconds = durationSeconds % 60
                        val durationString = String.format("%02d:%02d", minutes, seconds)
                        "通话结束，时长 $durationString（${finalReason}）"
                    } else if (isAiInitiated) {
                        "通话结束：${finalReason}"
                    } else {
                        recordText
                    }
                    
                    val contentJson = if (videoCallId != null) {
                        gson.toJson(mapOf("text" to displayText, "videoCallId" to videoCallId))
                    } else {
                        displayText
                    }
                    
                    val callRecordMessage = ChatMessage(
                        contactId = contactId,
                        type = "video_call_record",
                        content = contentJson,
                        role = if (isAiInitiated) "assistant" else "user"
                    )
                    
                    // 更新通话历史UI（仅AI挂断时显示）
                    if (isAiInitiated) {
                        val updatedHistory = _inCallHistory.value.orEmpty() + callRecordMessage
                        _inCallHistory.postValue(updatedHistory)
                    }
                    
                    // 插入通话记录消息到数据库
                    chatRepository.insertMessage(callRecordMessage)
                    
                    // 如果有通话历史需要抽取结构化记忆，则调用视频通话结构化抽取流程。
                    if (callHistoryForExtraction != null && videoCallId != null) {
                        val extractionJob = recoveryManager.prepareCallSummaryPrompt(
                            contactId = contactId,
                            callHistory = callHistoryForExtraction,
                            terminationReason = finalReason,
                            isUserHangup = isUserHangup,
                            videoCallId = videoCallId,
                            wasInitiatedByUser = wasCallInitiatedByUser
                        )
                        if (wasConnected && !isAiInitiated) {
                            extractionJob.join()
                        }
                    }
                    
                    // 刷新联系人列表以更新最新消息
                    qqContactManager.loadContacts()
                }
            }

            videoCallStartTime = null
            // 接通后挂断(InProgress/Connecting/Minimized)直接回到空闲、关闭界面;
            // 未接通(Outgoing/Incoming,即拒接/取消/未响应)保留界面给用户看原因。
            // 旧逻辑用 finalReason 文本匹配决定分支,文案一改就会失效,改用 wasConnected 标志。
            if (wasConnected) {
                _videoCallState.postValue(VideoCallState.Idle)
            } else {
                if (isAiInitiated) {
                    _videoCallState.postValue(VideoCallState.TerminatedByAi(finalReason))
                } else {
                    _videoCallState.postValue(VideoCallState.Terminated(finalReason, byUser = true))
                }
            }
        }
    }

    fun sendInCallMessage(text: String) {
        val currentState = _videoCallState.value
        val contact = when (currentState) {
            is VideoCallState.InProgress -> currentState.contact
            is VideoCallState.Connecting -> currentState.contact
            is VideoCallState.Minimized -> currentState.contact
            else -> null
        } ?: return
        
        val userMessage = ChatMessage(
            contactId = contact.id,
            content = text,
            role = "user"
        )
        val updatedHistory = _inCallHistory.value.orEmpty() + userMessage
        _inCallHistory.postValue(updatedHistory)
        triggerAiInCallResponse(contact, updatedHistory)
    }

    fun rerollLastInCallResponse() {
        val currentState = _videoCallState.value
        val contact = when (currentState) {
            is VideoCallState.InProgress -> currentState.contact
            is VideoCallState.Connecting -> currentState.contact
            is VideoCallState.Minimized -> currentState.contact
            else -> null
        } ?: return
        
        val currentHistory = _inCallHistory.value.orEmpty()
        val lastAiMessageIndex = currentHistory.indexOfLast { it.role == "assistant" }
        if (lastAiMessageIndex != -1) {
            val updatedHistory = currentHistory.subList(0, lastAiMessageIndex)
            _inCallHistory.postValue(updatedHistory)
            triggerAiInCallResponse(contact, updatedHistory, isReroll = true)
        }
    }

    fun updateInCallMessage(updatedMessage: ChatMessage) {
        val currentHistory = _inCallHistory.value.orEmpty().toMutableList()
        val index = currentHistory.indexOfFirst { it.id == updatedMessage.id }
        if (index != -1) {
            currentHistory[index] = updatedMessage
            _inCallHistory.postValue(currentHistory)
        }
    }

    private fun triggerAiInCallResponse(contact: PersonProfile, callHistoryForPrompt: List<ChatMessage>, isReroll: Boolean = false) {
        videoCallJob?.cancel()
        videoCallJob = coroutineScope.launch {
            try {
                // 设置等待状态为true，禁用说话按钮
                _isWaitingForAiResponse.postValue(true)
                
                val aiPromptService = AiDataApi.getAiPromptService()
                val promptRequest = aiPromptService.buildInCallPrompt(
                    contactId = contact.id,
                    callHistory = callHistoryForPrompt,
                    isReroll = isReroll
                )

                qqChatManager.setTypingState(contact.id, true)
                val aiResponseJson = try {
                    aiRequestService.getChatCompletion(context, promptRequest)
                } finally {
                    qqChatManager.setTypingState(contact.id, false)
                }

                if (!aiResponseJson.isNullOrBlank()) {
                    val parsedResponse = AiActionParser.parseMixedContent(context, aiResponseJson)
                    val textPart = parsedResponse.text
                    val actionPart = parsedResponse.action

                    // #14 兜底：接通阶段(Connecting)若AI首句既无文本也无action(空响应/解析失败),
                    // 状态会永远卡在"正在连接中…",用户只能看到挂断按钮。此处主动结束通话。
                    // 仅限 Connecting：通话中(InProgress)的空响应只是"这轮没新消息",不应挂断。
                    if (textPart.isBlank() && actionPart == null &&
                        _videoCallState.value is VideoCallState.Connecting) {
                        endVideoCall("对方网络不稳定", isAiInitiated = true)
                        return@launch
                    }

                    if (textPart.isNotBlank()) {
                        val aiMessage = ChatMessage(
                            contactId = contact.id,
                            content = textPart,
                            role = "assistant"
                        )
                        val updatedHistory = _inCallHistory.value.orEmpty() + aiMessage
                        _inCallHistory.postValue(updatedHistory)
                        
                        // 更新数据库记录
                        recoveryManager.updateCallHistoryInDatabase(
                            callId = currentCallHistoryId,
                            currentHistory = _inCallHistory.value.orEmpty(),
                            videoCallStartTime = videoCallStartTime
                        )
                        
                        // 收到AI第一句话后，从Connecting状态转为InProgress
                        // 但如果处于最小化状态，则保持最小化
                        val currentState = _videoCallState.value
                        if (currentState is VideoCallState.Connecting) {
                            _videoCallState.postValue(VideoCallState.InProgress(contact))
                        } else if (currentState is VideoCallState.Minimized) {
                            // 保持最小化状态，不自动恢复通话界面
                            Log.d("VideoCallManager", "通话已接通但保持最小化状态")
                        }
                    }

                    if (actionPart is AiAction.EndCall) {
                        // 注意：textPart 已在上面加入 _inCallHistory,此处传 null 避免 endVideoCall
                        // 再通过 lastMessageFromAi 重复添加同一句话(否则UI和数据库都会出现两次)。
                        endVideoCall(actionPart.reason, isAiInitiated = true, lastMessageFromAi = null)
                    }
                } else if (_videoCallState.value is VideoCallState.Connecting) {
                    // 接通后第一句返回空响应(aiResponseJson 为空),兜底结束,避免卡在 Connecting。
                    endVideoCall("对方网络不稳定", isAiInitiated = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 重抛取消异常,保持结构化并发的取消语义(reroll/重新触发时旧任务能正确取消)。
                throw e
            } catch (e: Exception) {
                Log.e("VideoCallManager", "Error getting AI response in call", e)
            } finally {
                // 无论成功或失败，都恢复等待状态为false，启用说话按钮
                _isWaitingForAiResponse.postValue(false)
            }
        }
    }

    fun setIncomingCall(contact: PersonProfile) {
        wasCallInitiatedByUser = false
        currentCallContact = contact // 缓存来电联系人,供 UI 重拨/弹窗直接取用
        _videoCallState.postValue(VideoCallState.Incoming(contact))
        startIncomingCallTimeout(contact) // 启动响铃超时计时:超时未接自动记为"未接来电(未响应)"
    }

    /**
     * 通过联系人ID设置来电状态。
     * 用途:全局来电入口(MainActivity 监听 EventBus.IncomingCallEvent、后台全屏来电通知拉起)
     * 无法持有 PersonProfile 对象,只有 contactId。此处用 repository 直接查联系人,
     * 不依赖 UI 层 contacts 缓存是否已加载(冷启动/后台拉起时缓存可能尚未就绪)。
     *
     * @param contactId 来电联系人ID
     */
    fun setIncomingCallById(contactId: String) {
        coroutineScope.launch {
            val contact = repository.getPersonProfileById(contactId)
            if (contact != null) {
                setIncomingCall(contact)
            } else {
                Log.w("VideoCallManager", "setIncomingCallById 找不到联系人: $contactId")
            }
        }
    }

    /**
     * 启动来电响铃超时计时。
     * 超过 INCOMING_CALL_TIMEOUT_MILLIS 用户仍未接听/拒接,自动记为"未接来电(未响应)"并结束来电。
     * 接听、拒接、或来电被其他流程结束时,通过 cancelIncomingCallTimeout 取消本任务。
     */
    private fun startIncomingCallTimeout(contact: PersonProfile) {
        incomingCallTimeoutJob?.cancel()
        incomingCallTimeoutJob = coroutineScope.launch {
            kotlinx.coroutines.delay(INCOMING_CALL_TIMEOUT_MILLIS)
            // 再次确认仍处于该联系人的来电状态,避免竞态(期间已被接听/拒接)
            val currentState = _videoCallState.value
            if (currentState is VideoCallState.Incoming && currentState.contact.id == contact.id) {
                Log.d("VideoCallManager", "来电响铃超时未接: ${contact.remarkName}")
                recordMissedCall(contact.id, reason = "未响应")
                _videoCallState.postValue(VideoCallState.Terminated("未接来电", byUser = false))
            }
        }
    }

    /**
     * 取消来电响铃超时计时。接听/拒接来电时调用。
     */
    private fun cancelIncomingCallTimeout() {
        incomingCallTimeoutJob?.cancel()
        incomingCallTimeoutJob = null
    }

    /**
     * 记录一条未接来电。
     * 1) 落 video_call_history 表(callStatus=missed,时长0,非用户发起);
     * 2) 往聊天插一条 video_call_record 消息,使聊天界面显示未接来电卡片,可点击查看详情。
     *
     * @param contactId 来电联系人ID
     * @param reason 未接原因(如"未响应"=响铃超时,"已拒接"=用户主动拒接)
     */
    private fun recordMissedCall(contactId: String, reason: String) {
        coroutineScope.launch {
            try {
                val now: Long = System.currentTimeMillis()
                val missedCallEntity = VideoCallHistoryEntity(
                    contactId = contactId,
                    timestamp = now,
                    duration = 0L,
                    messages = emptyList(),
                    wasInitiatedByUser = false, // AI 发起的来电
                    terminationReason = reason,
                    callStatus = "missed", // 未接:不可恢复,区别于 in_progress
                    lastUpdateTime = now
                )
                val missedCallId: Long = videoCallHistoryRepository.insertVideoCallHistory(missedCallEntity)

                // 插入聊天卡片消息,携带 videoCallId 供点击查看详情
                val contentJson: String = gson.toJson(
                    mapOf(
                        "text" to "未接来电",
                        "videoCallId" to missedCallId
                    )
                )
                val missedCallMessage = ChatMessage(
                    contactId = contactId,
                    type = "video_call_record",
                    content = contentJson,
                    role = "user"
                )
                chatRepository.insertMessage(missedCallMessage)
                // 刷新联系人列表以更新最新消息
                qqContactManager.loadContacts()
                Log.i("VideoCallManager", "未接来电已记录: contactId=$contactId, reason=$reason, videoCallId=$missedCallId")
            } catch (e: Exception) {
                Log.e("VideoCallManager", "记录未接来电失败: contactId=$contactId", e)
            }
        }
    }

    /**
     * 判断给定状态是否属于"已接通"的通话。
     * 已接通: Connecting(对方已接,等AI首句)、InProgress(通话中)、
     *        以及最小化前已接通的 Minimized(wasConnected=true)。
     * 未接通: Outgoing(呼叫中,对方没接)、呼叫中最小化的 Minimized(wasConnected=false)。
     * 供挂断逻辑判定是否计入通话时长、是否触发记忆提取,避免未接通呼叫产生脏数据。
     */
    private fun isConnectedCall(state: VideoCallState?): Boolean =
        state is VideoCallState.InProgress ||
        state is VideoCallState.Connecting ||
        (state is VideoCallState.Minimized && state.wasConnected)

    /**
     * 设置通话为最小化状态（显示为悬浮窗）
     * @param contact 当前通话的联系人
     */
    fun setMinimizedState(contact: PersonProfile) {
        val currentState = _videoCallState.value
        // 允许在呼叫中(Outgoing)、连接中(Connecting)、通话进行中(InProgress)最小化。
        // wasConnected 记录最小化前是否已接通,供恢复和挂断逻辑判定:
        // - Outgoing(呼叫中,对方还没接) -> wasConnected = false
        // - Connecting/InProgress(已接通) -> wasConnected = true
        when (currentState) {
            is VideoCallState.Outgoing -> {
                _videoCallState.postValue(VideoCallState.Minimized(contact, wasConnected = false))
                Log.d("VideoCallManager", "视频通话已最小化(呼叫中): ${contact.remarkName}")
            }
            is VideoCallState.Connecting, is VideoCallState.InProgress -> {
                _videoCallState.postValue(VideoCallState.Minimized(contact, wasConnected = true))
                Log.d("VideoCallManager", "视频通话已最小化(已接通): ${contact.remarkName}")
            }
            else -> {
                // 其他状态(Idle/Incoming/Terminated等)不允许最小化
            }
        }
    }
    
    /**
     * 从最小化状态恢复到通话界面
     */
    fun restoreFromMinimized() {
        val currentState = _videoCallState.value
        if (currentState is VideoCallState.Minimized) {
            // 按 wasConnected 恢复到正确的前序状态:
            // - 未接通(呼叫中最小化) -> 回到 Outgoing,继续显示"正在呼叫…",避免跳过呼叫阶段误进通话中
            // - 已接通 -> 回到 InProgress
            val restoredState = if (currentState.wasConnected) {
                VideoCallState.InProgress(currentState.contact)
            } else {
                VideoCallState.Outgoing(currentState.contact)
            }
            _videoCallState.postValue(restoredState)
            // 发送恢复事件,通知UI重新打开VideoCallFragment
            _restoreFromMinimizedEvent.postValue(Event(currentState.contact))
            Log.d("VideoCallManager", "从最小化恢复通话(wasConnected=${currentState.wasConnected}): ${currentState.contact.remarkName}")
        }
    }
    
    /**
     * 获取当前通话的时长文本(格式: MM:SS)
     * @return 通话时长字符串,如果没有通话则返回null
     */
    fun getCurrentCallDuration(): String? {
        if (videoCallStartTime == null) return null
        val durationMillis = System.currentTimeMillis() - videoCallStartTime!!
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * 获取当前通话的开始时间戳(墙钟,毫秒),无通话时返回 null。
     * 供 UI 计时器用墙钟时间反推 Chronometer.base,实现最小化恢复/崩溃恢复时正确接续已用时长。
     * 取代旧的 chronometerBase 存储方案:旧方案存的是 SystemClock.elapsedRealtime()(相对开机时间),
     * 进程重启后开机基准变化导致该值失效,且从未被读回,是只写不读的死字段。
     */
    fun getVideoCallStartTimeMillis(): Long? = videoCallStartTime
    
    /**
     * 从数据库恢复通话
     */
    fun restoreCallFromDatabase(callHistory: VideoCallHistoryEntity) {
        recoveryManager.restoreCallFromDatabase(
            callHistory = callHistory,
            videoCallState = _videoCallState,
            inCallHistory = _inCallHistory,
            currentCallHistoryIdSetter = { currentCallHistoryId = it },
            videoCallStartTimeSetter = { videoCallStartTime = it },
            wasCallInitiatedByUserSetter = { wasCallInitiatedByUser = it }
        )
    }

    companion object {
        // 来电响铃最长时间(毫秒)。AI 发起来电后,用户在此时间内未接听/拒接,
        // 自动记为"未接来电(未响应)"并结束来电。当前定为 30 秒。
        private const val INCOMING_CALL_TIMEOUT_MILLIS: Long = 30_000L
    }
}