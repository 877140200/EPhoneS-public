package com.susking.ephone_s.cphone.ui.taobao

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneTaobaoOrderBinding
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone淘宝订单列表Adapter
 */
class CPhoneTaobaoAdapter(
    private val onOrderClick: (TaobaoPurchase) -> Unit,
    private val onOrderEdit: (TaobaoPurchase) -> Unit,
    private val onOrderDelete: (TaobaoPurchase) -> Unit
) : ListAdapter<TaobaoPurchase, CPhoneTaobaoAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemCphoneTaobaoOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding, onOrderClick, onOrderEdit, onOrderDelete)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder
     */
    class OrderViewHolder(
        private val binding: ItemCphoneTaobaoOrderBinding,
        private val onOrderClick: (TaobaoPurchase) -> Unit,
        private val onOrderEdit: (TaobaoPurchase) -> Unit,
        private val onOrderDelete: (TaobaoPurchase) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(purchase: TaobaoPurchase) {
            binding.apply {
                // 设置商品名称
                tvProductName.text = purchase.itemName
                
                // 设置价格
                tvPrice.text = "¥${String.format("%.2f", purchase.price)}"
                
                // 设置数量(默认为1)
                tvQuantity.text = "x1"
                
                // 设置订单状态
                tvStatus.text = purchase.status
                
                // 设置订单时间 - TaobaoPurchase没有timestamp字段，显示固定文本
                tvOrderTime.text = "最近订单"
                
                // 设置商品图片
                if (!purchase.imageUrl.isNullOrEmpty()) {
                    // 如果有图片URL，使用Coil加载图片
                    ivProduct.load(File(purchase.imageUrl)) {
                        placeholder(R.drawable.bg_image_placeholder)
                        error(R.drawable.bg_image_placeholder)
                        crossfade(true)
                    }
                } else {
                    // 如果没有图片URL，显示占位符
                    ivProduct.setImageResource(R.drawable.bg_image_placeholder)
                }
                
                // 设置点击事件,显示购买理由对话框
                root.setOnClickListener {
                    showReasonDialog(purchase)
                }
                
                // 设置长按事件显示上下文菜单
                root.setOnLongClickListener {
                    showContextMenu(purchase)
                    true
                }
            }
        }
        
        /**
         * 显示购买理由对话框
         */
        private fun showReasonDialog(purchase: TaobaoPurchase) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("购买理由")
                .setMessage(purchase.reason)
                .setPositiveButton("确定", null)
                .show()
        }
        
        /**
         * 显示上下文菜单
         */
        private fun showContextMenu(purchase: TaobaoPurchase) {
            val popup = PopupMenu(binding.root.context, binding.root)
            popup.menuInflater.inflate(R.menu.menu_pop, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onOrderEdit(purchase)
                        true
                    }
                    R.id.action_delete -> {
                        onOrderDelete(purchase)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }

        /**
         * 格式化时间戳
         */
        private fun formatTimestamp(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    /**
     * DiffUtil回调
     */
    private class OrderDiffCallback : DiffUtil.ItemCallback<TaobaoPurchase>() {
        override fun areItemsTheSame(oldItem: TaobaoPurchase, newItem: TaobaoPurchase): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TaobaoPurchase, newItem: TaobaoPurchase): Boolean {
            return oldItem == newItem
        }
    }
}