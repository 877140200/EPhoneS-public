package com.susking.ephone_s.aidata.data.repository

import com.susking.ephone_s.aidata.domain.repository.CallStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通话状态管理 Repository 实现
 * 使用内存缓存存储通话发起状态
 */
class CallStateRepositoryImpl : CallStateRepository {
    
    // 内存缓存: contactId -> 是否由用户发起
    private val callStateMap = mutableMapOf<String, Boolean>()
    
    override suspend fun getWasCallInitiatedByUser(contactId: String): Boolean? = withContext(Dispatchers.IO) {
        callStateMap[contactId]
    }
    
    override suspend fun setWasCallInitiatedByUser(contactId: String, wasInitiatedByUser: Boolean) = withContext(Dispatchers.IO) {
        callStateMap[contactId] = wasInitiatedByUser
    }
    
    override suspend fun clearCallState(contactId: String): Unit = withContext(Dispatchers.IO) {
        callStateMap.remove(contactId)
        Unit
    }
    
    override suspend fun clearAllCallStates() = withContext(Dispatchers.IO) {
        callStateMap.clear()
    }
}