package com.susking.ephone_s.qq.ui.qzone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.qq.R

/**
 * 预览评论适配器
 * 用于在说说预览中显示评论
 */
class PreviewCommentAdapter : ListAdapter<QzoneComment, PreviewCommentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)
        holder.bind(comment)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val commentUsername: TextView = itemView.findViewById(R.id.comment_username)
        private val commentTime: TextView = itemView.findViewById(R.id.comment_time)
        private val commentText: TextView = itemView.findViewById(R.id.comment_text)
        private val commentImagesRecyclerView: RecyclerView = itemView.findViewById(R.id.comment_images_recycler_view)
        
        private val imageAdapter = PreviewImageAdapter()

        init {
            commentImagesRecyclerView.apply {
                layoutManager = GridLayoutManager(itemView.context, 3)
                adapter = imageAdapter
            }
        }

        fun bind(comment: QzoneComment) {
            commentUsername.text = comment.username
            commentTime.text = comment.time
            
            // emoji已在ViewModel中转换为Unicode emoji
            commentText.text = comment.text
            
            // 从评论文本中提取图片URL（如果有的话）
            val imageUrls = extractImageUrlsFromComment(comment.text)
            if (imageUrls.isNotEmpty()) {
                commentImagesRecyclerView.visibility = View.VISIBLE
                imageAdapter.submitList(imageUrls)
            } else {
                commentImagesRecyclerView.visibility = View.GONE
            }
        }
        
        /**
         * 从评论文本中提取图片URL
         * 匹配格式：（带图片评论：...）
         */
        private fun extractImageUrlsFromComment(text: String): List<String> {
            val imageUrls = mutableListOf<String>()
            val pattern = """（带图片评论：([^）]+)）""".toRegex()
            pattern.findAll(text).forEach { matchResult ->
                val url = matchResult.groupValues[1]
                if (url.isNotBlank() && !url.contains("...")) {
                    imageUrls.add(url)
                }
            }
            return imageUrls
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<QzoneComment>() {
        override fun areItemsTheSame(oldItem: QzoneComment, newItem: QzoneComment): Boolean {
            return oldItem.username == newItem.username && 
                   oldItem.time == newItem.time &&
                   oldItem.text == newItem.text
        }

        override fun areContentsTheSame(oldItem: QzoneComment, newItem: QzoneComment): Boolean {
            return oldItem == newItem
        }
    }
}