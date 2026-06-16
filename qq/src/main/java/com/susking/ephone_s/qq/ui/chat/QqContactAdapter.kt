package com.susking.ephone_s.qq.ui.chat

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.ItemQqChatBinding
import com.susking.ephone_s.qq.domain.model.ContactWithLatestMessage

class QqContactAdapter(
    private val onItemClicked: (PersonProfile) -> Unit
) : ListAdapter<ContactWithLatestMessage, QqContactAdapter.ContactViewHolder>(ContactDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemQqChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contactWithMessage = getItem(position)
        holder.bind(contactWithMessage)
    }

    class ContactViewHolder(
        private val binding: ItemQqChatBinding,
        private val onItemClicked: (PersonProfile) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contactWithMessage: ContactWithLatestMessage) {
            val contact = contactWithMessage.profile

            binding.textViewCharacterName.text = contact.remarkName
            // 根据联系人是否置顶来更新 pin_button 的文本
            binding.pinButton.text = if (contact.isPinned) "取消置顶" else "置顶"

            val latestMessageText = when (contactWithMessage.latestMessageType) {
                "naiimag", "ai_image", "image" -> "[图片]"
                "sticker" -> "[表情]"
                else -> contactWithMessage.latestMessage ?: "在线"
            }

            // 隐私模式：如果开启，将消息内容替换为马赛克
            val displayText = if (contact.privacyModeEnabled && latestMessageText != "在线") {
                applyMosaicEffect(latestMessageText)
            } else {
                latestMessageText
            }
            binding.textViewCharacterStatus.text = displayText
            binding.textViewLastMessageTime.text = contactWithMessage.latestMessageTime

            if (!contact.avatarUri.isNullOrBlank()) {
                Glide.with(binding.root.context)
                    .load(contact.avatarUri)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(binding.imageViewAvatar)
            } else {
                binding.imageViewAvatar.setImageResource(R.drawable.ic_default_avatar)
            }

            binding.textViewNewMessageCount.visibility = if (contact.unreadMessageCount > 0) {
                binding.textViewNewMessageCount.text = contact.unreadMessageCount.toString()
                View.VISIBLE
            } else {
                View.GONE
            }

            val context = binding.root.context
            val backgroundColorAttr = if (contact.isPinned) com.google.android.material.R.attr.colorSurfaceVariant else com.google.android.material.R.attr.colorSurface
            val typedValue = TypedValue()
            context.theme.resolveAttribute(backgroundColorAttr, typedValue, true)
            binding.contactItemLayout.setBackgroundColor(typedValue.data)

            binding.contactItemLayout.setOnClickListener {
                onItemClicked(contact)
            }
        }

        /**
         * 应用马赛克效果
         * 将文本中的字符替换为█符号
         */
        private fun applyMosaicEffect(text: String): String {
            return text.map { char ->
                when {
                    char.isWhitespace() -> char  // 保留空格
                    char in "[]()" -> char  // 保留特殊标记符号
                    else -> '█'  // 其他字符替换为马赛克
                }
            }.joinToString("")
        }
    }

    object ContactDiffCallback : DiffUtil.ItemCallback<ContactWithLatestMessage>() {
        override fun areItemsTheSame(oldItem: ContactWithLatestMessage, newItem: ContactWithLatestMessage): Boolean {
            return oldItem.profile.id == newItem.profile.id
        }

        override fun areContentsTheSame(oldItem: ContactWithLatestMessage, newItem: ContactWithLatestMessage): Boolean {
            return oldItem == newItem
        }
    }
}