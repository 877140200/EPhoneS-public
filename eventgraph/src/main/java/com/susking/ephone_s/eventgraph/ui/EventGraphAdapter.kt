package com.susking.ephone_s.eventgraph.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.eventgraph.databinding.ItemEventGraphEntryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 事件图谱条目适配器。
 */
class EventGraphAdapter(
    private val onEditItem: (EventGraphItem) -> Unit,
    private val onDeleteItem: (EventGraphItem) -> Unit,
    private val onOpenDetail: (EventGraphItem) -> Unit
) : ListAdapter<EventGraphItem, EventGraphAdapter.EventGraphViewHolder>(EventGraphDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventGraphViewHolder {
        val binding: ItemEventGraphEntryBinding = ItemEventGraphEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventGraphViewHolder(binding, onEditItem, onDeleteItem, onOpenDetail)
    }

    override fun onBindViewHolder(holder: EventGraphViewHolder, position: Int): Unit {
        holder.bind(getItem(position))
    }

    class EventGraphViewHolder(
        private val binding: ItemEventGraphEntryBinding,
        private val onEditItem: (EventGraphItem) -> Unit,
        private val onDeleteItem: (EventGraphItem) -> Unit,
        private val onOpenDetail: (EventGraphItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)

        fun bind(item: EventGraphItem): Unit {
            val isRelationshipChain: Boolean = item.itemType == EventGraphItemType.RELATION_CHAIN
            binding.tvItemType.text = item.type
            binding.tvItemTitle.text = item.title
            binding.tvItemContent.text = item.content
            binding.tvItemMeta.text = "${item.meta} · 时间：${dateFormat.format(Date(item.timestamp))}"
            binding.btnEditItem.text = if (isRelationshipChain) "详情" else "修改"
            binding.btnDeleteItem.isEnabled = !isRelationshipChain
            binding.btnDeleteItem.alpha = if (isRelationshipChain) DISABLED_BUTTON_ALPHA else ENABLED_BUTTON_ALPHA
            binding.root.setOnClickListener { onOpenDetail(item) }
            binding.btnEditItem.setOnClickListener {
                if (isRelationshipChain) onOpenDetail(item) else onEditItem(item)
            }
            binding.btnDeleteItem.setOnClickListener {
                if (!isRelationshipChain) onDeleteItem(item)
            }
        }
    }

    private companion object {
        private const val ENABLED_BUTTON_ALPHA: Float = 1.0f
        private const val DISABLED_BUTTON_ALPHA: Float = 0.38f
    }
}

class EventGraphDiffCallback : DiffUtil.ItemCallback<EventGraphItem>() {
    override fun areItemsTheSame(oldItem: EventGraphItem, newItem: EventGraphItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: EventGraphItem, newItem: EventGraphItem): Boolean {
        return oldItem == newItem
    }
}
