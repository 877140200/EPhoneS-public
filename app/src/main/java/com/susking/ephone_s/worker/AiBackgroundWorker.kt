package com.susking.ephone_s.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.susking.ephone_s.EPhoneSApplication
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.repository.CallStateRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.ChatRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FavoriteMessageRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.FeedRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.LongTermMemoryRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.PersonProfileRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.SettingsRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.StickerRepositoryImpl
import com.susking.ephone_s.aidata.data.repository.WorldBookEntryRepositoryImpl
import com.susking.ephone_s.aidata.domain.model.AiActionParser
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.prompt.BackgroundPromptBuilder
import com.susking.ephone_s.brain.api.BrainApi
import com.susking.ephone_s.brain.service.ActionExecutor
import com.susking.ephone_s.core.util.CallBusyState
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.IncomingCallEvent
import com.susking.ephone_s.notification.IncomingCallNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * AI后台独立行动决策的工作器。
 * 遵循 "AI在非活跃状态下的独立行动决策" (ssryuanma) 文档规范进行重构。
 *
 * 主要职责:
 * 1. 检查后台活动是否启用。
 * 2. 筛选已启用独立后台活动、未拉黑、非群聊且单人冷却结束的AI角色。
 * 3. 从数据库和仓库中收集所有必要的上下文信息。
 * 4. 调用 [BackgroundPromptBuilder] 构建符合规范的系统提示词。
 * 5. 通过 Brain 模块转发请求并获取AI决策。
 * 6. 解析AI返回的行动指令，并复用聊天侧行动执行器执行。
 * 7. 记录完整的活动日志。
 */
@HiltWorker
class AiBackgroundWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val actionExecutor: ActionExecutor
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "AiBackgroundActionWorker"
        private const val TAG = "AiBackgroundWorker"
    }

    private val gson = GsonBuilder().create()


    /**
     * 清理AI返回的JSON字符串，移除可能存在的Markdown代码块。
     */
    private fun cleanupAiResponse(response: String?): String {
        if (response.isNullOrBlank()) {
            return ""
        }
        
        // 【诊断日志】记录清理前的响应
        Log.d(TAG, "【诊断】cleanupAiResponse 输入前200字符: ${response.take(200)}")
        
        // 正则表达式，用于提取被 ´´´json ... ´´´ 包裹或没有被包裹的JSON内容
        val regex = Regex("(?s)```json\\s*(.*?)\\s*```|\\s*(.*)")
        val matchResult = regex.find(response)
        // 优先返回第一个捕获组（被包裹的内容），否则返回第二个（未被包裹的内容）
        val cleaned = matchResult?.groups?.get(1)?.value?.trim() ?: matchResult?.groups?.get(2)?.value?.trim() ?: ""
        
        // 【诊断日志】记录清理后的结果
        Log.d(TAG, "【诊断】cleanupAiResponse 输出前200字符: ${cleaned.take(200)}")
        
        return cleaned
    }


    private fun isContactEligibleForBackgroundAction(contact: PersonProfile, now: Long): Boolean {
        if (!contact.backgroundActivityEnabled) return false
        if (contact.isBlocked) return false
        if (contact.isGroupChat) return false

        val cooldownMillis: Long = contact.actionCooldownMinutes
            .coerceAtLeast(0)
            .toLong() * 60_000L
        val lastActionTimestamp: Long = contact.lastBackgroundActionTimestamp ?: 0L
        return now >= lastActionTimestamp + cooldownMillis
    }

    private fun countVisibleAssistantMessages(messages: List<ChatMessage>): Int {
        return messages.count { message ->
            message.role == "assistant" && message.type !in setOf("hidden", "system", "thought", "thought_chain")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "AI 独立行动决策开始...")

        // 1. 实例化所有需要的依赖
        val aiDataDb = EPhoneSApplication.db
        val settingsRepository = SettingsRepositoryImpl(applicationContext)
        val personProfileRepository = PersonProfileRepositoryImpl(applicationContext, aiDataDb)
        val favoriteMessageRepository = FavoriteMessageRepositoryImpl(aiDataDb)
        // Worker 中不需要 ActiveContactTracker，因为后台任务不受用户当前界面影响
        val chatRepository = ChatRepositoryImpl(aiDataDb, applicationContext, favoriteMessageRepository, personProfileRepository, null)
        val saveImageUseCase = AiDataApi.getSaveImageUseCase()
        val feedRepository = FeedRepositoryImpl(aiDataDb.feedDao(), saveImageUseCase)
        val stickerRepository = StickerRepositoryImpl(aiDataDb.stickerDao())
        val longTermMemoryRepository = LongTermMemoryRepositoryImpl(aiDataDb.longTermMemoryDao())
        val worldBookEntryRepository = WorldBookEntryRepositoryImpl(aiDataDb.worldBookEntryDao())
        val callStateRepository = CallStateRepositoryImpl()
        val activityLogger = EPhoneSApplication.activityLogger

        // 2. 检查后台活动是否启用
        if (!settingsRepository.isBackgroundActivityEnabled()) {
            Log.d(TAG, "后台活动已被禁用，任务终止。")
            return@withContext Result.success()
        }

        val activityChainId = UUID.randomUUID().toString()
        var activityLog = AiActivity(
            activityChainId = activityChainId,
            id = UUID.randomUUID().mostSignificantBits,
            description = "AI 独立行动决策任务开始",
            prompt = "初始化中...",
            rawResponse = "",
            timestamp = System.currentTimeMillis(),
            status = AiActivityStatus.PROCESSING
        )
        activityLogger.log(activityLog)

        // 2.5. 【新增】检查是否有任何联系人刚刚结束拉黑冷静期
        val allContacts = personProfileRepository.getPersonProfiles()
        val now = System.currentTimeMillis()
        for (contact in allContacts) {
            if (contact.isBlocked) {
                val blockTimestamp = contact.blockTimestamp
                val blockCooldownHours = contact.blockCooldownHours
                if (blockTimestamp != null && blockCooldownHours != null) {
                    val cooldownMillis = blockCooldownHours.toLong() * 60 * 60 * 1000
                    if (now >= blockTimestamp + cooldownMillis) {
                        Log.d(TAG, "检测到 ${contact.remarkName} 的拉黑冷静期已结束，将触发好友申请。")

                        // 使用新的Builder构建申请提示词
                        val userProfile = personProfileRepository.getUserProfile()
                        val contactHistory = chatRepository.getMessagesForContact(contact.id).firstOrNull() ?: emptyList()
                        val worldBookPrompts = worldBookEntryRepository.getEnabledWorldBookPrompts()
                        val applicationPrompt = BackgroundPromptBuilder.buildFriendApplicationPrompt(
                            actingContact = contact,
                            userProfile = userProfile,
                            contactHistory = contactHistory,
                            worldBookPrompts = worldBookPrompts,
                            shortTermMemoryCount = contact.shortTermMemoryLimit
                        )

                        // 更新活动日志以包含生成的提示词
                        activityLog = activityLog.copy(prompt = applicationPrompt, description = "为 ${contact.remarkName} 准备好友申请...")
                        activityLogger.log(activityLog)

                        // 调用AI服务获取决策
                        val promptRequest = AiDataApi.getAiPromptService().buildBackgroundActionPrompt(applicationPrompt)
                        val aiResponseJson = BrainApi.getAiRequestService().getChatCompletion(applicationContext, promptRequest)
                        val cleanedJson = cleanupAiResponse(aiResponseJson)

                        try {
                            val responseObj = gson.fromJson(cleanedJson, JsonObject::class.java)
                            val decision = responseObj.get("decision")?.asString
                            val reason = responseObj.get("reason")?.asString

                            if (decision == "apply" && !reason.isNullOrBlank()) {
                                // AI决定申请，发送好友申请
                                val applicationMessage = ChatMessage(
                                    contactId = contact.id,
                                    type = "friend_application",
                                    content = reason,
                                    status = "pending",
                                    role = "assistant"
                                )
                                chatRepository.insertMessage(applicationMessage)

                                // 更新联系人状态，标记申请已发送，避免重复
                                val updatedContact = contact.copy(applicationReason = reason, isBlocked = false) // 解除拉黑状态
                                personProfileRepository.updatePersonProfile(updatedContact)
                                Log.d(TAG, "AI决定为 ${contact.remarkName} 申请好友，理由: $reason")

                                activityLog = activityLog.copy(
                                    description = "为 ${contact.remarkName} 发送好友申请",
                                    status = AiActivityStatus.SUCCESS,
                                    rawResponse = cleanedJson
                                )
                                activityLogger.log(activityLog)
                            } else {
                                // AI决定不申请，重置冷静期
                                val updatedContact = contact.copy(blockTimestamp = System.currentTimeMillis())
                                personProfileRepository.updatePersonProfile(updatedContact)
                                Log.d(TAG, "AI决定暂时不为 ${contact.remarkName} 发送好友申请，重置冷静期。")

                                activityLog = activityLog.copy(
                                    description = "AI决定不为 ${contact.remarkName} 发送好友申请",
                                    status = AiActivityStatus.SUCCESS,
                                    rawResponse = cleanedJson
                                )
                                activityLogger.log(activityLog)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析好友申请决策JSON失败", e)
                            // 解析失败也重置冷静期，防止卡死
                            val updatedContact = contact.copy(blockTimestamp = System.currentTimeMillis())
                            personProfileRepository.updatePersonProfile(updatedContact)
                            activityLog = activityLog.copy(
                                description = "解析好友申请决策失败",
                                status = AiActivityStatus.FAILED,
                                rawResponse = cleanedJson
                            )
                            activityLogger.log(activityLog)
                        }
                        // 无论如何，本次任务只处理这一个事件
                        return@withContext Result.success()
                    }
                }
            }
        }

        try {
            // 3. 选取行动角色：仅允许单联系人独立后台活动开启、未拉黑、非群聊且单人冷却已结束的联系人参与筛选。
            // 同时排除"正在与之通话"的联系人,避免 AI 一边和用户视频通话、一边在后台又给用户发消息或再拨来电。
            val busyCallContactId: String? = CallBusyState.currentBusyContactId
            val eligibleContacts: List<PersonProfile> = allContacts.filter { contact ->
                isContactEligibleForBackgroundAction(contact, now) && contact.id != busyCallContactId
            }
            if (eligibleContacts.isEmpty()) {
                Log.d(TAG, "没有满足独立后台活动开关与单人冷却条件的联系人，行动终止。")
                activityLog = activityLog.copy(description = "没有满足独立后台活动条件的联系人，行动终止。", status = AiActivityStatus.SUCCESS)
                activityLogger.log(activityLog)
                return@withContext Result.success()
            }
            val actingContact = eligibleContacts.random()
            Log.d(TAG, "行动角色选定: ${actingContact.remarkName}")

            // 4. 收集上下文信息
            val userProfile = personProfileRepository.getUserProfile()
            val contactHistory = chatRepository.getMessagesForContact(actingContact.id).firstOrNull() ?: emptyList()
            val allRecentFeeds = feedRepository.getAllFeeds().firstOrNull() ?: emptyList()
            val worldBookPrompts = worldBookEntryRepository.getEnabledWorldBookPrompts()
            val longTermMemories = longTermMemoryRepository.getMemories(actingContact.id).firstOrNull() ?: emptyList()
            val availableStickers = stickerRepository.getAllStickers().firstOrNull() ?: emptyList()

            // 5. 调用 BackgroundPromptBuilder 构建提示词
            val systemPrompt = BackgroundPromptBuilder.buildPrompt(
                actingContact = actingContact,
                userProfile = userProfile,
                contactHistory = contactHistory,
                allRecentFeeds = allRecentFeeds,
                worldBookPrompts = worldBookPrompts,
                longTermMemories = longTermMemories,
                availableStickers = availableStickers,
                wasCallInitiatedByUser = callStateRepository.getWasCallInitiatedByUser(actingContact.id)
            )

            // 更新活动日志
            activityLog = activityLog.copy(prompt = systemPrompt, description = "${actingContact.remarkName} 独立行动决策中...")
            activityLogger.log(activityLog)

            // 6. 调用AI服务获取决策
            Log.d(TAG, "正在为后台活动发送API请求...")
            val promptRequest = AiDataApi.getAiPromptService().buildBackgroundActionPrompt(systemPrompt)
            val aiResponseJson = BrainApi.getAiRequestService().getChatCompletion(applicationContext, promptRequest)
            val cleanedJson = cleanupAiResponse(aiResponseJson)

            if (cleanedJson.isBlank()) {
                Log.w(TAG, "API返回为空或清理后为空，本次行动跳过。")
                activityLog = activityLog.copy(
                    description = "AI决策完成",
                    rawResponse = "API返回为空或清理后为空",
                    status = AiActivityStatus.SUCCESS,
                    timestamp = System.currentTimeMillis()
                )
                activityLogger.log(activityLog)
                return@withContext Result.success()
            }

            Log.d(TAG, "收到AI决策: $cleanedJson")

            // 7. 解析并执行行动
            val actions = AiActionParser.parseAiActions(cleanedJson)

            activityLog = activityLog.copy(
                description = "${actingContact.remarkName} 决策完成",
                rawResponse = cleanedJson,
                status = AiActivityStatus.SUCCESS,
                timestamp = System.currentTimeMillis()
            )
            activityLogger.log(activityLog)

            if (actions.isNullOrEmpty()) {
                Log.d(TAG, "AI决定本次不行动。")
                return@withContext Result.success()
            }

            val turnId: String = UUID.randomUUID().toString()
            val executionResult = actionExecutor.executeActions(
                context = applicationContext,
                actions = actions,
                contactId = actingContact.id,
                aiTurnId = turnId
            )

            val messages: List<ChatMessage> = executionResult.messages
            messages.forEach { message ->
                chatRepository.insertMessage(message.copy(aiTurnId = turnId))
            }

            executionResult.asyncTasks.forEach { task ->
                try {
                    task()
                } catch (e: Exception) {
                    Log.e(TAG, "执行后台异步行动任务失败", e)
                }
            }

            executionResult.incomingCallContactId?.let { incomingContactId ->
                // AI 在后台独立行动时发起视频通话。根据应用当前前后台状态分流处理：
                // - 前台(用户正看着小手机)：发全局来电事件,MainActivity 直接弹出来电界面(复用前台来电链路)。
                // - 后台(切走/熄屏)：发全屏来电通知,系统在锁屏/后台直接全屏弹出,点击进入 MainActivity 弹来电界面。
                // - 后台且通知发送失败(如缺权限)：兜底记一条未接来电,避免来电"凭空消失"。
                handleBackgroundIncomingCall(
                    contactId = incomingContactId,
                    chatRepository = chatRepository,
                    personProfileRepository = personProfileRepository
                )
            }

            val visibleMessageCount: Int = countVisibleAssistantMessages(messages)
            val latestContact: PersonProfile = personProfileRepository.getPersonProfileById(actingContact.id) ?: actingContact
            val updatedContact: PersonProfile = latestContact.copy(
                unreadMessageCount = latestContact.unreadMessageCount + visibleMessageCount,
                lastBackgroundActionTimestamp = System.currentTimeMillis()
            )
            personProfileRepository.updatePersonProfile(updatedContact)
            Log.i(TAG, "已通过ActionExecutor执行 ${actions.size} 个后台行动，并更新 ${actingContact.remarkName} 的后台行动时间。")
            Log.d(TAG, "AI 独立行动决策执行完毕。")
            EventBus.postNewAiActivityEvent()
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "AI 独立行动决策任务失败", e)
            activityLog = activityLog.copy(
                description = "AI 独立行动决策任务异常",
                rawResponse = e.message ?: "未知错误",
                status = AiActivityStatus.FAILED,
                timestamp = System.currentTimeMillis()
            )
            activityLogger.log(activityLog)
            Result.failure()
        }
    }

    /**
     * 处理后台 AI 来电：按应用前后台状态分流。
     *
     * - 前台(用户正看着小手机)：发全局来电事件,MainActivity 直接弹出来电界面(复用前台来电链路)。
     * - 后台(切走/熄屏)：发全屏来电通知,系统在锁屏/后台直接全屏弹出,点击进入 MainActivity 弹来电界面。
     * - 后台且通知发送失败(如缺权限)：兜底记一条未接来电,避免来电"凭空消失"。
     *
     * 前后台判断用 [ProcessLifecycleOwner]：它维护整个应用进程的聚合生命周期状态,
     * 任一 Activity 处于前台时全局状态即 >= STARTED,无需自行维护标志位。
     * 注意:ProcessLifecycleOwner.currentState 须在主线程读取,故用 withContext(Dispatchers.Main) 切换。
     *
     * @param contactId 来电联系人ID
     * @param chatRepository 聊天仓库(用于兜底插入未接来电卡片消息)
     * @param personProfileRepository 联系人仓库(用于查询来电联系人显示名)
     */
    private suspend fun handleBackgroundIncomingCall(
        contactId: String,
        chatRepository: ChatRepositoryImpl,
        personProfileRepository: PersonProfileRepositoryImpl
    ) {
        // 在主线程读取全局生命周期状态,判断应用当前是否在前台。
        val isAppInForeground: Boolean = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        }

        if (isAppInForeground) {
            // 前台:直接发全局事件,MainActivity 会弹出来电界面。
            EventBus.post(IncomingCallEvent(contactId))
            Log.i(TAG, "后台来电[前台分流]: 已发全局来电事件, contactId=$contactId")
            return
        }

        // 后台:查联系人显示名后发全屏来电通知。
        val contact: PersonProfile? = personProfileRepository.getPersonProfileById(contactId)
        val contactName: String = contact?.remarkName ?: "未知联系人"
        val isNotificationSent: Boolean = IncomingCallNotificationHelper.sendIncomingCallNotification(
            context = applicationContext,
            contactId = contactId,
            contactName = contactName
        )

        if (isNotificationSent) {
            Log.i(TAG, "后台来电[后台分流]: 已发全屏来电通知, contactId=$contactId")
        } else {
            // 通知发送失败(如缺权限):兜底记未接来电,避免来电凭空消失。
            Log.w(TAG, "后台来电[后台分流]: 全屏通知发送失败,转未接来电兜底, contactId=$contactId")
            recordMissedIncomingCall(contactId, chatRepository)
        }
    }

    /**
     * 兜底记录一条未接来电。
     *
     * 仅在「后台来电且全屏通知发送失败」时调用,确保来电不会凭空消失。
     * 1) 落 video_call_history 表(callStatus=missed,时长0,非用户发起);
     * 2) 往聊天插一条 video_call_record 消息,使聊天界面显示未接来电卡片,可点击查看详情。
     *
     * 注意:此处仅新增数据,不涉及表结构变更,无数据迁移问题。
     *
     * @param contactId 来电联系人ID
     * @param chatRepository 聊天仓库,用于插入未接来电卡片消息
     */
    private suspend fun recordMissedIncomingCall(
        contactId: String,
        chatRepository: ChatRepositoryImpl
    ) {
        try {
            val videoCallHistoryRepository = AiDataApi.getVideoCallHistoryRepository()
            val now: Long = System.currentTimeMillis()
            val missedCallEntity = com.susking.ephone_s.aidata.data.local.entity.VideoCallHistoryEntity(
                contactId = contactId,
                timestamp = now,
                duration = 0L,
                messages = emptyList(),
                wasInitiatedByUser = false, // AI 发起的来电
                terminationReason = "未接来电",
                callStatus = "missed", // 未接:不可恢复,区别于 in_progress
                lastUpdateTime = now
            )
            val missedCallId: Long = videoCallHistoryRepository.insertVideoCallHistory(missedCallEntity)

            // 插入聊天卡片消息,携带 videoCallId 供点击查看详情。
            val contentJson: String = gson.toJson(mapOf(
                "text" to "未接来电",
                "videoCallId" to missedCallId
            ))
            val missedCallMessage = ChatMessage(
                contactId = contactId,
                type = "video_call_record",
                content = contentJson,
                role = "user"
            )
            chatRepository.insertMessage(missedCallMessage)
            Log.i(TAG, "后台未接来电已记录: contactId=$contactId, videoCallId=$missedCallId")
        } catch (e: Exception) {
            Log.e(TAG, "记录后台未接来电失败: contactId=$contactId", e)
        }
    }
}