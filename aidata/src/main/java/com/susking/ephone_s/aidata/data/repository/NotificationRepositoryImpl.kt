package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.data.local.dao.NotificationDao
import com.susking.ephone_s.aidata.data.local.entity.NotificationEntity
import com.susking.ephone_s.aidata.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow

class NotificationRepositoryImpl(private val notificationDao: NotificationDao) : NotificationRepository {

    override suspend fun addNotification(notification: NotificationEntity) {
        notificationDao.insertNotification(notification)
    }

    override fun getAllNotifications(): Flow<List<NotificationEntity>> {
        return notificationDao.getAllNotifications()
    }

    override fun getUnreadNotificationCount(): Flow<Int> {
        return notificationDao.getUnreadNotificationCount()
    }

    override suspend fun markAsRead(notificationId: Int) {
        notificationDao.markNotificationAsRead(notificationId)
    }

    override suspend fun markAllAsRead() {
        notificationDao.markAllNotificationsAsRead()
    }
}