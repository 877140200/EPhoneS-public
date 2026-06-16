package com.susking.ephone_s.features.worldbook.ui.detail

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.databinding.ItemWorldBookEntryBinding
import java.util.Collections

class WorldBookEntryAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onListReordered: (List<WorldBookEntryEntity>) -> Unit,
    private val onEntryChanged: (WorldBookEntryEntity) -> Unit,
    private val onEditClicked: (WorldBookEntryEntity) -> Unit
) : ListAdapter<WorldBookEntryEntity, WorldBookEntryAdapter.EntryViewHolder>(EntryDiffCallback),
    ItemMoveCallback.ItemTouchHelperContract {

    private val expansionState = mutableMapOf<Long, Boolean>()
    private val mutableList: MutableList<WorldBookEntryEntity>
        get() = currentList.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemWorldBookEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class EntryViewHolder(private val binding: ItemWorldBookEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: WorldBookEntryEntity) {
            binding.entryName.text = entry.name
            binding.entryContent.text = entry.content
            binding.entrySwitch.isChecked = entry.isEnabled

            val isExpanded = expansionState[entry.entryId] ?: false
            binding.entryContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // 系统条目特殊处理
            if (entry.isSystemEntry) {
                binding.dragHandle.visibility = View.GONE // 系统条目不可拖动
                binding.entryName.isClickable = false // 系统条目名称不可修改
                binding.entryName.alpha = 0.6f // 名称显示为半透明
            } else {
                binding.dragHandle.visibility = View.VISIBLE // 普通条目可拖动
                binding.entryName.isClickable = true
                binding.entryName.alpha = 1.0f
            }


            // Listeners
            binding.entrySwitch.setOnCheckedChangeListener { _, isChecked ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentEntry = getItem(bindingAdapterPosition)
                    if (currentEntry.isEnabled != isChecked) {
                        onEntryChanged(currentEntry.copy(isEnabled = isChecked))
                    }
                }
            }

            binding.editButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onEditClicked(getItem(bindingAdapterPosition))
                }
            }

            binding.entryName.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentEntry = getItem(bindingAdapterPosition)
                    val currentState = expansionState[currentEntry.entryId] ?: false
                    expansionState[currentEntry.entryId] = !currentState
                    notifyItemChanged(bindingAdapterPosition)
                }
            }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !entry.isSystemEntry) { // 系统条目不可拖动
                    onStartDrag(this)
                }
                false
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        // 过滤掉系统条目，系统条目不参与手动排序
        val nonSystemList = mutableList.filter { !it.isSystemEntry }.toMutableList()
        val systemEntries = mutableList.filter { it.isSystemEntry }

        val fromItem = currentList[fromPosition] // 使用 currentList 获取原始位置的元素
        val toItem = currentList[toPosition] // 使用 currentList 获取原始位置的元素

        if (fromItem.isSystemEntry || toItem.isSystemEntry) {
            // 如果涉及到系统条目，则不允许拖动，并恢复原始位置
            notifyItemChanged(fromPosition)
            notifyItemChanged(toPosition)
            return
        }

        val fromIndexInNonSystem = nonSystemList.indexOf(fromItem)
        val toIndexInNonSystem = nonSystemList.indexOf(toItem)

        if (fromIndexInNonSystem != -1 && toIndexInNonSystem != -1) {
            Collections.swap(nonSystemList, fromIndexInNonSystem, toIndexInNonSystem)

            // 重新构建完整列表，系统条目在前，非系统条目在后
            val newList = systemEntries.sortedBy { it.displayOrder }.toMutableList()
            newList.addAll(nonSystemList)

            submitList(newList) {
                onListReordered(newList) // 通知ViewModel更新数据库
            }
        } else {
            // 出现异常情况，直接通知更新整个列表以恢复正确状态
            onListReordered(currentList)
        }
    }

    override fun onRowSelected(myViewHolder: RecyclerView.ViewHolder) {
        val entry = getItem(myViewHolder.bindingAdapterPosition)
        if (!entry.isSystemEntry) { // 系统条目不改变alpha
            myViewHolder.itemView.alpha = 0.7f
        }
    }

    override fun onRowClear(myViewHolder: RecyclerView.ViewHolder) {
        val entry = getItem(myViewHolder.bindingAdapterPosition)
        if (!entry.isSystemEntry) { // 系统条目不改变alpha
            myViewHolder.itemView.alpha = 1.0f
        }
        // 当拖拽结束时，通知ViewModel更新顺序
        // onListReordered(currentList) // 已经在onRowMoved中调用
    }

    override fun isSystemEntry(position: Int): Boolean {
        return getItem(position).isSystemEntry
    }
}

object EntryDiffCallback : DiffUtil.ItemCallback<WorldBookEntryEntity>() {
    override fun areItemsTheSame(oldItem: WorldBookEntryEntity, newItem: WorldBookEntryEntity) =
        oldItem.entryId == newItem.entryId

    override fun areContentsTheSame(oldItem: WorldBookEntryEntity, newItem: WorldBookEntryEntity) =
        oldItem == newItem
}