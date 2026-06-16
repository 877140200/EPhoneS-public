package com.susking.ephone_s.aidata.di

import android.content.Context
import com.susking.ephone_s.aidata.api.AiRequestService
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.dao.LongTermMemoryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEmbeddingDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEvidenceDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryGraphDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemorySummaryDao
import com.susking.ephone_s.aidata.data.local.dao.memory.MemoryRecallDebugDao
import com.susking.ephone_s.aidata.data.service.MemoryFactGraphExtractionServiceImpl
import com.susking.ephone_s.aidata.data.service.MemoryRecallDebugServiceImpl
import com.susking.ephone_s.aidata.data.service.MemoryRecallServiceImpl
import com.susking.ephone_s.aidata.data.service.MemorySummarizationServiceImpl
import com.susking.ephone_s.aidata.data.service.MemoryVectorizationServiceImpl
import com.susking.ephone_s.aidata.data.service.OnlineEmbeddingServiceImpl
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.domain.service.MemoryRecallDebugService
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.service.MemorySummarizationService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.domain.service.OnlineEmbeddingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {

    // --- DAO ---
    @Provides
    fun provideLongTermMemoryDao(database: AiDataDatabase) = database.longTermMemoryDao()

    @Provides
    fun provideMemoryEmbeddingDao(database: AiDataDatabase) = database.memoryEmbeddingDao()

    @Provides
    fun provideMemorySummaryDao(database: AiDataDatabase) = database.memorySummaryDao()

    @Provides
    fun provideMemoryEventDao(database: AiDataDatabase) = database.memoryEventDao()

    @Provides
    fun provideMemoryEvidenceDao(database: AiDataDatabase): MemoryEvidenceDao = database.memoryEvidenceDao()
 
    @Provides
    fun provideMemoryGraphDao(database: AiDataDatabase) = database.memoryGraphDao()

    @Provides
    fun provideMemoryRecallDebugDao(database: AiDataDatabase) = database.memoryRecallDebugDao()

    // --- 服务与仓库 ---
    @Provides
    @Singleton
    fun provideOnlineEmbeddingService(
        aiRequestService: AiRequestService
    ): OnlineEmbeddingService {
        return OnlineEmbeddingServiceImpl(aiRequestService)
    }

    @Provides
    @Singleton
    fun provideMemoryRecallDebugService(
        memoryRecallDebugDao: MemoryRecallDebugDao,
        longTermMemoryDao: LongTermMemoryDao,
        memoryEventDao: MemoryEventDao,
        memorySummaryDao: MemorySummaryDao
    ): MemoryRecallDebugService {
        return MemoryRecallDebugServiceImpl(
            memoryRecallDebugDao,
            longTermMemoryDao,
            memoryEventDao,
            memorySummaryDao
        )
    }

    @Provides
    @Singleton
    fun provideMemoryRecallService(
        embeddingService: OnlineEmbeddingService,
        memoryEmbeddingDao: MemoryEmbeddingDao,
        longTermMemoryDao: LongTermMemoryDao,
        memoryEventDao: MemoryEventDao,
        memorySummaryDao: MemorySummaryDao,
        memoryGraphDao: MemoryGraphDao,
        memoryRecallDebugService: MemoryRecallDebugService
    ): MemoryRecallService {
        return MemoryRecallServiceImpl(
            embeddingService,
            memoryEmbeddingDao,
            longTermMemoryDao,
            memoryEventDao,
            memorySummaryDao,
            memoryGraphDao,
            memoryRecallDebugService
        )
    }

    @Provides
    @Singleton
    fun provideMemoryVectorizationService(
        onlineEmbeddingService: OnlineEmbeddingService,
        memoryEmbeddingDao: MemoryEmbeddingDao
    ): MemoryVectorizationService {
        return MemoryVectorizationServiceImpl(onlineEmbeddingService, memoryEmbeddingDao)
    }

    @Provides
    @Singleton
    fun provideMemorySummarizationService(
        @ApplicationContext context: Context,
        longTermMemoryDao: LongTermMemoryDao,
        chatMessageDao: ChatMessageDao,
        memorySummaryDao: MemorySummaryDao,
        memoryEventDao: MemoryEventDao,
        memoryEmbeddingDao: MemoryEmbeddingDao,
        personProfileRepository: PersonProfileRepository,
        settingsRepository: SettingsRepository,
        aiRequestService: AiRequestService,
        memoryVectorizationService: MemoryVectorizationService
    ): MemorySummarizationService {
        return MemorySummarizationServiceImpl(
            context,
            longTermMemoryDao,
            chatMessageDao,
            memorySummaryDao,
            memoryEventDao,
            memoryEmbeddingDao,
            personProfileRepository,
            settingsRepository,
            aiRequestService,
            memoryVectorizationService
        )
    }

    @Provides
    @Singleton
    fun provideMemoryFactGraphExtractionService(
        memoryEventDao: MemoryEventDao,
        memoryEvidenceDao: MemoryEvidenceDao,
        memoryGraphDao: MemoryGraphDao,
        longTermMemoryDao: LongTermMemoryDao,
        personProfileRepository: PersonProfileRepository,
        memoryVectorizationService: MemoryVectorizationService
    ): MemoryFactGraphExtractionService {
        return MemoryFactGraphExtractionServiceImpl(
            memoryEventDao,
            memoryEvidenceDao,
            memoryGraphDao,
            longTermMemoryDao,
            personProfileRepository,
            memoryVectorizationService
        )
    }

}
