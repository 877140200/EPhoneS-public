package com.susking.ephone_s.qq.ui.chat.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.qq.databinding.ItemBoundWorldBookBinding

/**
 * 已绑定世界书的RecyclerView适配器
 */
class BoundWorldBookAdapter(
    private val onRemoveClick: (Long) -> Unit
) : ListAdapter<WorldBookEntity, BoundWorldBookAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBoundWorldBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onRemoveClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemBoundWorldBookBinding,
        private val onRemoveClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(worldBook: WorldBookEntity) {
            binding.worldBookTitle.text = worldBook.title
            binding.worldBookCategory.text = "分类: ${worldBook.category}"
            
            binding.removeButton.setOnClickListener {
                onRemoveClick(worldBook.worldBookId)
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