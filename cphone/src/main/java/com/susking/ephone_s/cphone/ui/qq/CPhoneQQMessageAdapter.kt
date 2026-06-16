package com.susking.ephone_s.cphone.ui.qq

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.domain.model.SimulatedQQMessage

/**
 * 模拟QQ消息列表适配器
 */
class CPhoneQQMessageAdapter(
    private val currentUserId: String = "me" // 当前用户ID，用于区分左右气泡
) : ListAdapter<SimulatedQQMessage, CPhoneQQMessageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cphone_qq_message, parent, false)
        return ViewHolder(view, currentUserId)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val itemView: View,
        private val currentUserId: String
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val llMessageLeft: LinearLayout = itemView.findViewById(R.id.ll_message_left)
        private val llMessageRight: LinearLayout = itemView.findViewById(R.id.ll_message_right)
        private val ivAvatarLeft: ImageView = itemView.findViewById(R.id.iv_avatar_left)
        private val ivAvatarRight: ImageView = itemView.findViewById(R.id.iv_avatar_right)
        private val tvMessageLeft: TextView = itemView.findViewById(R.id.tv_message_left)
        private val tvMessageRight: TextView = itemView.findViewById(R.id.tv_message_right)

        fun bind(message: SimulatedQQMessage) {
            // 判断是否为当前用户发送的消息
            val isMyMessage = message.senderId == currentUserId
            
            if (isMyMessage) {
                // 显示右侧气泡（自己发送的消息）
                llMessageLeft.visibility = View.GONE
                llMessageRight.visibility = View.VISIBLE
                tvMessageRight.text = message.content
                
                // TODO: 加载自己的头像
                ivAvatarRight.setImageResource(R.drawable.bg_image_placeholder)
            } else {
                // 显示左侧气泡（对方发送的消息）
                llMessageLeft.visibility = View.VISIBLE
                llMessageRight.visibility = View.GONE
                tvMessageLeft.text = message.content
                
                // TODO: 加载对方的头像
                ivAvatarLeft.setImageResource(R.drawable.bg_image_placeholder)
            }
            
            // TODO: 根据messageType显示不同类型的消息（文本、图片、表情等）
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SimulatedQQMessage>() {
        override fun areItemsTheSame(
            oldItem: SimulatedQQMessage,
            newItem: SimulatedQQMessage
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: SimulatedQQMessage,
            newItem: SimulatedQQMessage
        ): Boolean {
            return oldItem == newItem
        }
    }
}