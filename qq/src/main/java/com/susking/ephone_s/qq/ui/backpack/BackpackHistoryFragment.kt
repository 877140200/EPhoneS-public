package com.susking.ephone_s.qq.ui.backpack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.qq.R
import com.susking.ephone_s.qq.databinding.FragmentBackpackHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 背包历史Fragment
 * 
 * 显示已丢弃/赠送/卖出的物品历史记录
 */
@AndroidEntryPoint
class BackpackHistoryFragment : Fragment() {
    
    private var _binding: FragmentBackpackHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BackpackHistoryViewModel by viewModels()
    
    private val adapter by lazy {
        BackpackHistoryAdapter { item ->
            // 删除单条历史记录
            showDeleteConfirmDialog(item.id, item.productName)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackpackHistoryBinding.inflate(inflater, container, false)
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
     * 设置工具栏
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        binding.recyclerViewHistory.adapter = adapter
    }
    
    /**
     * 设置FAB按钮
     */
    private fun setupFab() {
        binding.fabClearHistory.setOnClickListener {
            showClearAllConfirmDialog()
        }
    }
    
    /**
     * 观察ViewModel数据
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 历史记录列表
                launch {
                    viewModel.historyItems.collect { items ->
                        adapter.submitList(items)
                        
                        // 显示/隐藏空状态
                        if (items.isEmpty()) {
                            binding.recyclerViewHistory.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.fabClearHistory.visibility = View.GONE
                        } else {
                            binding.recyclerViewHistory.visibility = View.VISIBLE
                            binding.layoutEmpty.visibility = View.GONE
                            binding.fabClearHistory.visibility = View.VISIBLE
                        }
                    }
                }
                
                // 加载状态
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                // 错误信息
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 显示删除单条记录确认对话框
     */
    private fun showDeleteConfirmDialog(itemId: Long, itemName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_history_item)
            .setMessage(getString(R.string.delete_history_item_message, itemName))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteHistoryItem(itemId)
                Snackbar.make(binding.root, R.string.history_item_deleted, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * 显示清空所有历史记录确认对话框
     */
    private fun showClearAllConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_all_history)
            .setMessage(R.string.clear_all_history_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearAllHistory()
                Snackbar.make(binding.root, R.string.all_history_cleared, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = BackpackHistoryFragment()
    }
}