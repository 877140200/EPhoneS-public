package com.susking.ephone_s.qq.ui.chat.memory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.databinding.ItemVideoCallHistoryBinding

/**
 * 视频通话历史记录条目
 */
data class VideoCallHistoryItem(
    val id: String,
    val timestamp: Long,
    val duration: Long, // 通话时长（秒）
    val messages: List<VideoCallMessage> = emptyList()
)

/**
 * 视频通话消息
 */
data class VideoCallMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val isFromUser: Boolean
)

class VideoCallHistoryAdapter(
    private val onItemClick: (VideoCallHistoryItem) -> Unit
) : ListAdapter<VideoCallHistoryItem, VideoCallHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVideoCallHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemVideoCallHistoryBinding,
        private val onItemClick: (VideoCallHistoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoCallHistoryItem) {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            binding.callTimeText.text = dateFormat.format(java.util.Date(item.timestamp))
            
            val minutes = item.duration / 60
            val seconds = item.duration % 60
            binding.callDurationText.text = "通话时长: ${minutes}分${seconds}秒"
            
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<VideoCallHistoryItem>() {
        override fun areItemsTheSame(
            oldItem: VideoCallHistoryItem,
            newItem: VideoCallHistoryItem
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: VideoCallHistoryItem,
            newItem: VideoCallHistoryItem
        ): Boolean = oldItem == newItem
    }
}