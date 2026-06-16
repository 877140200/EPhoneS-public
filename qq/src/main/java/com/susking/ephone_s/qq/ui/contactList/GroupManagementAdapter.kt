package com.susking.ephone_s.qq.ui.contactList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.databinding.ItemGroupEditableBinding
import java.util.Collections

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int)
}

class GroupManagementAdapter(
    private val onDeleteClicked: (String) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<String, GroupManagementAdapter.GroupViewHolder>(GroupDiffCallback()), ItemTouchHelperAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupEditableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        val newList = currentList.toMutableList()
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(newList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(newList, i, i - 1)
            }
        }
        submitList(newList)
        onOrderChanged(newList)
    }

    inner class GroupViewHolder(private val binding: ItemGroupEditableBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(groupName: String) {
            binding.groupNameTextView.text = groupName

            // 所有分组都应该可以拖动排序
            binding.dragHandle.visibility = View.VISIBLE
            binding.dragHandle.setOnTouchListener { _, _ ->
                onStartDrag(this)
                false
            }

            // 只有“特别关心”和“我的好友”不可删除
            val isNonDeletable = groupName == "特别关心" || groupName == "我的好友"

            if (isNonDeletable) {
                binding.deleteGroupButton.visibility = View.GONE
                binding.deleteGroupButton.setOnClickListener(null)
            } else {
                binding.deleteGroupButton.visibility = View.VISIBLE
                binding.deleteGroupButton.setOnClickListener {
                    onDeleteClicked(groupName)
                }
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}