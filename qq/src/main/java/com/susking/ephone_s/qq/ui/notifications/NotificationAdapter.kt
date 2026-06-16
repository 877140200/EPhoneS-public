package com.susking.ephone_s.qq.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.domain.model.Notification
import com.susking.ephone_s.aidata.domain.model.NotificationType
import com.susking.ephone_s.qq.databinding.ItemQqNotificationBinding

class NotificationAdapter : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemQqNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(private val binding: ItemQqNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            binding.nicknameTextView.text = notification.senderNickname
            binding.timestampTextView.text = notification.timestamp
            binding.originalFeedContentTextView.text = notification.originalFeedContent
            binding.replyTextView.text = "回复${notification.senderNickname}:"

            when (notification.type) {
                NotificationType.LIKE -> {
                    binding.notificationIcon.setImageResource(R.drawable.ic_like_filled)
                    binding.notificationText.text = "赞了我"
                }
                NotificationType.COMMENT -> {
                    binding.notificationIcon.setImageResource(R.drawable.ic_comment) // You'll need to create this drawable
                    binding.notificationText.text = notification.content
                }
            }

            Glide.with(binding.root.context)
                .load(notification.senderAvatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .into(binding.avatarImageView)
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}