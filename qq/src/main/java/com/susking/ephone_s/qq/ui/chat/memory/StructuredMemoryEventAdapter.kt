package com.susking.ephone_s.qq.ui.chat.memory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEvent
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventType
import com.susking.ephone_s.qq.databinding.ItemStructuredMemoryEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StructuredMemoryEventAdapter(
    private val listener: OnStructuredEventInteractionListener
) : ListAdapter<MemoryEvent, StructuredMemoryEventAdapter.StructuredEventViewHolder>(StructuredEventDiffCallback()) {

    interface OnStructuredEventInteractionListener {
        fun onStructuredEventClick(event: MemoryEvent): Unit
        fun onStructuredEventEdit(event: MemoryEvent): Unit
        fun onStructuredEventDelete(event: MemoryEvent): Unit
        fun onStructuredEventInsertAfter(event: MemoryEvent): Unit
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StructuredEventViewHolder {
        val binding: ItemStructuredMemoryEventBinding = ItemStructuredMemoryEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StructuredEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StructuredEventViewHolder, position: Int): Unit {
        val canInsertAfter: Boolean = position < itemCount - 1
        holder.bind(getItem(position), listener, canInsertAfter)
    }

    class StructuredEventViewHolder(
        private val binding: ItemStructuredMemoryEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: MemoryEvent, listener: OnStructuredEventInteractionListener, canInsertAfter: Boolean): Unit {
            binding.eventTypeText.text = event.eventType.toDisplayText()
            binding.eventStatusText.text = event.status.toDisplayText()
            binding.eventTitleText.text = event.title
            binding.eventContentText.text = event.content
            binding.eventMetaText.text = buildMetaText(event)
            binding.root.setOnClickListener { listener.onStructuredEventClick(event) }
            binding.editButton.setOnClickListener { listener.onStructuredEventEdit(event) }
            binding.deleteButton.setOnClickListener { listener.onStructuredEventDelete(event) }
            binding.insertAfterButton.visibility = if (canInsertAfter) View.VISIBLE else View.GONE
            binding.insertAfterButton.setOnClickListener { listener.onStructuredEventInsertAfter(event) }
        }

        private fun buildMetaText(event: MemoryEvent): String {
            val confidenceText: String = String.format(Locale.CHINA, "%.2f", event.confidenceScore)
            return "重要度 ${event.importanceScore} · 置信度 $confidenceText · ${formatTime(event.eventTime)}"
        }

        private fun formatTime(timestamp: Long): String {
            val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            return dateFormat.format(Date(timestamp))
        }
    }

    class StructuredEventDiffCallback : DiffUtil.ItemCallback<MemoryEvent>() {
        override fun areItemsTheSame(oldItem: MemoryEvent, newItem: MemoryEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MemoryEvent, newItem: MemoryEvent): Boolean {
            return oldItem == newItem
        }
    }
}

private fun MemoryEventType.toDisplayText(): String {
    return when (this) {
        MemoryEventType.COMMITMENT -> "承诺"
        MemoryEventType.PREFERENCE -> "偏好"
        MemoryEventType.PROHIBITION -> "禁忌"
        MemoryEventType.ANNIVERSARY -> "纪念日"
        MemoryEventType.RELATIONSHIP -> "关系"
        MemoryEventType.FACT -> "事实"
        MemoryEventType.OPINION -> "观点"
        MemoryEventType.OTHER -> "其他"
    }
}

private fun MemoryEventStatus.toDisplayText(): String {
    return when (this) {
        MemoryEventStatus.ACTIVE -> "活跃"
        MemoryEventStatus.PENDING -> "待确认"
        MemoryEventStatus.RESOLVED -> "已完成"
        MemoryEventStatus.CANCELLED -> "已取消"
        MemoryEventStatus.EXPIRED -> "已过期"
        MemoryEventStatus.SUPERSEDED -> "已替代"
        MemoryEventStatus.ARCHIVED -> "已归档"
    }
}
