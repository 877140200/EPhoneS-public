package com.susking.ephone_s.brain

import com.susking.ephone_s.brain.api.NotificationProvider
import com.susking.ephone_s.aidata.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow

/**
 * NotificationProvider的实现，桥接到NotificationRepository。
 */
class NotificationProviderImpl(
    private val notificationRepository: NotificationRepository
) : NotificationProvider {
    
    override fun getUnreadNotificationCount(): Flow<Int> {
        return notificationRepository.getUnreadNotificationCount()
    }
    
    override suspend fun markAllAsRead() {
        notificationRepository.markAllAsRead()
    }
}