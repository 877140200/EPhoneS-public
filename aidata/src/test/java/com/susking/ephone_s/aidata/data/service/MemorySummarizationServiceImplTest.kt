package com.susking.ephone_s.aidata.data.service

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemorySummarizationServiceImplTest {
    private val context: Context = mock()
    private val longTermMemoryDao: LongTermMemoryDao = mock()
    private val chatMessageDao: ChatMessageDao = mock()
    private val memorySummaryDao: MemorySummaryDao = mock()
    private val memoryEventDao: MemoryEventDao = mock()
    private val memoryEmbeddingDao: MemoryEmbeddingDao = mock()
    private val personProfileRepository: PersonProfileRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val aiRequestService: AiRequestService = mock()
    private val memoryVectorizationService: MemoryVectorizationService = mock()
    private val service: MemorySummarizationServiceImpl = MemorySummarizationServiceImpl(
        context = context,
        longTermMemoryDao = longTermMemoryDao,
        chatMessageDao = chatMessageDao,
        memorySummaryDao = memorySummaryDao,
        memoryEventDao = memoryEventDao,
        memoryEmbeddingDao = memoryEmbeddingDao,
        personProfileRepository = personProfileRepository,
        settingsRepository = settingsRepository,
        aiRequestService = aiRequestService,
        memoryVectorizationService = memoryVectorizationService
    )

    @Test
    fun `parseSummaryGenerationResult reads new json importance fields`() {
        val actualResult: Any? = invokePrivate(
            methodName = "parseSummaryGenerationResult",
            parameterTypes = arrayOf(String::class.java),
            args = arrayOf("""{"summary":"我和小北约定明天买牛奶","importanceScore":8,"importanceReason":"明确承诺","confidenceScore":0.9}""")
        )

        assertThat(readProperty<String>(actualResult, "summaryText")).isEqualTo("我和小北约定明天买牛奶")
        assertThat(readProperty<Int>(actualResult, "modelImportanceScore")).isEqualTo(8)
        assertThat(readProperty<String>(actualResult, "importanceReason")).isEqualTo("明确承诺")
        assertThat(readProperty<Float>(actualResult, "confidenceScore")).isEqualTo(0.9f)
        assertThat(readProperty<Boolean>(actualResult, "isFallback")).isFalse()
    }

    @Test
    fun `parseSummaryGenerationResult keeps old json compatible`() {
        val actualResult: Any? = invokePrivate(
            methodName = "parseSummaryGenerationResult",
            parameterTypes = arrayOf(String::class.java),
            args = arrayOf("""{"summary":"今天只是普通聊天。"}""")
        )

        assertThat(readProperty<String>(actualResult, "summaryText")).isEqualTo("今天只是普通聊天。")
        assertThat(readProperty<Int?>(actualResult, "modelImportanceScore")).isNull()
        assertThat(readProperty<Boolean>(actualResult, "isFallback")).isFalse()
    }

    @Test
    fun `parseSummaryGenerationResult falls back to plain text`() {
        val actualResult: Any? = invokePrivate(
            methodName = "parseSummaryGenerationResult",
            parameterTypes = arrayOf(String::class.java),
            args = arrayOf("小北今天说以后不要忘记纪念日。")
        )

        assertThat(readProperty<String>(actualResult, "summaryText")).isEqualTo("小北今天说以后不要忘记纪念日。")
        assertThat(readProperty<Int?>(actualResult, "modelImportanceScore")).isNull()
        assertThat(readProperty<Boolean>(actualResult, "isFallback")).isTrue()
    }

    @Test
    fun `calculateHigherLevelImportanceScore preserves critical child importance floor`() {
        val summaryResult: Any = createSummaryGenerationResult(
            summaryText = "我和小北确认了一个长期关系约定。",
            modelImportanceScore = 3,
            importanceReason = "模型低估",
            confidenceScore = 0.8f,
            isFallback = false
        )
        val sourceSummaries: List<MemorySummary> = listOf(
            createSummary(id = "daily-critical", level = SummaryLevel.DAILY, importanceScore = 9),
            createSummary(id = "daily-normal", level = SummaryLevel.DAILY, importanceScore = 3)
        )

        val actualScore: Int = invokePrivate(
            methodName = "calculateHigherLevelImportanceScore",
            parameterTypes = arrayOf(List::class.java, summaryResult.javaClass),
            args = arrayOf(sourceSummaries, summaryResult)
        ) as Int

        assertThat(actualScore).isAtLeast(8)
    }

    @Test
    fun `repairDefaultImportanceSummaries recalculates daily summary from memory and event signals`() = runTest {
        val summary: MemorySummary = createSummary(
            id = "summary-default",
            level = SummaryLevel.DAILY,
            importanceScore = 3,
            summaryText = "我答应小北以后不要忘记纪念日。"
        )
        val importantMemory: LongTermMemory = createLongTermMemory(importanceScore = 8)
        val commitmentEvent: MemoryEvent = createMemoryEvent(importanceScore = 9, eventType = MemoryEventType.COMMITMENT)
        whenever(memorySummaryDao.getSummaryListForContact(CONTACT_ID)).thenReturn(listOf(summary))
        whenever(longTermMemoryDao.getMemoriesInTimeRange(CONTACT_ID, summary.startTimestamp, summary.endTimestamp)).thenReturn(listOf(importantMemory))
        whenever(memoryEventDao.getEventsInTimeRange(CONTACT_ID, summary.startTimestamp, summary.endTimestamp)).thenReturn(listOf(commitmentEvent))
        whenever(longTermMemoryDao.getMemoriesForContact(CONTACT_ID)).thenReturn(flowOf(listOf(importantMemory)))
        whenever(memoryVectorizationService.vectorizeSummary(any(), eq(importantMemory.id))).thenReturn(Result.success(createVectorizationResult(importantMemory.id, summary.id)))

        val actualCount: Int = service.repairDefaultImportanceSummaries(CONTACT_ID)

        assertThat(actualCount).isEqualTo(1)
        verify(memoryEmbeddingDao).deactivateEmbeddingsForIndexedObject(MemoryIndexedObjectType.SUMMARY, summary.id)
        verify(memorySummaryDao).insert(argThat { id == summary.id && importanceScore > 3 })
        verify(memoryVectorizationService).vectorizeSummary(argThat { id == summary.id && importanceScore > 3 }, eq(importantMemory.id))
    }

    @Test
    fun `repairDefaultImportanceSummaries skips summaries that remain default importance`() = runTest {
        val summary: MemorySummary = createSummary(
            id = "summary-default",
            level = SummaryLevel.DAILY,
            importanceScore = 3,
            summaryText = "今天普通聊天。"
        )
        whenever(memorySummaryDao.getSummaryListForContact(CONTACT_ID)).thenReturn(listOf(summary))
        whenever(longTermMemoryDao.getMemoriesInTimeRange(CONTACT_ID, summary.startTimestamp, summary.endTimestamp)).thenReturn(emptyList())
        whenever(memoryEventDao.getEventsInTimeRange(CONTACT_ID, summary.startTimestamp, summary.endTimestamp)).thenReturn(emptyList())

        val actualCount: Int = service.repairDefaultImportanceSummaries(CONTACT_ID)

        assertThat(actualCount).isEqualTo(0)
        verify(memorySummaryDao, never()).insert(any())
        verify(memoryEmbeddingDao, never()).deactivateEmbeddingsForIndexedObject(any(), any())
    }

    private fun invokePrivate(methodName: String, parameterTypes: Array<Class<*>>, args: Array<Any?>): Any? {
        val method = service.javaClass.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        return method.invoke(service, *args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readProperty(target: Any?, propertyName: String): T {
        requireNotNull(target)
        val getterName: String = "get${propertyName.replaceFirstChar { character: Char -> character.uppercase() }}"
        val booleanGetterName: String = propertyName
        val method = target.javaClass.methods.firstOrNull { method -> method.name == getterName || method.name == booleanGetterName }
            ?: target.javaClass.getDeclaredMethod(getterName)
        method.isAccessible = true
        return method.invoke(target) as T
    }

    private fun createSummaryGenerationResult(
        summaryText: String,
        modelImportanceScore: Int?,
        importanceReason: String?,
        confidenceScore: Float?,
        isFallback: Boolean
    ): Any {
        val resultClass: Class<*> = Class.forName("com.susking.ephone_s.aidata.data.service.MemorySummarizationServiceImpl\$SummaryGenerationResult")
        val constructor = resultClass.getDeclaredConstructor(
            String::class.java,
            Integer::class.java,
            String::class.java,
            java.lang.Float::class.java,
            Boolean::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        return constructor.newInstance(summaryText, modelImportanceScore, importanceReason, confidenceScore, isFallback)
    }

    private fun createSummary(
        id: String,
        level: SummaryLevel,
        importanceScore: Int,
        summaryText: String = "我和小北确认了一个重要约定。"
    ): MemorySummary {
        return MemorySummary(
            id = id,
            contactId = CONTACT_ID,
            summaryLevel = level,
            startTimestamp = WINDOW_START,
            endTimestamp = WINDOW_END,
            summaryText = summaryText,
            sourceMemoryCount = 2,
            importanceScore = importanceScore,
            modelVersion = "test-model"
        )
    }

    private fun createLongTermMemory(importanceScore: Int): LongTermMemory {
        return LongTermMemory(
            id = "memory-important",
            contactId = CONTACT_ID,
            memoryText = "我答应小北以后不要忘记纪念日。",
            timestamp = WINDOW_START + HOUR_MILLISECONDS,
            importanceScore = importanceScore
        )
    }

    private fun createMemoryEvent(importanceScore: Int, eventType: MemoryEventType): MemoryEvent {
        return MemoryEvent(
            id = "event-important",
            contactId = CONTACT_ID,
            evidenceMemoryId = "memory-important",
            eventType = eventType,
            title = "纪念日约定",
            content = "我答应小北以后不要忘记纪念日。",
            eventTime = WINDOW_START + HOUR_MILLISECONDS,
            importanceScore = importanceScore,
            confidenceScore = 0.95f,
            sourceModule = "test",
            status = MemoryEventStatus.PENDING
        )
    }

    private fun createVectorizationResult(memoryId: String, objectId: String): MemoryVectorizationService.VectorizationResult {
        return MemoryVectorizationService.VectorizationResult(
            memoryId = memoryId,
            contactId = CONTACT_ID,
            objectType = MemoryIndexedObjectType.SUMMARY,
            objectId = objectId,
            dimension = 2,
            modelName = "test-model",
            modelVersion = "test-version"
        )
    }

    private companion object {
        private const val CONTACT_ID: String = "contact-1"
        private const val WINDOW_START: Long = 1_700_000_000_000L
        private const val WINDOW_END: Long = 1_700_086_400_000L
        private const val HOUR_MILLISECONDS: Long = 3_600_000L
    }
}
