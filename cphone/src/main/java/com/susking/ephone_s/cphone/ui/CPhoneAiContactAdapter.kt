package com.susking.ephone_s.cphone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.core.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneAiContactBinding

/**
 * CPhone AI联系人列表的Adapter
 */
class CPhoneAiContactAdapter(
    private val onItemClick: (PersonProfile) -> Unit
) : ListAdapter<PersonProfile, CPhoneAiContactAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCphoneAiContactBinding.inflate(
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
        private val binding: ItemCphoneAiContactBinding,
        private val onItemClick: (PersonProfile) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: PersonProfile) {
            binding.apply {
                // 设置头像
                ivAvatar.load(contact.avatarUri) {
                    placeholder(R.drawable.ic_default_avatar)
                    error(R.drawable.ic_default_avatar)
                }

                // 设置昵称
                tvNickname.text = contact.remarkName

                // 设置点击事件
                root.setOnClickListener {
                    onItemClick(contact)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PersonProfile>() {
        override fun areItemsTheSame(oldItem: PersonProfile, newItem: PersonProfile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PersonProfile, newItem: PersonProfile): Boolean {
            return oldItem == newItem
        }
    }
}