package com.susking.ephone_s.qq.ui.backpack

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.data.model.BackpackItem
import com.susking.ephone_s.qq.databinding.ItemBackpackHistoryBinding

/**
 * 背包历史记录适配器
 * 
 * 用于显示已丢弃/赠送/卖出的物品历史记录
 */
class BackpackHistoryAdapter(
    private val onDeleteClick: (BackpackItem) -> Unit
) : ListAdapter<BackpackItem, BackpackHistoryAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBackpackHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemBackpackHistoryBinding,
        private val onDeleteClick: (BackpackItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: BackpackItem) {
            val context = binding.root.context
            
            // 商品图片
            binding.imageViewProduct.load(item.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
            }
            
            // 商品名称
            binding.textViewProductName.text = item.productName
            
            // 物品价值
            binding.textViewPrice.text = item.getFormattedPrice()
            
            // 来源标签
            binding.chipSource.text = item.source
            
            // 操作类型标签
            binding.chipOperationType.text = item.getOperationTypeText()
            
            // 根据操作类型设置不同颜色
            val chipColor = when (item.operationType) {
                "discarded" -> ContextCompat.getColor(context, R.color.chip_discarded)
                "gifted" -> ContextCompat.getColor(context, R.color.chip_gifted)
                "sold" -> ContextCompat.getColor(context, R.color.chip_sold)
                else -> ContextCompat.getColor(context, R.color.chip_default)
            }
            binding.chipOperationType.setChipBackgroundColorResource(
                when (item.operationType) {
                    "discarded" -> R.color.chip_discarded
                    "gifted" -> R.color.chip_gifted
                    "sold" -> R.color.chip_sold
                    else -> R.color.chip_default
                }
            )
            
            // 获得时间
            binding.textViewObtainedTime.text = "获得时间: ${item.getFormattedObtainedTime()}"
            
            // 操作时间
            val operationLabel = when (item.operationType) {
                "discarded" -> "丢弃时间"
                "gifted" -> "赠送时间"
                "sold" -> "卖出时间"
                else -> "操作时间"
            }
            binding.textViewOperationTime.text = "$operationLabel: ${item.getFormattedOperationTime()}"
            
            // 赠送目标(仅在operationType=gifted时显示)
            if (item.operationType == "gifted" && !item.giftRecipient.isNullOrBlank()) {
                binding.textViewGiftRecipient.visibility = android.view.View.VISIBLE
                binding.textViewGiftRecipient.text = "赠送给: ${item.giftRecipient}"
            } else {
                binding.textViewGiftRecipient.visibility = android.view.View.GONE
            }
            
            // 删除按钮
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<BackpackItem>() {
        override fun areItemsTheSame(
            oldItem: BackpackItem,
            newItem: BackpackItem
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: BackpackItem,
            newItem: BackpackItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}