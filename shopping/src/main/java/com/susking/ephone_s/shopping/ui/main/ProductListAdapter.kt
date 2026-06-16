package com.susking.ephone_s.shopping.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.aidata.domain.model.ShoppingProduct
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.ItemProductBinding
import com.susking.ephone_s.shopping.databinding.ItemProductAddBinding

/**
 * 商品列表适配器
 * 支持在列表末尾显示"添加商品"卡片
 */
class ProductListAdapter(
    private val onProductClick: (ShoppingProduct) -> Unit,
    private val onEditClick: ((ShoppingProduct) -> Unit)? = null,
    private val onDeleteClick: ((ShoppingProduct) -> Unit)? = null,
    private val onAddClick: (() -> Unit)? = null
) : ListAdapter<ShoppingProduct, RecyclerView.ViewHolder>(ProductDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_PRODUCT = 0
        private const val VIEW_TYPE_ADD = 1
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position == itemCount - 1 && onAddClick != null) {
            VIEW_TYPE_ADD
        } else {
            VIEW_TYPE_PRODUCT
        }
    }
    
    override fun getItemCount(): Int {
        val baseCount = super.getItemCount()
        return if (onAddClick != null) baseCount + 1 else baseCount
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ADD -> {
                val binding = ItemProductAddBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                AddViewHolder(binding, onAddClick)
            }
            else -> {
                val binding = ItemProductBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ProductViewHolder(binding, onProductClick, onEditClick, onDeleteClick)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProductViewHolder -> holder.bind(getItem(position))
            is AddViewHolder -> {
                // 添加卡片不需要绑定数据
            }
        }
    }
    
    /**
     * ViewHolder
     */
    class ProductViewHolder(
        private val binding: ItemProductBinding,
        private val onProductClick: (ShoppingProduct) -> Unit,
        private val onEditClick: ((ShoppingProduct) -> Unit)?,
        private val onDeleteClick: ((ShoppingProduct) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(product: ShoppingProduct) {
            binding.apply {
                // 商品图片
                imageViewProduct.load(product.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image_placeholder)
                    error(R.drawable.ic_image_error)
                }
                // 商品名称
                textViewProductName.text = product.name
                // 商品价格
                textViewPrice.text = String.format("¥%.2f", product.price)
                // 商品描述
                textViewDescription.text = product.description
                // 款式数量提示
                if (product.variations.isNotEmpty()) {
                    textViewVariations.text = "${product.variations.size}款可选"
                    textViewVariations.visibility = View.VISIBLE
                } else {
                    textViewVariations.visibility = View.GONE
                }
                // 初始隐藏覆盖层
                overlayActions.visibility = View.GONE
                // 点击事件
                root.setOnClickListener {
                    if (overlayActions.visibility == View.VISIBLE) {
                        overlayActions.visibility = View.GONE
                    } else {
                        onProductClick(product)
                    }
                }
                // 长按显示操作按钮
                root.setOnLongClickListener {
                    overlayActions.visibility = View.VISIBLE
                    true
                }
                // 编辑按钮
                buttonEdit.setOnClickListener {
                    overlayActions.visibility = View.GONE
                    onEditClick?.invoke(product)
                }
                // 删除按钮
                buttonDelete.setOnClickListener {
                    overlayActions.visibility = View.GONE
                    onDeleteClick?.invoke(product)
                }
            }
        }
    }
    
    /**
     * 添加商品ViewHolder
     */
    class AddViewHolder(
        private val binding: ItemProductAddBinding,
        private val onAddClick: (() -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                onAddClick?.invoke()
            }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class ProductDiffCallback : DiffUtil.ItemCallback<ShoppingProduct>() {
        override fun areItemsTheSame(
            oldItem: ShoppingProduct,
            newItem: ShoppingProduct
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: ShoppingProduct,
            newItem: ShoppingProduct
        ): Boolean {
            return oldItem == newItem
        }
    }
}