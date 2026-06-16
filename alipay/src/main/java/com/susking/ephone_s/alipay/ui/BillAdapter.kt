package com.susking.ephone_s.alipay.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.alipay.R
import com.susking.ephone_s.alipay.databinding.ItemBillRecordBinding
import com.susking.ephone_s.aidata.domain.alipay.BillRecord

/**
 * 账单列表适配器
 */
class BillAdapter : ListAdapter<BillRecord, BillAdapter.BillViewHolder>(BillDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BillViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    /**
     * ViewHolder
     */
    class BillViewHolder(
        private val binding: ItemBillRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(bill: BillRecord) {
            binding.apply {
                // 设置描述
                textDescription.text = bill.description
                
                // 设置类型
                textType.text = bill.getTypeDisplayText()
                
                // 设置时间
                textTime.text = bill.getFormattedTime()
                
                // 设置金额和颜色
                textAmount.text = bill.getFormattedAmount()
                
                val amountColor = if (bill.isIncome()) {
                    ContextCompat.getColor(root.context, R.color.income_green)
                } else {
                    ContextCompat.getColor(root.context, R.color.expense_red)
                }
                textAmount.setTextColor(amountColor)
            }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class BillDiffCallback : DiffUtil.ItemCallback<BillRecord>() {
        override fun areItemsTheSame(oldItem: BillRecord, newItem: BillRecord): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: BillRecord, newItem: BillRecord): Boolean {
            return oldItem == newItem
        }
    }
}