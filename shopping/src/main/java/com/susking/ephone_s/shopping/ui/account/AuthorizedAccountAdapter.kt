package com.susking.ephone_s.shopping.ui.account

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.aidata.domain.model.AuthorizedAccount
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.ItemAuthorizedAccountBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 已授权账号列表适配器
 */
class AuthorizedAccountAdapter(
    private val onAccountClick: (AuthorizedAccount) -> Unit,
    private val onDeleteClick: (AuthorizedAccount) -> Unit,
    private val contactsMap: Map<String, PersonProfile>
) : ListAdapter<AuthorizedAccount, AuthorizedAccountAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAuthorizedAccountBinding.inflate(
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
        private val binding: ItemAuthorizedAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(account: AuthorizedAccount) {
            val contact = contactsMap[account.contactId]
            
            // 显示联系人名称
            binding.textViewName.text = contact?.remarkName ?: account.contactId
            
            // 显示授权时间
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.textViewTime.text = "授权于 ${dateFormat.format(Date(account.authorizedTimestamp))}"
            
            // 加载头像
            if (!contact?.avatarUri.isNullOrEmpty()) {
                Glide.with(binding.imageViewAvatar.context)
                    .load(contact?.avatarUri)
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .circleCrop()
                    .into(binding.imageViewAvatar)
            } else {
                binding.imageViewAvatar.setImageResource(R.drawable.ic_person_24)
            }
            
            // 点击事件
            binding.root.setOnClickListener {
                onAccountClick(account)
            }
            
            // 删除按钮
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(account)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<AuthorizedAccount>() {
        override fun areItemsTheSame(oldItem: AuthorizedAccount, newItem: AuthorizedAccount): Boolean {
            return oldItem.contactId == newItem.contactId
        }
        
        override fun areContentsTheSame(oldItem: AuthorizedAccount, newItem: AuthorizedAccount): Boolean {
            return oldItem == newItem
        }
    }
}