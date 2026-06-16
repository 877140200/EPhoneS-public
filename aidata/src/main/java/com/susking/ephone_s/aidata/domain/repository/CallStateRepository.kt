package com.susking.ephone_s.aidata.domain.repository

/**
 * 通话状态管理 Repository
 * 管理每个联系人的通话发起状态
 */
interface CallStateRepository {
    
    /**
     * 获取通话是否由用户发起
     * @param contactId 联系人ID
     * @return true=用户发起, false=AI发起, null=未记录
     */
    suspend fun getWasCallInitiatedByUser(contactId: String): Boolean?
    
    /**
     * 设置通话发起方
     * @param contactId 联系人ID
     * @param wasInitiatedByUser true=用户发起, false=AI发起
     */
    suspend fun setWasCallInitiatedByUser(contactId: String, wasInitiatedByUser: Boolean)
    
    /**
     * 清除指定联系人的通话状态
     * @param contactId 联系人ID
     */
    suspend fun clearCallState(contactId: String)
    
    /**
     * 清除所有通话状态
     */
    suspend fun clearAllCallStates()
}