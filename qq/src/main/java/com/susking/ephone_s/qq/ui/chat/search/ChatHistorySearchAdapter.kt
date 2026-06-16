package com.susking.ephone_s.qq.ui.chat.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.ItemChatMessageSearchResultBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistorySearchAdapter(
    private val onMessageClick: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, ChatHistorySearchAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private var contact: PersonProfile? = null
    private var userProfile: UserProfile? = null

    fun updateProfiles(contact: PersonProfile?, userProfile: UserProfile?) {
        this.contact = contact
        this.userProfile = userProfile
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                onMessageClick(getItem(bindingAdapterPosition))
            }
        }

        fun bind(message: ChatMessage) {
            binding.senderNameTextView.text = getSenderName(message)
            binding.messageContentTextView.text = getMessagePreview(message)
            binding.timestampTextView.text = timeFormat.format(Date(message.timestamp))

            Glide.with(binding.avatarImageView)
                .load(getSenderAvatar(message) ?: R.drawable.ic_default_avatar)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.avatarImageView)
        }

        private fun getSenderName(message: ChatMessage): String {
            if (message.role == "user") {
                return userProfile?.nickname?.takeIf { it.isNotBlank() } ?: "我"
            }

            return message.senderName?.takeIf { it.isNotBlank() }
                ?: contact?.remarkName?.takeIf { it.isNotBlank() }
                ?: contact?.realName?.takeIf { it.isNotBlank() }
                ?: "对方"
        }

        private fun getSenderAvatar(message: ChatMessage): String? {
            return if (message.role == "user") {
                userProfile?.avatarUri
            } else {
                contact?.avatarUri
            }
        }

        private fun getMessagePreview(message: ChatMessage): String {
            if (message.isRecalled) {
                return message.recalledContent?.takeIf { it.isNotBlank() } ?: "[已撤回消息]"
            }

            return when (message.type) {
                "text", "offline_text", "system", "offline_response", "pat_message" -> message.content.orPlaceholder("[文字消息]")
                "image", "image_url", "ai_image", "naiimag" -> getImagePreview(message)
                "sticker" -> "[表情：${message.stickerName.orPlaceholder("表情")}]"
                "voice_message" -> getVoicePreview(message)
                "location_share" -> "[位置：${message.content.orPlaceholder("位置")}]"
                "transfer" -> "[转账：${formatAmount(message.amount)}]"
                "accept_transfer" -> "[已收款：${formatAmount(message.amount)}]"
                "decline_transfer" -> "[已拒收转账：${formatAmount(message.amount)}]"
                "gift" -> "[礼物：${message.giftName.orPlaceholder("礼物")}]"
                "waimai_request" -> "[代付请求：${message.productInfo.orPlaceholder("商品信息")}]"
                "waimai_order" -> "[外卖订单：${message.productInfo.orPlaceholder("商品信息")}]"
                "friend_application" -> message.content.orPlaceholder("[好友申请]")
                "shopping_access_request" -> message.content.orPlaceholder("[购物访问申请]")
                "video_call_record" -> message.content.orPlaceholder("[视频通话记录]")
                "offline_meeting_request" -> getOfflineMeetingPreview(message)
                else -> message.content.orPlaceholder("[${message.type}消息]")
            }
        }

        private fun getImagePreview(message: ChatMessage): String {
            return message.imageDescription?.takeIf { it.isNotBlank() }
                ?: message.content?.takeIf { it.isNotBlank() }?.let { "[图片：$it]" }
                ?: "[图片]"
        }

        private fun getVoicePreview(message: ChatMessage): String {
            val durationSeconds = ((message.voiceDurationMillis ?: 0L) / 1000L).coerceAtLeast(1L)
            val text = message.content?.takeIf { it.isNotBlank() }
            return if (text == null) {
                "[语音 ${durationSeconds}\"]"
            } else {
                "[语音 ${durationSeconds}\"] $text"
            }
        }

        private fun getOfflineMeetingPreview(message: ChatMessage): String {
            val location = message.offlineLocation.orPlaceholder("未指定地点")
            val reason = message.offlineReason.orPlaceholder("想见面聊聊")
            return "[线下见面：$location，$reason]"
        }

        private fun formatAmount(amount: Double?): String {
            return String.format(Locale.CHINA, "¥%.2f", amount ?: 0.0)
        }

        private fun String?.orPlaceholder(placeholder: String): String {
            return this?.takeIf { it.isNotBlank() } ?: placeholder
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
