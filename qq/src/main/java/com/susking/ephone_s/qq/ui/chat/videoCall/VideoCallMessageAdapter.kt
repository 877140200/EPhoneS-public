package com.susking.ephone_s.qq.ui.chat.videoCall

import android.view.LayoutInflater
import com.susking.ephone_s.qq.util.MarkdownRenderer
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.aidata.domain.model.ChatMessage

class VideoCallMessageAdapter(
    private val onEditClickListener: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, VideoCallMessageAdapter.MessageViewHolder>(ChatMessageDiffCallback()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(message: ChatMessage)
    }

    inner class SentMessageViewHolder(view: View) : MessageViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.sentMessageTextView)
        private val editButton: ImageButton = view.findViewById(R.id.editButton)

        override fun bind(message: ChatMessage) {
            // 使用 Markwon 渲染 Markdown 和 HTML 格式
            MarkdownRenderer.renderMarkdown(messageText, message.content)
            editButton.setOnClickListener { onEditClickListener(message) }
        }
    }

    inner class ReceivedMessageViewHolder(view: View) : MessageViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.receivedMessageTextView)
        private val editButton: ImageButton = view.findViewById(R.id.editButton)

        override fun bind(message: ChatMessage) {
            // 使用 Markwon 渲染 Markdown 和 HTML 格式
            MarkdownRenderer.renderMarkdown(messageText, message.content)
            editButton.setOnClickListener { onEditClickListener(message) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.role == "user") {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = layoutInflater.inflate(R.layout.item_video_call_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = layoutInflater.inflate(R.layout.item_video_call_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }
}