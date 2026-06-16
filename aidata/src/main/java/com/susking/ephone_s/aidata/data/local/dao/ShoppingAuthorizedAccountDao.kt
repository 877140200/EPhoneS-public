package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * 购物app已授权账号数据访问对象
 */
@Dao
interface ShoppingAuthorizedAccountDao {
    
    /**
     * 获取所有已授权账号
     */
    @Query("SELECT * FROM shopping_authorized_accounts ORDER BY authorizedTimestamp DESC")
    fun getAllAuthorizedAccounts(): Flow<List<ShoppingAuthorizedAccountEntity>>

    /**
     * 同步获取所有已授权账号(用于导出备份)
     */
    @Query("SELECT * FROM shopping_authorized_accounts ORDER BY authorizedTimestamp DESC")
    suspend fun getAllAuthorizedAccountsList(): List<ShoppingAuthorizedAccountEntity>

    /**
     * 检查某个联系人是否已授权
     */
    @Query("SELECT * FROM shopping_authorized_accounts WHERE contactId = :contactId LIMIT 1")
    suspend fun getAuthorizedAccount(contactId: String): ShoppingAuthorizedAccountEntity?
    
    /**
     * 添加已授权账号
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorizedAccount(account: ShoppingAuthorizedAccountEntity)

    /**
     * 批量添加已授权账号(用于导入备份)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorizedAccounts(accounts: List<ShoppingAuthorizedAccountEntity>)

    /**
     * 删除已授权账号
     */
    @Query("DELETE FROM shopping_authorized_accounts WHERE contactId = :contactId")
    suspend fun deleteAuthorizedAccount(contactId: String)
    
    /**
     * 清空所有已授权账号
     */
    @Query("DELETE FROM shopping_authorized_accounts")
    suspend fun deleteAllAuthorizedAccounts()
}