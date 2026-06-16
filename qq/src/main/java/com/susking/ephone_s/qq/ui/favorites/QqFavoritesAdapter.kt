package com.susking.ephone_s.qq.ui.favorites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.core.databinding.ContentFavoriteTextBinding
import com.susking.ephone_s.core.ui.viewholder.TextFavoriteViewHolder
import com.susking.ephone_s.qq.databinding.ContentMessageImageBinding
import com.susking.ephone_s.qq.databinding.ContentMessageLocationBinding
import com.susking.ephone_s.qq.databinding.ContentMessageStickerBinding
import com.susking.ephone_s.qq.databinding.ContentMessageTransferBinding
import com.susking.ephone_s.qq.databinding.ContentMessageWaimaiBinding
import com.susking.ephone_s.qq.databinding.ContentMessageWaimaiOrderBinding
import com.susking.ephone_s.qq.databinding.ItemQqFavoriteBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QqFavoritesAdapter(
    private val onEditClicked: (FavoriteMessageEntity) -> Unit,
    private val onDeleteClicked: (FavoriteMessageEntity) -> Unit
) : ListAdapter<FavoriteMessageEntity, RecyclerView.ViewHolder>(FavoriteDiffCallback) {

    private var displayMode: String = "collapsed" // 默认折叠
    private val expandedItems = mutableSetOf<String>() // 跟踪用户手动展开的项目

    fun setDisplayMode(mode: String) {
        displayMode = mode
        expandedItems.clear() // 模式改变时，重置手动展开的状态
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when (message.type) {
            "text", "offline_text", "system" -> VIEW_TYPE_TEXT
            "image", "ai_image", "naiimag", "image_url" -> VIEW_TYPE_IMAGE
            "sticker" -> VIEW_TYPE_STICKER
            "location_share" -> VIEW_TYPE_LOCATION
            "transfer" -> VIEW_TYPE_TRANSFER
            "waimai_request" -> VIEW_TYPE_WAIMAI
            "waimai_order" -> VIEW_TYPE_WAIMAI_ORDER
            else -> VIEW_TYPE_TEXT // 默认文本
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val favoriteBinding = ItemQqFavoriteBinding.inflate(inflater, parent, false)
        val contentContainer = favoriteBinding.contentContainer

        return when (viewType) {
            VIEW_TYPE_IMAGE -> {
                val contentBinding = ContentMessageImageBinding.inflate(inflater, contentContainer, true)
                ImageFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            VIEW_TYPE_STICKER -> {
                val contentBinding = ContentMessageStickerBinding.inflate(inflater, contentContainer, true)
                StickerFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            VIEW_TYPE_LOCATION -> {
                val contentBinding = ContentMessageLocationBinding.inflate(inflater, contentContainer, true)
                LocationFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            VIEW_TYPE_TRANSFER -> {
                val contentBinding = ContentMessageTransferBinding.inflate(inflater, contentContainer, true)
                TransferFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            VIEW_TYPE_WAIMAI -> {
                val contentBinding = ContentMessageWaimaiBinding.inflate(inflater, contentContainer, true)
                WaimaiFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            VIEW_TYPE_WAIMAI_ORDER -> {
                val contentBinding = ContentMessageWaimaiOrderBinding.inflate(inflater, contentContainer, true)
                WaimaiOrderFavoriteViewHolder(favoriteBinding, contentBinding)
            }
            else -> { // VIEW_TYPE_TEXT
                val contentBinding = ContentFavoriteTextBinding.inflate(inflater, contentContainer, true)
                TextFavoriteViewHolderWrapper(favoriteBinding, contentBinding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is TextFavoriteViewHolderWrapper -> holder.bind(item)
            is ImageFavoriteViewHolder -> holder.bind(item)
            is StickerFavoriteViewHolder -> holder.bind(item)
            is LocationFavoriteViewHolder -> holder.bind(item)
            is TransferFavoriteViewHolder -> holder.bind(item)
            is WaimaiFavoriteViewHolder -> holder.bind(item)
            is WaimaiOrderFavoriteViewHolder -> holder.bind(item)
        }
    }

    abstract inner class BaseFavoriteViewHolder(protected val binding: ItemQqFavoriteBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val messageId = getItem(adapterPosition).messageId
                    if (expandedItems.contains(messageId)) {
                        expandedItems.remove(messageId)
                    } else {
                        expandedItems.add(messageId)
                    }
                    notifyItemChanged(adapterPosition)
                }
            }
            binding.editButton.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onEditClicked(getItem(adapterPosition))
                }
            }
            binding.deleteButton.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClicked(getItem(adapterPosition))
                }
            }
        }
        open fun bind(message: FavoriteMessageEntity) {
            binding.favoriteSenderName.text = message.senderName
            val fullTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            binding.favoriteTimestamp.text = fullTimestamp

            Glide.with(binding.root.context)
                .load(message.senderAvatar)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.favoriteSenderAvatar)

            val sourceText = if (message.source.isNullOrEmpty()) "" else "来自: ${message.source}"
            binding.favoriteSource.text = sourceText
            binding.favoriteSource.visibility = if (sourceText.isEmpty()) View.GONE else View.VISIBLE
            val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            val collapsedText = "${sourceText.replace("来自: ", "")} $timeOnly".trim()
            binding.collapsedDetailsText.text = collapsedText
            // 修复展开/折叠逻辑:
            // expanded模式: 默认展开,在expandedItems中表示用户手动折叠了
            // collapsed模式: 默认折叠,在expandedItems中表示用户手动展开了
            val isExpanded = if (displayMode == "expanded") {
                !expandedItems.contains(message.messageId) // 不在列表中=展开
            } else {
                expandedItems.contains(message.messageId) // 在列表中=展开
            }
            binding.footerLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.collapsedDetailsText.visibility = if (isExpanded) View.GONE else View.VISIBLE
            binding.contentContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }
    /**
     * TextFavoriteViewHolder的包装类
     * 继承BaseFavoriteViewHolder以支持展开/折叠功能
     */
    inner class TextFavoriteViewHolderWrapper(
        binding: ItemQqFavoriteBinding,
        private val contentBinding: ContentFavoriteTextBinding
    ) : BaseFavoriteViewHolder(binding) {
        private val textViewHolder = TextFavoriteViewHolder(contentBinding)
        
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            val text = message.text ?: ""
            textViewHolder.bind(text)
        }
    }

    inner class ImageFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageImageBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            val imageUrl = message.imageUrl
            val imageLoader = Glide.with(itemView.context)

            if (!imageUrl.isNullOrBlank()) {
                val imageFile = File(imageUrl)
                val loadTarget = if (imageFile.exists()) {
                    imageFile
                } else {
                    imageUrl
                }
                imageLoader.load(loadTarget)
                    .override(Target.SIZE_ORIGINAL)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.ic_image_load_failed)
                    .into(contentBinding.messageImageView)
            } else {
                 contentBinding.messageImageView.setImageResource(R.drawable.ic_image_load_failed)
            }
        }
    }

    inner class StickerFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageStickerBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            Glide.with(itemView.context)
                .load(message.stickerUrl)
                .into(contentBinding.stickerImageView)
        }
    }

    inner class LocationFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageLocationBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            contentBinding.locationNameTextView.text = message.content ?: "位置"
        }
    }

    inner class TransferFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageTransferBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            contentBinding.transferTitle.text = "转账给 ${message.recipientName ?: "对方"}"
            contentBinding.transferAmount.text = String.format(Locale.getDefault(), "¥%.2f", message.amount ?: 0.0)
            contentBinding.transferNotes.text = message.notes ?: ""
            contentBinding.transferNotes.visibility = if (message.notes.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    inner class WaimaiFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageWaimaiBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            contentBinding.waimaiAmountText.text = String.format(Locale.getDefault(), "¥%.2f", message.amount ?: 0.0)
        }
    }

    inner class WaimaiOrderFavoriteViewHolder(binding: ItemQqFavoriteBinding, private val contentBinding: ContentMessageWaimaiOrderBinding) : BaseFavoriteViewHolder(binding) {
        override fun bind(message: FavoriteMessageEntity) {
            super.bind(message)
            contentBinding.waimaiOrderTitle.text = "${message.senderName ?: "对方"} 已为你下单，请慢用~"
            contentBinding.waimaiOrderProductInfo.text = message.productInfo ?: "商品信息"
            contentBinding.waimaiOrderAmount.text = String.format(Locale.getDefault(), "¥%.2f", message.amount ?: 0.0)
        }
    }

    companion object {
        private const val VIEW_TYPE_TEXT = 0
        private const val VIEW_TYPE_IMAGE = 1
        private const val VIEW_TYPE_STICKER = 2
        private const val VIEW_TYPE_LOCATION = 3
        private const val VIEW_TYPE_TRANSFER = 4
        private const val VIEW_TYPE_WAIMAI = 5
        private const val VIEW_TYPE_WAIMAI_ORDER = 6

        private val FavoriteDiffCallback = object : DiffUtil.ItemCallback<FavoriteMessageEntity>() {
            override fun areItemsTheSame(oldItem: FavoriteMessageEntity, newItem: FavoriteMessageEntity): Boolean {
                return oldItem.messageId == newItem.messageId
            }
            override fun areContentsTheSame(oldItem: FavoriteMessageEntity, newItem: FavoriteMessageEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}