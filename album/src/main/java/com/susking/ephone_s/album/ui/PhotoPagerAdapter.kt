package com.susking.ephone_s.album.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.album.databinding.ItemPhotoViewerBinding
import kotlin.math.abs

class PhotoPagerAdapter(
    private val photos: List<String>,
    private val onPhotoTap: () -> Unit,
    private val onSwipeDown: () -> Unit
) : RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding, onPhotoTap, onSwipeDown)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    class PhotoViewHolder(
        private val binding: ItemPhotoViewerBinding,
        private val onPhotoTap: () -> Unit,
        private val onSwipeDown: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photoUrl: String) {
            Glide.with(itemView.context)
                .load(photoUrl)
                .into(binding.photoView)

            binding.photoView.setOnPhotoTapListener { _, _, _ ->
                onPhotoTap()
            }

            binding.photoView.setOnSingleFlingListener { e1, e2, velocityX, velocityY ->
                // 计算垂直位移
                val deltaY = e2.y - e1.y
                // 当垂直速度大于水平速度（确保是垂直滑动），且位移足够大，且图片未被放大时，触发回调
                if (abs(velocityY) > abs(velocityX) && deltaY > 200 && binding.photoView.scale == 1.0f) {
                    onSwipeDown()
                    true // 消耗事件
                } else {
                    false // 不消耗事件
                }
            }
        }
    }
}
