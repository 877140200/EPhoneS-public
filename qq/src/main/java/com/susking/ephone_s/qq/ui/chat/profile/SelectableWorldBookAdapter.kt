package com.susking.ephone_s.qq.ui.chat.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.qq.databinding.ItemSelectableWorldBookBinding

/**
 * 可选择世界书的RecyclerView适配器
 */
class SelectableWorldBookAdapter(
    private val onSelectionChanged: (WorldBookEntity, Boolean) -> Unit
) : ListAdapter<WorldBookEntity, SelectableWorldBookAdapter.ViewHolder>(DiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectableWorldBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onSelectionChanged, selectedIds)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSelectableWorldBookBinding,
        private val onSelectionChanged: (WorldBookEntity, Boolean) -> Unit,
        private val selectedIds: MutableSet<Long>
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(worldBook: WorldBookEntity) {
            binding.worldBookTitle.text = worldBook.title
            binding.worldBookCategory.text = "分类: ${worldBook.category}"
            binding.worldBookEntryCount.text = "条目数: 加载中..."
            
            // 设置复选框状态
            val isSelected = worldBook.worldBookId in selectedIds
            binding.checkbox.isChecked = isSelected
            
            // 整个卡片可点击
            binding.cardView.setOnClickListener {
                val newState = !binding.checkbox.isChecked
                binding.checkbox.isChecked = newState
                
                if (newState) {
                    selectedIds.add(worldBook.worldBookId)
                } else {
                    selectedIds.remove(worldBook.worldBookId)
                }
                
                onSelectionChanged(worldBook, newState)
            }
            
            // 复选框本身也可以点击
            binding.checkbox.setOnClickListener {
                val newState = binding.checkbox.isChecked
                
                if (newState) {
                    selectedIds.add(worldBook.worldBookId)
                } else {
                    selectedIds.remove(worldBook.worldBookId)
                }
                
                onSelectionChanged(worldBook, newState)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WorldBookEntity>() {
        override fun areItemsTheSame(oldItem: WorldBookEntity, newItem: WorldBookEntity): Boolean {
            return oldItem.worldBookId == newItem.worldBookId
        }

        override fun areContentsTheSame(oldItem: WorldBookEntity, newItem: WorldBookEntity): Boolean {
            return oldItem == newItem
        }
    }
}