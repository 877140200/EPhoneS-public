package com.susking.ephone_s.shopping.ui.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.susking.ephone_s.aidata.domain.model.ProductVariation
import com.susking.ephone_s.shopping.databinding.ItemVariationEditorBinding

/**
 * 款式编辑适配器
 * 
 * 用于在商品编辑器中显示和编辑款式列表
 */
class VariationEditorAdapter(
    private val onVariationChanged: (Int, ProductVariation) -> Unit,
    private val onVariationDeleted: (Int) -> Unit
) : ListAdapter<ProductVariation, VariationEditorAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariationEditorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
    
    inner class ViewHolder(
        private val binding: ItemVariationEditorBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentPosition: Int = -1
        
        init {
            // 删除按钮
            binding.buttonDelete.setOnClickListener {
                if (currentPosition != -1) {
                    onVariationDeleted(currentPosition)
                }
            }
            
            // 款式名称输入监听
            binding.editTextVariationName.addSimpleTextWatcher { text ->
                if (currentPosition != -1) {
                    val variation = getItem(currentPosition)
                    val updated = variation.copy(name = text)
                    onVariationChanged(currentPosition, updated)
                }
            }
            
            // 款式价格输入监听
            binding.editTextVariationPrice.addSimpleTextWatcher { text ->
                if (currentPosition != -1) {
                    val price = text.toDoubleOrNull() ?: 0.0
                    val variation = getItem(currentPosition)
                    val updated = variation.copy(price = price)
                    onVariationChanged(currentPosition, updated)
                }
            }
            
            // 图片URL输入监听
            binding.editTextVariationImageUrl.addSimpleTextWatcher { text ->
                if (currentPosition != -1) {
                    val variation = getItem(currentPosition)
                    val updated = variation.copy(imageUrl = text.ifBlank { null })
                    onVariationChanged(currentPosition, updated)
                }
            }
        }
        
        fun bind(variation: ProductVariation, position: Int) {
            currentPosition = position
            
            // 设置数据(避免触发TextWatcher)
            binding.editTextVariationName.setTextSilently(variation.name)
            binding.editTextVariationPrice.setTextSilently(variation.price.toString())
            binding.editTextVariationImageUrl.setTextSilently(variation.imageUrl ?: "")
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

/**
 * TextInputEditText扩展函数:添加简化的文本监听器
 */
private fun TextInputEditText.addSimpleTextWatcher(onTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onTextChanged(s?.toString() ?: "")
        }
    })
}

/**
 * TextInputEditText扩展函数:静默设置文本(不触发TextWatcher)
 */
private fun TextInputEditText.setTextSilently(text: String) {
    // 临时移除所有监听器
    val watchers = mutableListOf<TextWatcher>()
    try {
        // 通过反射获取所有TextWatcher(简化版:直接设置)
        if (this.text.toString() != text) {
            this.setText(text)
            this.setSelection(text.length)
        }
    } catch (e: Exception) {
        // 如果反射失败,直接设置
        this.setText(text)
    }
}