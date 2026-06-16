package com.susking.ephone_s.qq.ui.chat.memory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.databinding.ItemVideoCallMessageBubbleBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 视频通话消息列表Adapter
 */
class VideoCallMessageAdapter : ListAdapter<VideoCallMessage, VideoCallMessageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVideoCallMessageBubbleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        
        // 计算是否显示时间分割线
        val showTimeDivider = if (position == 0) {
            true // 第一条消息总显示时间
        } else {
            val prevMessage = getItem(position - 1)
            // 如果两条消息之间的时间差大于5分钟（300,000毫秒），则显示时间
            message.timestamp - prevMessage.timestamp > 300000
        }
        
        holder.bind(message, showTimeDivider)
    }

    class ViewHolder(
        private val binding: ItemVideoCallMessageBubbleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

        fun bind(message: VideoCallMessage, showTimeDivider: Boolean) {
            // 显示时间分割线
            binding.timeDividerTextView.isVisible = showTimeDivider
            if (showTimeDivider) {
                binding.timeDividerTextView.text = formatTimeDivider(message.timestamp)
            }
            
            if (message.isFromUser) {
                // 发送的消息 (右侧)
                binding.sentMessageContainer.visibility = View.VISIBLE
                binding.receivedMessageContainer.visibility = View.GONE
                binding.sentMessageText.text = message.content
                binding.sentMessageTime.text = timeFormat.format(Date(message.timestamp))
            } else {
                // 接收的消息 (左侧)
                binding.receivedMessageContainer.visibility = View.VISIBLE
                binding.sentMessageContainer.visibility = View.GONE
                binding.receivedMessageText.text = message.content
                binding.receivedMessageTime.text = timeFormat.format(Date(message.timestamp))
            }
        }
        
        private fun formatTimeDivider(timestamp: Long): String {
            val messageDate = Date(timestamp)
            val today = Calendar.getInstance()
            val messageCal = Calendar.getInstance().apply { time = messageDate }

            return when {
                today.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR) -> {
                    // 今天
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
                }
                today.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) - 1 == messageCal.get(Calendar.DAY_OF_YEAR) -> {
                    // 昨天
                    "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
                }
                else -> {
                    // 更早
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(messageDate)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<VideoCallMessage>() {
        override fun areItemsTheSame(
            oldItem: VideoCallMessage,
            newItem: VideoCallMessage
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: VideoCallMessage,
            newItem: VideoCallMessage
        ): Boolean = oldItem == newItem
    }
}