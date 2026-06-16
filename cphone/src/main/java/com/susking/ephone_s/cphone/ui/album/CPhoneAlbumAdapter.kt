package com.susking.ephone_s.cphone.ui.album

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.susking.ephone_s.aidata.domain.model.AlbumPhoto
import com.susking.ephone_s.cphone.databinding.ItemCphoneAlbumPhotoBinding

/**
 * 相册照片列表Adapter
 */
class CPhoneAlbumAdapter(
    private val onPhotoClick: (AlbumPhoto, Int) -> Unit
) : ListAdapter<AlbumPhoto, CPhoneAlbumAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemCphoneAlbumPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding, onPhotoClick)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class PhotoViewHolder(
        private val binding: ItemCphoneAlbumPhotoBinding,
        private val onPhotoClick: (AlbumPhoto, Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: AlbumPhoto, position: Int) {
            binding.apply {
                // 设置照片描述
                tvDescription.text = photo.description

                // 加载照片图片
                // 直接使用imageUrl，如果为空则显示占位符
                // 图片应该通过NovelAI服务在其他地方提前生成
                ivPhoto.load(photo.imageUrl) {
                    crossfade(true)
                    placeholder(com.susking.ephone_s.cphone.R.drawable.bg_image_placeholder)
                    error(com.susking.ephone_s.cphone.R.drawable.bg_image_placeholder)
                }

                // 设置点击事件
                root.setOnClickListener {
                    onPhotoClick(photo, position)
                }
            }
        }
    }

    private class PhotoDiffCallback : DiffUtil.ItemCallback<AlbumPhoto>() {
        override fun areItemsTheSame(oldItem: AlbumPhoto, newItem: AlbumPhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AlbumPhoto, newItem: AlbumPhoto): Boolean {
            return oldItem == newItem
        }
    }
}