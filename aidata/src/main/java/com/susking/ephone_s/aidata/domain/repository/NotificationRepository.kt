package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {

    suspend fun addNotification(notification: NotificationEntity)

    fun getAllNotifications(): Flow<List<NotificationEntity>>

    fun getUnreadNotificationCount(): Flow<Int>

    suspend fun markAsRead(notificationId: Int)

    suspend fun markAllAsRead()
}