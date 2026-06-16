package com.susking.ephone_s.qq.ui.chat.memory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.qq.databinding.ItemLongTermMemoryBinding

class LongTermMemoryAdapter(
    private val listener: OnMemoryInteractionListener
) : ListAdapter<LongTermMemory, LongTermMemoryAdapter.MemoryViewHolder>(MemoryDiffCallback()) {

    interface OnMemoryInteractionListener {
        fun onMemoryClick(memory: LongTermMemory)
        fun onDetail(memory: LongTermMemory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val binding = ItemLongTermMemoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MemoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory: LongTermMemory = getItem(position)
        holder.bind(memory, listener)
    }

    class MemoryViewHolder(private val binding: ItemLongTermMemoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(memory: LongTermMemory, listener: OnMemoryInteractionListener): Unit {
            binding.memoryText.text = memory.memoryText
            binding.root.strokeWidth = DEFAULT_STROKE_WIDTH
            binding.root.alpha = SELECTED_ALPHA
            binding.root.setOnClickListener { listener.onMemoryClick(memory) }
            binding.editButton.visibility = View.VISIBLE
            binding.deleteButton.visibility = View.GONE
            binding.editButton.contentDescription = "查看原子事件纪念记录"
            binding.editButton.setOnClickListener { listener.onDetail(memory) }
        }
    }

    class MemoryDiffCallback : DiffUtil.ItemCallback<LongTermMemory>() {
        override fun areItemsTheSame(oldItem: LongTermMemory, newItem: LongTermMemory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LongTermMemory, newItem: LongTermMemory): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        private const val DEFAULT_STROKE_WIDTH: Int = 0
        private const val SELECTED_ALPHA: Float = 1.0f
    }
}