package com.susking.ephone_s.album.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.album.databinding.ItemAlbumBinding
import com.susking.ephone_s.album.domain.model.Album

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit,
    private val onAlbumLongClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding, onAlbumClick, onAlbumLongClick)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlbumViewHolder(
        private val binding: ItemAlbumBinding,
        private val onAlbumClick: (Album) -> Unit,
        private val onAlbumLongClick: (Album) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.albumName.text = album.name
            binding.albumPhotoCount.text = "${album.photoCount} 张照片"
            binding.root.setOnClickListener { onAlbumClick(album) }
            binding.root.setOnLongClickListener {
                onAlbumLongClick(album)
                true
            }

            if (album.coverImagePath != null) {
                Glide.with(binding.root.context)
                    .load(album.coverImagePath)
                    .into(binding.albumThumbnail)
            } else {
                binding.albumThumbnail.setImageResource(R.drawable.ic_folder)
            }
        }
    }
}

class AlbumDiffCallback : DiffUtil.ItemCallback<Album>() {
    override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean {
        return oldItem == newItem
    }
}