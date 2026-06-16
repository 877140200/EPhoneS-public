package com.susking.ephone_s.qq.ui.qzone

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.ItemQqFeedBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface FeedItemListener {
    fun onDeleteClicked(feedId: Int)
    fun onFavoriteClicked(feedId: Int)
    fun onLikeClicked(feedId: Int)
    fun onCommentClicked(feedId: Int)
    fun onShareClicked(feedId: Int)
    fun onCommentDeleteClicked(feedId: Int, commentId: Long)
    fun onCommentEditClicked(feedId: Int, commentId: Long)
    fun onEditClicked(feedId: Int)
    fun onImageClicked(feedId: Int, imagePosition: Int)
}

class QqFeedsAdapter(
    private val listener: FeedItemListener
) : ListAdapter<FeedItemUiModel, QqFeedsAdapter.FeedViewHolder>(FeedUiModelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemQqFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeedViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FeedViewHolder(
        private val binding: ItemQqFeedBinding,
        private val listener: FeedItemListener
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = itemView.context
        private var currentUiModel: FeedItemUiModel? = null

        init {
            binding.moreButton.setOnClickListener { view ->
                currentUiModel?.let { showPopupMenu(view, it.feed.id) }
            }
            binding.likeButton.setOnClickListener {
                currentUiModel?.let { listener.onLikeClicked(it.feed.id) }
            }
            binding.commentButton.setOnClickListener {
                currentUiModel?.let { listener.onCommentClicked(it.feed.id) }
            }
            binding.repostButton.setOnClickListener {
                currentUiModel?.let { listener.onShareClicked(it.feed.id) }
            }
        }

        private fun showPopupMenu(view: View, feedId: Int) {
            val popup = PopupMenu(context, view)
            popup.inflate(com.susking.ephone_s.core.R.menu.menu_feed_item)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    com.susking.ephone_s.core.R.id.action_delete_feed -> {
                        listener.onDeleteClicked(feedId)
                        true
                    }
                    com.susking.ephone_s.core.R.id.action_favorite_feed -> {
                        listener.onFavoriteClicked(feedId)
                        true
                    }
                    com.susking.ephone_s.core.R.id.action_edit_feed -> {
                       listener.onEditClicked(feedId)
                       true
                   }
                    else -> false
                }
            }
            popup.show()
        }

        private fun showCommentOptions(view: View, feedId: Int, commentId: Long) {
            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "编辑")
            popup.menu.add(0, 2, 0, "删除")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        listener.onCommentEditClicked(feedId, commentId)
                        true
                    }
                    2 -> {
                        listener.onCommentDeleteClicked(feedId, commentId)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        fun bind(uiModel: FeedItemUiModel) {
            currentUiModel = uiModel
            val feed = uiModel.feed

            binding.authorName.text = feed.authorName
            binding.feedContent.text = feed.content
            binding.feedTimestamp.text = formatTimestamp(feed.timestamp)

            Glide.with(context)
                .load(uiModel.authorAvatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.authorAvatar)

            // 显示图片(如果有)
            if (feed.imageUrls.isNotEmpty()) {
                binding.feedImage.visibility = View.VISIBLE
                // 只显示第一张图片,如果需要显示多图可以扩展为网格布局
                val firstImageUrl = feed.imageUrls.first()
                Glide.with(context)
                    .load(firstImageUrl)
                    .placeholder(com.susking.ephone_s.core.R.drawable.bg_image_placeholder)
                    .error(com.susking.ephone_s.core.R.drawable.ic_image_load_failed)
                    .into(binding.feedImage)
                
                // 添加图片点击事件
                binding.feedImage.setOnClickListener {
                    listener.onImageClicked(feed.id, 0)
                }
            } else {
                binding.feedImage.visibility = View.GONE
            }

            binding.likeButton.setImageResource(
                if (uiModel.isLikedByUser) com.susking.ephone_s.core.R.drawable.ic_like_filled_24 else com.susking.ephone_s.core.R.drawable.ic_like_empty_24
            )

            val hasLikes = uiModel.likerNames.isNotEmpty()
            val hasComments = feed.comments.isNotEmpty()
            val hasInteractions = hasLikes || hasComments

            binding.interactionsContainer.visibility = if (hasInteractions) View.VISIBLE else View.GONE
            binding.likesText.visibility = if (hasLikes) View.VISIBLE else View.GONE
            binding.likesCommentsDivider.visibility = if (hasLikes && hasComments) View.VISIBLE else View.GONE

            if(hasLikes) {
                binding.likesText.text = uiModel.likerNames
            }

            binding.commentsContainer.removeAllViews()
            if (hasComments) {
                feed.comments.forEach { comment ->
                    val commentView = LayoutInflater.from(context).inflate(R.layout.item_qq_feed_comment, binding.commentsContainer, false)
                    val commentTextView = commentView.findViewById<TextView>(R.id.comment_text)
                    val commentImageView = commentView.findViewById<ImageView>(R.id.comment_image)

                    if (!comment.stickerUrl.isNullOrBlank()) {
                        // Sticker comment
                        commentImageView.visibility = View.VISIBLE
                        commentTextView.visibility = View.VISIBLE

                        val commenterNameText = "${comment.commenterName}:"
                        val spannable = SpannableString(commenterNameText)
                        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, commenterNameText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        commentTextView.text = spannable

                        Glide.with(context)
                            .load(comment.stickerUrl)
                            .into(commentImageView)
                    } else {
                        // Text comment
                        commentImageView.visibility = View.GONE
                        commentTextView.visibility = View.VISIBLE
                        
                        val fullCommentText = "${comment.commenterName}: ${comment.commentText ?: ""}"
                        val spannable = SpannableString(fullCommentText)
                        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, comment.commenterName.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        commentTextView.text = spannable
                    }

                    commentView.setOnLongClickListener {
                        showCommentOptions(it, feed.id, comment.id)
                        true
                    }

                    binding.commentsContainer.addView(commentView)
                }
            }

            if (uiModel.originalFeed != null) {
                binding.repostedFeedCard.visibility = View.VISIBLE
                binding.repostedFeedContent.text = "@${uiModel.originalFeed.authorName}: ${uiModel.originalFeed.content}"
            } else {
                binding.repostedFeedCard.visibility = View.GONE
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val date = Date(timestamp)
            val today = Date()
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdfDate = SimpleDateFormat("MM-dd", Locale.getDefault())

            return when {
                isSameDay(date, today) -> "今天 ${sdfTime.format(date)}"
                isYesterday(date, today) -> "昨天 ${sdfTime.format(date)}"
                else -> "${sdfDate.format(date)} ${sdfTime.format(date)}"
            }
        }

        private fun isSameDay(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isYesterday(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            cal2.add(Calendar.DAY_OF_YEAR, -1)
            return isSameDay(cal1.time, cal2.time)
        }
    }
}

class FeedUiModelDiffCallback : DiffUtil.ItemCallback<FeedItemUiModel>() {
    override fun areItemsTheSame(oldItem: FeedItemUiModel, newItem: FeedItemUiModel): Boolean {
        return oldItem.feed.id == newItem.feed.id
    }

    override fun areContentsTheSame(oldItem: FeedItemUiModel, newItem: FeedItemUiModel): Boolean {
        return oldItem == newItem
    }
}