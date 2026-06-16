package com.susking.ephone_s.qq.ui.chat.memory

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.MemoryType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEmbedding
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.prompt.SummarizeChatHistoryPromptBuilder
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.brain.service.AiRequestService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LongTermMemoryViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val repository: LongTermMemoryRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val summarizeChatHistoryPromptBuilder: SummarizeChatHistoryPromptBuilder,
    private val aiRequestService: AiRequestService,
    private val memoryVectorizationService: MemoryVectorizationService,
    private val memoryFactGraphExtractionService: MemoryFactGraphExtractionService,
    private val memoryEmbeddingDao: MemoryEmbeddingDao,
    private val memoryEventDao: MemoryEventDao,
    private val memorySummaryDao: MemorySummaryDao,
    private val chatMessageDao: ChatMessageDao
) : AndroidViewModel(application) {

    private val contactId: String = savedStateHandle.get<String>("contactId")
        ?: throw IllegalArgumentException("contactId is required")

    val memories = repository.getMemories(contactId)
        .map { memories: List<LongTermMemory> ->
            memories.filter { memory: LongTermMemory -> memory.sourceModule !in HIDDEN_STRUCTURED_EVENT_COMPATIBLE_SOURCES }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastExtractionPositionTimestamp = MutableStateFlow<Long?>(null)
    val lastExtractionPositionTimestamp = _lastExtractionPositionTimestamp.asStateFlow()

    private val _newMessageCountSinceLastExtraction = MutableStateFlow<Int>(0)
    val newMessageCountSinceLastExtraction = _newMessageCountSinceLastExtraction.asStateFlow()

    val structuredEvents = memoryEventDao.getAllEvents(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val summaries = memorySummaryDao.getSummariesForContact(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeEmbeddings = memoryEmbeddingDao.getActiveEmbeddingsForContact(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryCenterUiState = combine(memories, structuredEvents, summaries, activeEmbeddings) { legacyMemories: List<LongTermMemory>, events: List<MemoryEvent>, summaryList: List<MemorySummary>, embeddings: List<MemoryEmbedding> ->
        MemoryCenterUiState.from(legacyMemories, events, summaryList, embeddings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryCenterUiState.EMPTY)

    // 用于传递一次性事件的状态
    private val _summaryResult = MutableStateFlow<Result<String>?>(null)
    val summaryResult = _summaryResult.asStateFlow()

    private val _pendingPromptRequest = MutableStateFlow<AiPromptRequest?>(null)
    val pendingPromptRequest = _pendingPromptRequest.asStateFlow()

    
    private val _pendingExtractionPreview = MutableStateFlow<FactGraphExtractionPreview?>(null)
    val pendingExtractionPreview = _pendingExtractionPreview.asStateFlow()

    init {
        refreshLastExtractionPositionTimestamp()
    }

    // 标记是否使用了自定义时间戳（用于清理）
    private var usedCustomTimestamp: Boolean = false
    private var originalTimestamp: Long? = null
    private var nextMemoryTimestamp: Long? = null // 为解决手动总结后时间戳不正确的问题

    fun createStructuredEvent(
        eventType: MemoryEventType,
        title: String,
        content: String,
        importanceScore: Int,
        confidenceScore: Float
    ): Unit {
        createStructuredEventAtTime(
            eventType = eventType,
            title = title,
            content = content,
            importanceScore = importanceScore,
            confidenceScore = confidenceScore,
            eventTime = System.currentTimeMillis()
        )
    }

    fun createStructuredEventAtTime(
        eventType: MemoryEventType,
        title: String,
        content: String,
        importanceScore: Int,
        confidenceScore: Float,
        eventTime: Long
    ): Unit {
        val trimmedTitle: String = title.trim()
        val trimmedContent: String = content.trim()
        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            _summaryResult.value = Result.failure(IllegalArgumentException("结构化事件标题和内容不能为空"))
            return
        }
        viewModelScope.launch {
            val rawEvidenceText = buildStructuredEventMemoryText(trimmedTitle, trimmedContent)
            val event = MemoryEvent(
                contactId = contactId,
                evidenceMemoryId = null, // 废弃兼容性原子事件
                eventType = eventType,
                title = trimmedTitle,
                content = trimmedContent,
                eventTime = eventTime,
                importanceScore = importanceScore.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
                confidenceScore = confidenceScore.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE),
                sourceModule = STRUCTURED_EVENT_MANUAL_SOURCE,
                rawEvidenceText = rawEvidenceText,
                dedupeKey = buildManualStructuredEventDedupeKey(eventType, trimmedTitle, eventTime)
            )
            memoryEventDao.insert(event)
            val compatibleMemoryId: String = createStructuredEventCompatibleMemory(event)
            // 使用 event.id 自身作为向量化对象 ID，避免 Room 插入函数返回 Unit 时生成错误兼容 ID。
            vectorizeStructuredEventSafely(event, compatibleMemoryId)
        }
    }

    fun updateStructuredEvent(
        event: MemoryEvent,
        eventType: MemoryEventType,
        title: String,
        content: String,
        importanceScore: Int,
        confidenceScore: Float,
        status: MemoryEventStatus,
        eventTime: Long
    ): Unit {
        val trimmedTitle: String = title.trim()
        val trimmedContent: String = content.trim()
        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            _summaryResult.value = Result.failure(IllegalArgumentException("结构化事件标题和内容不能为空"))
            return
        }
        viewModelScope.launch {
            val updatedEvent: MemoryEvent = event.copy(
                eventType = eventType,
                title = trimmedTitle,
                content = trimmedContent,
                eventTime = eventTime,
                importanceScore = importanceScore.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
                confidenceScore = confidenceScore.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE),
                status = status,
                updatedAt = System.currentTimeMillis()
            )
            memoryEventDao.update(updatedEvent)
            val compatibleMemoryId: String = createStructuredEventCompatibleMemory(updatedEvent)
            memoryEmbeddingDao.deleteEmbeddingsForIndexedObjectId(updatedEvent.id)
            vectorizeStructuredEventSafely(updatedEvent, compatibleMemoryId)
        }
    }

    fun deleteStructuredEvent(event: MemoryEvent): Unit {
        viewModelScope.launch {
            memoryEmbeddingDao.deleteEmbeddingsForIndexedObjectId(event.id)
            memoryEventDao.delete(event)
        }
    }

    fun updateMemory(memory: LongTermMemory, newText: String) {
        _summaryResult.value = Result.failure(UnsupportedOperationException("原子事件已改为只读纪念记录，不再允许编辑"))
    }

    fun deleteMemory(memory: LongTermMemory) {
        _summaryResult.value = Result.failure(UnsupportedOperationException("原子事件已改为只读纪念记录，不再允许删除"))
    }

    suspend fun getVectorDetailText(memory: LongTermMemory): String {
        val embedding: MemoryEmbedding? = memoryEmbeddingDao.getActiveEmbeddingForMemory(memory.id)
        return buildVectorDetailText(memory, embedding)
    }

    suspend fun vectorizeMemory(memory: LongTermMemory, currentText: String): Result<String> {
        return Result.failure(UnsupportedOperationException("原子事件已改为只读纪念记录，不再允许手动向量化"))
    }

    private suspend fun createStructuredEventCompatibleMemory(event: MemoryEvent): String {
        repository.addMemory(
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

    private suspend fun vectorizeStructuredEventSafely(event: MemoryEvent, compatibleMemoryId: String): Unit {
        memoryVectorizationService.vectorizeEvent(event, compatibleMemoryId)
            .onSuccess { result: MemoryVectorizationService.VectorizationResult ->
                Log.i("LongTermMemoryVM", "结构化事件向量化成功: eventId=${event.id}, memoryId=${result.memoryId}, model=${result.modelName}/${result.modelVersion}")
            }
            .onFailure { error: Throwable ->
                Log.w("LongTermMemoryVM", "结构化事件向量化失败，将保留事件正文: eventId=${event.id}, 原因=${error.message}", error)
            }
    }

    private fun buildStructuredEventMemoryText(title: String, content: String): String {
        return "$title\n$content"
    }

    private fun buildManualStructuredEventDedupeKey(eventType: MemoryEventType, title: String, timestamp: Long): String {
        return "manual:${contactId}:${eventType.name}:${title.lowercase(Locale.CHINA)}:$timestamp"
    }

    private fun MemoryEventType.toIndexedObjectType(): MemoryIndexedObjectType {
        return when (this) {
            MemoryEventType.COMMITMENT -> MemoryIndexedObjectType.COMMITMENT
            MemoryEventType.FACT,
            MemoryEventType.PREFERENCE,
            MemoryEventType.PROHIBITION,
            MemoryEventType.RELATIONSHIP -> MemoryIndexedObjectType.FACT
            MemoryEventType.ANNIVERSARY,
            MemoryEventType.OPINION,
            MemoryEventType.OTHER -> MemoryIndexedObjectType.EVENT
        }
    }

    private fun buildVectorDetailText(memory: LongTermMemory, embedding: MemoryEmbedding?): String {
        val statusText: String = if (memory.isVectorized || embedding != null) "已向量化" else "未向量化"
        val modelVersionText: String = memory.embeddingVersion ?: embedding?.modelVersion ?: "无"
        val dimensionText: String = embedding?.dimension?.toString() ?: "无"
        val modelNameText: String = embedding?.modelName ?: "无"
        val createdAtText: String = embedding?.createdAt?.let { formatTime(it) } ?: "无"
        val updatedAtText: String = embedding?.updatedAt?.let { formatTime(it) } ?: "无"
        return """
            状态：$statusText
            记忆ID：${memory.id}
            向量模型：$modelNameText
            向量版本：$modelVersionText
            向量维度：$dimensionText
            创建时间：$createdAtText
            更新时间：$updatedAtText
            召回次数：${memory.retrievalCount}
        """.trimIndent()
    }

    private fun formatTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        return dateFormat.format(Date(timestamp))
    }

    /**
     * 刷新“从上次位置开始”实际使用的默认提取起点和新增消息数量。
     */
    fun refreshLastExtractionPositionTimestamp(): Unit {
        viewModelScope.launch {
            // 统一使用联系人资料中的 lastSummaryTimestamp，保证记忆管理中心展示、AI资料设置间隔、自动触发判断、Brain自动执行器使用同一提取位置。
            val positionTimestamp: Long? = personProfileRepository.getPersonProfileById(contactId)?.lastSummaryTimestamp
            _lastExtractionPositionTimestamp.value = positionTimestamp
            _newMessageCountSinceLastExtraction.value = calculateNewMessageCountSinceLastExtraction(positionTimestamp)
        }
    }

    private suspend fun calculateNewMessageCountSinceLastExtraction(positionTimestamp: Long?): Int {
        return positionTimestamp?.let { timestamp: Long ->
            chatMessageDao.getMessageCountAfterTimestamp(contactId, timestamp)
        } ?: chatMessageDao.getMessageCountForContact(contactId)
    }

    /**
     * 从聊天记录中提取新的结构化事件。
     * @param customStartTimestamp 自定义的起始时间戳，null 表示使用最新记忆的时间戳
     * @param customEndTimestamp 自定义的结束时间戳，null 表示不限制
     */
    fun summarizeChatHistory(customStartTimestamp: Long? = null, customEndTimestamp: Long? = null) {
        viewModelScope.launch {
            // BUG修复：根据总结类型，预设好新记忆应该使用的时间戳
            // 1. 如果是范围总结，使用结束时间
            // 2. 如果是自动或只有开始时间的总结，使用当前时间
            nextMemoryTimestamp = customEndTimestamp ?: System.currentTimeMillis()

            val userProfile = personProfileRepository.getUserProfile()
            val contact = personProfileRepository.getPersonProfileById(contactId)

            if (contact == null) {
                _summaryResult.value = Result.failure(Exception("找不到联系人信息"))
                return@launch
            }

            // 获取起始时间戳：优先使用自定义时间戳，否则使用联系人资料中的上次提取位置。
            val startTimestamp = customStartTimestamp ?: contact.lastSummaryTimestamp

            // 保存原始时间戳（用于恢复）
            originalTimestamp = startTimestamp
            usedCustomTimestamp = customStartTimestamp != null

            summarizeChatHistoryPromptBuilder.prepare(
                contactId = contactId,
                userNickname = userProfile.nickname,
                characterName = contact.realName,
                characterPersona = contact.persona,
                userPersona = userProfile.persona,
                startTimestamp = startTimestamp,
                endTimestamp = customEndTimestamp
            )
                .onSuccess { promptRequest ->
                    _pendingPromptRequest.value = promptRequest
                }
                .onFailure { error ->
                    _summaryResult.value = Result.failure(error)
                }
        }
    }

    fun executeConfirmedRequest(): Unit {
        val request: AiPromptRequest = _pendingPromptRequest.value ?: return
        viewModelScope.launch {
            try {
                val rawResponse: String = aiRequestService.getChatCompletion(getApplication(), request)
                    ?: throw IllegalStateException("AI未返回任何内容。")
                prepareExtractionPreview(rawResponse)
            } catch (e: Exception) {
                _summaryResult.value = Result.failure(e)
            } finally {
                _pendingPromptRequest.value = null // 清除待处理的请求
            }
        }
    }

    private fun prepareExtractionPreview(rawResponse: String): Unit {
        if (rawResponse.isBlank()) {
            _summaryResult.value = Result.failure(Exception("AI未返回任何内容。"))
            return
        }
        val normalizedResponse: String = rawResponse.trim()
        _pendingExtractionPreview.value = FactGraphExtractionPreview(
            responseText = normalizedResponse,
            changeSummaryText = buildExtractionPreviewSummary(normalizedResponse)
        )
    }

    fun onExtractionPreviewShown(): Unit {
        _pendingExtractionPreview.value = null
    }

    fun confirmSelectedFactGraphExtraction(editedResponse: String): Unit {
        val normalizedResponse: String = editedResponse.trim()
        if (normalizedResponse.isBlank()) {
            _summaryResult.value = Result.failure(IllegalArgumentException("结构化提取结果不能为空"))
            return
        }
        viewModelScope.launch {
            handleSummaryResponse(normalizedResponse)
        }
    }

    fun cancelSelectedFactGraphPreview(): Unit {
        _pendingExtractionPreview.value = null
        usedCustomTimestamp = false
        originalTimestamp = null
        nextMemoryTimestamp = null
    }

    private fun buildExtractionPreviewSummary(responseText: String): String {
        return runCatching {
            val rootObject: JsonObject = parseStructuredEventRoot(responseText)
            val eventCount: Int = rootObject.countJsonArrayItems("events") + rootObject.countJsonArrayItems("structuredEvents")
            val nodeCount: Int = rootObject.countJsonArrayItems("nodes")
            val relationCount: Int = rootObject.countJsonArrayItems("relations")
            "确认后将写入结构化记忆：事件 $eventCount 条，节点 $nodeCount 个，关系 $relationCount 条。你可以先调整下方内容，再确认保存。"
        }.getOrDefault("确认后将按下方内容写入结构化记忆。你可以先调整下方内容，再确认保存。")
    }

    private suspend fun handleSummaryResponse(rawResponse: String): Unit {
        if (rawResponse.isBlank()) {
            _summaryResult.value = Result.failure(Exception("AI未返回任何内容。"))
            return
        }
        memoryFactGraphExtractionService.saveChatFactGraphResponse(contactId, rawResponse)
            .onSuccess { result: MemoryFactGraphExtractionService.ExtractionResult ->
                updateLastExtractionPositionAfterManualSave()
                refreshLastExtractionPositionTimestamp()
                _summaryResult.value = Result.success("已保存结构化记忆：事件 ${result.eventCount} 条，节点 ${result.nodeCount} 个，关系 ${result.relationCount} 条。")
            }
            .onFailure { error: Throwable ->
                Log.w("LongTermMemoryVM", "完整图谱保存失败，回退到旧结构化事件保存链条: ${error.message}", error)
                val extractedEvents: List<ExtractedStructuredEvent> = parseExtractedStructuredEvents(rawResponse)
                if (extractedEvents.isEmpty()) {
                    _summaryResult.value = Result.failure(IllegalStateException("AI未返回可保存的结构化事件。"))
                    return@onFailure
                }
                val baseTime: Long = nextMemoryTimestamp ?: System.currentTimeMillis()
                extractedEvents.forEachIndexed { index: Int, extractedEvent: ExtractedStructuredEvent ->
                    saveExtractedStructuredEvent(extractedEvent, baseTime + index)
                }
                updateLastExtractionPositionAfterManualSave()
                refreshLastExtractionPositionTimestamp()
                _summaryResult.value = Result.success("已保存结构化事件 ${extractedEvents.size} 条。")
            }
    }

    private suspend fun updateLastExtractionPositionAfterManualSave(): Unit {
        val contact = personProfileRepository.getPersonProfileById(contactId) ?: return
        val updatedTimestamp: Long = nextMemoryTimestamp ?: System.currentTimeMillis()
        personProfileRepository.updatePersonProfile(contact.copy(lastSummaryTimestamp = updatedTimestamp))
    }

    private suspend fun saveExtractedStructuredEvent(extractedEvent: ExtractedStructuredEvent, eventTime: Long): Unit {
        val rawEvidenceText = buildStructuredEventMemoryText(extractedEvent.title, extractedEvent.content)
        val event = MemoryEvent(
            contactId = contactId,
            evidenceMemoryId = null, // 废弃兼容性原子事件
            eventType = extractedEvent.eventType,
            title = extractedEvent.title,
            content = extractedEvent.content,
            eventTime = eventTime,
            importanceScore = extractedEvent.importanceScore,
            confidenceScore = extractedEvent.confidenceScore,
            sourceModule = STRUCTURED_EVENT_EXTRACTION_SOURCE,
            rawEvidenceText = rawEvidenceText,
            dedupeKey = buildManualStructuredEventDedupeKey(extractedEvent.eventType, extractedEvent.title, eventTime)
        )
        memoryEventDao.insert(event)
        val compatibleMemoryId: String = createStructuredEventCompatibleMemory(event)
        // 使用 event.id 自身作为向量化对象 ID，避免 Room 插入函数返回 Unit 时生成错误兼容 ID。
        vectorizeStructuredEventSafely(event, compatibleMemoryId)
    }

    private fun parseExtractedStructuredEvents(rawResponse: String): List<ExtractedStructuredEvent> {
        return runCatching {
            val rootObject: JsonObject = parseStructuredEventRoot(rawResponse)
            val eventArray: JsonArray = when {
                rootObject.has("events") && rootObject.get("events").isJsonArray -> rootObject.getAsJsonArray("events")
                rootObject.has("structuredEvents") && rootObject.get("structuredEvents").isJsonArray -> rootObject.getAsJsonArray("structuredEvents")
                else -> JsonArray()
            }
            eventArray.mapNotNull { element: JsonElement -> parseExtractedStructuredEvent(element) }
        }.onFailure { error: Throwable ->
            Log.e("LongTermMemoryVM", "解析结构化事件失败: ${error.message}", error)
        }.getOrDefault(emptyList())
    }

    private fun parseExtractedStructuredEvent(element: JsonElement): ExtractedStructuredEvent? {
        if (!element.isJsonObject) return null
        val itemObject: JsonObject = element.asJsonObject
        val title: String = itemObject.getStringOrNull("title")?.trim().orEmpty()
        val content: String = itemObject.getStringOrNull("content")?.trim().orEmpty()
        if (title.isBlank() || content.isBlank()) return null
        return ExtractedStructuredEvent(
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
        return Gson().fromJson(jsonText, JsonObject::class.java)
    }

    private fun parseMemoryEventType(value: String?): MemoryEventType {
        return when (value?.trim()?.uppercase(Locale.ROOT)) {
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

    private fun JsonObject.getStringOrNull(name: String): String? {
        return if (has(name) && !get(name).isJsonNull) get(name).asString else null
    }

    private fun JsonObject.countJsonArrayItems(name: String): Int {
        return if (has(name) && get(name).isJsonArray) getAsJsonArray(name).size() else 0
    }

    private fun JsonObject.getIntOrNull(name: String): Int? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asInt else null }.getOrNull()
    }

    private fun JsonObject.getFloatOrNull(name: String): Float? {
        return runCatching { if (has(name) && !get(name).isJsonNull) get(name).asFloat else null }.getOrNull()
    }
    
    /**
     * 用户确认结构化事件已保存后调用
     * 由于时间戳已经存储在结构化事件中，这里只需要清除临时标记
     */
    fun confirmAndUpdateTimestamp() {
        // 清除标记
        usedCustomTimestamp = false
        originalTimestamp = null
        nextMemoryTimestamp = null // 清除预设的时间戳
    }

    fun cancelRequest() {
        _pendingPromptRequest.value = null
        _pendingExtractionPreview.value = null
        // 清除标记
        usedCustomTimestamp = false
        originalTimestamp = null
        nextMemoryTimestamp = null // 清除预D设的时间戳
    }
    
    /**
     * 用户选择"关闭"时调用
     */
    fun onSummaryDismissed() {
        // 清除标记
        usedCustomTimestamp = false
        originalTimestamp = null
        nextMemoryTimestamp = null // 清除预设的时间戳
    }

    // 用于在UI处理完事件后重置状态
    fun onSummaryResultConsumed() {
        _summaryResult.value = null
    }

}

data class FactGraphExtractionPreview(
    val responseText: String,
    val changeSummaryText: String
)

data class ExtractedStructuredEvent(
    val eventType: MemoryEventType,
    val title: String,
    val content: String,
    val importanceScore: Int,
    val confidenceScore: Float
)

private const val MIN_IMPORTANCE_SCORE: Int = 1
private const val MAX_IMPORTANCE_SCORE: Int = 10
private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.75f
private const val STRUCTURED_EVENT_MANUAL_SOURCE: String = "StructuredMemoryEventManual"
private const val STRUCTURED_EVENT_EXTRACTION_SOURCE: String = "StructuredMemoryEventExtraction"
private const val AUTO_STRUCTURED_EVENT_SOURCE: String = "AutoStructuredEventExtraction"
private const val VIDEO_CALL_SUMMARY_SOURCE: String = "VideoCallSummary"
private const val VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_SOURCE: String = "VideoCallStructuredEventCompatibility"
private const val TEMPORARY_VECTOR_REBUILD_SOURCE: String = "TemporaryVectorRebuild"
private val HIDDEN_STRUCTURED_EVENT_COMPATIBLE_SOURCES: Set<String> = setOf(
    STRUCTURED_EVENT_MANUAL_SOURCE,
    STRUCTURED_EVENT_EXTRACTION_SOURCE,
    AUTO_STRUCTURED_EVENT_SOURCE,
    VIDEO_CALL_SUMMARY_SOURCE,
    VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_SOURCE,
    TEMPORARY_VECTOR_REBUILD_SOURCE
)

data class MemoryCenterUiState(
    val legacyMemoryCount: Int,
    val structuredEventCount: Int,
    val factCount: Int,
    val commitmentCount: Int,
    val preferenceCount: Int,
    val prohibitionCount: Int,
    val relationshipCount: Int,
    val summaryCount: Int,
    val activeVectorCount: Int,
    val indexedEventVectorCount: Int,
    val indexedSummaryVectorCount: Int,
    val totalVisibleMemoryCount: Int
) {
    val categorySummaryText: String
        get() = "结构化 $structuredEventCount · 事实 $factCount · 承诺 $commitmentCount · 偏好 $preferenceCount · 禁忌 $prohibitionCount · 关系 $relationshipCount · 摘要 $summaryCount"

    val vectorSummaryText: String
        get() = "活跃向量 $activeVectorCount · 事件索引 $indexedEventVectorCount · 摘要索引 $indexedSummaryVectorCount"

    val recallHintText: String
        get() = "召回链路：按语义、关键词、图谱、重要度、时效、置信度和状态综合排序"

    companion object {
        val EMPTY: MemoryCenterUiState = MemoryCenterUiState(
            legacyMemoryCount = 0,
            structuredEventCount = 0,
            factCount = 0,
            commitmentCount = 0,
            preferenceCount = 0,
            prohibitionCount = 0,
            relationshipCount = 0,
            summaryCount = 0,
            activeVectorCount = 0,
            indexedEventVectorCount = 0,
            indexedSummaryVectorCount = 0,
            totalVisibleMemoryCount = 0
        )

        fun from(legacyMemories: List<LongTermMemory>, events: List<MemoryEvent>, summaries: List<MemorySummary>, embeddings: List<MemoryEmbedding>): MemoryCenterUiState {
            return MemoryCenterUiState(
                legacyMemoryCount = legacyMemories.size,
                structuredEventCount = events.size,
                factCount = events.countByType(MemoryEventType.FACT),
                commitmentCount = events.countByType(MemoryEventType.COMMITMENT),
                preferenceCount = events.countByType(MemoryEventType.PREFERENCE),
                prohibitionCount = events.countByType(MemoryEventType.PROHIBITION),
                relationshipCount = events.countByType(MemoryEventType.RELATIONSHIP),
                summaryCount = summaries.size,
                activeVectorCount = embeddings.size,
                indexedEventVectorCount = embeddings.count { embedding: MemoryEmbedding -> embedding.indexedObjectType in EVENT_INDEX_OBJECT_TYPES },
                indexedSummaryVectorCount = embeddings.count { embedding: MemoryEmbedding -> embedding.indexedObjectType == MemoryIndexedObjectType.SUMMARY },
                totalVisibleMemoryCount = legacyMemories.size + events.size + summaries.size
            )
        }

        private val EVENT_INDEX_OBJECT_TYPES: Set<MemoryIndexedObjectType> = setOf(
            MemoryIndexedObjectType.EVENT,
            MemoryIndexedObjectType.FACT,
            MemoryIndexedObjectType.COMMITMENT
        )

        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
        private const val STRUCTURED_EVENT_MANUAL_SOURCE: String = "StructuredMemoryEventManual"
        private const val STRUCTURED_EVENT_EXTRACTION_SOURCE: String = "StructuredMemoryEventExtraction"
        private const val AUTO_STRUCTURED_EVENT_SOURCE: String = "AutoStructuredEventExtraction"
        private const val VIDEO_CALL_SUMMARY_SOURCE: String = "VideoCallSummary"
        private const val VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_SOURCE: String = "VideoCallStructuredEventCompatibility"
        private val HIDDEN_STRUCTURED_EVENT_COMPATIBLE_SOURCES: Set<String> = setOf(
            STRUCTURED_EVENT_MANUAL_SOURCE,
            STRUCTURED_EVENT_EXTRACTION_SOURCE,
            AUTO_STRUCTURED_EVENT_SOURCE,
            VIDEO_CALL_SUMMARY_SOURCE,
            VIDEO_CALL_STRUCTURED_EVENT_COMPATIBLE_SOURCE
        )
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 5
        private const val DEFAULT_CONFIDENCE_SCORE: Float = 0.75f

        private fun List<MemoryEvent>.countByType(type: MemoryEventType): Int {
            return count { event: MemoryEvent -> event.eventType == type }
        }
    }
}