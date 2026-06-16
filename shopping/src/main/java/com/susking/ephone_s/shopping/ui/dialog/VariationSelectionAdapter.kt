package com.susking.ephone_s.shopping.ui.dialog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.shopping.databinding.ItemVariationSelectionBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 款式选择适配器
 * 
 * 用于在对话框中显示可选择的款式列表
 */
class VariationSelectionAdapter(
    private val onVariationClick: (ProductVariation) -> Unit
) : ListAdapter<ProductVariation, VariationSelectionAdapter.ViewHolder>(DiffCallback()) {
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariationSelectionBinding.inflate(
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
        private val binding: ItemVariationSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onVariationClick(getItem(position))
                }
            }
        }
        
        fun bind(variation: ProductVariation) {
            binding.textViewVariationName.text = variation.name
            binding.textViewVariationPrice.text = currencyFormat.format(variation.price)
            
            // TODO: 加载款式图片(如果有)
            // if (variation.imageUrl != null) {
            //     Glide.with(binding.imageViewVariation)
            //         .load(variation.imageUrl)
            //         .placeholder(R.drawable.ic_image_placeholder)
            //         .into(binding.imageViewVariation)
            // }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class DiffCallback : DiffUtil.ItemCallback<ProductVariation>() {
        override fun areItemsTheSame(
            oldItem: ProductVariation,
            newItem: ProductVariation
        ): Boolean {
            return oldItem.name == newItem.name
        }
        
        override fun areContentsTheSame(
            oldItem: ProductVariation,
            newItem: ProductVariation
        ): Boolean {
            return oldItem == newItem
        }
    }
}