package com.susking.ephone_s.shopping.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.ShoppingCategory
import com.susking.ephone_s.shopping.databinding.ItemCategoryBinding

/**
 * 分类列表适配器
 */
class CategoryListAdapter(
    private val onCategoryClick: (ShoppingCategory) -> Unit,
    private val onCategoryLongClick: ((ShoppingCategory, android.view.View) -> Unit)? = null
) : ListAdapter<ShoppingCategory, CategoryListAdapter.CategoryViewHolder>(CategoryDiffCallback()) {
    
    private var selectedCategoryId: Long = -1L
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding, onCategoryClick, onCategoryLongClick)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        val isSelected = category.id == selectedCategoryId
        holder.bind(category, isSelected)
    }
    
    /**
     * 设置选中的分类
     */
    fun setSelectedCategory(categoryId: Long) {
        val oldSelectedId = selectedCategoryId
        selectedCategoryId = categoryId
        
        // 刷新旧的和新的选中项
        currentList.forEachIndexed { index, category ->
            if (category.id == oldSelectedId || category.id == categoryId) {
                notifyItemChanged(index)
            }
        }
    }
    
    /**
     * ViewHolder
     */
    class CategoryViewHolder(
        private val binding: ItemCategoryBinding,
        private val onCategoryClick: (ShoppingCategory) -> Unit,
        private val onCategoryLongClick: ((ShoppingCategory, android.view.View) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(category: ShoppingCategory, isSelected: Boolean) {
            binding.apply {
                // 分类名称
                chipCategory.text = category.name
                // 选中状态（Chip使用isChecked而非isSelected）
                chipCategory.isChecked = isSelected
                // 点击事件（设置在Chip上，避免Chip自身的点击切换逻辑）
                chipCategory.setOnClickListener {
                    onCategoryClick(category)
                }
                // 长按事件（排除"全部"分类，id为-1L）
                chipCategory.setOnLongClickListener { view ->
                    if (category.id != -1L && onCategoryLongClick != null) {
                        onCategoryLongClick.invoke(category, view)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class CategoryDiffCallback : DiffUtil.ItemCallback<ShoppingCategory>() {
        override fun areItemsTheSame(
            oldItem: ShoppingCategory,
            newItem: ShoppingCategory
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: ShoppingCategory,
            newItem: ShoppingCategory
        ): Boolean {
            return oldItem == newItem
        }
    }
}