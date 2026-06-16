package com.susking.ephone_s.qq.ui.chat.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.qq.databinding.ItemFeaturedPhotoBinding

class FeaturedPhotosAdapter(
    private val onAddClick: () -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val onPhotoClick: (String) -> Unit
) : RecyclerView.Adapter<FeaturedPhotosAdapter.ViewHolder>() {

    private var photos: List<String> = emptyList()
    private var maxPhotos: Int = 4
    private var isEditMode: Boolean = false

    fun submitList(newPhotos: List<String>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return if (photos.size < maxPhotos) {
            photos.size + 1 // Add 1 for the "add" button
        } else {
            photos.size
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeaturedPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isAddItem = position == photos.size && photos.size < maxPhotos
        if (isAddItem) {
            holder.bindAdd()
        } else {
            holder.bindPhoto(photos[position])
        }
    }

    inner class ViewHolder(private val binding: ItemFeaturedPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindPhoto(photoUri: String) {
            Glide.with(binding.root.context)
                .load(photoUri)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.ic_image_load_failed)
                .into(binding.photoImageView)

            binding.deleteButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
            binding.deleteButton.setOnClickListener {
                onDeleteClick(photoUri)
            }
            binding.root.setOnClickListener {
                if (!isEditMode) {
                    onPhotoClick(photoUri)
                }
            }
        }

        fun bindAdd() {
            binding.photoImageView.setImageResource(R.drawable.ic_add_photo)
            binding.deleteButton.visibility = View.GONE
            binding.root.setOnClickListener {
                onAddClick()
            }
        }
    }
}