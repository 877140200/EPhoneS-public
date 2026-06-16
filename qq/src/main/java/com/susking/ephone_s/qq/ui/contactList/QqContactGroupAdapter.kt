package com.susking.ephone_s.qq.ui.contactList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.susking.ephone_s.core.R
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.databinding.ItemContactBinding
import com.susking.ephone_s.qq.databinding.ItemContactGroupHeaderBinding
import com.susking.ephone_s.qq.databinding.ItemManageGroupsFooterBinding

sealed class ContactListItem {
    data class HeaderItem(val title: String, val count: Int, val isExpanded: Boolean = true) : ContactListItem()
    data class ContactItem(val contact: PersonProfile) : ContactListItem()
    object FooterItem : ContactListItem() // 新增页脚类型
}

class QqContactGroupAdapter(
    private val onContactClicked: (PersonProfile) -> Unit,
    private val onHeaderClicked: (ContactListItem.HeaderItem) -> Unit,
    private val onFooterClicked: () -> Unit // 新增页脚点击回调
) : ListAdapter<ContactListItem, RecyclerView.ViewHolder>(ContactDiffCallback()) {

    private var showFooter = false

    fun setShowFooter(shouldShow: Boolean) {
        if (showFooter != shouldShow) {
            showFooter = shouldShow
            notifyDataSetChanged() // 数据集结构已更改
        }
    }

    override fun getItemCount(): Int {
        val baseCount = super.getItemCount()
        return if (showFooter) baseCount + 1 else baseCount
    }

    override fun getItemViewType(position: Int): Int {
        return if (showFooter && position == super.getItemCount()) {
            VIEW_TYPE_FOOTER
        } else {
            when (getItem(position)) {
                is ContactListItem.HeaderItem -> VIEW_TYPE_HEADER
                is ContactListItem.ContactItem -> VIEW_TYPE_CONTACT
                is ContactListItem.FooterItem -> VIEW_TYPE_FOOTER // 理论上不会走到这里
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemContactGroupHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_CONTACT -> {
                val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ContactViewHolder(binding)
            }
            VIEW_TYPE_FOOTER -> {
                val binding = ItemManageGroupsFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                FooterViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterViewHolder) {
            holder.bind()
        } else if (position < super.getItemCount()) {
            when (val item = getItem(position)) {
                is ContactListItem.HeaderItem -> (holder as HeaderViewHolder).bind(item)
                is ContactListItem.ContactItem -> (holder as ContactViewHolder).bind(item.contact)
                else -> {} // 其他情况忽略
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemContactGroupHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ContactListItem.HeaderItem) {
            binding.groupTitle.text = header.title
            val isLetterHeader = header.title.length == 1 && header.title[0].isLetter()

            if (isLetterHeader) {
                binding.groupCount.visibility = View.GONE
                binding.groupIndicator.visibility = View.GONE
                binding.root.isClickable = false
            } else {
                binding.groupCount.visibility = View.VISIBLE
                binding.groupIndicator.visibility = View.VISIBLE
                binding.groupCount.text = header.count.toString()
                binding.root.setOnClickListener { onHeaderClicked(header) }
                binding.groupIndicator.rotation = if (header.isExpanded) 90f else 0f
                binding.root.isClickable = true
            }
        }
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: PersonProfile) {
            binding.contactName.text = contact.remarkName
            binding.contactStatus.text = contact.statusText
            if (!contact.avatarUri.isNullOrBlank()) {
                Glide.with(binding.root.context)
                    .load(contact.avatarUri)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(binding.contactAvatar)
            } else {
                binding.contactAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
            binding.root.setOnClickListener { onContactClicked(contact) }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
        override fun areItemsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return when {
                oldItem is ContactListItem.HeaderItem && newItem is ContactListItem.HeaderItem -> oldItem.title == newItem.title
                oldItem is ContactListItem.ContactItem && newItem is ContactListItem.ContactItem -> oldItem.contact.id == newItem.contact.id
                oldItem is ContactListItem.FooterItem && newItem is ContactListItem.FooterItem -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return oldItem == newItem
        }
    }

    fun getPositionForSection(section: Char): Int {
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item is ContactListItem.HeaderItem && item.title.first().uppercaseChar() == section) {
                return i
            }
        }
        return -1
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_FOOTER = 2 // 新增页脚视图类型
    }

    inner class FooterViewHolder(binding: ItemManageGroupsFooterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            itemView.setOnClickListener { onFooterClicked() }
        }
    }
}