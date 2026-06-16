package com.susking.ephone_s.aidata.domain.repository

import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile

/**
 * ActionRepository 接口
 * 为 ActionExecutor 提供所需的数据操作
 */
interface ActionRepository {
    /**
     * 获取Gson实例(用于AiAction序列化/反序列化)
     */
    fun getGsonInstance(): Gson
    
    /**
     * 根据联系人ID获取角色信息
     */
    suspend fun getPersonProfile(contactId: String): PersonProfile?
    
    /**
     * 更新角色信息
     */
    suspend fun updatePersonProfile(profile: PersonProfile)
    
    /**
     * 根据时间戳获取消息
     */
    suspend fun getMessageByTimestamp(contactId: String, timestamp: Long): ChatMessage?
    
    /**
     * 根据时间戳更新消息状态
     */
    suspend fun updateMessageStatusByTimestamp(
        contactId: String,
        timestamp: Long,
        newStatus: String,
        statusSuffix: String
    )
    
    /**
     * 获取新动态数量
     */
    suspend fun getNewFeedsCount(): Int
    
    /**
     * 设置新动态数量
     */
    suspend fun setNewFeedsCount(count: Int)
    
    /**
     * 更新指定联系人最新的某类型消息的状态
     */
    suspend fun updateLatestMessageStatusByType(
        contactId: String,
        messageType: String,
        newStatus: String
    )
    
    /**
     * 添加已授权的购物账号
     */
    suspend fun addShoppingAuthorizedAccount(contactId: String, note: String?)
}