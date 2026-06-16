package com.susking.ephone_s.cphone.ui.memo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneMemoBinding
import com.susking.ephone_s.aidata.domain.model.Memo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备忘录列表Adapter
 */
class CPhoneMemoAdapter(
    private val onMemoClick: (Memo) -> Unit,
    private val onFavoriteClick: (Memo) -> Unit
) : ListAdapter<Memo, CPhoneMemoAdapter.MemoViewHolder>(MemoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val binding = ItemCphoneMemoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemoViewHolder(binding, onMemoClick, onFavoriteClick)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MemoViewHolder(
        private val binding: ItemCphoneMemoBinding,
        private val onMemoClick: (Memo) -> Unit,
        private val onFavoriteClick: (Memo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(memo: Memo) {
            binding.apply {
                // 设置标题
                tvTitle.text = memo.title
                
                // 设置内容预览(最多3行)
                tvContentPreview.text = memo.content
                
                // 设置时间
                tvTime.text = formatTime(memo.timestamp)
                
                // 设置收藏图标
                ivFavorite.setImageResource(
                    if (memo.isFavorite) {
                        R.drawable.ic_star_full
                    } else {
                        R.drawable.ic_star_outline
                    }
                )
                
                // 设置点击事件
                root.setOnClickListener {
                    onMemoClick(memo)
                }
                
                // 设置收藏按钮点击事件
                ivFavorite.setOnClickListener {
                    onFavoriteClick(memo)
                }
            }
        }

        /**
         * 格式化时间戳
         */
        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    private class MemoDiffCallback : DiffUtil.ItemCallback<Memo>() {
        override fun areItemsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Memo, newItem: Memo): Boolean {
            return oldItem == newItem
        }
    }
}