package com.susking.ephone_s.qq.ui.sticker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.data.local.entity.StickerEntity
import com.susking.ephone_s.qq.databinding.ItemStickerBinding

class StickerAdapter(
    private val onStickerClick: (StickerEntity) -> Unit,
    private val onStickerLongClick: (StickerEntity) -> Unit,
    private val onUploadClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var isManagementMode = false
    private val selectedItems = mutableSetOf<Int>()

    // 列表头部固定占用一个“上传按钮”位置，故所有数据项的真实显示位置需整体偏移 HEADER_OFFSET。
    // 直接继承 ListAdapter 时，DiffUtil 通知会按数据原始下标发出，与显示位置错位（off-by-one），
    // 导致改名等纯内容更新刷新到错误的行。这里改用 AsyncListDiffer，并为其通知统一加上偏移，使刷新对齐。
    private val offsetUpdateCallback: ListUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position + HEADER_OFFSET, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position + HEADER_OFFSET, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition + HEADER_OFFSET, toPosition + HEADER_OFFSET)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position + HEADER_OFFSET, count, payload)
        }
    }

    private val differ: AsyncListDiffer<StickerEntity> =
        AsyncListDiffer(offsetUpdateCallback, AsyncDifferConfig.Builder(StickerDiffCallback).build())

    /**
     * 当前数据列表（不含头部上传按钮）。
     * 替代原 ListAdapter.currentList，供外部读取与内部定位使用。
     */
    val currentList: List<StickerEntity>
        get() = differ.currentList

    /**
     * 提交新列表，由 AsyncListDiffer 在后台计算差异并通过偏移回调刷新。
     * 保持与原 ListAdapter.submitList 相同的调用方式。
     */
    fun submitList(list: List<StickerEntity>) {
        differ.submitList(list)
    }

    /**
     * 按数据下标获取表情项（不含头部偏移）。
     */
    private fun getItem(position: Int): StickerEntity = differ.currentList[position]

    companion object {
        private const val VIEW_TYPE_STICKER = 0
        private const val VIEW_TYPE_UPLOAD = 1
        // 头部上传按钮占用的位置数量
        private const val HEADER_OFFSET = 1
    }

    fun setManagementMode(enabled: Boolean) {
        if (isManagementMode != enabled) {
            isManagementMode = enabled
            if (!enabled) {
                selectedItems.clear()
            }
            notifyDataSetChanged()
        }
    }

    fun toggleSelection(stickerId: Int) {
        if (selectedItems.contains(stickerId)) {
            selectedItems.remove(stickerId)
        } else {
            selectedItems.add(stickerId)
        }
        // 只更新变化的item，提高效率
        // 因为第一个位置是上传按钮,所以实际显示位置要加1
        val index = currentList.indexOfFirst { it.id == stickerId }
        if (index != -1) {
            notifyItemChanged(index + 1)
        }
    }

    fun selectAll() {
        if (selectedItems.size == currentList.size) {
            selectedItems.clear()
        } else {
            selectedItems.clear()
            selectedItems.addAll(currentList.map { it.id })
        }
        notifyDataSetChanged()
    }


    fun getSelectedItems(): List<StickerEntity> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        // 数据项数量 + 头部固定的一个上传按钮位置
        return differ.currentList.size + HEADER_OFFSET
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            VIEW_TYPE_UPLOAD
        } else {
            VIEW_TYPE_STICKER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_STICKER -> {
                val binding = ItemStickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                StickerViewHolder(binding)
            }
            VIEW_TYPE_UPLOAD -> {
                val binding = ItemStickerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                UploadViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is StickerViewHolder -> {
                // 因为第一个位置是上传按钮,所以实际表情的索引要减1
                val sticker = getItem(position - 1)
                holder.bind(sticker)
            }
            is UploadViewHolder -> {
                holder.bind()
            }
        }
    }

    inner class StickerViewHolder(
        private val binding: ItemStickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sticker: StickerEntity) {
            val isSelected = selectedItems.contains(sticker.id)

            binding.stickerName.text = sticker.name
            Glide.with(itemView.context).load(sticker.url).into(binding.stickerImage)

            // 管理模式下只显示选中描边，删除统一通过底部删除按钮执行。
            val strokeColor = if (isSelected) {
                itemView.context.getColor(R.color.primaryColor)
            } else {
                Color.TRANSPARENT
            }
            binding.stickerCard.strokeColor = strokeColor

            itemView.setOnClickListener {
                onStickerClick(sticker)
            }

            itemView.setOnLongClickListener {
                onStickerLongClick(sticker)
                true
            }
        }
    }
    
    inner class UploadViewHolder(
        private val binding: ItemStickerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            // 隐藏表情名称
            binding.stickerName.visibility = View.GONE
            
            // 显示加号图标 - 设置为fitCenter确保居中显示
            binding.stickerImage.setImageResource(R.drawable.ic_add_circle_outline_24)
            binding.stickerImage.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            
            // 调整加号图标大小 - 设置padding来缩小图标
            val paddingDp = 24 // 24dp的padding
            val scale = itemView.context.resources.displayMetrics.density
            val paddingPx = (paddingDp * scale + 0.5f).toInt()
            binding.stickerImage.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            
            // 设置虚线边框
            binding.stickerCard.setBackgroundResource(com.susking.ephone_s.qq.R.drawable.bg_dashed_border)
            binding.stickerCard.strokeWidth = 0
            
            // 根据管理模式设置点击事件
            if (isManagementMode) {
                // 管理模式下禁用上传按钮
                binding.stickerCard.setOnClickListener(null)
                binding.stickerCard.isClickable = false
                // 设置半透明效果表示禁用状态
                binding.stickerCard.alpha = 0.5f
            } else {
                // 正常模式下启用上传按钮
                binding.stickerCard.isClickable = true
                binding.stickerCard.alpha = 1.0f
                binding.stickerCard.setOnClickListener {
                    onUploadClick()
                }
            }
            
            // 禁用长按事件
            binding.stickerCard.setOnLongClickListener { true }
        }
    }
}

object StickerDiffCallback : DiffUtil.ItemCallback<StickerEntity>() {
    override fun areItemsTheSame(oldItem: StickerEntity, newItem: StickerEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: StickerEntity, newItem: StickerEntity): Boolean {
        return oldItem == newItem
    }
}