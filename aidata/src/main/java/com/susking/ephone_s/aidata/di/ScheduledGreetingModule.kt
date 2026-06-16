package com.susking.ephone_s.aidata.di

import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.dao.ScheduledGreetingDao
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.service.ScheduledGreetingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt模块：提供定时祝福相关的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object ScheduledGreetingModule {
    
    @Provides
    @Singleton
    fun provideScheduledGreetingService(
        scheduledGreetingDao: ScheduledGreetingDao,
        chatMessageDao: ChatMessageDao,
        personProfileRepository: PersonProfileRepository
    ): ScheduledGreetingService {
        return ScheduledGreetingService(
            scheduledGreetingDao,
            chatMessageDao,
            personProfileRepository
        )
    }
}