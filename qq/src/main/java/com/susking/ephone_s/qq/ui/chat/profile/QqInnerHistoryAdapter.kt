package com.susking.ephone_s.qq.ui.chat.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.databinding.ItemQqInnerHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InnerHistoryItem(
    val timestamp: Long,
    val heartbeatContent: String?,
    val jottingContent: String?
)

class QqInnerHistoryAdapter : ListAdapter<InnerHistoryItem, QqInnerHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQqInnerHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class ViewHolder(private val binding: ItemQqInnerHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InnerHistoryItem) {
            binding.timestampTextView.text = formatTimestamp(item.timestamp)

            if (item.heartbeatContent != null) {
                binding.heartfeltWordsLayout.visibility = View.VISIBLE
                binding.heartfeltWordsTextView.text = item.heartbeatContent
            } else {
                binding.heartfeltWordsLayout.visibility = View.GONE
            }

            if (item.jottingContent != null) {
                binding.casualNotesLayout.visibility = View.VISIBLE
                binding.casualNotesTextView.text = item.jottingContent
            } else {
                binding.casualNotesLayout.visibility = View.GONE
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val date = Date(timestamp)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(date)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<InnerHistoryItem>() {
        override fun areItemsTheSame(oldItem: InnerHistoryItem, newItem: InnerHistoryItem): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: InnerHistoryItem, newItem: InnerHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
