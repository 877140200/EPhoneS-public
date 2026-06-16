package com.susking.ephone_s.tavern.ui.jinnang

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.tavern.R

/** 提示词列表项：id 用于删除，content 用于展示（句子与词语共用）。 */
data class PromptListItem(val id: Long, val content: String)

/**
 * 提示词列表适配器，句子与词语共用。
 *
 * 条目长按触发 [onLongClick] 回调（由 Fragment 弹删除确认）。
 */
class PromptItemAdapter(
    private val onLongClick: (PromptListItem) -> Unit
) : ListAdapter<PromptListItem, PromptItemAdapter.ViewHolder>(DIFF) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content: TextView = itemView.findViewById(R.id.prompt_item_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prompt, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item: PromptListItem = getItem(position)
        holder.content.text = item.content
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PromptListItem>() {
            override fun areItemsTheSame(oldItem: PromptListItem, newItem: PromptListItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PromptListItem, newItem: PromptListItem): Boolean =
                oldItem == newItem
        }
    }
}
