package com.susking.ephone_s.qq.ui.chat

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
 * 礼物选择适配器
 * 
 * 用于在礼物选择对话框中显示可赠送的物品
 */
class GiftSelectionAdapter(
    private val onItemClick: (BackpackItem) -> Unit
) : ListAdapter<BackpackItem, GiftSelectionAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBackpackBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemBackpackBinding,
        private val onItemClick: (BackpackItem) -> Unit
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
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            binding.textViewTime.text = "获得时间: ${sdf.format(java.util.Date(item.obtainedTime))}"
            
            // 隐藏操作按钮布局（在礼物选择时不需要显示）
            binding.layoutActions.visibility = android.view.View.GONE
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onItemClick(item)
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