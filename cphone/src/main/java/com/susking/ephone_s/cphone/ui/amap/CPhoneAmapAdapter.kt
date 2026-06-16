package com.susking.ephone_s.cphone.ui.amap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneAmapFootprintBinding
import com.susking.ephone_s.aidata.domain.model.AmapFootprint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone高德地图足迹列表Adapter
 * 显示时间轴样式的足迹列表
 */
class CPhoneAmapAdapter(
    private val onFootprintClick: (AmapFootprint) -> Unit,
    private val onFootprintEdit: (AmapFootprint) -> Unit,
    private val onFootprintDelete: (AmapFootprint) -> Unit
) : ListAdapter<AmapFootprint, CPhoneAmapAdapter.FootprintViewHolder>(FootprintDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FootprintViewHolder {
        val binding = ItemCphoneAmapFootprintBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FootprintViewHolder(binding, onFootprintClick, onFootprintEdit, onFootprintDelete)
    }

    override fun onBindViewHolder(holder: FootprintViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder
     */
    class FootprintViewHolder(
        private val binding: ItemCphoneAmapFootprintBinding,
        private val onFootprintClick: (AmapFootprint) -> Unit,
        private val onFootprintEdit: (AmapFootprint) -> Unit,
        private val onFootprintDelete: (AmapFootprint) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(footprint: AmapFootprint) {
            binding.apply {
                // 设置地点名称
                tvPlaceName.text = footprint.locationName
                
                // 设置详细地址
                tvAddress.text = footprint.address
                
                // 设置时间
                tvTime.text = formatTime(footprint.timestamp)
                
                // 设置评论/备注
                if (footprint.comment.isNotEmpty()) {
                    tvComment.visibility = View.VISIBLE
                    tvComment.text = footprint.comment
                } else {
                    tvComment.visibility = View.GONE
                }
                
                // 设置照片网格
                // TODO: 实现照片网格显示
                // 当前暂时隐藏照片网格
                rvPhotos.visibility = View.GONE
                
                // 设置点击事件
                root.setOnClickListener {
                    onFootprintClick(footprint)
                }
                
                // 设置长按事件显示上下文菜单
                root.setOnLongClickListener {
                    showContextMenu(footprint)
                    true
                }
            }
        }
        
        /**
         * 显示上下文菜单
         */
        private fun showContextMenu(footprint: AmapFootprint) {
            val popup = PopupMenu(binding.root.context, binding.root)
            popup.menuInflater.inflate(R.menu.menu_pop, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onFootprintEdit(footprint)
                        true
                    }
                    R.id.action_delete -> {
                        onFootprintDelete(footprint)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        /**
         * 格式化时间
         * timestamp是ISO 8601格式的字符串，如"2025-09-25T18:30:00Z"
         */
        private fun formatTime(timestamp: String): String {
            return try {
                // 尝试解析ISO 8601格式
                val instant = Instant.parse(timestamp)
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            } catch (e: Exception) {
                // 如果解析失败，尝试作为Long处理
                try {
                    val timestampLong = timestamp.toLongOrNull() ?: 0L
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    dateFormat.format(Date(timestampLong))
                } catch (e2: Exception) {
                    timestamp // 返回原始字符串
                }
            }
        }
    }

    /**
     * DiffUtil回调
     */
    private class FootprintDiffCallback : DiffUtil.ItemCallback<AmapFootprint>() {
        override fun areItemsTheSame(oldItem: AmapFootprint, newItem: AmapFootprint): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AmapFootprint, newItem: AmapFootprint): Boolean {
            return oldItem == newItem
        }
    }
}