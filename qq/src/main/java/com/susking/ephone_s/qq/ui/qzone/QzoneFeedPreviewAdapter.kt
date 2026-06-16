package com.susking.ephone_s.qq.ui.qzone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.R

/**
 * QQ空间说说预览适配器
 * 用于在导入前预览解析出的说说
 */
class QzoneFeedPreviewAdapter : ListAdapter<QzoneFeed, QzoneFeedPreviewAdapter.ViewHolder>(DiffCallback()) {

    // 当前页的起始索引（用于显示全局编号）
    var pageStartIndex: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_qzone_feed, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feed = getItem(position)
        holder.bind(feed, pageStartIndex + position)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val feedNumber: TextView = itemView.findViewById(R.id.feed_number)
        private val feedTypeLabel: TextView = itemView.findViewById(R.id.feed_type_label)
        private val forwarderText: TextView = itemView.findViewById(R.id.forwarder_text)
        private val originalAuthor: TextView = itemView.findViewById(R.id.original_author)
        private val feedContent: TextView = itemView.findViewById(R.id.feed_content)
        private val imagesInfo: TextView = itemView.findViewById(R.id.images_info)
        private val originalImagesInfo: TextView = itemView.findViewById(R.id.original_images_info)
        private val videosInfo: TextView = itemView.findViewById(R.id.videos_info)
        private val timeText: TextView = itemView.findViewById(R.id.time_text)
        private val deviceText: TextView = itemView.findViewById(R.id.device_text)
        private val likesInfo: TextView = itemView.findViewById(R.id.likes_info)
        private val commentsInfo: TextView = itemView.findViewById(R.id.comments_info)
        private val viewsInfo: TextView = itemView.findViewById(R.id.views_info)
        private val imagesRecyclerView: RecyclerView = itemView.findViewById(R.id.images_recycler_view)
        private val commentsLayout: LinearLayout = itemView.findViewById(R.id.comments_layout)
        private val commentsRecyclerView: RecyclerView = itemView.findViewById(R.id.comments_recycler_view)
        
        private val imageAdapter = PreviewImageAdapter()
        private val commentAdapter = PreviewCommentAdapter()

        init {
            // 设置图片RecyclerView
            imagesRecyclerView.apply {
                layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
                adapter = imageAdapter
            }
            
            // 设置评论RecyclerView
            commentsRecyclerView.apply {
                layoutManager = LinearLayoutManager(itemView.context)
                adapter = commentAdapter
                isNestedScrollingEnabled = false
            }
        }

        fun bind(feed: QzoneFeed, globalIndex: Int) {
            // 设置编号（从1开始）
            feedNumber.text = "第 ${globalIndex + 1} 条"

            // 设置类型标签
            feedTypeLabel.text = if (feed.isForward) "转发说说" else "原创说说"

            // 转发说说的额外信息
            if (feed.isForward) {
                // 转发者文字（emoji已在ViewModel中转换）
                if (!feed.forwarderText.isNullOrBlank()) {
                    forwarderText.visibility = View.VISIBLE
                    forwarderText.text = feed.forwarderText
                } else {
                    forwarderText.visibility = View.GONE
                }

                // 原作者
                if (!feed.originalAuthor.isNullOrBlank()) {
                    originalAuthor.visibility = View.VISIBLE
                    originalAuthor.text = "原作者：${feed.originalAuthor}"
                } else {
                    originalAuthor.visibility = View.GONE
                }

                // 原图片信息
                val originalImageCount = feed.originalImageUrls?.size ?: 0
                if (originalImageCount > 0) {
                    originalImagesInfo.visibility = View.VISIBLE
                    originalImagesInfo.text = "📷 原说说包含 $originalImageCount 张图片"
                } else {
                    originalImagesInfo.visibility = View.GONE
                }
            } else {
                forwarderText.visibility = View.GONE
                originalAuthor.visibility = View.GONE
                originalImagesInfo.visibility = View.GONE
            }

            // 说说内容（emoji已在ViewModel中转换为Unicode emoji）
            feedContent.text = feed.content

            // 显示图片
            val imageCount = feed.imageUrls.size
            if (imageCount > 0) {
                imagesRecyclerView.visibility = View.VISIBLE
                imageAdapter.submitList(feed.imageUrls)
                imagesInfo.visibility = View.VISIBLE
                imagesInfo.text = "📷 包含 $imageCount 张图片"
            } else {
                imagesRecyclerView.visibility = View.GONE
                imagesInfo.visibility = View.GONE
            }

            // 视频信息
            val videoCount = feed.videoUrls.size
            if (videoCount > 0) {
                videosInfo.visibility = View.VISIBLE
                videosInfo.text = "🎬 包含 $videoCount 个视频"
            } else {
                videosInfo.visibility = View.GONE
            }

            // 时间
            timeText.text = feed.timeText

            // 设备
            deviceText.text = if (feed.device.isNotBlank()) {
                "来自${feed.device}"
            } else {
                ""
            }

            // 点赞信息
            likesInfo.text = "👍 ${feed.likes}"

            // 评论信息
            val commentCount = feed.comments.size
            commentsInfo.text = "💬 $commentCount"

            // 浏览信息
            viewsInfo.text = "👀 ${feed.views}"
            
            // 显示评论
            if (feed.comments.isNotEmpty()) {
                commentsLayout.visibility = View.VISIBLE
                commentAdapter.submitList(feed.comments)
            } else {
                commentsLayout.visibility = View.GONE
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<QzoneFeed>() {
        override fun areItemsTheSame(oldItem: QzoneFeed, newItem: QzoneFeed): Boolean {
            // 使用内容和时间作为唯一标识
            return oldItem.content == newItem.content && oldItem.timeText == newItem.timeText
        }

        override fun areContentsTheSame(oldItem: QzoneFeed, newItem: QzoneFeed): Boolean {
            return oldItem == newItem
        }
    }
}