package com.susking.ephone_s.qq.ui.backpack

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.data.model.BackpackItem
import com.susking.ephone_s.qq.databinding.ItemBackpackBinding

/**
 * 背包物品适配器
 * 
 * 用于显示背包中的物品列表
 */
class BackpackItemAdapter(
    private val onDiscardClick: (BackpackItem) -> Unit,
    private val onGiftClick: (BackpackItem) -> Unit
) : ListAdapter<BackpackItem, BackpackItemAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBackpackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onDiscardClick, onGiftClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemBackpackBinding,
        private val onDiscardClick: (BackpackItem) -> Unit,
        private val onGiftClick: (BackpackItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: BackpackItem) {
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
            
            // 获得时间
            binding.textViewTime.text = "获得时间: ${item.getFormattedObtainedTime()}"
            
            // 丢弃按钮
            binding.buttonDiscard.setOnClickListener {
                onDiscardClick(item)
            }
            
            // 赠送按钮
            binding.buttonGift.isEnabled = true
            binding.buttonGift.setOnClickListener {
                onGiftClick(item)
            }
            
            // TODO: 卖掉按钮暂时禁用
            binding.buttonSell.isEnabled = false
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