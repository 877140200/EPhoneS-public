package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import com.susking.ephone_s.aidata.data.local.AlipayDatabase
import com.susking.ephone_s.aidata.data.local.entity.AlipayBillEntity
import com.susking.ephone_s.aidata.data.local.entity.AlipayWalletEntity
import com.susking.ephone_s.aidata.data.local.entity.WorkStatusEntity
import com.susking.ephone_s.aidata.domain.alipay.AlipayRepository
import com.susking.ephone_s.aidata.domain.alipay.BillRecord
import com.susking.ephone_s.aidata.domain.alipay.WalletInfo
import com.susking.ephone_s.aidata.domain.model.WorkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 支付宝数据仓库实现类
 * 使用aidata模块的AlipayDatabase来访问数据库
 */
class AlipayRepositoryImpl(
    private val context: Context
) : AlipayRepository {

    private val database = AlipayDatabase.Companion.getDatabase(context)
    private val alipayDao = database.alipayDao()

    companion object {
        private const val DEFAULT_USER_ID = "user_main"
    }

    override fun getWalletInfo(userId: String): Flow<WalletInfo?> {
        return alipayDao.getWallet(userId).map { entity ->
            entity?.let {
                WalletInfo(
                    userId = it.userId,
                    balance = it.balance
                )
            }
        }
    }

    override suspend fun updateBalance(userId: String, newBalance: BigDecimal) {
        val wallet = AlipayWalletEntity(userId, newBalance)
        alipayDao.updateWallet(wallet)
    }

    override suspend fun initializeWallet(userId: String, initialBalance: BigDecimal) {
        val wallet = AlipayWalletEntity(userId, initialBalance)
        alipayDao.insertWallet(wallet)
    }

    override fun getBillList(limit: Int?): Flow<List<BillRecord>> {
        return alipayDao.getAllBills().map { entities ->
            val bills = entities.map { entity ->
                BillRecord(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    amount = entity.amount,
                    type = entity.type,
                    description = entity.description,
                    relatedContactId = entity.relatedContactId
                )
            }
            if (limit != null) bills.take(limit) else bills
        }
    }

    override fun getBillsByTimeRange(startTime: Long, endTime: Long): Flow<List<BillRecord>> {
        return alipayDao.getBillsByTimeRange(startTime, endTime).map { entities ->
            entities.map { entity ->
                BillRecord(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    amount = entity.amount,
                    type = entity.type,
                    description = entity.description,
                    relatedContactId = entity.relatedContactId
                )
            }
        }
    }

    override suspend fun addBillRecord(
        amount: BigDecimal,
        type: String,
        description: String,
        relatedContactId: String?
    ): Long {
        val bill = AlipayBillEntity(
            timestamp = System.currentTimeMillis(),
            amount = amount,
            type = type,
            description = description,
            relatedContactId = relatedContactId
        )
        return alipayDao.insertBill(bill)
    }

    override suspend fun getBillById(billId: Long): BillRecord? {
        val entity = alipayDao.getBillById(billId)
        return entity?.let {
            BillRecord(
                id = it.id,
                timestamp = it.timestamp,
                amount = it.amount,
                type = it.type,
                description = it.description,
                relatedContactId = it.relatedContactId
            )
        }
    }

    // ========== 交易相关方法实现 ==========

    override suspend fun performTransaction(
        amount: BigDecimal,
        type: String,
        description: String,
        contactId: String?
    ) {
        // 更新余额
        val currentWallet = alipayDao.getWallet(DEFAULT_USER_ID).first()
        if (currentWallet == null) {
            val newWallet = AlipayWalletEntity(DEFAULT_USER_ID, amount)
            alipayDao.insertWallet(newWallet)
        } else {
            val updatedWallet = currentWallet.copy(balance = currentWallet.balance + amount)
            alipayDao.updateWallet(updatedWallet)
        }

        // 添加账单记录
        addBillRecord(amount, type, description, contactId)
    }

    override suspend fun transferFunds(
        amount: BigDecimal,
        contactId: String,
        contactName: String?,
        isPayer: Boolean
    ) {
        val finalAmount = if (isPayer) amount.negate() else amount
        val description = if (isPayer) {
            "向 ${contactName ?: "未知用户"} 转账"
        } else {
            "收到 ${contactName ?: "未知用户"} 的转账"
        }
        performTransaction(finalAmount, "transfer", description, contactId)
    }

    override suspend fun refundTransaction(amount: BigDecimal, description: String) {
        performTransaction(amount, "refund", description, null)
    }

    // ========== 上班打卡相关方法实现 ==========

    override suspend fun startWork(): Boolean {
        // 检查是否在工作时间内（早上8点到晚上6点）
        if (!isWithinWorkHours()) {
            return false
        }
        
        val today = getTodayDateString()
        val workStatus = alipayDao.getWorkStatusSync(DEFAULT_USER_ID)

        // 检查今天是否已经上过班
        if (workStatus != null && workStatus.lastWorkDate == today) {
            return false
        }

        // 开始上班
        val newStatus = WorkStatusEntity(
            userId = DEFAULT_USER_ID,
            isWorking = true,
            workStartTime = System.currentTimeMillis(),
            workEndTime = 0,
            lastWorkDate = today
        )
        alipayDao.insertWorkStatus(newStatus)
        return true
    }

    override suspend fun finishWork(): Boolean {
        val workStatus = alipayDao.getWorkStatusSync(DEFAULT_USER_ID)

        if (workStatus == null || !workStatus.isWorking) {
            return false
        }

        // 计算实际工作时长（到当前时间或晚上6点，取较早的）
        val endTime = getActualEndTime(workStatus.workStartTime)
        val workDurationHours = TimeUnit.MILLISECONDS.toHours(
            endTime - workStatus.workStartTime
        )

        // 只有工作满1小时才能下班
        if (workDurationHours < 1) {
            return false
        }

        val reward = BigDecimal(workDurationHours).multiply(BigDecimal("30")) // 每小时30元

        // 发放工资
        performTransaction(reward, "salary", "工作时长: $workDurationHours 小时")

        // 更新工作状态
        val updatedStatus = workStatus.copy(
            isWorking = false,
            workEndTime = endTime
        )
        alipayDao.updateWorkStatus(updatedStatus)
        return true
    }

    override suspend fun forceFinishWork() {
        val workStatus = alipayDao.getWorkStatusSync(DEFAULT_USER_ID)

        if (workStatus == null || !workStatus.isWorking) {
            return
        }

        // 计算实际工作时长（到晚上6点）
        val endTime = getActualEndTime(workStatus.workStartTime)
        val workDurationHours = TimeUnit.MILLISECONDS.toHours(
            endTime - workStatus.workStartTime
        )

        // 只有工作满1小时才发放工资
        if (workDurationHours >= 1) {
            val reward = BigDecimal(workDurationHours).multiply(BigDecimal("30"))
            performTransaction(
                reward,
                "salary",
                "自动下班 工作时长: $workDurationHours 小时"
            )
        }

        val updatedStatus = workStatus.copy(
            isWorking = false,
            workEndTime = endTime
        )
        alipayDao.updateWorkStatus(updatedStatus)
    }

    override fun getWorkStatus(): Flow<WorkStatus> {
        return alipayDao.getWorkStatus(DEFAULT_USER_ID).map { entity ->
            val today = getTodayDateString()

            if (entity == null || entity.lastWorkDate != today) {
                // 没有工作记录或不是今天的记录
                WorkStatus(
                    isWorking = false,
                    canFinishWork = false,
                    hasWorkedToday = false,
                    workStartTime = 0,
                    workEndTime = 0
                )
            } else {
                val isWorking = entity.isWorking
                val workDuration = if (isWorking) {
                    System.currentTimeMillis() - entity.workStartTime
                } else {
                    0L
                }
                val canFinishWork = isWorking && workDuration >= TimeUnit.HOURS.toMillis(1)

                WorkStatus(
                    isWorking = isWorking,
                    canFinishWork = canFinishWork,
                    hasWorkedToday = true,
                    workStartTime = entity.workStartTime,
                    workEndTime = entity.workEndTime
                )
            }
        }
    }
    
    override suspend fun checkAndAutoFinishWork(): BigDecimal? {
        val workStatus = alipayDao.getWorkStatusSync(DEFAULT_USER_ID)
        
        // 如果没有工作记录或者已经下班了，直接返回
        if (workStatus == null || !workStatus.isWorking) {
            return null
        }
        
        // 获取上班当天的18点时间戳
        val workStartCalendar = Calendar.getInstance().apply {
            timeInMillis = workStatus.workStartTime
        }
        val workDayEndTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, workStartCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, workStartCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, workStartCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val now = System.currentTimeMillis()
        
        // 如果当前时间已经过了上班当天的18点，自动下班
        if (now > workDayEndTime) {
            // 计算实际工作时长（从上班时间到当天18点）
            val workDurationHours = TimeUnit.MILLISECONDS.toHours(
                workDayEndTime - workStatus.workStartTime
            )
            
            // 只有工作满1小时才发放工资
            val reward = if (workDurationHours >= 1) {
                val amount = BigDecimal(workDurationHours).multiply(BigDecimal("30"))
                performTransaction(
                    amount,
                    "salary",
                    "自动下班 工作时长: $workDurationHours 小时"
                )
                amount
            } else {
                BigDecimal.ZERO
            }
            
            // 更新工作状态为已下班
            val updatedStatus = workStatus.copy(
                isWorking = false,
                workEndTime = workDayEndTime
            )
            alipayDao.updateWorkStatus(updatedStatus)
            
            return reward
        }
        
        return null
    }

    /**
     * 获取今天的日期字符串(yyyy-MM-dd格式)
     */
    private fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    /**
     * 检查当前是否在工作时间内（早上8点到晚上6点）
     */
    private fun isWithinWorkHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour in 8..17 // 8:00 到 17:59 可以上班
    }
    
    /**
     * 获取实际下班时间
     * 如果当前时间在晚上6点之前，返回当前时间
     * 如果当前时间在晚上6点之后，返回今天的晚上6点
     */
    private fun getActualEndTime(startTime: Long): Long {
        val now = System.currentTimeMillis()
        val todayEndOfWork = getTodayEndOfWorkTime()
        
        // 如果开始时间在晚上6点之后（这种情况理论上不应该发生，但做防御性编程）
        if (startTime > todayEndOfWork) {
            return now
        }
        
        // 返回当前时间和晚上6点中较早的那个
        return minOf(now, todayEndOfWork)
    }
    
    /**
     * 获取今天晚上6点的时间戳
     */
    private fun getTodayEndOfWorkTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}