package com.susking.ephone_s.cphone.ui.qq

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.domain.model.SimulatedQQConversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模拟QQ会话列表适配器
 */
class CPhoneQQConversationAdapter(
    private val onConversationClick: (SimulatedQQConversation) -> Unit
) : ListAdapter<SimulatedQQConversation, CPhoneQQConversationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cphone_qq_conversation, parent, false)
        return ViewHolder(view as ViewGroup, onConversationClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val container: ViewGroup,
        private val onConversationClick: (SimulatedQQConversation) -> Unit
    ) : RecyclerView.ViewHolder(container) {
        
        private val ivAvatar: ImageView = container.findViewById(R.id.iv_avatar)
        private val tvContactName: TextView = container.findViewById(R.id.tv_contact_name)
        private val tvLastMessage: TextView = container.findViewById(R.id.tv_last_message)
        private val tvTime: TextView = container.findViewById(R.id.tv_time)
        private val badgeUnread: TextView = container.findViewById(R.id.badge_unread)

        fun bind(conversation: SimulatedQQConversation) {
            tvContactName.text = conversation.name
            tvLastMessage.text = conversation.lastMessage
            tvTime.text = formatTime(conversation.timestamp)
            
            // TODO: 加载头像 (使用avatarPrompt通过AI生成)
            // 暂时使用占位图
            ivAvatar.setImageResource(R.drawable.bg_image_placeholder)
            
            // 暂时不显示未读徽章
            badgeUnread.visibility = View.GONE
            
            container.setOnClickListener {
                onConversationClick(conversation)
            }
        }
        
        /**
         * 格式化时间显示
         */
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60_000 -> "刚刚" // 1分钟内
                diff < 3600_000 -> "${diff / 60_000}分钟前" // 1小时内
                diff < 86400_000 -> { // 今天
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
                diff < 172800_000 -> "昨天" // 昨天
                else -> { // 更早
                    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SimulatedQQConversation>() {
        override fun areItemsTheSame(
            oldItem: SimulatedQQConversation,
            newItem: SimulatedQQConversation
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: SimulatedQQConversation,
            newItem: SimulatedQQConversation
        ): Boolean {
            return oldItem == newItem
        }
    }
}