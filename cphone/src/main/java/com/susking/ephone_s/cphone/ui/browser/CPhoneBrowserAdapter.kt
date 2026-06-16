package com.susking.ephone_s.cphone.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneBrowserHistoryBinding
import com.susking.ephone_s.aidata.domain.model.BrowserRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 浏览器历史记录列表Adapter
 */
class CPhoneBrowserAdapter(
    private val onHistoryClick: (BrowserRecord) -> Unit,
    private val onFavoriteClick: (BrowserRecord) -> Unit,
    private val onHistoryEdit: (BrowserRecord) -> Unit,
    private val onHistoryDelete: (BrowserRecord) -> Unit
) : ListAdapter<BrowserRecord, CPhoneBrowserAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemCphoneBrowserHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding, onHistoryClick, onFavoriteClick, onHistoryEdit, onHistoryDelete)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemCphoneBrowserHistoryBinding,
        private val onHistoryClick: (BrowserRecord) -> Unit,
        private val onFavoriteClick: (BrowserRecord) -> Unit,
        private val onHistoryEdit: (BrowserRecord) -> Unit,
        private val onHistoryDelete: (BrowserRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(history: BrowserRecord) {
            binding.apply {
                // 设置标题
                tvTitle.text = history.title
                
                // 设置URL
                tvUrl.text = history.url
                
                // 设置时间
                tvTime.text = formatTime(history.timestamp)
                
                // 设置收藏图标
                ivFavorite.setImageResource(
                    if (history.isFavorite) {
                        R.drawable.ic_star_full
                    } else {
                        R.drawable.ic_star_outline
                    }
                )
                
                // 设置点击事件
                root.setOnClickListener {
                    onHistoryClick(history)
                }
                
                // 设置收藏按钮点击事件
                ivFavorite.setOnClickListener {
                    onFavoriteClick(history)
                }
                
                // 设置长按事件显示上下文菜单
                root.setOnLongClickListener {
                    showContextMenu(history)
                    true
                }
            }
        }
        
        /**
         * 显示上下文菜单
         */
        private fun showContextMenu(history: BrowserRecord) {
            val popup = PopupMenu(binding.root.context, binding.root)
            popup.menuInflater.inflate(R.menu.menu_pop, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onHistoryEdit(history)
                        true
                    }
                    R.id.action_delete -> {
                        onHistoryDelete(history)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        /**
         * 格式化时间戳
         */
        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<BrowserRecord>() {
        override fun areItemsTheSame(oldItem: BrowserRecord, newItem: BrowserRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BrowserRecord, newItem: BrowserRecord): Boolean {
            return oldItem == newItem
        }
    }
}