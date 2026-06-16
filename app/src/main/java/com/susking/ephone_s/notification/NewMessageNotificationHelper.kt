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
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile

/**
 * 新消息系统通知帮助类。
 *
 * 设计动机：
 * AI 一轮回复（可能含多条气泡）完整落库后，由 [com.susking.ephone_s.EPhoneSApplication] 监听
 * QqEvent.AiResponseCompleted 统一发一条通知，而非每条气泡一条，避免密集打扰。
 * 用户点击通知后经 [MainActivity.ACTION_OPEN_CHAT] 直达该联系人聊天界面。
 *
 * 为何放在 app 模块（而非 brain）：
 * 点击通知需拉起 [MainActivity] 并携带 ACTION_OPEN_CHAT / EXTRA_CHAT_CONTACT_ID 常量；
 * brain 模块位于 app 模块下游(app -> brain)，无法反向引用 MainActivity，
 * 故与 [IncomingCallNotificationHelper] 一样放在 app 模块，常量统一。
 */
object NewMessageNotificationHelper {

    // 新消息专用渠道，独立于来电渠道(incoming_call)，便于用户单独控制。
    // 用新渠道 ID（而非旧 qq_messages）：渠道一旦创建其属性不可再改，
    // 新建以确保震动交给 HapticFeedbackManager 统一控制（渠道本身关闭震动）。
    private const val CHANNEL_ID: String = "qq_new_message"
    private const val CHANNEL_NAME: String = "新消息"

    private const val DEFAULT_SUMMARY: String = "[新消息]"

    /**
     * 初始化新消息通知渠道。
     * IMPORTANCE_HIGH 保证横幅弹出；渠道本身关闭震动(enableVibration=false)，
     * 震动统一由 HapticFeedbackManager 触发，避免渠道与主动震动双重触发。
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接收联系人新消息通知"
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送新消息通知（一轮 AI 回复合并为一条）。
     *
     * @param context Context
     * @param contact 来信联系人
     * @param lastMessage 本轮最后一条 AI 消息(用于正文摘要)，可空
     * @param unreadCount 该联系人当前未读数，大于 1 时正文前缀「[共N条] 」
     */
    fun send(
        context: Context,
        contact: PersonProfile,
        lastMessage: ChatMessage?,
        unreadCount: Int
    ) {
        createNotificationChannel(context)
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 点击通知：拉起 MainActivity 并定位到该联系人聊天界面。
        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CHAT
            putExtra(MainActivity.EXTRA_CHAT_CONTACT_ID, contact.id)
            // 单 Activity 架构：复用已存在实例并带到前台，经 onNewIntent 处理。
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            contact.id.hashCode(),
            openChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary: String = buildSummaryText(lastMessage)
        val contentText: String = if (unreadCount > 1) "[共${unreadCount}条] $summary" else summary

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contact.remarkName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_nav_messages)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 通知 ID = contactId.hashCode()：每个联系人一条，新回复覆盖旧的，避免堆积。
        notificationManager.notify(contact.id.hashCode(), notification)
    }

    /** 按消息类型生成正文摘要，沿用聊天通知的类型转义习惯。 */
    private fun buildSummaryText(message: ChatMessage?): String {
        if (message == null) return DEFAULT_SUMMARY
        return when (message.type) {
            "text", "offline_text" -> message.content ?: ""
            "image_url", "naiimag" -> "[图片]"
            "sticker" -> "[表情]"
            "transfer" -> "[转账]"
            "waimai" -> "[外卖订单]"
            "gift" -> "[礼物]"
            else -> "[消息]"
        }
    }
}
