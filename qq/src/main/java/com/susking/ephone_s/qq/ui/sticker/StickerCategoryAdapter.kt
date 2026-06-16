package com.susking.ephone_s.qq.ui.sticker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.data.local.entity.StickerCategoryEntity
import com.susking.ephone_s.qq.databinding.ItemStickerCategoryTabBinding

class StickerCategoryAdapter(
    private val onClick: (StickerCategoryEntity) -> Unit
) : ListAdapter<StickerCategoryEntity, StickerCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemStickerCategoryTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CategoryViewHolder(
        private val binding: ItemStickerCategoryTabBinding,
        private val onClick: (StickerCategoryEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentCategory: StickerCategoryEntity? = null

        init {
            binding.root.setOnClickListener {
                currentCategory?.let {
                    onClick(it)
                }
            }
        }

        fun bind(category: StickerCategoryEntity) {
            currentCategory = category
            binding.categoryName.text = category.name
        }
    }
}

object CategoryDiffCallback : DiffUtil.ItemCallback<StickerCategoryEntity>() {
    override fun areItemsTheSame(oldItem: StickerCategoryEntity, newItem: StickerCategoryEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StickerCategoryEntity, newItem: StickerCategoryEntity): Boolean {
        return oldItem == newItem
    }
}