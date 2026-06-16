package com.susking.ephone_s.qq.ui.backpack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.qq.databinding.FragmentBackpackBinding
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.forward.ForwardCallback
import com.susking.ephone_s.qq.ui.forward.ForwardSelectorFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 背包Fragment
 * 
 * 显示用户背包中的所有物品
 */
@AndroidEntryPoint
class BackpackFragment : Fragment() {
    
    private var _binding: FragmentBackpackBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BackpackViewModel by viewModels()
    private val qqViewModel: QqViewModel by activityViewModels()
    
    @Inject lateinit var backpackRepository: BackpackRepository
    @Inject lateinit var qqChatManager: QqChatManager
    
    private lateinit var backpackAdapter: BackpackItemAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackpackBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }
    
    /**
     * 设置全面屏适配
     */
    private fun setupWindowInsets() {
        // 为 AppBarLayout 应用顶部内边距，避开状态栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        
        // 为根布局应用底部内边距，避开导航栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
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
     * 设置FAB按钮
     */
    private fun setupFab() {
        binding.fabViewHistory.setOnClickListener {
            // 跳转到背包历史界面
            // 使用与BackpackFragment相同的容器ID
            parentFragmentManager.beginTransaction()
                .replace(com.susking.ephone_s.qq.R.id.fragment_container_for_persona, BackpackHistoryFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        backpackAdapter = BackpackItemAdapter(
            onDiscardClick = { item ->
                showDiscardConfirmDialog(item)
            },
            onGiftClick = { item ->
                showGiftContactSelector(item)
            }
        )
        
        binding.recyclerViewBackpack.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = backpackAdapter
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        // 观察物品列表
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect { items ->
                backpackAdapter.submitList(items)
                
                // 显示/隐藏空状态
                binding.layoutEmpty.isVisible = items.isEmpty()
                binding.recyclerViewBackpack.isVisible = items.isNotEmpty()
            }
        }
        
        // 观察加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressIndicator.isVisible = isLoading
            }
        }
        
        // 观察错误信息
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { message ->
                message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    /**
     * 显示丢弃确认对话框
     */
    private fun showDiscardConfirmDialog(item: com.susking.ephone_s.qq.data.model.BackpackItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("丢弃物品")
            .setMessage("确定要丢弃「${item.productName}」吗？\n\n注意：丢弃后物品将从背包中移除，但订单记录会保留。")
            .setPositiveButton("丢弃") { _, _ ->
                viewModel.discardItem(item.id)
                Toast.makeText(requireContext(), "已丢弃", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示联系人选择器用于赠送
     */
    private fun showGiftContactSelector(item: com.susking.ephone_s.qq.data.model.BackpackItem) {
        val fragment = ForwardSelectorFragment.newInstance(
            contentType = "backpack_gift",
            contentId = item.id.toString(),
            callback = object : ForwardCallback {
                override fun onContactsSelected(
                    contactIds: List<String>,
                    contentType: String?,
                    contentId: String?
                ) {
                    // 单选模式下只有一个联系人
                    val contactId = contactIds.firstOrNull() ?: return
                    confirmGiftToContact(item, contactId)
                }
            }
        )
        
        // 使用父FragmentManager显示联系人选择器
        parentFragmentManager.beginTransaction()
            .replace(com.susking.ephone_s.qq.R.id.fragment_container_for_persona, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    /**
     * 确认赠送给指定联系人
     */
    private fun confirmGiftToContact(
        item: com.susking.ephone_s.qq.data.model.BackpackItem,
        contactId: String
    ) {
        // 获取联系人名称
        val contact = qqViewModel.qqContactManager.contacts.value?.find { it.id == contactId }
        val contactName = contact?.remarkName ?: contact?.realName ?: "未知联系人"
        
        // 创建备注输入框
        val editText = EditText(requireContext()).apply {
            hint = "（可选）输入赠礼备注"
            setPadding(40, 40, 40, 40)
        }
        
        // 显示确认对话框
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("赠送礼物")
            .setMessage("确定要赠送「${item.productName}」给 $contactName 吗?\n\n物品价值: ${item.getFormattedPrice()}")
            .setView(editText)
            .setPositiveButton("确定赠送") { _, _ ->
                val giftNote = editText.text.toString().trim().ifEmpty { null }
                performGiftOperation(item, contactId, contactName, giftNote)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行赠送操作
     */
    private fun performGiftOperation(
        item: com.susking.ephone_s.qq.data.model.BackpackItem,
        contactId: String,
        recipientName: String,
        giftNote: String?
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. 记录背包操作(会自动从背包移除并加入历史)
                backpackRepository.recordGiftOperation(item.id, recipientName)
                
                // 2. 发送礼物消息到聊天记录
                qqChatManager.sendGiftMessage(
                    contactId = contactId,
                    giftItemId = item.id,
                    giftName = item.productName,
                    giftImageUrl = item.imageUrl,
                    giftValue = item.price,
                    giftNote = giftNote
                )
                
                Toast.makeText(
                    requireContext(),
                    "已赠送「${item.productName}」给 $recipientName",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "赠送失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        /**
         * 创建新实例
         */
        fun newInstance(): BackpackFragment {
            return BackpackFragment()
        }
    }
}