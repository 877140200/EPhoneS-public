package com.susking.ephone_s.cphone.ui.diary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.ItemCphoneDiaryBinding
import com.susking.ephone_s.aidata.domain.model.DiaryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CPhone日记列表Adapter
 */
class CPhoneDiaryAdapter(
    private val onDiaryClick: (DiaryEntry) -> Unit,
    private val onFavoriteClick: (DiaryEntry) -> Unit,
    private val onEditClick: (DiaryEntry) -> Unit,
    private val onDeleteClick: (DiaryEntry) -> Unit
) : ListAdapter<DiaryEntry, CPhoneDiaryAdapter.DiaryViewHolder>(DiaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemCphoneDiaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DiaryViewHolder(binding, onDiaryClick, onFavoriteClick, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder
     */
    class DiaryViewHolder(
        private val binding: ItemCphoneDiaryBinding,
        private val onDiaryClick: (DiaryEntry) -> Unit,
        private val onFavoriteClick: (DiaryEntry) -> Unit,
        private val onEditClick: (DiaryEntry) -> Unit,
        private val onDeleteClick: (DiaryEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(diary: DiaryEntry) {
            binding.apply {
                // 设置标题
                tvTitle.text = diary.title
                
                // 设置内容预览(去除Markdown标记)
                tvContentPreview.text = removeMarkdown(diary.content)
                
                // 设置日期
                tvDate.text = formatDate(diary.timestamp)
                
                // 设置收藏图标
                ivFavorite.setImageResource(
                    if (diary.isFavorite) {
                        R.drawable.ic_star_full
                    } else {
                        R.drawable.ic_star_outline
                    }
                )
                
                // 设置点击事件
                root.setOnClickListener {
                    onDiaryClick(diary)
                }
                
                ivFavorite.setOnClickListener {
                    onFavoriteClick(diary)
                }

                btnEditDiary.setOnClickListener {
                    onEditClick(diary)
                }

                btnDeleteDiary.setOnClickListener {
                    onDeleteClick(diary)
                }
            }
        }

        /**
         * 去除Markdown标记,只保留纯文本
         */
        private fun removeMarkdown(markdown: String): String {
            return markdown
                .replace(Regex("#+ "), "") // 去除标题标记
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1") // 去除粗体
                .replace(Regex("~~(.+?)~~"), "$1") // 去除删除线
                .replace(Regex("\\*(.+?)\\*"), "$1") // 去除斜体
                .replace(Regex("![huewm]\\{([^}]*)\\}"), "$1") // 去除日记专属标记 !h{}/!u{}/!e{}/!w{}/!m{}，保留内容
                .replace(Regex("\\|\\|(.+?)\\|\\|"), "$1") // 去除涂黑标记，预览里直接显示原文
                .replace(Regex("- "), "• ") // 替换列表标记
                .trim()
        }

        /**
         * 格式化日期
         */
        private fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    /**
     * DiffUtil回调
     */
    private class DiaryDiffCallback : DiffUtil.ItemCallback<DiaryEntry>() {
        override fun areItemsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean {
            return oldItem == newItem
        }
    }
}