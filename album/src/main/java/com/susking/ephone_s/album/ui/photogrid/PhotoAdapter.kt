package com.susking.ephone_s.album.ui.photogrid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import coil.load
import com.susking.ephone_s.album.databinding.ItemPhotoBinding
import com.susking.ephone_s.album.domain.model.Photo

class PhotoAdapter(
    private val onPhotoClick: (Photo, Int) -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    var isSelectionMode = false
    private val selectedItems = mutableSetOf<Photo>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    fun toggleSelectionMode(enable: Boolean) {
        isSelectionMode = enable
        if (!enable) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }
    fun getSelectedItems(): Set<Photo> = selectedItems

    fun selectAll() {
        if (!isSelectionMode) return
        selectedItems.clear()
        selectedItems.addAll(currentList)
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }

    fun clearSelection() {
        if (!isSelectionMode) return
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }


    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: Photo, position: Int) {
            binding.photoImageView.load(photo.uri) {
                crossfade(true)
            }

            binding.photoCheckbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.photoCheckbox.isChecked = selectedItems.contains(photo)

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(photo)
                } else {
                    onPhotoClick(photo, position)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    toggleSelectionMode(true)
                    toggleSelection(photo)
                }
                true
            }
        }

        private fun toggleSelection(photo: Photo) {
            if (selectedItems.contains(photo)) {
                selectedItems.remove(photo)
            } else {
                selectedItems.add(photo)
            }
            if(isSelectionMode){
                notifyItemChanged(adapterPosition)
            }
            onSelectionChange(selectedItems.size)
        }
    }
}

class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
    override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean {
        return oldItem == newItem
    }
}
