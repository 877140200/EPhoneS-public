package com.susking.ephone_s.qq.ui.forward

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.qq.R

/**
 * 转发联系人列表适配器
 * 
 * 支持单选和多选模式
 */
class ForwardContactAdapter(
    private val onContactClick: (PersonProfile) -> Unit
) : ListAdapter<PersonProfile, ForwardContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    /**
     * 是否多选模式
     */
    var isMultiSelectMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /**
     * 选中的联系人ID集合
     */
    private val selectedContactIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forward_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
    }

    /**
     * 获取选中的联系人ID列表
     */
    fun getSelectedContactIds(): List<String> {
        return selectedContactIds.toList()
    }

    /**
     * 清空选中状态
     */
    fun clearSelection() {
        selectedContactIds.clear()
        notifyDataSetChanged()
    }

    /**
     * 切换联系人选中状态
     */
    private fun toggleSelection(contactId: String) {
        if (selectedContactIds.contains(contactId)) {
            selectedContactIds.remove(contactId)
        } else {
            selectedContactIds.add(contactId)
        }
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkboxSelected: CheckBox = itemView.findViewById(R.id.checkboxSelected)
        private val imageViewAvatar: ShapeableImageView = itemView.findViewById(R.id.imageViewAvatar)
        private val textViewName: TextView = itemView.findViewById(R.id.textViewName)

        fun bind(contact: PersonProfile) {
            // 显示联系人名称（优先备注名，否则显示真实姓名）
            textViewName.text = contact.remarkName.ifEmpty { contact.realName }

            // 加载头像
            val avatarUri = contact.avatarUri
            if (!avatarUri.isNullOrEmpty()) {
                val uri = when {
                    avatarUri.startsWith("file://") -> Uri.parse(avatarUri)
                    avatarUri.startsWith("/") -> Uri.parse("file://$avatarUri")
                    else -> Uri.parse(avatarUri)
                }
                Glide.with(itemView.context)
                    .load(uri)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(imageViewAvatar)
            } else {
                imageViewAvatar.setImageResource(R.drawable.ic_default_avatar)
            }

            // 多选模式显示checkbox
            checkboxSelected.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            checkboxSelected.isChecked = selectedContactIds.contains(contact.id)

            // 点击事件
            itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    // 多选模式：切换选中状态
                    toggleSelection(contact.id)
                    checkboxSelected.isChecked = selectedContactIds.contains(contact.id)
                    onContactClick(contact)
                } else {
                    // 单选模式：直接回调
                    onContactClick(contact)
                }
            }

            // Checkbox点击事件
            checkboxSelected.setOnClickListener {
                toggleSelection(contact.id)
                onContactClick(contact)
            }
        }
    }

    /**
     * DiffUtil回调，用于高效更新列表
     */
    private class ContactDiffCallback : DiffUtil.ItemCallback<PersonProfile>() {
        override fun areItemsTheSame(oldItem: PersonProfile, newItem: PersonProfile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PersonProfile, newItem: PersonProfile): Boolean {
            return oldItem == newItem
        }
    }
}