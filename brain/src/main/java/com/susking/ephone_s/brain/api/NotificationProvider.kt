package com.susking.ephone_s.brain.api

import kotlinx.coroutines.flow.Flow

/**
 * 通知提供者接口，用于解耦brain模块与通知系统的依赖。
 */
interface NotificationProvider {
    
    /**
     * 获取未读通知数量的Flow。
     */
    fun getUnreadNotificationCount(): Flow<Int>
    
    /**
     * 将所有通知标记为已读。
     */
    suspend fun markAllAsRead()
}