package com.susking.ephone_s.aidata.domain.alipay

import com.susking.ephone_s.aidata.domain.model.WorkStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * 支付宝数据仓库接口
 * 定义支付宝模块的数据访问契约
 */
interface AlipayRepository {

    /**
     * 获取钱包信息
     * @param userId 用户ID
     * @return 钱包信息Flow
     */
    fun getWalletInfo(userId: String): Flow<WalletInfo?>

    /**
     * 更新钱包余额
     * @param userId 用户ID
     * @param newBalance 新余额
     */
    suspend fun updateBalance(userId: String, newBalance: BigDecimal)

    /**
     * 初始化钱包(如果不存在)
     * @param userId 用户ID
     * @param initialBalance 初始余额
     */
    suspend fun initializeWallet(userId: String, initialBalance: BigDecimal)

    /**
     * 获取账单列表
     * @param limit 限制数量,null表示获取全部
     * @return 账单列表Flow
     */
    fun getBillList(limit: Int? = null): Flow<List<BillRecord>>

    /**
     * 根据时间范围获取账单
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 账单列表Flow
     */
    fun getBillsByTimeRange(startTime: Long, endTime: Long): Flow<List<BillRecord>>

    /**
     * 添加账单记录
     * @param amount 金额
     * @param type 类型
     * @param description 描述
     * @param relatedContactId 关联联系人ID
     * @return 新增的账单ID
     */
    suspend fun addBillRecord(
        amount: BigDecimal,
        type: String,
        description: String,
        relatedContactId: String? = null
    ): Long

    /**
     * 获取账单详情
     * @param billId 账单ID
     * @return 账单记录
     */
    suspend fun getBillById(billId: Long): BillRecord?

    // ========== 交易相关方法(兼容旧WalletRepository) ==========

    /**
     * 执行一笔交易,同时更新钱包余额并创建账单记录
     * @param amount 交易金额(正数为收入,负数为支出)
     * @param type 交易类型
     * @param description 交易描述
     * @param contactId 关联联系人ID
     */
    suspend fun performTransaction(
        amount: BigDecimal,
        type: String,
        description: String,
        contactId: String? = null
    )

    /**
     * 处理转账
     * @param amount 转账金额
     * @param contactId 收款人ID
     * @param contactName 收款人姓名
     * @param isPayer 你是付款人吗
     */
    suspend fun transferFunds(
        amount: BigDecimal,
        contactId: String,
        contactName: String?,
        isPayer: Boolean
    )

    /**
     * 处理退款
     * @param amount 退款金额
     * @param description 退款描述
     */
    suspend fun refundTransaction(amount: BigDecimal, description: String)

    // ========== 上班打卡相关方法 ==========

    /**
     * 开始上班
     * @return 如果操作成功,则返回true;如果今天已经上过班,则返回false
     */
    suspend fun startWork(): Boolean

    /**
     * 结束上班
     * @return 如果操作成功,则返回true;如果还未上班或已经下班,则返回false
     */
    suspend fun finishWork(): Boolean

    /**
     * 强制结束上班,忽略下班时间限制
     */
    suspend fun forceFinishWork()

    /**
     * 获取当前工作状态
     * @return 当前的工作状态
     */
    fun getWorkStatus(): Flow<WorkStatus>
    
    /**
     * 检查并自动下班
     * 在用户进入支付宝时调用，如果已过下班时间则自动结算工资
     * @return 自动下班获得的工资金额，如果没有自动下班则返回null
     */
    suspend fun checkAndAutoFinishWork(): BigDecimal?
}