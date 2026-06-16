package com.susking.ephone_s.shopping.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.aidata.domain.model.OrderProduct
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.ItemOrderProductBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 订单商品适配器
 * 
 * 用于在订单详情中显示商品列表
 */
class OrderProductAdapter : ListAdapter<OrderProduct, OrderProductAdapter.ViewHolder>(DiffCallback()) {
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemOrderProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(product: OrderProduct) {
            binding.textViewProductName.text = product.name
            binding.textViewPrice.text = currencyFormat.format(product.price)
            binding.textViewQuantity.text = "x${product.quantity}"
            
            // 款式名称
            if (product.variationName != null) {
                binding.textViewVariation.text = "款式: ${product.variationName}"
                binding.textViewVariation.visibility = View.VISIBLE
            } else {
                binding.textViewVariation.visibility = View.GONE
            }

            // 商品图片
            binding.imageViewProduct.load(product.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
            }

        }
    }
    
    /**
     * DiffUtil回调
     */
    private class DiffCallback : DiffUtil.ItemCallback<OrderProduct>() {
        override fun areItemsTheSame(
            oldItem: OrderProduct,
            newItem: OrderProduct
        ): Boolean {
            return oldItem.name == newItem.name && 
                   oldItem.variationName == newItem.variationName
        }
        
        override fun areContentsTheSame(
            oldItem: OrderProduct,
            newItem: OrderProduct
        ): Boolean {
            return oldItem == newItem
        }
    }
}