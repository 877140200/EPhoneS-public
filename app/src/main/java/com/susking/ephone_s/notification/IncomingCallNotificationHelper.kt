package com.susking.ephone_s.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.susking.ephone_s.MainActivity
import com.susking.ephone_s.R

/**
 * 全屏来电通知帮助类。
 *
 * 设计动机：
 * 当应用处于后台时，后台 worker 检测到 AI 来电无法直接拉起 VideoCallFragment 界面，
 * 因此改用「全屏来电通知」(full-screen intent)。系统会在锁屏/后台时直接全屏弹出来电界面，
 * 点击或系统拉起后进入 MainActivity，由 MainActivity 解析 Intent 弹出真正的来电界面。
 *
 * 为何放在 app 模块而非 brain 模块：
 * full-screen intent 需要引用 [MainActivity] 类及其 ACTION_INCOMING_CALL / EXTRA_INCOMING_CALL_CONTACT_ID
 * 常量。brain 模块位于 app 模块下游(app -> brain)，无法反向引用 MainActivity，
 * 若硬编码类名与常量则两头分散易错。app 模块可安全直接引用，常量统一。
 */
object IncomingCallNotificationHelper {

    // 来电通知专用渠道，独立于普通消息渠道(qq_messages)，便于用户单独控制来电提醒。
    private const val CHANNEL_ID: String = "incoming_call"
    private const val CHANNEL_NAME: String = "来电通知"

    // 来电通知固定 ID：同一时刻只允许一个来电通知，新来电覆盖旧的，避免堆积。
    private const val INCOMING_CALL_NOTIFICATION_ID: Int = 920_001

    /**
     * 初始化来电通知渠道。
     * 用 IMPORTANCE_HIGH 保证横幅弹出与响铃，配合 full-screen intent 在后台/锁屏全屏弹出。
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接收联系人来电的全屏通知"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送全屏来电通知。
     *
     * @param context Context
     * @param contactId 来电联系人ID，随 Intent 透传给 MainActivity
     * @param contactName 来电联系人显示名(备注名)，用于通知标题
     * @return 是否成功发送(供调用方据此决定是否走未接来电兜底)
     */
    fun sendIncomingCallNotification(
        context: Context,
        contactId: String,
        contactName: String
    ): Boolean {
        return try {
            createNotificationChannel(context)
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 拉起 MainActivity 的 Intent：携带来电 action 与 contactId，
            // MainActivity.handleIncomingCallIntent 解析后调用 setIncomingCallById 弹出来电界面。
            val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_INCOMING_CALL
                putExtra(MainActivity.EXTRA_INCOMING_CALL_CONTACT_ID, contactId)
                // 单 Activity 架构：复用已存在实例并将其带到前台，经 onNewIntent 处理。
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val fullScreenPendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                contactId.hashCode(),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(contactName)
                .setContentText("邀请你进行视频通话…")
                .setSmallIcon(R.drawable.baseline_phone_iphone_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                // 点击通知本身也进入来电界面。
                .setContentIntent(fullScreenPendingIntent)
                // 全屏 intent：后台/锁屏时系统直接全屏弹出来电界面(需 USE_FULL_SCREEN_INTENT 权限)。
                .setFullScreenIntent(fullScreenPendingIntent, true)
                // 来电通知不可被用户随手划掉，避免误触错过来电；接听/超时后由代码取消。
                .setOngoing(true)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
            true
        } catch (e: Exception) {
            // 发送失败(如缺权限)：返回 false，调用方走未接来电兜底，避免来电凭空消失。
            false
        }
    }

    /**
     * 取消当前来电通知。
     * 在用户接听、来电超时或拒接后调用，避免来电通知滞留。
     */
    fun cancelIncomingCallNotification(context: Context) {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}
