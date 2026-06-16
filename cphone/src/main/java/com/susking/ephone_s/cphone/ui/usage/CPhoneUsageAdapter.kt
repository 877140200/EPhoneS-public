package com.susking.ephone_s.cphone.ui.usage

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.aidata.domain.model.AppUsageRecord

/**
 * App使用记录列表适配器
 */
class CPhoneUsageAdapter(
    private val onItemClick: (AppUsageRecord) -> Unit,
    private val onItemEdit: (AppUsageRecord) -> Unit,
    private val onItemDelete: (AppUsageRecord) -> Unit
) : ListAdapter<AppUsageRecord, CPhoneUsageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cphone_app_usage, parent, false)
        return ViewHolder(view as ViewGroup, onItemClick, onItemEdit, onItemDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val container: ViewGroup,
        private val onItemClick: (AppUsageRecord) -> Unit,
        private val onItemEdit: (AppUsageRecord) -> Unit,
        private val onItemDelete: (AppUsageRecord) -> Unit
    ) : RecyclerView.ViewHolder(container) {
        
        private val ivAppIcon: ImageView = container.findViewById(R.id.iv_app_icon)
        private val tvAppName: TextView = container.findViewById(R.id.tv_app_name)
        private val tvCategory: TextView = container.findViewById(R.id.tv_category)
        private val tvUsageTime: TextView = container.findViewById(R.id.tv_usage_time)

        fun bind(record: AppUsageRecord) {
            tvAppName.text = record.appName
            tvCategory.text = record.category
            tvUsageTime.text = formatUsageTime(record.usageTimeMinutes)
            
            // TODO: 加载App图标 (使用imagePrompt通过AI生成)
            // 暂时使用占位图
            ivAppIcon.setImageResource(R.drawable.bg_image_placeholder)
            
            container.setOnClickListener {
                onItemClick(record)
            }
            
            // 设置长按事件显示上下文菜单
            container.setOnLongClickListener {
                showContextMenu(record)
                true
            }
        }
        
        /**
         * 显示上下文菜单
         */
        private fun showContextMenu(record: AppUsageRecord) {
            val popup = PopupMenu(container.context, container)
            popup.menuInflater.inflate(R.menu.menu_pop, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onItemEdit(record)
                        true
                    }
                    R.id.action_delete -> {
                        onItemDelete(record)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
        
        /**
         * 格式化使用时长显示
         */
        private fun formatUsageTime(minutes: Int): String {
            return when {
                minutes < 60 -> "${minutes}分钟"
                minutes < 1440 -> {
                    val hours = minutes / 60
                    val mins = minutes % 60
                    if (mins == 0) {
                        "${hours}小时"
                    } else {
                        "${hours}.${mins / 6}小时"
                    }
                }
                else -> {
                    val days = minutes / 1440
                    val hours = (minutes % 1440) / 60
                    if (hours == 0) {
                        "${days}天"
                    } else {
                        "${days}天${hours}小时"
                    }
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AppUsageRecord>() {
        override fun areItemsTheSame(oldItem: AppUsageRecord, newItem: AppUsageRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AppUsageRecord, newItem: AppUsageRecord): Boolean {
            return oldItem == newItem
        }
    }
}