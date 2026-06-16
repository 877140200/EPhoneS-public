package com.susking.ephone_s.shopping.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.aidata.domain.model.CartItem
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.ItemCartBinding

/**
 * 购物车商品适配器
 */
class ShoppingCartAdapter(
    private val onQuantityChanged: (CartItem, Int) -> Unit,
    private val onRemoveClick: (CartItem) -> Unit
) : ListAdapter<CartItem, ShoppingCartAdapter.CartItemViewHolder>(CartItemDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartItemViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CartItemViewHolder(binding, onQuantityChanged, onRemoveClick)
    }
    
    override fun onBindViewHolder(holder: CartItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    /**
     * ViewHolder
     */
    class CartItemViewHolder(
        private val binding: ItemCartBinding,
        private val onQuantityChanged: (CartItem, Int) -> Unit,
        private val onRemoveClick: (CartItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(cartItem: CartItem) {
            binding.apply {
                // 商品图片
                imageViewProduct.load(cartItem.product.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image_placeholder)
                    error(R.drawable.ic_image_error)
                }
                
                // 商品名称
                textViewProductName.text = cartItem.product.name
                
                // 款式信息
                val variationIndex = cartItem.selectedVariationIndex
                if (variationIndex != null &&
                    variationIndex < cartItem.product.variations.size) {
                    val variation = cartItem.product.variations[variationIndex]
                    textViewVariation.text = variation.name
                    textViewVariation.visibility = android.view.View.VISIBLE
                } else {
                    textViewVariation.visibility = android.view.View.GONE
                }
                
                // 价格
                textViewPrice.text = String.format("¥%.2f", cartItem.getUnitPrice())
                
                // 小计
                textViewSubtotal.text = String.format("¥%.2f", cartItem.getSubtotal())
                
                // 数量
                textViewQuantity.text = cartItem.quantity.toString()
                
                // 减少数量按钮
                buttonDecrease.setOnClickListener {
                    val newQuantity = cartItem.quantity - 1
                    if (newQuantity > 0) {
                        onQuantityChanged(cartItem, newQuantity)
                    }
                }
                
                // 增加数量按钮
                buttonIncrease.setOnClickListener {
                    val newQuantity = cartItem.quantity + 1
                    onQuantityChanged(cartItem, newQuantity)
                }
                
                // 删除按钮
                buttonRemove.setOnClickListener {
                    onRemoveClick(cartItem)
                }
            }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class CartItemDiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem == newItem
        }
    }
}