package com.susking.ephone_s.aidata.data.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageWithVersions
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.service.MemorySummarizationService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.aidata.prompt.ChatCompletionRequest
import com.susking.ephone_s.aidata.prompt.ChatMessagePayload
import com.susking.ephone_s.aidata.prompt.PromptComponentBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分层记忆摘要服务实现。
 * 按照日、周、月、年逐层压缩聊天原文和下级摘要，减少后续提示词上下文压力。
 */
@Singleton
class MemorySummarizationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val longTermMemoryDao: LongTermMemoryDao,
    private val chatMessageDao: ChatMessageDao,
    private val memorySummaryDao: MemorySummaryDao,
    private val memoryEventDao: MemoryEventDao,
    private val memoryEmbeddingDao: MemoryEmbeddingDao,
    private val personProfileRepository: PersonProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val aiRequestService: AiRequestService,
    private val memoryVectorizationService: MemoryVectorizationService
) : MemorySummarizationService {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    override suspend fun generateSummaryWindow(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long): MemorySummary? {
        val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId) ?: return null
        val existingSummary: MemorySummary? = memorySummaryDao.getSummaryForWindow(contactId, level, windowStart, windowEnd)
        if (existingSummary != null) return existingSummary
        return when (level) {
            SummaryLevel.DAILY -> generateDailySummaryForWindow(profile, windowStart, windowEnd)
            SummaryLevel.WEEKLY -> generateHigherLevelSummaryForWindow(profile, SummaryLevel.WEEKLY, SummaryLevel.DAILY, windowStart, windowEnd)
            SummaryLevel.MONTHLY -> generateHigherLevelSummaryForWindow(profile, SummaryLevel.MONTHLY, SummaryLevel.WEEKLY, windowStart, windowEnd)
            SummaryLevel.YEARLY -> generateHigherLevelSummaryForWindow(profile, SummaryLevel.YEARLY, SummaryLevel.MONTHLY, windowStart, windowEnd)
        }
    }

    override suspend fun buildSummaryWindowPrompt(contactId: String, level: SummaryLevel, windowStart: Long, windowEnd: Long): AiPromptRequest? {
        val profile: PersonProfile = personProfileRepository.getPersonProfileById(contactId) ?: return null
        val sourceText: String = buildSummaryWindowSourceText(profile, level, windowStart, windowEnd) ?: return null
        return buildSummaryPromptRequest(profile, level, sourceText)
    }

    override suspend fun regenerateSummaryWindow(summary: MemorySummary): MemorySummary? {
        val profile: PersonProfile = personProfileRepository.getPersonProfileById(summary.contactId) ?: return null
        return when (summary.summaryLevel) {
            SummaryLevel.DAILY -> regenerateDailySummaryForWindow(profile, summary)
            SummaryLevel.WEEKLY -> regenerateHigherLevelSummaryForWindow(profile, summary, SummaryLevel.DAILY)
            SummaryLevel.MONTHLY -> regenerateHigherLevelSummaryForWindow(profile, summary, SummaryLevel.WEEKLY)
            SummaryLevel.YEARLY -> regenerateHigherLevelSummaryForWindow(profile, summary, SummaryLevel.MONTHLY)
        }
    }

    override suspend fun repairDefaultImportanceSummaries(contactId: String): Int {        val summaries: List<MemorySummary> = memorySummaryDao.getSummaryListForContact(contactId)
        val defaultSummaries: List<MemorySummary> = summaries.filter { summary: MemorySummary -> summary.importanceScore == DEFAULT_IMPORTANCE_SCORE }
        var repairedCount: Int = 0
        defaultSummaries.sortedWith(compareBy<MemorySummary> { summary: MemorySummary -> summary.summaryLevel.ordinal }.thenBy { summary: MemorySummary -> summary.startTimestamp })
            .forEach { summary: MemorySummary ->
                val repairedSummary: MemorySummary? = repairSummaryImportance(summary)
                if (repairedSummary != null) repairedCount++
            }
        return repairedCount
    }

    override suspend fun vectorizeStoredSummary(summary: MemorySummary): Unit {
        vectorizeSummarySafely(summary)
    }

    private suspend fun generateDailySummaryForWindow(profile: PersonProfile, windowStart: Long, windowEnd: Long): MemorySummary? {
        val existingSummary: MemorySummary? = memorySummaryDao.getSummaryForWindow(profile.id, SummaryLevel.DAILY, windowStart, windowEnd)
        if (existingSummary != null) return null
        val messages: List<ChatMessageWithVersions> = chatMessageDao.getMessagesInTimeRange(profile.id, windowStart, windowEnd)
        if (messages.size < MIN_SOURCE_COUNT) return null
        val userProfile: UserProfile = personProfileRepository.getUserProfile()
        val sourceText: String = buildRawChatSourceText(profile, userProfile, messages)
        val summaryResult: SummaryGenerationResult = requestSummary(profile, SummaryLevel.DAILY, sourceText) ?: return null
        val importanceScore: Int = calculateDailyImportanceScore(profile.id, windowStart, windowEnd, summaryResult)
        val summary: MemorySummary = MemorySummary(
            contactId = profile.id,
            summaryLevel = SummaryLevel.DAILY,
            startTimestamp = windowStart,
            endTimestamp = windowEnd,
            summaryText = summaryResult.summaryText,
            sourceMemoryCount = messages.size,
            importanceScore = importanceScore,
            modelVersion = settingsRepository.getMainModel()
        )
        memorySummaryDao.insert(summary)
        vectorizeSummarySafely(summary)
        return summary
    }

    private suspend fun generateHigherLevelSummaryForWindow(
        profile: PersonProfile,
        targetLevel: SummaryLevel,
        sourceLevel: SummaryLevel,
        windowStart: Long,
        windowEnd: Long
    ): MemorySummary? {
        val existingSummary: MemorySummary? = memorySummaryDao.getSummaryForWindow(profile.id, targetLevel, windowStart, windowEnd)
        if (existingSummary != null) return null
        val sourceSummaries: List<MemorySummary> = memorySummaryDao.getSummariesByLevelInTimeRange(profile.id, sourceLevel, windowStart, windowEnd)
        if (sourceSummaries.size < MIN_SOURCE_COUNT) return null
        val sourceText: String = sourceSummaries.joinToString(separator = "\n") { summary: MemorySummary -> "- ${summary.summaryText}" }
        val summaryResult: SummaryGenerationResult = requestSummary(profile, targetLevel, sourceText) ?: return null
        val importanceScore: Int = calculateHigherLevelImportanceScore(sourceSummaries, summaryResult)
        val summary: MemorySummary = MemorySummary(
            contactId = profile.id,
            summaryLevel = targetLevel,
            startTimestamp = windowStart,
            endTimestamp = windowEnd,
            summaryText = summaryResult.summaryText,
            sourceMemoryCount = sourceSummaries.sumOf { sourceSummary: MemorySummary -> sourceSummary.sourceMemoryCount },
            importanceScore = importanceScore,
            modelVersion = settingsRepository.getMainModel()
        )
        memorySummaryDao.insert(summary)
        vectorizeSummarySafely(summary)
        return summary
    }

    private suspend fun regenerateDailySummaryForWindow(profile: PersonProfile, summary: MemorySummary): MemorySummary? {
        val messages: List<ChatMessageWithVersions> = chatMessageDao.getMessagesInTimeRange(profile.id, summary.startTimestamp, summary.endTimestamp)
        if (messages.size < MIN_SOURCE_COUNT) return null
        val userProfile: UserProfile = personProfileRepository.getUserProfile()
        val sourceText: String = buildRawChatSourceText(profile, userProfile, messages)
        val summaryResult: SummaryGenerationResult = requestSummary(profile, SummaryLevel.DAILY, sourceText) ?: return null
        val importanceScore: Int = calculateDailyImportanceScore(profile.id, summary.startTimestamp, summary.endTimestamp, summaryResult)
        val updatedSummary: MemorySummary = summary.copy(
            summaryText = summaryResult.summaryText,
            sourceMemoryCount = messages.size,
            importanceScore = importanceScore,
            modelVersion = settingsRepository.getMainModel(),
            updatedAt = System.currentTimeMillis()
        )
        memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
        memorySummaryDao.insert(updatedSummary)
        vectorizeSummarySafely(updatedSummary)
        return updatedSummary
    }

    private suspend fun regenerateHigherLevelSummaryForWindow(profile: PersonProfile, summary: MemorySummary, sourceLevel: SummaryLevel): MemorySummary? {
        val sourceSummaries: List<MemorySummary> = memorySummaryDao.getSummariesByLevelInTimeRange(profile.id, sourceLevel, summary.startTimestamp, summary.endTimestamp)
        if (sourceSummaries.size < MIN_SOURCE_COUNT) return null
        val sourceText: String = sourceSummaries.joinToString(separator = "\n") { sourceSummary: MemorySummary -> "- ${sourceSummary.summaryText}" }
        val summaryResult: SummaryGenerationResult = requestSummary(profile, summary.summaryLevel, sourceText) ?: return null
        val importanceScore: Int = calculateHigherLevelImportanceScore(sourceSummaries, summaryResult)
        val updatedSummary: MemorySummary = summary.copy(
            summaryText = summaryResult.summaryText,
            sourceMemoryCount = sourceSummaries.sumOf { sourceSummary: MemorySummary -> sourceSummary.sourceMemoryCount },
            importanceScore = importanceScore,
            modelVersion = settingsRepository.getMainModel(),
            updatedAt = System.currentTimeMillis()
        )
        memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
        memorySummaryDao.insert(updatedSummary)
        vectorizeSummarySafely(updatedSummary)
        return updatedSummary
    }

    private suspend fun repairSummaryImportance(summary: MemorySummary): MemorySummary? {
        val recalculatedImportanceScore: Int = when (summary.summaryLevel) {
            SummaryLevel.DAILY -> calculateDailyImportanceScore(
                contactId = summary.contactId,
                windowStart = summary.startTimestamp,
                windowEnd = summary.endTimestamp,
                summaryResult = summary.toRepairSummaryResult()
            )
            SummaryLevel.WEEKLY -> calculateRepairedHigherLevelImportanceScore(summary, SummaryLevel.DAILY)
            SummaryLevel.MONTHLY -> calculateRepairedHigherLevelImportanceScore(summary, SummaryLevel.WEEKLY)
            SummaryLevel.YEARLY -> calculateRepairedHigherLevelImportanceScore(summary, SummaryLevel.MONTHLY)
        }
        if (recalculatedImportanceScore == summary.importanceScore) return null
        val repairedSummary: MemorySummary = summary.copy(
            importanceScore = recalculatedImportanceScore,
            updatedAt = System.currentTimeMillis()
        )
        memoryEmbeddingDao.deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
        memorySummaryDao.insert(repairedSummary)
        vectorizeSummarySafely(repairedSummary)
        return repairedSummary
    }

    private suspend fun calculateRepairedHigherLevelImportanceScore(summary: MemorySummary, sourceLevel: SummaryLevel): Int {
        val sourceSummaries: List<MemorySummary> = memorySummaryDao.getSummariesByLevelInTimeRange(summary.contactId, sourceLevel, summary.startTimestamp, summary.endTimestamp)
        if (sourceSummaries.isEmpty()) return calculateKeywordImportanceBoost(summary.summaryText).plus(DEFAULT_IMPORTANCE_SCORE).coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE)
        return calculateHigherLevelImportanceScore(sourceSummaries, summary.toRepairSummaryResult())
    }

    private fun MemorySummary.toRepairSummaryResult(): SummaryGenerationResult {
        return SummaryGenerationResult(
            summaryText = summaryText,
            modelImportanceScore = null,
            importanceReason = null,
            confidenceScore = null,
            isFallback = true
        )
    }

    private fun buildRawChatSourceText(profile: PersonProfile, userProfile: UserProfile, messages: List<ChatMessageWithVersions>): String {
        val chatMessages = messages.map { it.chatMessage.toDomainModel() }
        return PromptComponentBuilder.buildSimplifiedHistorySummary(
            history = chatMessages,
            contact = profile,
            userProfile = userProfile
        )
    }

    private suspend fun vectorizeSummarySafely(summary: MemorySummary): Unit {
        val compatibleMemoryId: String = findCompatibleMemoryId(summary) ?: return
        memoryVectorizationService.vectorizeSummary(summary, compatibleMemoryId).onFailure { exception: Throwable ->
            Log.e(TAG, "分层摘要向量化失败: summaryId=${summary.id}, level=${summary.summaryLevel}, memoryId=$compatibleMemoryId", exception)
        }
    }

    private suspend fun findCompatibleMemoryId(summary: MemorySummary): String? {
        val memories: List<LongTermMemory> = longTermMemoryDao.getMemoriesForContact(summary.contactId).first()
        return memories.filter { memory: LongTermMemory -> memory.timestamp in summary.startTimestamp..summary.endTimestamp }
            .maxWithOrNull(compareBy<LongTermMemory> { memory: LongTermMemory -> memory.importanceScore }.thenBy { memory: LongTermMemory -> memory.timestamp })
            ?.id
            ?: memories.maxByOrNull { memory: LongTermMemory -> memory.timestamp }?.id
    }

    private suspend fun buildSummaryWindowSourceText(profile: PersonProfile, level: SummaryLevel, windowStart: Long, windowEnd: Long): String? {
        return when (level) {
            SummaryLevel.DAILY -> buildDailySummarySourceText(profile, windowStart, windowEnd)
            SummaryLevel.WEEKLY -> buildHigherLevelSummarySourceText(profile.id, SummaryLevel.DAILY, windowStart, windowEnd)
            SummaryLevel.MONTHLY -> buildHigherLevelSummarySourceText(profile.id, SummaryLevel.WEEKLY, windowStart, windowEnd)
            SummaryLevel.YEARLY -> buildHigherLevelSummarySourceText(profile.id, SummaryLevel.MONTHLY, windowStart, windowEnd)
        }
    }

    private suspend fun buildDailySummarySourceText(profile: PersonProfile, windowStart: Long, windowEnd: Long): String? {
        val messages: List<ChatMessageWithVersions> = chatMessageDao.getMessagesInTimeRange(profile.id, windowStart, windowEnd)
        if (messages.size < MIN_SOURCE_COUNT) return null
        val userProfile: UserProfile = personProfileRepository.getUserProfile()
        return buildRawChatSourceText(profile, userProfile, messages)
    }

    private suspend fun buildHigherLevelSummarySourceText(contactId: String, sourceLevel: SummaryLevel, windowStart: Long, windowEnd: Long): String? {
        val sourceSummaries: List<MemorySummary> = memorySummaryDao.getSummariesByLevelInTimeRange(contactId, sourceLevel, windowStart, windowEnd)
        if (sourceSummaries.size < MIN_SOURCE_COUNT) return null
        return sourceSummaries.joinToString(separator = "\n") { sourceSummary: MemorySummary -> "- ${sourceSummary.summaryText}" }
    }

    private suspend fun requestSummary(profile: PersonProfile, level: SummaryLevel, sourceText: String): SummaryGenerationResult? {
        return try {
            val promptRequest: AiPromptRequest = buildSummaryPromptRequest(profile, level, sourceText)
            val response: String = aiRequestService.getChatCompletion(context, promptRequest) ?: return null
            parseSummaryGenerationResult(response)
        } catch (exception: Exception) {
            Log.e(TAG, "分层摘要生成失败: contactId=${profile.id}, level=$level", exception)
            null
        }
    }

    private suspend fun buildSummaryPromptRequest(profile: PersonProfile, level: SummaryLevel, sourceText: String): AiPromptRequest {
        val systemPrompt: String = """
你是角色“${profile.realName}”的聊天原文与记忆摘要整理器。
请把用户消息中的材料压缩成一段${getLevelName(level)}摘要。
要求：
1. 使用第一人称“我”的视角。
2. 保留具体事件、决定、承诺、偏好、禁忌、未完成事项。
3. 删除重复、闲聊和无意义情绪宣泄。
4. 如果存在时间信息，尽量保留清楚。
5. 评估该摘要对长期记忆、后续对话和关系状态判断的重要度。
6. 只输出JSON，格式为：{"summary":"摘要内容","importanceScore":1,"importanceReason":"评分依据","confidenceScore":0.8}。

重要度评分标准：
1-2：普通闲聊、重复表达、无长期价值。
3-4：普通日常信息，有轻微上下文价值。
5-6：偏好、计划、轻微关系变化、可用于后续对话。
7-8：明确承诺、禁忌、重要偏好、显著情绪或关系事件。
9-10：核心人设、长期关系状态、强约束、重大事件。

角色设定：
${profile.persona}
        """.trimIndent()
        val userPrompt: String = """
待摘要材料：
$sourceText
        """.trimIndent()
        val request: ChatCompletionRequest = ChatCompletionRequest(
            model = settingsRepository.getMainModel(),
            messages = listOf(
                ChatMessagePayload(role = "system", content = systemPrompt),
                ChatMessagePayload(role = "user", content = userPrompt)
            ),
            temperature = (settingsRepository.getApiTemperature() - TEMPERATURE_OFFSET).coerceAtLeast(MIN_TEMPERATURE)
        )
        return AiPromptRequest(
            request = request,
            url = "${settingsRepository.getMainApiUrl()}/v1/chat/completions",
            displayPromptJson = gson.toJson(request),
            timestamp = System.currentTimeMillis(),
            contactName = profile.realName,
            activityType = "分层记忆摘要"
        )
    }

    private fun parseSummaryGenerationResult(response: String): SummaryGenerationResult? {
        val cleanedResponse: String = response.replace("```json", "").replace("```", "").trim()
        return try {
            val jsonObject: JsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val summaryText: String = jsonObject.get("summary")?.asString?.takeIf { summary: String -> summary.isNotBlank() } ?: return parseFallbackSummaryResult(cleanedResponse)
            SummaryGenerationResult(
                summaryText = summaryText,
                modelImportanceScore = jsonObject.get("importanceScore")?.asInt?.coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE),
                importanceReason = jsonObject.get("importanceReason")?.asString,
                confidenceScore = jsonObject.get("confidenceScore")?.asFloat?.coerceIn(MIN_CONFIDENCE_SCORE, MAX_CONFIDENCE_SCORE),
                isFallback = false
            )
        } catch (exception: Exception) {
            parseFallbackSummaryResult(cleanedResponse)
        }
    }

    private fun parseFallbackSummaryResult(response: String): SummaryGenerationResult? {
        val summaryText: String = response.takeIf { text: String -> text.isNotBlank() } ?: return null
        return SummaryGenerationResult(
            summaryText = summaryText,
            modelImportanceScore = null,
            importanceReason = null,
            confidenceScore = null,
            isFallback = true
        )
    }

    private suspend fun calculateDailyImportanceScore(contactId: String, windowStart: Long, windowEnd: Long, summaryResult: SummaryGenerationResult): Int {
        val memories: List<LongTermMemory> = longTermMemoryDao.getMemoriesInTimeRange(contactId, windowStart, windowEnd)
        val events: List<MemoryEvent> = memoryEventDao.getEventsInTimeRange(contactId, windowStart, windowEnd)
        val sourceScores: List<Int> = memories.map { memory: LongTermMemory -> memory.importanceScore } + events.map { event: MemoryEvent -> event.importanceScore }
        val modelScore: Int = summaryResult.modelImportanceScore ?: DEFAULT_IMPORTANCE_SCORE
        val peakSourceScore: Int = sourceScores.maxOrNull() ?: DEFAULT_IMPORTANCE_SCORE
        val averageSourceScore: Int = sourceScores.takeIf { scores: List<Int> -> scores.isNotEmpty() }
            ?.average()
            ?.toInt()
            ?: DEFAULT_IMPORTANCE_SCORE
        val eventTypeBoost: Int = events.maxOfOrNull { event: MemoryEvent -> event.eventType.toImportanceBoost() } ?: NO_IMPORTANCE_BOOST
        val keywordBoost: Int = calculateKeywordImportanceBoost(summaryResult.summaryText)
        val densityBoost: Int = calculateDensityImportanceBoost(sourceScores)
        val weightedScore: Int = ((modelScore * MODEL_SCORE_WEIGHT) + (peakSourceScore * PEAK_SOURCE_SCORE_WEIGHT) + (averageSourceScore * AVERAGE_SOURCE_SCORE_WEIGHT)).toInt()
        return (weightedScore + eventTypeBoost + keywordBoost + densityBoost)
            .coerceAtLeast(peakSourceScore - PEAK_SOURCE_SCORE_ALLOWANCE)
            .coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE)
    }

    private fun calculateHigherLevelImportanceScore(sourceSummaries: List<MemorySummary>, summaryResult: SummaryGenerationResult): Int {
        val sourceScores: List<Int> = sourceSummaries.map { sourceSummary: MemorySummary -> sourceSummary.importanceScore }
        val modelScore: Int = summaryResult.modelImportanceScore ?: DEFAULT_IMPORTANCE_SCORE
        val peakScore: Int = sourceScores.maxOrNull() ?: DEFAULT_IMPORTANCE_SCORE
        val averageScore: Int = sourceScores.takeIf { scores: List<Int> -> scores.isNotEmpty() }
            ?.average()
            ?.toInt()
            ?: DEFAULT_IMPORTANCE_SCORE
        val highScoreRatio: Float = sourceScores.count { score: Int -> score >= HIGH_IMPORTANCE_SCORE }.toFloat() / sourceScores.size.coerceAtLeast(ONE_COUNT).toFloat()
        val mediumScoreRatio: Float = sourceScores.count { score: Int -> score >= MEDIUM_IMPORTANCE_SCORE }.toFloat() / sourceScores.size.coerceAtLeast(ONE_COUNT).toFloat()
        val continuityBoost: Int = when {
            highScoreRatio >= HIGH_IMPORTANCE_RATIO_THRESHOLD -> HIGH_CONTINUITY_BOOST
            mediumScoreRatio >= MEDIUM_IMPORTANCE_RATIO_THRESHOLD -> MEDIUM_CONTINUITY_BOOST
            else -> NO_IMPORTANCE_BOOST
        }
        val keywordBoost: Int = calculateKeywordImportanceBoost(summaryResult.summaryText)
        val weightedScore: Int = ((modelScore * HIGHER_MODEL_SCORE_WEIGHT) + (peakScore * HIGHER_PEAK_SCORE_WEIGHT) + (averageScore * HIGHER_AVERAGE_SCORE_WEIGHT)).toInt()
        val minimumScore: Int = when {
            peakScore >= CRITICAL_IMPORTANCE_SCORE -> CRITICAL_HIGHER_LEVEL_FLOOR
            sourceScores.count { score: Int -> score >= HIGH_IMPORTANCE_SCORE } >= MULTIPLE_HIGH_IMPORTANCE_COUNT -> HIGHER_LEVEL_MULTI_HIGH_FLOOR
            else -> MIN_IMPORTANCE_SCORE
        }
        return (weightedScore + continuityBoost + keywordBoost)
            .coerceAtLeast(minimumScore)
            .coerceIn(MIN_IMPORTANCE_SCORE, MAX_IMPORTANCE_SCORE)
    }

    private fun calculateDensityImportanceBoost(scores: List<Int>): Int {
        val highScoreCount: Int = scores.count { score: Int -> score >= HIGH_IMPORTANCE_SCORE }
        return when {
            highScoreCount >= DENSE_HIGH_IMPORTANCE_COUNT -> HIGH_DENSITY_BOOST
            highScoreCount >= MULTIPLE_HIGH_IMPORTANCE_COUNT -> MEDIUM_DENSITY_BOOST
            else -> NO_IMPORTANCE_BOOST
        }
    }

    private fun calculateKeywordImportanceBoost(summaryText: String): Int {
        val normalizedText: String = summaryText.lowercase()
        return if (IMPORTANT_KEYWORDS.any { keyword: String -> normalizedText.contains(keyword) }) KEYWORD_IMPORTANCE_BOOST else NO_IMPORTANCE_BOOST
    }

    private fun MemoryEventType.toImportanceBoost(): Int {
        return when (this) {
            MemoryEventType.COMMITMENT,
            MemoryEventType.PROHIBITION,
            MemoryEventType.RELATIONSHIP -> STRONG_EVENT_IMPORTANCE_BOOST
            MemoryEventType.ANNIVERSARY,
            MemoryEventType.PREFERENCE -> MEDIUM_EVENT_IMPORTANCE_BOOST
            MemoryEventType.FACT,
            MemoryEventType.OPINION,
            MemoryEventType.OTHER -> NO_IMPORTANCE_BOOST
        }
    }

    private fun getStartOfSummaryDay(timestamp: Long): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.get(Calendar.HOUR_OF_DAY) < SUMMARY_DAY_START_HOUR) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, SUMMARY_DAY_START_HOUR)
        return calendar.timeInMillis
    }

    private fun formatSourceTimestamp(timestamp: Long): String {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val month: Int = calendar.get(Calendar.MONTH) + 1
        val day: Int = calendar.get(Calendar.DAY_OF_MONTH)
        val hour: Int = calendar.get(Calendar.HOUR_OF_DAY)
        val minute: Int = calendar.get(Calendar.MINUTE)
        return "%02d-%02d %02d:%02d".format(month, day, hour, minute)
    }

    private fun addDays(timestamp: Long, days: Int): Long {
        val calendar: Calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.timeInMillis
    }

    private fun getLevelName(level: SummaryLevel): String {
        return when (level) {
            SummaryLevel.DAILY -> "每日"
            SummaryLevel.WEEKLY -> "每周"
            SummaryLevel.MONTHLY -> "每月"
            SummaryLevel.YEARLY -> "每年"
        }
    }

    private data class SummaryGenerationResult(
        val summaryText: String,
        val modelImportanceScore: Int?,
        val importanceReason: String?,
        val confidenceScore: Float?,
        val isFallback: Boolean
    )

    private companion object {
        private const val TAG: String = "MemorySummarization"
        private const val MIN_SOURCE_COUNT: Int = 2
        private const val SUMMARY_DAY_START_HOUR: Int = 5
        private const val DAILY_WINDOW_DAYS: Int = 1
        private const val DEFAULT_IMPORTANCE_SCORE: Int = 3
        private const val MIN_IMPORTANCE_SCORE: Int = 1
        private const val MAX_IMPORTANCE_SCORE: Int = 10
        private const val HIGH_IMPORTANCE_SCORE: Int = 7
        private const val MEDIUM_IMPORTANCE_SCORE: Int = 5
        private const val CRITICAL_IMPORTANCE_SCORE: Int = 9
        private const val CRITICAL_HIGHER_LEVEL_FLOOR: Int = 8
        private const val HIGHER_LEVEL_MULTI_HIGH_FLOOR: Int = 7
        private const val MULTIPLE_HIGH_IMPORTANCE_COUNT: Int = 2
        private const val DENSE_HIGH_IMPORTANCE_COUNT: Int = 3
        private const val ONE_COUNT: Int = 1
        private const val NO_IMPORTANCE_BOOST: Int = 0
        private const val KEYWORD_IMPORTANCE_BOOST: Int = 1
        private const val STRONG_EVENT_IMPORTANCE_BOOST: Int = 2
        private const val MEDIUM_EVENT_IMPORTANCE_BOOST: Int = 1
        private const val HIGH_DENSITY_BOOST: Int = 2
        private const val MEDIUM_DENSITY_BOOST: Int = 1
        private const val HIGH_CONTINUITY_BOOST: Int = 2
        private const val MEDIUM_CONTINUITY_BOOST: Int = 1
        private const val PEAK_SOURCE_SCORE_ALLOWANCE: Int = 1
        private const val MODEL_SCORE_WEIGHT: Float = 0.45f
        private const val PEAK_SOURCE_SCORE_WEIGHT: Float = 0.40f
        private const val AVERAGE_SOURCE_SCORE_WEIGHT: Float = 0.15f
        private const val HIGHER_MODEL_SCORE_WEIGHT: Float = 0.40f
        private const val HIGHER_PEAK_SCORE_WEIGHT: Float = 0.35f
        private const val HIGHER_AVERAGE_SCORE_WEIGHT: Float = 0.25f
        private const val HIGH_IMPORTANCE_RATIO_THRESHOLD: Float = 0.35f
        private const val MEDIUM_IMPORTANCE_RATIO_THRESHOLD: Float = 0.50f
        private const val MIN_CONFIDENCE_SCORE: Float = 0.0f
        private const val MAX_CONFIDENCE_SCORE: Float = 1.0f
        private const val TEMPERATURE_OFFSET: Float = 0.2f
        private const val MIN_TEMPERATURE: Float = 0.1f
        private val IMPORTANT_KEYWORDS: List<String> = listOf(
            "承诺",
            "约定",
            "答应",
            "禁止",
            "禁忌",
            "不能",
            "不要",
            "喜欢",
            "讨厌",
            "关系",
            "恋人",
            "分手",
            "结婚",
            "纪念日",
            "必须",
            "永远",
            "以后"
        )
    }
}
