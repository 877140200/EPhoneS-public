package com.susking.ephone_s.shopping.ui.account

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.aidata.domain.model.AuthorizedAccount
import com.susking.ephone_s.shopping.databinding.DialogSelectAccountBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 选择账号对话框
 * 
 * 显示已授权账号列表,并提供添加新账号的功能
 */
@AndroidEntryPoint
class SelectAccountDialogFragment : DialogFragment() {
    
    private var _binding: DialogSelectAccountBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SelectAccountViewModel by viewModels()
    
    private lateinit var adapter: AuthorizedAccountAdapter
    
    /**
     * 账号选择回调
     */
    var onAccountSelected: ((String) -> Unit)? = null
    
    /**
     * 添加账号回调(打开联系人选择器)
     */
    var onAddAccountClick: (() -> Unit)? = null
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSelectAccountBinding.inflate(layoutInflater)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = AuthorizedAccountAdapter(
            onAccountClick = { account ->
                onAccountSelected?.invoke(account.contactId)
                dismiss()
            },
            onDeleteClick = { account ->
                showDeleteConfirmDialog(account)
            },
            contactsMap = viewModel.contactsMap.value
        )
        
        binding.recyclerViewAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SelectAccountDialogFragment.adapter
        }
    }
    
    /**
     * 设置点击监听
     */
    private fun setupClickListeners() {
        binding.buttonAddAccount.setOnClickListener {
            onAddAccountClick?.invoke()
            dismiss()
        }
        
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察已授权账号列表
                launch {
                    viewModel.authorizedAccounts.collect { accounts ->
                        adapter.submitList(accounts)
                    }
                }
                
                // 观察联系人Map变化
                launch {
                    viewModel.contactsMap.collect { contactsMap ->
                        // 更新adapter的contactsMap
                        adapter = AuthorizedAccountAdapter(
                            onAccountClick = { account ->
                                onAccountSelected?.invoke(account.contactId)
                                dismiss()
                            },
                            onDeleteClick = { account ->
                                showDeleteConfirmDialog(account)
                            },
                            contactsMap = contactsMap
                        )
                        binding.recyclerViewAccounts.adapter = adapter
                        adapter.submitList(viewModel.authorizedAccounts.value)
                    }
                }
            }
        }
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(account: AuthorizedAccount) {
        // 创建自定义视图,包含复选框
        val customView = LayoutInflater.from(requireContext()).inflate(
            android.R.layout.select_dialog_multichoice,
            null
        )
        
        // 创建复选框
        val checkBox = CheckBox(requireContext()).apply {
            text = "保留该账号内的商品"
            isChecked = true // 默认勾选(保留商品)
            setPadding(50, 30, 50, 30)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除账号")
            .setMessage("确定要删除此账号吗?")
            .setView(checkBox)
            .setPositiveButton("删除") { _, _ ->
                val keepProducts = checkBox.isChecked
                viewModel.removeAccount(account.contactId, keepProducts)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "SelectAccountDialogFragment"
        
        fun newInstance(): SelectAccountDialogFragment {
            return SelectAccountDialogFragment()
        }
    }
}