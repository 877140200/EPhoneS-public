package com.susking.ephone_s.aidata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.susking.ephone_s.aidata.data.local.entity.AlipayBillEntity
import com.susking.ephone_s.aidata.data.local.entity.AlipayWalletEntity
import com.susking.ephone_s.aidata.data.local.entity.WorkStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * 支付宝数据访问对象
 */
@Dao
interface AlipayDao {
    
    // 钱包相关
    @Query("SELECT * FROM alipay_wallets WHERE userId = :userId")
    fun getWallet(userId: String): Flow<AlipayWalletEntity?>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWallet(wallet: AlipayWalletEntity)
    
    @Update
    suspend fun updateWallet(wallet: AlipayWalletEntity)
    
    // 账单相关
    @Query("SELECT * FROM alipay_bills ORDER BY timestamp DESC")
    fun getAllBills(): Flow<List<AlipayBillEntity>>
    
    @Query("SELECT * FROM alipay_bills WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getBillsByTimeRange(startTime: Long, endTime: Long): Flow<List<AlipayBillEntity>>
    
    @Query("SELECT * FROM alipay_bills WHERE id = :billId")
    suspend fun getBillById(billId: Long): AlipayBillEntity?
    
    @Insert
    suspend fun insertBill(bill: AlipayBillEntity): Long
    
    // 工作状态相关
    @Query("SELECT * FROM work_status WHERE userId = :userId")
    fun getWorkStatus(userId: String): Flow<WorkStatusEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkStatus(workStatus: WorkStatusEntity)
    
    @Update
    suspend fun updateWorkStatus(workStatus: WorkStatusEntity)
    
    @Query("SELECT * FROM work_status WHERE userId = :userId")
    suspend fun getWorkStatusSync(userId: String): WorkStatusEntity?

    // 导入导出相关方法
    @Query("DELETE FROM alipay_bills")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM alipay_wallets")
    suspend fun clearWallet()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTransactions(transactions: List<AlipayBillEntity>)

    @Query("SELECT * FROM alipay_bills ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSync(): List<AlipayBillEntity>

    @Query("SELECT * FROM alipay_wallets")
    suspend fun getAllWalletsSync(): List<AlipayWalletEntity>
}