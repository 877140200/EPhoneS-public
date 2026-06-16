package com.susking.ephone_s.qq.ui.chat.videoCall

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity
import com.susking.ephone_s.aidata.data.local.entity.VideoCallMessageEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.VideoCallHistoryRepository
import com.susking.ephone_s.aidata.domain.use_case.SummarizeCallTranscriptUseCase
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.domain.use_case.ai.ExecuteAiResponseUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频通话恢复管理器
 * 负责处理通话崩溃后的恢复逻辑和数据持久化
 */
@Singleton
class VideoCallRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoCallHistoryRepository: VideoCallHistoryRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val chatRepository: ChatRepository,
    private val summarizeCallTranscriptUseCase: SummarizeCallTranscriptUseCase,
    private val executeAiResponseUseCase: ExecuteAiResponseUseCase,
    private val coroutineScope: CoroutineScope,
    private val qqContactManager: QqContactManager,
    private val qqChatManager: QqChatManager
) {
    private val gson = Gson()
    
    companion object {
        private const val TAG = "VideoCallRecoveryManager"
        private const val TIMEOUT_MILLIS = 30 * 60 * 1000L  // 30分钟超时
    }
    
    /**
     * 检查是否有未完成的通话（应用启动时调用）
     * @return 未完成的通话记录，如果没有或已超时则返回null
     */
    suspend fun checkForInterruptedCall(): VideoCallHistoryEntity? {
        return try {
            val inProgressCalls = videoCallHistoryRepository.getInProgressCalls()
            if (inProgressCalls.isEmpty()) {
                return null
            }
            // 取最新的一条进行中的通话
            val latestCall = inProgressCalls.maxByOrNull { it.lastUpdateTime }
            if (latestCall != null) {
                val elapsedTime = System.currentTimeMillis() - latestCall.lastUpdateTime
                if (elapsedTime > TIMEOUT_MILLIS) {
                    // 超时，标记为中断
                    videoCallHistoryRepository.updateCallStatus(
                        latestCall.id,
                        callStatus = "interrupted",
                        terminationReason = "通话超时中断"
                    )
                    return null
                }
                return latestCall
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "检查中断通话失败", e)
            null
        }
    }
    
    /**
     * 检查是否在30分钟内
     */
    fun isWithinTimeout(callHistory: VideoCallHistoryEntity): Boolean {
        val elapsedTime = System.currentTimeMillis() - callHistory.lastUpdateTime
        return elapsedTime <= TIMEOUT_MILLIS
    }
    
    /**
     * 获取中断时间（分钟）
     */
    fun getInterruptedMinutes(callHistory: VideoCallHistoryEntity): Long {
        return (System.currentTimeMillis() - callHistory.lastUpdateTime) / 60000
    }
    
    /**
     * 获取联系人名称
     */
    suspend fun getContactName(contactId: String): String {
        return personProfileRepository.getPersonProfileById(contactId)?.remarkName ?: "未知联系人"
    }
    
    /**
     * 用户拒绝恢复通话，触发完整的通话结束流程
     */
    fun declineCallRestoration(callHistory: VideoCallHistoryEntity) {
        coroutineScope.launch {
            val contact = personProfileRepository.getPersonProfileById(callHistory.contactId)
            if (contact == null) {
                videoCallHistoryRepository.updateCallStatus(
                    callHistory.id,
                    callStatus = "interrupted",
                    terminationReason = "联系人不存在"
                )
                return@launch
            }
            // 标记为已完成
            videoCallHistoryRepository.updateCallStatus(
                callHistory.id,
                callStatus = "completed",
                terminationReason = "通话异常中断",
                duration = callHistory.duration
            )
            // 恢复消息历史
            val messages = callHistory.messages.map { entity ->
                ChatMessage(
                    id = entity.id,
                    contactId = callHistory.contactId,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    type = "text"
                )
            }
            // 创建通话记录消息
            val contentJson = gson.toJson(mapOf(
                "text" to "通话异常中断",
                "videoCallId" to callHistory.id
            ))
            val callRecordMessage = ChatMessage(
                contactId = callHistory.contactId,
                type = "video_call_record",
                content = contentJson,
                role = "user"
            )
            chatRepository.insertMessage(callRecordMessage)
            // 如果有通话历史,触发总结
            if (messages.isNotEmpty()) {
                prepareCallSummaryPrompt(
                    contactId = callHistory.contactId,
                    callHistory = messages,
                    terminationReason = "通话异常中断",
                    isUserHangup = true,
                    videoCallId = callHistory.id,
                    wasInitiatedByUser = callHistory.wasInitiatedByUser,
                    hangupTimestamp = callHistory.lastUpdateTime
                )
            }
            // 刷新联系人列表
            qqContactManager.loadContacts()
            Log.d(TAG, "用户拒绝恢复通话,已触发结束流程")
        }
    }
    
    /**
     * 处理超时的中断通话（标记为中断状态）
     */
    suspend fun handleTimeoutInterruptedCall(callHistory: VideoCallHistoryEntity): Boolean {
        return try {
            videoCallHistoryRepository.updateCallStatus(
                callHistory.id,
                callStatus = "interrupted",
                terminationReason = "通话超时中断"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "处理超时中断通话失败", e)
            false
        }
    }
    
    /**
     * 用户选择触发超时通话的结束流程
     */
    fun triggerTimeoutCallEndFlow(callHistory: VideoCallHistoryEntity) {
        coroutineScope.launch {
            val contact = personProfileRepository.getPersonProfileById(callHistory.contactId)
            if (contact == null) {
                return@launch
            }
            val interruptedMinutes = getInterruptedMinutes(callHistory)
            // 恢复消息历史
            val messages = callHistory.messages.map { entity ->
                ChatMessage(
                    id = entity.id,
                    contactId = callHistory.contactId,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    type = "text"
                )
            }
            // 创建通话记录消息
            val contentJson = gson.toJson(mapOf(
                "text" to "通话在${interruptedMinutes}分钟前异常中断",
                "videoCallId" to callHistory.id
            ))
            val callRecordMessage = ChatMessage(
                contactId = callHistory.contactId,
                type = "video_call_record",
                content = contentJson,
                role = "user"
            )
            chatRepository.insertMessage(callRecordMessage)
            // 如果有通话历史,触发总结
            if (messages.isNotEmpty()) {
                prepareCallSummaryPrompt(
                    contactId = callHistory.contactId,
                    callHistory = messages,
                    terminationReason = "通话异常中断",
                    isUserHangup = true,
                    videoCallId = callHistory.id,
                    wasInitiatedByUser = callHistory.wasInitiatedByUser,
                    hangupTimestamp = callHistory.lastUpdateTime
                )
            }
            // 刷新联系人列表
            qqContactManager.loadContacts()
            Log.d(TAG, "已触发超时通话的结束流程")
        }
    }
    
    /**
     * 从数据库恢复通话
     * @param callHistory 通话历史记录
     * @param videoCallState 视频通话状态LiveData（用于更新状态）
     * @param inCallHistory 通话历史消息LiveData（用于恢复消息）
     * @param currentCallHistoryIdSetter 设置当前通话ID的回调
     * @param videoCallStartTimeSetter 设置通话开始时间的回调
     * @param wasCallInitiatedByUserSetter 设置是否由用户发起的回调
     */
    fun restoreCallFromDatabase(
        callHistory: VideoCallHistoryEntity,
        videoCallState: MutableLiveData<VideoCallState>,
        inCallHistory: MutableLiveData<List<ChatMessage>>,
        currentCallHistoryIdSetter: (Long?) -> Unit,
        videoCallStartTimeSetter: (Long?) -> Unit,
        wasCallInitiatedByUserSetter: (Boolean) -> Unit
    ) {
        coroutineScope.launch {
            val contact = personProfileRepository.getPersonProfileById(callHistory.contactId)
            if (contact == null) {
                videoCallHistoryRepository.updateCallStatus(
                    callHistory.id,
                    callStatus = "interrupted",
                    terminationReason = "联系人不存在"
                )
                return@launch
            }
            // 恢复通话状态
            currentCallHistoryIdSetter(callHistory.id)
            videoCallStartTimeSetter(callHistory.timestamp)
            wasCallInitiatedByUserSetter(callHistory.wasInitiatedByUser)
            // 恢复消息历史
            val messages = callHistory.messages.map { entity ->
                ChatMessage(
                    id = entity.id,
                    contactId = callHistory.contactId,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    type = "text"
                )
            }
            inCallHistory.postValue(messages)
            // 恢复为通话进行中状态
            videoCallState.postValue(VideoCallState.InProgress(contact))
            Log.d(TAG, "从数据库恢复通话: id=${callHistory.id}, 联系人=${contact.remarkName}")
        }
    }
    
    /**
     * 更新数据库中的通话历史记录
     * @param callId 通话记录ID
     * @param currentHistory 当前通话消息历史
     * @param videoCallStartTime 通话开始时间
     */
    fun updateCallHistoryInDatabase(
        callId: Long?,
        currentHistory: List<ChatMessage>,
        videoCallStartTime: Long?
    ) {
        coroutineScope.launch {
            val id = callId ?: return@launch
            val messages = currentHistory.map { chatMessage ->
                VideoCallMessageEntity(
                    id = chatMessage.id.ifEmpty { UUID.randomUUID().toString() },
                    content = chatMessage.content ?: "",
                    timestamp = chatMessage.timestamp,
                    role = chatMessage.role
                )
            }
            val duration = if (videoCallStartTime != null) {
                (System.currentTimeMillis() - videoCallStartTime) / 1000
            } else 0L
            videoCallHistoryRepository.updateVideoCallHistoryFields(
                id,
                messages = messages,
                duration = duration,
                callStatus = "in_progress",
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 保存视频通话历史记录到数据库
     * @param contactId 联系人ID
     * @param durationSeconds 通话时长（秒）
     * @param terminationReason 挂断原因（可空）
     * @param callHistory 通话消息历史
     * @param videoCallStartTime 通话开始时间
     * @param wasInitiatedByUser 是否由用户发起
     * @return 保存的视频通话记录ID，如果保存失败则返回null
     */
    suspend fun saveVideoCallHistory(
        contactId: String,
        durationSeconds: Long,
        terminationReason: String?,
        callHistory: List<ChatMessage>,
        videoCallStartTime: Long?,
        wasInitiatedByUser: Boolean
    ): Long? {
        return try {
            // 只保存有实际对话内容的通话记录
            if (callHistory.isEmpty() || durationSeconds < 1) {
                Log.d(TAG, "通话时间太短或无对话内容，不保存历史记录")
                return null
            }
            
            // 将ChatMessage转换为VideoCallMessageEntity
            val messages = callHistory.map { chatMessage ->
                VideoCallMessageEntity(
                    id = chatMessage.id.ifEmpty { UUID.randomUUID().toString() },
                    content = chatMessage.content ?: "",
                    timestamp = chatMessage.timestamp,
                    role = chatMessage.role
                )
            }
            
            // 创建视频通话历史记录实体
            val videoCallHistory = VideoCallHistoryEntity(
                id = 0, // 使用0让Room自动生成ID
                contactId = contactId,
                timestamp = videoCallStartTime ?: System.currentTimeMillis(),
                duration = durationSeconds,
                messages = messages,
                wasInitiatedByUser = wasInitiatedByUser,
                terminationReason = terminationReason
            )
            
            // 保存到数据库并返回ID
            val videoCallId = videoCallHistoryRepository.insertVideoCallHistory(videoCallHistory)
            Log.d(TAG, "视频通话历史记录已保存: id=$videoCallId, contactId=$contactId, duration=${durationSeconds}s, messages=${messages.size}条")
            videoCallId
        } catch (e: Exception) {
            Log.e(TAG, "保存视频通话历史记录失败", e)
            null
        }
    }
    
    /**
     * 准备视频通话结构化记忆抽取并触发AI回应（统一的通话结束处理方法）
     * @param contactId 联系人ID
     * @param callHistory 通话消息历史
     * @param terminationReason 挂断原因
     * @param isUserHangup 是否用户挂断
     * @param videoCallId 视频通话记录ID
     * @param wasInitiatedByUser 是否由用户发起
     * @param lastMessageTimestamp 最后一条消息时间戳（可选）
     * @param hangupTimestamp 挂断时间戳（可选）
     * @return Job 协程任务
     */
    fun prepareCallSummaryPrompt(
        contactId: String,
        callHistory: List<ChatMessage>,
        terminationReason: String,
        isUserHangup: Boolean,
        videoCallId: Long,
        wasInitiatedByUser: Boolean,
        lastMessageTimestamp: Long? = null,
        hangupTimestamp: Long? = null
    ): Job {
        return coroutineScope.launch {
            if (callHistory.isEmpty()) {
                Log.d(TAG, "视频通话结构化抽取条件不满足：没有通话记录。")
                return@launch
            }
            val contact = personProfileRepository.getPersonProfileById(contactId)
            if (contact == null) {
                Log.e(TAG, "无法确定通话联系人，视频通话结构化抽取失败。")
                return@launch
            }
            val userNickname = qqContactManager.getCurrentUserProfile()?.nickname ?: "你"
            val contactName = contact.realName
            val stringBuilder = StringBuilder()
            if (wasInitiatedByUser) {
                stringBuilder.append("[$userNickname 拨打了 $contactName 的电话]\n")
            } else {
                stringBuilder.append("[$contactName 拨打了 $userNickname 的电话]\n")
            }
            val transcriptContent = callHistory.joinToString("\n") { message ->
                val speaker = if (message.role == "user") userNickname else contactName
                "$speaker: ${message.content}"
            }
            stringBuilder.append(transcriptContent)
            stringBuilder.append("\n[$terminationReason]")
            val transcriptText = stringBuilder.toString()
            Log.d(TAG, "准备抽取视频通话结构化记忆:\n$transcriptText")
            // 获取最后一句话的时间戳和挂断时间
            val finalLastMessageTimestamp = lastMessageTimestamp ?: callHistory.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            val finalHangupTimestamp = hangupTimestamp ?: System.currentTimeMillis()
            // 抽取视频通话结构化记忆（传入视频通话记录ID）
            summarizeCallTranscriptUseCase(
                contactId = contactId,
                transcript = transcriptText,
                lastMessageTimestamp = finalLastMessageTimestamp,
                hangupTimestamp = finalHangupTimestamp,
                isUserHangup = isUserHangup,
                videoCallId = videoCallId
            )
            // 让AI主动发消息(无论谁挂断)
            try {
                val aiPromptService = AiDataApi.getAiPromptService()
                val postCallPromptRequest = aiPromptService.buildPostCallPrompt(
                    contactId = contactId,
                    lastMessageTimestamp = finalLastMessageTimestamp,
                    hangupTimestamp = finalHangupTimestamp,
                    isUserHangup = isUserHangup
                )
                
                // 设置typing状态，显示"正在输入中"并禁用按钮
                qqChatManager.setTypingState(contactId, true)
                
                // 直接使用 ExecuteAiResponseUseCase 处理AI的回应,它内部会处理请求和显示
                executeAiResponseUseCase(
                    prompt = postCallPromptRequest,
                    contactId = contactId,
                    isOfflineMode = false,
                ).onSuccess {
                    Log.d(TAG, "AI通话后主动发送消息成功")
                    qqContactManager.loadContacts()
                }.onFailure { error ->
                    Log.e(TAG, "AI通话后发送消息失败: ${error.message}", error)
                }.also {
                    // 无论成功或失败，都要清除typing状态
                    qqChatManager.setTypingState(contactId, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI通话后发送消息失败", e)
                // 发生异常时也要清除typing状态
                qqChatManager.setTypingState(contactId, false)
            }
        }
    }

    /**
     * 处理用户拒接视频通话后的 AI 回应（使用拒接专属提示词）。
     *
     * 去重要点：先把"拒接理由"作为用户消息插入聊天记录,但标记 hasBeenSeenByAi = true,
     * 这样不会触发 QqChatFragment 的通用自动回复(它依赖 hasBeenSeenByAi = false 的未读用户消息);
     * 然后用 buildDeclinedCallPrompt 构建"被拒接后角色化反应"的专属提示词触发 AI 回应。
     * 否则会出现"通用自动回复"和"拒接专属回应"同时触发、AI 回两次的问题。
     *
     * @param contactId 联系人ID
     * @param reason 用户填写的拒接理由
     */
    fun prepareDeclinedCallResponse(contactId: String, reason: String) {
        coroutineScope.launch {
            // 1. 插入拒接理由消息(标记已被AI看见,抑制通用自动回复)
            val declineReasonMessage = ChatMessage(
                contactId = contactId,
                type = "text",
                content = reason,
                role = "user",
                hasBeenSeenByAi = true
            )
            chatRepository.insertMessage(declineReasonMessage)
            qqContactManager.loadContacts()

            // 2. 使用拒接专属提示词触发 AI 角色化反应
            try {
                val aiPromptService = AiDataApi.getAiPromptService()
                val declinedPromptRequest = aiPromptService.buildDeclinedCallPrompt(
                    contactId = contactId,
                    reason = reason
                )

                // 设置typing状态,显示"正在输入中"并禁用按钮
                qqChatManager.setTypingState(contactId, true)

                executeAiResponseUseCase(
                    prompt = declinedPromptRequest,
                    contactId = contactId,
                    isOfflineMode = false,
                ).onSuccess {
                    Log.d(TAG, "拒接后 AI 回应成功")
                    qqContactManager.loadContacts()
                }.onFailure { error ->
                    Log.e(TAG, "拒接后 AI 回应失败: ${error.message}", error)
                }.also {
                    qqChatManager.setTypingState(contactId, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "拒接后 AI 回应失败", e)
                qqChatManager.setTypingState(contactId, false)
            }
        }
    }
}