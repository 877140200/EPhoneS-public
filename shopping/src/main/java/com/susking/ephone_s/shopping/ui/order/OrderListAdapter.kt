package com.susking.ephone_s.shopping.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.aidata.domain.model.ShoppingOrder
import com.susking.ephone_s.shopping.databinding.ItemOrderBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 订单列表适配器
 * 
 * 用于显示订单列表
 */
class OrderListAdapter(
    private val onOrderClick: (ShoppingOrder) -> Unit,
    private val onDeleteClick: (ShoppingOrder) -> Unit
) : ListAdapter<ShoppingOrder, OrderListAdapter.ViewHolder>(DiffCallback()) {
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderBinding.inflate(
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
        private val binding: ItemOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val productAdapter = OrderProductAdapter()
        
        init {
            binding.recyclerViewProducts.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = productAdapter
            }
            
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onOrderClick(getItem(position))
                }
            }
            
            binding.buttonDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }
        
        fun bind(order: ShoppingOrder) {
            // 订单ID和日期
            binding.textViewOrderId.text = "订单 #${order.id}"
            binding.textViewOrderDate.text = dateFormat.format(Date(order.timestamp))
            
            // 收礼人信息
            binding.textViewRecipient.text = order.recipient.name
            binding.textViewRecipientPhone.text = maskPhone(order.recipient.phone)
            
            // 商品列表
            productAdapter.submitList(order.products)
            
            // 订单备注
            if (!order.note.isNullOrBlank()) {
                binding.textViewNote.text = "备注: ${order.note}"
                binding.textViewNote.visibility = View.VISIBLE
            } else {
                binding.textViewNote.visibility = View.GONE
            }
            
            // 总金额
            binding.textViewTotalAmount.text = currencyFormat.format(order.totalAmount)
        }
        
        /**
         * 脱敏手机号
         */
        private fun maskPhone(phone: String): String {
            return if (phone.length == 11) {
                phone.replaceRange(3, 7, "****")
            } else {
                phone
            }
        }
    }
    
    /**
     * DiffUtil回调
     */
    private class DiffCallback : DiffUtil.ItemCallback<ShoppingOrder>() {
        override fun areItemsTheSame(
            oldItem: ShoppingOrder,
            newItem: ShoppingOrder
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: ShoppingOrder,
            newItem: ShoppingOrder
        ): Boolean {
            return oldItem == newItem
        }
    }
}