package com.susking.ephone_s.qq.ui.chat.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.qq.databinding.DialogWorldBookBindingBinding
import com.susking.ephone_s.qq.ui.QqViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 世界书绑定配置对话框Fragment
 * 
 * 功能说明：
 * 1. 支持线上模式和线下模式的世界书绑定
 * 2. 每个模式分为全局和局部两个区域
 * 3. 全局世界书优先级高于局部世界书
 * 4. 当世界书在全局列表中时，会自动从局部列表中过滤掉
 */
@AndroidEntryPoint
class WorldBookBindingDialogFragment : Fragment() {

    private var _binding: DialogWorldBookBindingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QqViewModel by activityViewModels()
    
    @Inject
    lateinit var worldBookRepository: WorldBookRepository

    private var contactId: String? = null
    
    // 当前选择的模式：true=线上模式, false=线下模式
    private var isOnlineMode: Boolean = true
    
    // 全局和局部世界书适配器
    private lateinit var globalAdapter: BoundWorldBookAdapter
    private lateinit var localAdapter: BoundWorldBookAdapter
    
    // 当前绑定的世界书ID列表
    private val currentGlobalWorldBooks = mutableListOf<Long>()
    private val currentLocalWorldBooks = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = arguments?.getString(ARG_CONTACT_ID)
        if (contactId == null) {
            Toast.makeText(context, "错误：未找到联系人ID", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWorldBookBindingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupTabLayout()
        setupRecyclerViews()
        setupClickListeners()
        loadContactWorldBooks()
    }

    /**
     * 设置Toolbar
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 设置Tab布局
     */
    private fun setupTabLayout() {
        binding.modeTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // 线上模式
                        isOnlineMode = true
                        binding.modeDescriptionText.text = "线上模式：用于模拟在线聊天场景"
                        loadContactWorldBooks()
                    }
                    1 -> {
                        // 线下模式
                        isOnlineMode = false
                        binding.modeDescriptionText.text = "线下模式：用于模拟线下见面剧情"
                        loadContactWorldBooks()
                    }
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerViews() {
        // 全局世界书适配器
        globalAdapter = BoundWorldBookAdapter { worldBookId ->
            removeWorldBook(worldBookId, isGlobal = true)
        }
        binding.globalWorldBooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = globalAdapter
        }
        
        // 局部世界书适配器
        localAdapter = BoundWorldBookAdapter { worldBookId ->
            removeWorldBook(worldBookId, isGlobal = false)
        }
        binding.localWorldBooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = localAdapter
        }
    }

    /**
     * 设置点击监听器
     */
    private fun setupClickListeners() {
        // 添加全局世界书
        binding.addGlobalWorldBookButton.setOnClickListener {
            showWorldBookSelectionDialog(isGlobal = true)
        }
        
        // 添加局部世界书
        binding.addLocalWorldBookButton.setOnClickListener {
            showWorldBookSelectionDialog(isGlobal = false)
        }
        
        // 保存按钮
        binding.saveButton.setOnClickListener {
            saveWorldBookBindings()
        }
    }

    /**
     * 加载联系人的世界书绑定配置
     */
    private fun loadContactWorldBooks() {
        val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId } ?: return
        
        // 根据当前模式加载对应的世界书列表
        if (isOnlineMode) {
            currentGlobalWorldBooks.clear()
            currentGlobalWorldBooks.addAll(contact.onlineGlobalWorldBooks)
            currentLocalWorldBooks.clear()
            currentLocalWorldBooks.addAll(contact.onlineLocalWorldBooks)
        } else {
            currentGlobalWorldBooks.clear()
            currentGlobalWorldBooks.addAll(contact.offlineGlobalWorldBooks)
            currentLocalWorldBooks.clear()
            currentLocalWorldBooks.addAll(contact.offlineLocalWorldBooks)
        }
        
        // 刷新显示
        refreshWorldBookLists()
    }

    /**
     * 刷新世界书列表显示
     */
    private fun refreshWorldBookLists() {
        lifecycleScope.launch {
            // 获取所有世界书
            val allWorldBooks = worldBookRepository.getAllWorldBooks().first()
            
            // 过滤出全局和局部的世界书
            val globalWorldBooks = allWorldBooks.filter { it.worldBookId in currentGlobalWorldBooks }
            val localWorldBooks = allWorldBooks.filter { 
                it.worldBookId in currentLocalWorldBooks && it.worldBookId !in currentGlobalWorldBooks
            }
            
            // 更新适配器
            globalAdapter.submitList(globalWorldBooks)
            localAdapter.submitList(localWorldBooks)
        }
    }

    /**
     * 显示世界书选择对话框
     */
    private fun showWorldBookSelectionDialog(isGlobal: Boolean) {
        val dialog = SelectWorldBookDialogFragment.newInstance(
            excludedIds = if (isGlobal) {
                currentGlobalWorldBooks.toLongArray()
            } else {
                (currentGlobalWorldBooks + currentLocalWorldBooks).toLongArray()
            }
        )
        
        dialog.setOnWorldBooksSelectedListener { selectedIds ->
            if (isGlobal) {
                currentGlobalWorldBooks.addAll(selectedIds)
            } else {
                currentLocalWorldBooks.addAll(selectedIds)
            }
            refreshWorldBookLists()
        }
        
        dialog.show(childFragmentManager, "select_world_book")
    }

    /**
     * 移除世界书
     */
    private fun removeWorldBook(worldBookId: Long, isGlobal: Boolean) {
        if (isGlobal) {
            currentGlobalWorldBooks.remove(worldBookId)
        } else {
            currentLocalWorldBooks.remove(worldBookId)
        }
        refreshWorldBookLists()
    }

    /**
     * 保存世界书绑定配置
     */
    private fun saveWorldBookBindings() {
        val contact = viewModel.contactManager.contacts.value?.find { it.id == contactId }
        if (contact == null) {
            Toast.makeText(context, "保存失败：联系人数据丢失", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 更新联系人的世界书绑定配置
        val updatedContact = if (isOnlineMode) {
            contact.copy(
                onlineGlobalWorldBooks = currentGlobalWorldBooks.toList(),
                onlineLocalWorldBooks = currentLocalWorldBooks.toList()
            )
        } else {
            contact.copy(
                offlineGlobalWorldBooks = currentGlobalWorldBooks.toList(),
                offlineLocalWorldBooks = currentLocalWorldBooks.toList()
            )
        }
        
        viewModel.contactManager.updateContact(updatedContact)
        Toast.makeText(context, "世界书绑定配置已保存", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String): WorldBookBindingDialogFragment {
            return WorldBookBindingDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}