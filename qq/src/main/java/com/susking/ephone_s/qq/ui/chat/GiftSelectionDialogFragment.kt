package com.susking.ephone_s.qq.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.repository.BackpackRepository
import com.susking.ephone_s.qq.data.model.BackpackItem
import com.susking.ephone_s.qq.databinding.DialogGiftSelectionBinding
import com.susking.ephone_s.qq.domain.manager.QqChatManager
import com.susking.ephone_s.qq.domain.manager.QqContactManager
import com.susking.ephone_s.qq.ui.QqViewModel
import com.susking.ephone_s.qq.ui.backpack.BackpackViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 礼物选择对话框
 * 
 * 显示背包中的物品供用户选择赠送
 */
@AndroidEntryPoint
class GiftSelectionDialogFragment : DialogFragment() {
    
    private var _binding: DialogGiftSelectionBinding? = null
    private val binding get() = _binding!!
    
    private val backpackViewModel: BackpackViewModel by viewModels()
    private val qqViewModel: QqViewModel by activityViewModels()
    
    @Inject lateinit var qqChatManager: QqChatManager
    @Inject lateinit var qqContactManager: QqContactManager
    @Inject lateinit var backpackRepository: BackpackRepository
    
    private lateinit var giftAdapter: GiftSelectionAdapter
    
    private var contactId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog)
        
        contactId = arguments?.getString(ARG_CONTACT_ID) ?: ""
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGiftSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        giftAdapter = GiftSelectionAdapter { item ->
            showGiftNoteDialog(item)
        }
        
        binding.recyclerViewGifts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = giftAdapter
        }
    }
    
    /**
     * 设置按钮
     */
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            backpackViewModel.items.collect { items ->
                giftAdapter.submitList(items)
                
                // 显示/隐藏空状态
                binding.emptyStateLayout.isVisible = items.isEmpty()
                binding.recyclerViewGifts.isVisible = items.isNotEmpty()
            }
        }
    }
    
    /**
     * 显示礼物备注输入对话框
     */
    private fun showGiftNoteDialog(item: BackpackItem) {
        val contact = qqViewModel.qqContactManager.contacts.value?.find { it.id == contactId }
        val contactName = contact?.remarkName ?: contact?.realName ?: "对方"
        val userName = qqViewModel.qqContactManager.userProfile.value?.nickname ?: "我"
        
        // 创建输入框
        val editText = EditText(requireContext()).apply {
            hint = "（可选）输入赠礼备注"
            setPadding(40, 40, 40, 40)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("赠送礼物")
            .setMessage("确定要赠送「${item.productName}」给 $contactName 吗?\n\n物品价值: ${item.getFormattedPrice()}")
            .setView(editText)
            .setPositiveButton("确定赠送") { _, _ ->
                val giftNote = editText.text.toString().trim().ifEmpty { null }
                sendGift(item, contactName, userName, giftNote)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 发送礼物
     */
    private fun sendGift(item: BackpackItem, contactName: String, userName: String, giftNote: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. 记录背包操作
                backpackRepository.recordGiftOperation(item.id, contactName)
                
                // 2. 发送礼物消息
                qqChatManager.sendGiftMessage(
                    contactId = contactId,
                    giftItemId = item.id,
                    giftName = item.productName,
                    giftImageUrl = item.imageUrl,
                    giftValue = item.price,
                    giftNote = giftNote
                )
                
                Toast.makeText(requireContext(), "礼物已送出", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "送礼失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "GiftSelectionDialogFragment"
        private const val ARG_CONTACT_ID = "contact_id"
        
        /**
         * 创建新实例
         */
        fun newInstance(contactId: String): GiftSelectionDialogFragment {
            return GiftSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONTACT_ID, contactId)
                }
            }
        }
    }
}