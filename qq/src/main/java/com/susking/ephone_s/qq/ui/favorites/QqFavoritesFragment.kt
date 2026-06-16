package com.susking.ephone_s.qq.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.data.local.entity.FavoriteMessageEntity
import com.susking.ephone_s.core.R as CoreR
import com.susking.ephone_s.qq.databinding.DialogSelectContactFilterBinding
import com.susking.ephone_s.qq.databinding.FragmentQqFavoritesBinding
import com.susking.ephone_s.qq.databinding.ItemContactFilterBinding
import com.susking.ephone_s.qq.domain.manager.QqContentManager
import com.susking.ephone_s.qq.domain.manager.ContactWithFavoriteCount
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QqFavoritesFragment : Fragment() {

    private var _binding: FragmentQqFavoritesBinding? = null
    private val binding get() = _binding!!

    // 使用 Hilt 注入 ViewModel
    private val viewModel: QqViewModel by activityViewModels()
    
    // 注入 FavoriteManager
    @Inject lateinit var favoriteManager: QqContentManager

    private lateinit var favoritesAdapter: QqFavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        observeViewModel()
        favoriteManager.loadFavoritesDisplayMode()
    }

    override fun onResume() {
        super.onResume()
        // 此处应为空，或只包含与 Toolbar 无关的逻辑
    }

    private fun setupRecyclerView() {
        favoritesAdapter = QqFavoritesAdapter(
            onEditClicked = { favoriteMessage ->
                // Handle edit
                Toast.makeText(requireContext(), "编辑: ${favoriteMessage.text}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClicked = { favoriteMessage ->
                // Handle delete
                favoriteManager.removeFavorite(favoriteMessage)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
        )
        binding.favoritesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoritesAdapter
        }
    }
    
    private fun setupFilterChips() {
        // 创建筛选器选项
        val filters = listOf(
            FilterOption("all", "全部"),
            FilterOption("chat", "聊天"),
            FilterOption("inner", "内心"),
            FilterOption("contact", "特定联系人")
        )
        
        filters.forEach { filter ->
            val chip = Chip(requireContext()).apply {
                text = filter.label
                tag = filter.type // 设置tag用于后续识别
                isCheckable = true
                isChecked = filter.type == "all" // 默认选中"全部"
                
                setOnClickListener {
                    if (filter.type == "contact") {
                        // 显示联系人选择弹窗
                        showContactSelectionDialog()
                    } else {
                        // 应用筛选
                        favoriteManager.setFilter(filter.type)
                    }
                }
            }
            binding.filterChipGroup.addView(chip)
        }
    }
    
    private fun showContactSelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = favoriteManager.getContactsWithFavorites()

            if (contacts.isEmpty()) {
                Toast.makeText(requireContext(), "暂无联系人收藏", Toast.LENGTH_SHORT).show()
                // 重置选择到"全部"
                resetFilterToAll()
                return@launch
            }

            val dialogBinding = DialogSelectContactFilterBinding.inflate(layoutInflater)

            // Create the dialog instance first
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .setNegativeButton("取消") { d, _ ->
                    d.dismiss()
                    // 重置选择到"全部"
                    resetFilterToAll()
                }
                .create() // create() instead of show()

            val contactAdapter = ContactFilterAdapter(contacts) { contact ->
                // 应用联系人筛选
                favoriteManager.setFilter("contact", contact.contactId)
                // 更新chip文本显示联系人名称
                updateContactChipText(contact.contactName)
                // Dismiss the dialog from here
                dialog.dismiss()
            }

            dialogBinding.contactsRecyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = contactAdapter
            }

            dialog.show()
        }
    }
    
    private fun updateContactChipText(contactName: String) {
        // 找到"特定联系人"的chip并更新文本
        for (i in 0 until binding.filterChipGroup.childCount) {
            val chip = binding.filterChipGroup.getChildAt(i) as? Chip
            if (chip?.tag == "contact") {
                chip.text = contactName
                break
            }
        }
    }
    
    private fun resetFilterToAll() {
        // 重置到"全部"筛选
        for (i in 0 until binding.filterChipGroup.childCount) {
            val chip = binding.filterChipGroup.getChildAt(i) as? Chip
            chip?.isChecked = (i == 0) // 第一个是"全部"
        }
        favoriteManager.setFilter("all")
    }

    private fun observeViewModel() {
        favoriteManager.favoriteMessages.observe(viewLifecycleOwner) { messages ->
            favoritesAdapter.submitList(messages)
        }

        favoriteManager.favoritesDisplayMode.observe(viewLifecycleOwner) { mode ->
            favoritesAdapter.setDisplayMode(mode)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    fun showDisplayModeDialog() {
        val modes = arrayOf("全部折叠", "全部展开")
        val currentMode = favoriteManager.favoritesDisplayMode.value
        val checkedItem = if (currentMode == "expanded") 1 else 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置显示方式")
            .setSingleChoiceItems(modes, checkedItem) { dialog, which ->
                val selectedMode = if (which == 1) "expanded" else "collapsed"
                favoriteManager.saveFavoritesDisplayMode(selectedMode)
                dialog.dismiss()
            }
            .show()
    }


    companion object {
        fun newInstance() = QqFavoritesFragment()
    }
    
    // 筛选选项数据类
    private data class FilterOption(
        val type: String,
        val label: String
    )
    
    // 联系人筛选适配器
    private inner class ContactFilterAdapter(
        private val contacts: List<ContactWithFavoriteCount>,
        private val onContactClick: (ContactWithFavoriteCount) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ContactFilterAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemContactFilterBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemContactFilterBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.binding.apply {
                contactName.text = contact.contactName
                favoriteCount.text = "${contact.favoriteCount}条收藏"
                
                Glide.with(root.context)
                    .load(contact.avatarUri)
                    .placeholder(CoreR.drawable.ic_default_avatar)
                    .error(CoreR.drawable.ic_default_avatar)
                    .into(contactAvatar)
                
                root.setOnClickListener {
                    onContactClick(contact)
                    // 关闭对话框
                    (root.parent?.parent?.parent as? androidx.appcompat.app.AlertDialog)?.dismiss()
                }
            }
        }
        
        override fun getItemCount() = contacts.size
    }
}