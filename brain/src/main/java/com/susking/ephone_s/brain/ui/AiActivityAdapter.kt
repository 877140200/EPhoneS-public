package com.susking.ephone_s.brain.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.AiActivity
import com.susking.ephone_s.aidata.domain.model.AiActivityStatus
import com.susking.ephone_s.brain.databinding.BrainItemAiActivityBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * AI 活动列表的适配器。
 */
class AiActivityAdapter(
    private val onItemClick: (AiActivity) -> Unit,
    private val colorProvider: ColorProvider,
    private val onCancelTask: ((String) -> Unit)? = null
) : ListAdapter<AiActivity, AiActivityAdapter.AiActivityViewHolder>(AiActivityDiffCallback()) {

    /**
     * 颜色提供者接口，用于解耦颜色资源依赖。
     */
    interface ColorProvider {
        fun getUnreadColor(): Int
        fun getFailedColor(): Int
        fun getSuccessColor(): Int
        fun getDefaultColor(): Int
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiActivityViewHolder {
        val binding = BrainItemAiActivityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AiActivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AiActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AiActivityViewHolder(private val binding: BrainItemAiActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: AiActivity) {
            binding.textViewDescription.text = activity.description
            binding.textViewTimestamp.text = formatTimestamp(activity.timestamp)
            binding.textViewStatus.text = activity.status.name

            val statusColor = when {
                !activity.isRead -> colorProvider.getUnreadColor()
                activity.status == AiActivityStatus.FAILED -> colorProvider.getFailedColor()
                activity.status == AiActivityStatus.SUCCESS -> colorProvider.getSuccessColor()
                else -> colorProvider.getDefaultColor()
            }
            binding.textViewStatus.setTextColor(statusColor)

            binding.root.setOnClickListener { onItemClick(activity) }
            
            // 取消按钮：所有拥有活动链ID且仍在等待或处理中的条目都允许取消。
            val hasActivityChainId: Boolean = activity.activityChainId.isNotEmpty()
            val canCancel: Boolean = hasActivityChainId && (activity.status == AiActivityStatus.WAITING || activity.status == AiActivityStatus.PROCESSING)
            
            if (canCancel && onCancelTask != null) {
                binding.buttonCancelTask.visibility = View.VISIBLE
                binding.buttonCancelTask.setOnClickListener {
                    activity.activityChainId?.let { chainId ->
                        onCancelTask.invoke(chainId)
                    }
                }
            } else {
                binding.buttonCancelTask.visibility = View.GONE
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class AiActivityDiffCallback : DiffUtil.ItemCallback<AiActivity>() {
        override fun areItemsTheSame(oldItem: AiActivity, newItem: AiActivity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AiActivity, newItem: AiActivity): Boolean {
            return oldItem == newItem
        }
    }
}