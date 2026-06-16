package com.susking.ephone_s.cphone.ui.diary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.memory.MemorySummary
import com.susking.ephone_s.aidata.domain.model.memory.SummaryLevel
import com.susking.ephone_s.cphone.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分层摘要列表适配器。
 */
class CPhoneMemorySummaryAdapter(
    private val onEditClick: (MemorySummary) -> Unit,
    private val onDeleteClick: (MemorySummary) -> Unit,
    private val onRegenerateClick: (MemorySummary) -> Unit,
    private val onRevectorizeClick: (MemorySummary) -> Unit
) : ListAdapter<CPhoneMemorySummaryItem, CPhoneMemorySummaryAdapter.SummaryViewHolder>(SummaryDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.item_cphone_memory_summary, parent, false)
        return SummaryViewHolder(view, onEditClick, onDeleteClick, onRegenerateClick, onRevectorizeClick)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int): Unit {
        holder.bind(getItem(position))
    }

    class SummaryViewHolder(
        itemView: View,
        private val onEditClick: (MemorySummary) -> Unit,
        private val onDeleteClick: (MemorySummary) -> Unit,
        private val onRegenerateClick: (MemorySummary) -> Unit,
        private val onRevectorizeClick: (MemorySummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val levelTextView: TextView = itemView.findViewById(R.id.tv_summary_level)
        private val timeTextView: TextView = itemView.findViewById(R.id.tv_summary_time)
        private val contentTextView: TextView = itemView.findViewById(R.id.tv_summary_content)
        private val metaTextView: TextView = itemView.findViewById(R.id.tv_summary_meta)
        private val vectorStatusTextView: TextView = itemView.findViewById(R.id.tv_summary_vector_status)
        private val debugTextView: TextView = itemView.findViewById(R.id.tv_summary_debug)
        private val editButton: View = itemView.findViewById(R.id.btn_edit_summary)
        private val deleteButton: View = itemView.findViewById(R.id.btn_delete_summary)
        private val regenerateButton: View = itemView.findViewById(R.id.btn_regenerate_summary)
        private val vectorizeButton: View = itemView.findViewById(R.id.btn_vectorize_summary)
        private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
        private val dateTimeFormat: SimpleDateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)

        fun bind(item: CPhoneMemorySummaryItem): Unit {
            val summary: MemorySummary = item.summary
            levelTextView.text = getLevelName(summary.summaryLevel)
            timeTextView.text = "${formatDate(summary.startTimestamp)} - ${formatDate(summary.endTimestamp)}"
            contentTextView.text = summary.summaryText
            metaTextView.text = "来源数量：${summary.sourceMemoryCount} · 重要度：${summary.importanceScore}/10 · 模型：${summary.modelVersion}"
            vectorStatusTextView.text = if (item.isVectorized) "向量状态：已向量化" else "向量状态：未向量化"
            debugTextView.text = buildDebugText(summary, item.isVectorized)
            vectorizeButton.visibility = View.VISIBLE
            editButton.setOnClickListener { onEditClick(summary) }
            deleteButton.setOnClickListener { onDeleteClick(summary) }
            regenerateButton.setOnClickListener { onRegenerateClick(summary) }
            vectorizeButton.setOnClickListener { onRevectorizeClick(summary) }
        }

        private fun formatDate(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }

        private fun formatDateTime(timestamp: Long): String {
            return dateTimeFormat.format(Date(timestamp))
        }

        private fun buildDebugText(summary: MemorySummary, isVectorized: Boolean): String {
            return """
                调试字段：
                id：${summary.id}
                contactId：${summary.contactId}
                summaryLevel：${summary.summaryLevel.name}
                startTimestamp：${summary.startTimestamp}（${formatDateTime(summary.startTimestamp)}）
                endTimestamp：${summary.endTimestamp}（${formatDateTime(summary.endTimestamp)}）
                sourceMemoryCount：${summary.sourceMemoryCount}
                importanceScore：${summary.importanceScore}
                modelVersion：${summary.modelVersion}
                createdAt：${summary.createdAt}（${formatDateTime(summary.createdAt)}）
                updatedAt：${summary.updatedAt}（${formatDateTime(summary.updatedAt)}）
                isVectorized：$isVectorized
            """.trimIndent()
        }

        private fun getLevelName(level: SummaryLevel): String {
            return when (level) {
                SummaryLevel.DAILY -> "每日摘要"
                SummaryLevel.WEEKLY -> "每周摘要"
                SummaryLevel.MONTHLY -> "每月摘要"
                SummaryLevel.YEARLY -> "每年摘要"
            }
        }
    }
}

class SummaryDiffCallback : DiffUtil.ItemCallback<CPhoneMemorySummaryItem>() {
    override fun areItemsTheSame(oldItem: CPhoneMemorySummaryItem, newItem: CPhoneMemorySummaryItem): Boolean {
        return oldItem.summary.id == newItem.summary.id
    }

    override fun areContentsTheSame(oldItem: CPhoneMemorySummaryItem, newItem: CPhoneMemorySummaryItem): Boolean {
        return oldItem == newItem
    }
}
