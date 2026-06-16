package com.susking.ephone_s.aidata.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.susking.ephone_s.aidata.data.local.dao.ScheduledGreetingDao
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.entity.ChatMessageEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 定时发送预设祝福的Worker
 * 每分钟检查一次是否有需要发送的祝福
 */
@HiltWorker
class ScheduledGreetingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduledGreetingDao: ScheduledGreetingDao,
    private val chatMessageDao: ChatMessageDao
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "ScheduledGreetingWorker"
        private const val WORK_NAME = "ScheduledGreetingWork"
        
        /**
         * 调度周期性检查任务
         * 每分钟执行一次
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // 不要求电量充足
                .build()
            
            val repeatRequest = PeriodicWorkRequestBuilder<ScheduledGreetingWorker>(
                1, TimeUnit.MINUTES // 每分钟检查一次
            )
                .setConstraints(constraints)
                .setInitialDelay(0, TimeUnit.SECONDS) // 立即开始
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保持
                repeatRequest
            )
        }
        
        /**
         * 立即执行一次检查（用于测试或手动触发）
         */
        fun runNow(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<ScheduledGreetingWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
        
        /**
         * 取消所有祝福发送任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
            
            Log.i(TAG, "========== 定时祝福Worker执行 ==========")
            Log.i(TAG, "【Worker执行】当前时间: ${dateFormat.format(now)}")
            
            // 查询所有到期需要发送的祝福
            val dueGreetings = scheduledGreetingDao.getGreetingsDueBy(now)
            
            Log.i(TAG, "【Worker执行】待发送祝福数量: ${dueGreetings.size}")
            
            if (dueGreetings.isEmpty()) {
                Log.i(TAG, "【Worker执行】✓ 没有需要发送的祝福")
                Log.i(TAG, "========================================")
                return@withContext Result.success()
            }
            
            // 逐个发送祝福
            dueGreetings.forEachIndexed { index, greeting ->
                Log.i(TAG, "---------- 发送祝福 ${index + 1}/${dueGreetings.size} ----------")
                Log.i(TAG, "【发送祝福】祝福ID: ${greeting.id}")
                Log.i(TAG, "【发送祝福】联系人: ${greeting.contactId}")
                Log.i(TAG, "【发送祝福】祝福类型: ${greeting.greetingType}")
                Log.i(TAG, "【发送祝福】预定发送时间: ${dateFormat.format(greeting.scheduledTime)}")
                
                try {
                    sendGreeting(greeting, dateFormat)
                    Log.i(TAG, "【发送祝福】✓ 成功发送祝福")
                } catch (e: Exception) {
                    Log.e(TAG, "【发送祝福】✗ 发送失败: ${e.message}", e)
                    // 单个祝福发送失败不影响其他祝福
                }
                Log.i(TAG, "--------------------------------------------")
            }
            
            // 清理30天前的已发送祝福记录
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
            val deletedCount = scheduledGreetingDao.deleteOldSentGreetings(thirtyDaysAgo)
            Log.i(TAG, "【清理记录】清理了 $deletedCount 条30天前的祝福记录")
            
            Log.i(TAG, "【Worker执行】✓ Worker执行完成")
            Log.i(TAG, "========================================")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "【Worker执行】✗ Worker执行失败", e)
            Result.retry()
        }
    }
    
    /**
     * 发送单条祝福
     */
    private suspend fun sendGreeting(
        greeting: com.susking.ephone_s.aidata.data.local.entity.ScheduledGreetingEntity,
        dateFormat: SimpleDateFormat
    ) {
        Log.d(TAG, "【消息创建】创建祝福消息...")
        Log.d(TAG, "【消息创建】内容长度: ${greeting.greetingContent.length} 字符")
        Log.d(TAG, "【消息创建】内容预览: ${greeting.greetingContent.take(50)}...")
        
        // 创建消息实体
        val message = ChatMessageEntity(
            contactId = greeting.contactId,
            type = "text",
            content = greeting.greetingContent,
            timestamp = System.currentTimeMillis(),
            role = "assistant"
        )
        
        Log.d(TAG, "【消息插入】插入消息到聊天记录...")
        // 插入消息到聊天记录
        chatMessageDao.insertMessage(message)
        Log.d(TAG, "【消息插入】✓ 消息已插入")
        
        // 标记祝福为已发送
        val sentTime = System.currentTimeMillis()
        Log.d(TAG, "【状态更新】标记祝福为已发送: ${dateFormat.format(sentTime)}")
        scheduledGreetingDao.markAsSent(greeting.id, sentTime)
        Log.d(TAG, "【状态更新】✓ 祝福状态已更新")
    }

}