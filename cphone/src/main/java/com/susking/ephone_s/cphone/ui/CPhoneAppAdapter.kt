package com.susking.ephone_s.cphone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.databinding.ItemCphoneAppBinding
import com.susking.ephone_s.cphone.domain.model.CPhoneApp

/**
 * CPhone App图标列表的Adapter
 */
class CPhoneAppAdapter(
    private val onItemClick: (CPhoneApp) -> Unit
) : ListAdapter<CPhoneApp, CPhoneAppAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCphoneAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemCphoneAppBinding,
        private val onItemClick: (CPhoneApp) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: CPhoneApp) {
            binding.apply {
                // 设置App图标
                ivAppIcon.setImageResource(app.iconResId)

                // 设置App名称
                tvAppName.text = app.name

                // 设置点击事件
                root.setOnClickListener {
                    onItemClick(app)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CPhoneApp>() {
        override fun areItemsTheSame(oldItem: CPhoneApp, newItem: CPhoneApp): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: CPhoneApp, newItem: CPhoneApp): Boolean {
            return oldItem == newItem
        }
    }
}