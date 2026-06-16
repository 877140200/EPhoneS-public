package com.susking.ephone_s.aidata.domain.repository

import com.susking.ephone_s.aidata.domain.model.AuthorizedAccount
import kotlinx.coroutines.flow.Flow

/**
 * 购物app已授权账号仓库接口
 */
interface ShoppingAuthorizedAccountRepository {
    
    /**
     * 获取所有已授权账号
     */
    fun getAllAuthorizedAccounts(): Flow<List<AuthorizedAccount>>
    
    /**
     * 检查某个联系人是否已授权
     */
    suspend fun isContactAuthorized(contactId: String): Boolean
    
    /**
     * 添加已授权账号
     */
    suspend fun addAuthorizedAccount(contactId: String, note: String? = null)
    
    /**
     * 删除已授权账号
     */
    suspend fun removeAuthorizedAccount(contactId: String)
    
    /**
     * 删除已授权账号(可选择是否保留商品)
     * @param contactId 联系人ID
     * @param keepProducts 是否保留该账号内的商品,默认true(保留)
     */
    suspend fun removeAuthorizedAccount(contactId: String, keepProducts: Boolean)
    
    /**
     * 清空所有已授权账号
     */
    suspend fun clearAllAuthorizedAccounts()
}