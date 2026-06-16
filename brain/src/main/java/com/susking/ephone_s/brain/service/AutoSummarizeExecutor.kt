package com.susking.ephone_s.brain.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.MemoryType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.domain.use_case.TriggerAutoSummarizeUseCase
import com.susking.ephone_s.aidata.prompt.SummarizeChatHistoryPromptBuilder
import com.susking.ephone_s.core.util.EventBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动结构化事件提取执行器。
 *
 * 监听 AutoSummarizeRequestEvent 事件，自动执行结构化事件提取并保存到结构化事件表。
 */
@Singleton
class AutoSummarizeExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personProfileRepository: PersonProfileRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val summarizeChatHistoryPromptBuilder: SummarizeChatHistoryPromptBuilder,
    private val memoryVectorizationService: MemoryVectorizationService,
    private val memoryEventDao: MemoryEventDao,
    private val memoryFactGraphExtractionService: MemoryFactGraphExtractionService,
    private val aiRequestService: AiRequestService
) {
    
    companion object {
        private const val TAG = "AutoSummarizeExecutor"
        private const val AUTO_STRUCTURED_EVENT_SOURCE = "AutoStructuredEventExtraction"
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.75f
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val runningContactIds: MutableSet<String> = mutableSetOf()
    
    /**
     * 启动监听器
     */
    fun start() {
        Log.d(TAG, "自动结构化事件提取执行器启动，开始订阅自动总结事件")
        scope.launch {
            EventBus.events
                .filterIsInstance<TriggerAutoSummarizeUseCase.AutoSummarizeRequestEvent>()
                .collect { event ->
                    Log.i(TAG, "收到自动结构化事件提取请求: contactId=${event.contactId}, contactName=${event.contactName}")
                    executeSummarizeIfIdle(event.contactId, event.contactName)
                }
        }
    }
    
    /**
     * 停止监听器
     */
    fun stop() {
        Log.d(TAG, "自动结构化事件提取执行器停止")
        scope.cancel()
    }
    
    private suspend fun executeSummarizeIfIdle(contactId: String, contactName: String): Unit {
        synchronized(runningContactIds) {
            if (contactId in runningContactIds) {
                Log.d(TAG, "自动结构化事件提取跳过重复执行: contactId=$contactId, contactName=$contactName")
                return
            }
            runningContactIds.add(contactId)
        }
        try {
            executeSummarize(contactId, contactName)
        } finally {
            synchronized(runningContactIds) {
                runningContactIds.remove(contactId)
            }
        }
    }
    
    /**
     * 执行自动结构化事件提取。
     */
    private suspend fun executeSummarize(contactId: String, contactName: String) {
        try {
            Log.i(TAG, "开始为 $contactName 执行自动结构化事件提取")
            
            // 1. 获取联系人和用户信息
            val contact = personProfileRepository.getPersonProfileById(contactId)
            if (contact == null) {
                Log.e(TAG, "联系人不存在: $contactId")
                return
            }
            
            val userProfile = personProfileRepository.getUserProfile()

            Log.d(TAG, "自动结构化事件提取联系人配置: contactId=$contactId, autoSummaryEnabled=${contact.autoSummaryEnabled}, summaryInterval=${contact.summaryInterval}, lastSummaryTimestamp=${contact.lastSummaryTimestamp}, failureCount=${contact.autoSummaryFailureCount}")

            // 2. 准备封顶分批的结构化事件提取请求。窗口固定大小，斩断"失败-积压-更大窗口-再失败"的雪球死循环。
            val batchResult = summarizeChatHistoryPromptBuilder.prepareAutoBatch(
                contactId = contactId,
                userNickname = userProfile.nickname,
                characterName = contact.realName,
                characterPersona = contact.persona,
                userPersona = userProfile.persona,
                startTimestamp = contact.lastSummaryTimestamp
            )

            batchResult.onSuccess { autoBatch: SummarizeChatHistoryPromptBuilder.AutoBatchPrompt ->
                executeBatch(contactId, contactName, contact, autoBatch)
            }.onFailure { error ->
                // prepare 失败多为"消息过少"等良性跳过，不计入退避失败次数。
                Log.d(TAG, "准备结构化事件提取批次跳过: contactId=$contactId, 原因=${error.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "自动结构化事件提取执行失败: ${e.message}", e)
        }
    }

    /**
     * 执行单批提取并按结果推进游标 / 更新失败计数。
     *
     * 成功（含 AI 判定本批无可抽取内容）：游标推进到本批最大消息时间戳、失败计数清零；若仍有积压则再投递一次事件继续偿还。
     * 失败（请求异常、AI 返回为空、保存抛错）：游标保持不动，失败计数 +1，交给触发器做指数退避。
     */
    private suspend fun executeBatch(
        contactId: String,
        contactName: String,
        contact: com.susking.ephone_s.aidata.domain.model.PersonProfile,
        autoBatch: SummarizeChatHistoryPromptBuilder.AutoBatchPrompt
    ): Unit {
        val promptRequest = autoBatch.promptRequest
        try {
            Log.d(TAG, "自动结构化事件提取批次准备成功: contactId=$contactId, batchSize=${autoBatch.batchSize}, batchMaxTimestamp=${autoBatch.batchMaxTimestamp}, hasMoreBacklog=${autoBatch.hasMoreBacklog}, url=${promptRequest.url}")
            // 调用AI服务
            val aiResponse = aiRequestService.getChatCompletion(
                context = context,
                promptRequest = promptRequest
            )

            if (aiResponse.isNullOrBlank()) {
                Log.w(TAG, "AI返回为空，自动结构化事件提取失败，游标不推进")
                recordExtractionFailure(contact, contactName)
                return
            }
            Log.d(TAG, "自动结构化事件提取收到AI响应: contactId=$contactId, responseLength=${aiResponse.length}")

            // 优先通过完整事实图谱保存链条写入；失败时保留旧事件保存链条兜底。
            memoryFactGraphExtractionService.saveChatFactGraphResponse(contactId, aiResponse)
                .onSuccess { saveResult: MemoryFactGraphExtractionService.ExtractionResult ->
                    advanceCursorOnSuccess(contact, autoBatch.batchMaxTimestamp)
                    Log.i(TAG, "自动结构化记忆提取成功: 联系人=$contactName, 事件=${saveResult.eventCount}, 节点=${saveResult.nodeCount}, 关系=${saveResult.relationCount}, 游标推进至=${autoBatch.batchMaxTimestamp}")
                    requestNextBatchIfBacklogRemains(contactId, contactName, autoBatch.hasMoreBacklog)
                }
                .onFailure { saveError: Throwable ->
                    Log.w(TAG, "完整图谱保存失败，回退到旧结构化事件保存链条: 联系人=$contactName, 原因=${saveError.message}", saveError)
                    val extractedEvents: List<AutoExtractedStructuredEvent> = parseExtractedStructuredEvents(aiResponse)
                    if (extractedEvents.isEmpty()) {
                        Log.w(TAG, "无法从AI响应中提取结构化事件，游标不推进")
                        recordExtractionFailure(contact, contactName)
                    } else {
                        val baseTimestamp: Long = System.currentTimeMillis()
                        extractedEvents.forEachIndexed { index: Int, extractedEvent: AutoExtractedStructuredEvent ->
                            saveStructuredEvent(contactId, contactName, extractedEvent, baseTimestamp + index)
                        }
                        advanceCursorOnSuccess(contact, autoBatch.batchMaxTimestamp)
                        Log.i(TAG, "自动结构化事件提取成功(兜底链条): 联系人=$contactName, 结构化事件数量=${extractedEvents.size}, 游标推进至=${autoBatch.batchMaxTimestamp}")
                        requestNextBatchIfBacklogRemains(contactId, contactName, autoBatch.hasMoreBacklog)
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "执行自动结构化事件提取失败: ${e.message}", e)
            recordExtractionFailure(contact, contactName)
        }
    }

    /**
     * 提取成功：游标推进到本批最大消息时间戳（而非 now，避免提取期间新到的消息被跳过），失败计数清零。
     */
    private suspend fun advanceCursorOnSuccess(contact: com.susking.ephone_s.aidata.domain.model.PersonProfile, batchMaxTimestamp: Long): Unit {
        val updatedContact = contact.copy(
            lastSummaryTimestamp = batchMaxTimestamp,
            autoSummaryFailureCount = 0
        )
        personProfileRepository.updatePersonProfile(updatedContact)
    }

    /**
     * 提取失败：游标保持不动以免丢消息，失败计数 +1，供触发器做指数退避。
     */
    private suspend fun recordExtractionFailure(contact: com.susking.ephone_s.aidata.domain.model.PersonProfile, contactName: String): Unit {
        val latestContact = personProfileRepository.getPersonProfileById(contact.id) ?: contact
        val updatedFailureCount: Int = latestContact.autoSummaryFailureCount + 1
        personProfileRepository.updatePersonProfile(latestContact.copy(autoSummaryFailureCount = updatedFailureCount))
        Log.w(TAG, "自动结构化事件提取失败计数+1: 联系人=$contactName, failureCount=$updatedFailureCount")
    }

    /**
     * 本批之后仍有积压时再投递一次事件，驱动分批偿还。每次都严格推进游标，故必然收敛、不会死循环。
     */
    private fun requestNextBatchIfBacklogRemains(contactId: String, contactName: String, hasMoreBacklog: Boolean): Unit {
        if (!hasMoreBacklog) return
        Log.d(TAG, "本批后仍有积压，继续投递下一批提取事件: contactId=$contactId")
        EventBus.post(TriggerAutoSummarizeUseCase.AutoSummarizeRequestEvent(contactId, contactName))
    }

    private suspend fun saveStructuredEvent(contactId: String, contactName: String, extractedEvent: AutoExtractedStructuredEvent, eventTime: Long): Unit {
        val rawEvidenceText = buildStructuredEventMemoryText(extractedEvent.title, extractedEvent.content)
        val event = MemoryEvent(
            contactId = contactId,
            evidenceMemoryId = null,
            eventType = extractedEvent.eventType,
            title = extractedEvent.title,
            content = extractedEvent.content,
            eventTime = eventTime,
            importanceScore = extractedEvent.importanceScore,
            confidenceScore = extractedEvent.confidenceScore,
            sourceModule = AUTO_STRUCTURED_EVENT_SOURCE,
            rawEvidenceText = rawEvidenceText,
            dedupeKey = buildStructuredEventDedupeKey(extractedEvent.eventType, extractedEvent.title, eventTime)
        )
        memoryEventDao.insert(event)
        val compatibleMemoryId: String = createStructuredEventCompatibleMemory(event)
        vectorizeStructuredEvent(event, compatibleMemoryId, contactName)
    }

    private suspend fun createStructuredEventCompatibleMemory(event: MemoryEvent): String {
        longTermMemoryRepository.addMemory(
            LongTermMemory(
                id = event.id,
                contactId = event.contactId,
                memoryText = buildStructuredEventMemoryText(event.title, event.content),
                timestamp = event.eventTime,
                memoryType = MemoryType.EVENT,
                importanceScore = event.importanceScore,
                sourceModule = event.sourceModule,
                isVectorized = true
            )
        )
        return event.id
    }

    private suspend fun vectorizeStructuredEvent(event: MemoryEvent, compatibleMemoryId: String, contactName: String): Unit {
        memoryVectorizationService.vectorizeEvent(event, compatibleMemoryId)
            .onSuccess { result: MemoryVectorizationService.VectorizationResult ->
                Log.i(TAG, "自动结构化事件向量化成功: 联系人=$contactName, eventId=${event.id}, memoryId=${result.memoryId}, dimension=${result.dimension}, model=${result.modelName}/${result.modelVersion}")
            }
            .onFailure { error: Throwable ->
                Log.w(TAG, "自动结构化事件向量化失败，将保留事件正文: 联系人=$contactName, eventId=${event.id}, 原因=${error.message}", error)
            }
    }

    private fun parseExtractedStructuredEvents(rawResponse: String): List<AutoExtractedStructuredEvent> {
        return runCatching {
            val rootObject: JsonObject = parseStructuredEventRoot(rawResponse)
            val eventArray: JsonArray = when {
                rootObject.has("events") && rootObject.get("events").isJsonArray -> rootObject.getAsJsonArray("events")
                rootObject.has("structuredEvents") && rootObject.get("structuredEvents").isJsonArray -> rootObject.getAsJsonArray("structuredEvents")
                else -> JsonArray()
            }
            eventArray.mapNotNull { element: JsonElement -> parseExtractedStructuredEvent(element) }
        }.onFailure { error: Throwable ->
            Log.e(TAG, "解析结构化事件失败: ${error.message}", error)
        }.getOrDefault(emptyList())
    }

    private fun parseExtractedStructuredEvent(element: JsonElement): AutoExtractedStructuredEvent? {
        if (!element.isJsonObject) return null
        val itemObject: JsonObject = element.asJsonObject
        val title: String = itemObject.getStringOrNull("title")?.trim().orEmpty()
        val content: String = itemObject.getStringOrNull("content")?.trim().orEmpty()
        if (title.isBlank() || content.isBlank()) return null
        return AutoExtractedStructuredEvent(
            eventType = parseMemoryEventType(itemObject.getStringOrNull("eventType") ?: itemObject.getStringOrNull("type")),
            title = title,
            content = content,
            importanceScore = (itemObject.getIntOrNull("importanceScore") ?: DEFAULT_IMPORTANCE_SCORE).coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
            confidenceScore = (itemObject.getFloatOrNull("confidenceScore") ?: DEFAULT_CONFIDENCE_SCORE).coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE)
        )
    }

    private fun parseStructuredEventRoot(rawResponse: String): JsonObject {
        val cleanedText: String = rawResponse
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val startIndex: Int = cleanedText.indexOf('{')
        val endIndex: Int = cleanedText.lastIndexOf('}')
        val jsonText: String = if (startIndex >= 0 && endIndex > startIndex) cleanedText.substring(startIndex, endIndex + 1) else cleanedText
        return gson.fromJson(jsonText, JsonObject::class.java)
    }

    private fun parseMemoryEventType(value: String?): MemoryEventType {
        return when (value?.trim()?.uppercase()) {
            "COMMITMENT", "承诺", "约定" -> MemoryEventType.COMMITMENT
            "PREFERENCE", "偏好" -> MemoryEventType.PREFERENCE
            "PROHIBITION", "禁忌", "禁止" -> MemoryEventType.PROHIBITION
            "ANNIVERSARY", "纪念日" -> MemoryEventType.ANNIVERSARY
            "RELATIONSHIP", "关系", "关系变化" -> MemoryEventType.RELATIONSHIP
            "OPINION", "观点" -> MemoryEventType.OPINION
            "OTHER", "其他" -> MemoryEventType.OTHER
            else -> MemoryEventType.FACT
        }
    }

    private fun buildStructuredEventMemoryText(title: String, content: String): String {
        return "$title\n$content"
    }

    private fun buildStructuredEventDedupeKey(eventType: MemoryEventType, title: String, eventTime: Long): String {
        return "auto:${eventType.name}:${title.trim().lowercase()}:$eventTime"
    }

    private fun JsonObject.getStringOrNull(name: String): String? {
        return if (has(name) && !get(name).isJsonNull) get(name).asString else null
    }

    private fun JsonObject.getIntOrNull(name: String): Int? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asInt else null }.getOrNull()
    }

    private fun JsonObject.getFloatOrNull(name: String): Float? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asFloat else null }.getOrNull()
    }

    private data class AutoExtractedStructuredEvent(
        val eventType: MemoryEventType,
        val title: String,
        val content: String,
        val importanceScore: Int,
        val confidenceScore: Float
    )
}