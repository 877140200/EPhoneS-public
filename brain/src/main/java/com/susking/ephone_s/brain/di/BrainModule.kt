package com.susking.ephone_s.brain.di

import android.content.Context
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.repository.LongTermMemoryRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.service.ImageGenerationService
import com.susking.ephone_s.aidata.domain.service.MemoryVectorizationService
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.prompt.SummarizeChatHistoryPromptBuilder
import com.susking.ephone_s.brain.api.BrainApi
import com.susking.ephone_s.brain.service.AiRequestService
import com.susking.ephone_s.brain.service.AutoSummarizeExecutor
import com.susking.ephone_s.brain.service.NovelAiImageGenerationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Brain模块的依赖注入配置
 * 提供Brain模块特有的服务实现
 */
@Module
@InstallIn(SingletonComponent::class)
object BrainModule {

    /**
     * 提供ImageGenerationService的NovelAI实现
     * 依赖于已存在的Gson实例(由ThemeModule提供)
     */
    @Provides
    @Singleton
    fun provideImageGenerationService(
        gson: Gson
    ): ImageGenerationService {
        return NovelAiImageGenerationService(gson)
    }

    /**
     * 提供AiRequestService实现（接口类型）
     * 用于执行AI请求。
     *
     * 注意：必须复用 BrainApi 持有的同一个单例，绝不能在这里 new 一个新实例。
     * 否则会出现"两个发请求的实例"——取消按钮操作的是 BrainApi 那个实例的
     * activeCalls 映射表，而实际请求（如 Q 聊天回复）若由另一个实例发出，
     * 取消就命中不到对应的 OkHttp Call，导致"取消了但请求其实还在跑"。
     * 统一为同一个实例后，所有请求都经 brain 转发，取消才能真正生效。
     */
    @Provides
    @Singleton
    fun provideAiRequestService(): com.susking.ephone_s.aidata.api.AiRequestService {
        return BrainApi.getAiRequestService()
    }
    
    /**
     * 提供AutoSummarizeExecutor
     * 自动总结执行器
     */
    @Provides
    @Singleton
    fun provideAutoSummarizeExecutor(
        @ApplicationContext context: Context,
        personProfileRepository: PersonProfileRepository,
        longTermMemoryRepository: LongTermMemoryRepository,
        summarizeChatHistoryPromptBuilder: SummarizeChatHistoryPromptBuilder,
        memoryVectorizationService: MemoryVectorizationService,
        memoryEventDao: com.susking.ephone_s.aidata.data.local.dao.memory.MemoryEventDao,
        memoryFactGraphExtractionService: MemoryFactGraphExtractionService,
        aiRequestService: AiRequestService
    ): AutoSummarizeExecutor {
        return AutoSummarizeExecutor(
            context,
            personProfileRepository,
            longTermMemoryRepository,
            summarizeChatHistoryPromptBuilder,
            memoryVectorizationService,
            memoryEventDao,
            memoryFactGraphExtractionService,
            aiRequestService
        )
    }
}