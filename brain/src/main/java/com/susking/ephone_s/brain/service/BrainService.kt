package com.susking.ephone_s.brain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.susking.ephone_s.brain.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Brain后台服务 - 永不下班的管家
 *
 * 职责:
 * 1. 在后台持续运行,监听所有AI回复
 * 2. 收到AI消息后保存到数据库
 * 3. 如果用户不在聊天界面,发送系统通知
 * 4. 提供前台服务通知,确保不被系统杀死
 * 5. 【新增】自动总结功能监听和执行
 *
 * 生命周期:
 * - Application启动时创建
 * - 作为前台服务运行(Android 8.0+要求)
 * - 应用退出后仍可运行一段时间
 */
@AndroidEntryPoint
class BrainService : Service() {

    companion object {
        private const val TAG = "BrainService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "brain_service_channel"
        private const val CHANNEL_NAME = "Brain后台服务"
        
        // 服务运行状态
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
        
        /**
         * 启动Brain服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, BrainService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Brain服务启动请求已发送")
        }
        
        /**
         * 停止Brain服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BrainService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Brain服务停止请求已发送")
        }
    }

    // Binder用于Activity/Fragment绑定服务
    inner class BrainBinder : Binder() {
        fun getService(): BrainService = this@BrainService
    }

    private val binder = BrainBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 自动总结执行器
    @Inject
    lateinit var autoSummarizeExecutor: AutoSummarizeExecutor

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrainService onCreate")

        // 启动自动总结执行器
        try {
            autoSummarizeExecutor.start()
            Log.d(TAG, "自动总结执行器已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动自动总结执行器失败", e)
        }
        
        // 创建前台服务通知渠道
        createNotificationChannel()
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        _isServiceRunning.value = true
        Log.d(TAG, "Brain服务已启动,开始监听消息")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BrainService onStartCommand")
        
        // 返回START_STICKY确保服务被杀死后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "BrainService onBind")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BrainService onDestroy")
        
        // 停止自动总结执行器
        try {
            autoSummarizeExecutor.stop()
            Log.d(TAG, "自动总结执行器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止自动总结执行器失败", e)
        }
        
        serviceScope.cancel()
        _isServiceRunning.value = false
    }

    /**
     * 创建通知渠道(Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // 低重要性,不打扰用户
            ).apply {
                description = "Brain后台服务运行通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createForegroundNotification(): Notification {
        // 点击前台服务通知拉起 App 启动 Activity。
        // 用 getLaunchIntentForPackage 而非直接引用 MainActivity：brain 模块位于 app 下游(app -> brain)，
        // 无法反向引用 MainActivity 类，借包名取启动 Intent 最简洁。
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Brain服务运行中")
            .setContentText("后台监听AI消息")
            .setSmallIcon(R.drawable.ic_brain_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 不可滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}