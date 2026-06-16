package com.susking.ephone_s.cphone.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneMusicSongBinding
import com.susking.ephone_s.aidata.domain.model.MusicTrack

/**
 * CPhone音乐播放器歌曲列表Adapter
 * 模拟网易云音乐样式
 */
class CPhoneMusicAdapter(
    private val onSongClick: (MusicTrack) -> Unit,
    private val onMoreClick: (MusicTrack) -> Unit
) : ListAdapter<MusicTrack, CPhoneMusicAdapter.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemCphoneMusicSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding, onSongClick, onMoreClick)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder
     */
    class SongViewHolder(
        private val binding: ItemCphoneMusicSongBinding,
        private val onSongClick: (MusicTrack) -> Unit,
        private val onMoreClick: (MusicTrack) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: MusicTrack) {
            binding.apply {
                // 设置歌曲名
                tvSongName.text = song.songName

                // 设置歌手名
                tvArtist.text = song.artistName

                // 使用Coil加载专辑封面
                ivAlbumCover.load(song.coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_album_placeholder)
                    error(R.drawable.ic_album_placeholder)
                    transformations(RoundedCornersTransformation(8f))
                }
                
                // 设置点击事件
                root.setOnClickListener {
                    onSongClick(song)
                }
                
                ivMore.setOnClickListener {
                    onMoreClick(song)
                }
            }
        }
    }

    /**
     * DiffUtil回调
     */
    private class SongDiffCallback : DiffUtil.ItemCallback<MusicTrack>() {
        override fun areItemsTheSame(oldItem: MusicTrack, newItem: MusicTrack): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MusicTrack, newItem: MusicTrack): Boolean {
            return oldItem == newItem
        }
    }
}