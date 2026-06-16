package com.susking.ephone_s.features.worldbook.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.databinding.ItemWorldBookBinding // 假设您会创建 item_world_book.xml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorldBookAdapter(
    private val onItemClicked: (WorldBookEntity) -> Unit,
    private val onItemLongClicked: (WorldBookEntity) -> Boolean, // 返回Boolean表示是否消费事件
    private val onMoveItem: (Int, Int) -> Unit // 添加回调，用于通知ViewModel项目移动
) : ListAdapter<WorldBookEntity, WorldBookAdapter.WorldBookViewHolder>(WorldBookDiffCallback), com.susking.ephone_s.features.worldbook.ui.detail.ItemMoveCallback.ItemTouchHelperContract {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorldBookViewHolder {
        val binding = ItemWorldBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WorldBookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorldBookViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class WorldBookViewHolder(private val binding: ItemWorldBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                onItemClicked(getItem(bindingAdapterPosition))
            }
            binding.root.setOnLongClickListener {
                onItemLongClicked(getItem(bindingAdapterPosition))
            }
        }

        fun bind(worldBook: WorldBookEntity) {
            binding.worldBookTitle.text = worldBook.title
            binding.worldBookCategory.text = worldBook.category.ifEmpty { "无分类" }
            binding.worldBookUpdatedAt.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(worldBook.updatedAt))

            // 根据isSystem属性设置背景颜色
            if (worldBook.isSystem) {
                binding.root.setBackgroundColor(binding.root.context.resources.getColor(com.susking.ephone_s.R.color.system_world_book_background, null)) // 需要定义system_world_book_background颜色
            } else {
                binding.root.setBackgroundColor(binding.root.context.resources.getColor(com.susking.ephone_s.R.color.default_world_book_background, null)) // 恢复默认背景
            }
        }
    }

    companion object WorldBookDiffCallback : DiffUtil.ItemCallback<WorldBookEntity>() {
        override fun areItemsTheSame(oldItem: WorldBookEntity, newItem: WorldBookEntity): Boolean {
            return oldItem.worldBookId == newItem.worldBookId
        }

        override fun areContentsTheSame(oldItem: WorldBookEntity, newItem: WorldBookEntity): Boolean {
            return oldItem == newItem
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        // 在适配器内部更新列表顺序
        val currentList = currentList.toMutableList()
        java.util.Collections.swap(currentList, fromPosition, toPosition)
        submitList(currentList) {
            // 通知ViewModel项目移动
            onMoveItem(fromPosition, toPosition)
        }
    }

    override fun onRowSelected(myViewHolder: RecyclerView.ViewHolder) {
        // 拖动时改变背景色
        myViewHolder.itemView.setBackgroundColor(myViewHolder.itemView.context.resources.getColor(com.susking.ephone_s.R.color.drag_world_book_background, null)) // 需要定义drag_world_book_background颜色
    }

    override fun onRowClear(myViewHolder: RecyclerView.ViewHolder) {
        // 拖动结束后恢复背景色
        val worldBook = getItem(myViewHolder.bindingAdapterPosition)
        if (worldBook.isSystem) {
            myViewHolder.itemView.setBackgroundColor(myViewHolder.itemView.context.resources.getColor(com.susking.ephone_s.R.color.system_world_book_background, null))
        } else {
            myViewHolder.itemView.setBackgroundColor(myViewHolder.itemView.context.resources.getColor(com.susking.ephone_s.R.color.default_world_book_background, null)) // 恢复默认背景
        }
    }

    override fun isSystemEntry(position: Int): Boolean {
        return getItem(position).isSystem
    }
}
