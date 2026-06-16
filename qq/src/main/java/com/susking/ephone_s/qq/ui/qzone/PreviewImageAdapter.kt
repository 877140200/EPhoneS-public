package com.susking.ephone_s.qq.ui.qzone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.qq.R

/**
 * 预览图片适配器
 * 用于在说说预览中显示图片
 */
class PreviewImageAdapter : ListAdapter<String, PreviewImageAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageUrl = getItem(position)
        holder.bind(imageUrl)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.preview_image)

        fun bind(imageUrl: String) {
            // 处理emoji格式: [emoji:data:image/xxx]
            val actualUrl = if (imageUrl.startsWith("[emoji:") && imageUrl.endsWith("]")) {
                // 提取emoji中的base64数据
                imageUrl.substring(7, imageUrl.length - 1) // 去掉 "[emoji:" 和 "]"
            } else {
                imageUrl
            }
            
            // 使用Glide加载图片，支持网络图片和base64图片
            // Glide原生支持data:image/格式的base64图片
            Glide.with(itemView.context)
                .load(actualUrl)
                .placeholder(com.susking.ephone_s.core.R.drawable.bg_image_placeholder)
                .error(com.susking.ephone_s.core.R.drawable.ic_image_load_failed)
                .into(imageView)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}