package com.susking.ephone_s.shopping.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.shopping.databinding.FragmentOrderListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 订单列表Fragment
 * 
 * 显示用户的所有订单
 */
@AndroidEntryPoint
class OrderListFragment : Fragment() {
    
    private var _binding: FragmentOrderListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: OrderListViewModel by viewModels()
    
    private lateinit var orderAdapter: OrderListAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
//        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        
        // 检查是否需要筛选特定联系人的订单
        val chatId = arguments?.getString(ARG_CHAT_ID)
        if (chatId != null) {
            viewModel.filterByChatId(chatId)
        }
    }
    
//    /**
//     * 设置Toolbar
//     */
//    private fun setupToolbar() {
//        binding.toolbar.setNavigationOnClickListener {
//            requireActivity().onBackPressed()
//        }
//    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        orderAdapter = OrderListAdapter(
            onOrderClick = { order ->
                viewModel.viewOrderDetail(order)
            },
            onDeleteClick = { order ->
                showDeleteConfirmDialog(order.id)
            }
        )
        
        binding.recyclerViewOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderAdapter
        }
    }
    
    /**
     * 观察ViewModel
     */
    private fun observeViewModel() {
        // 订单列表
        lifecycleScope.launch {
            viewModel.orders.collect { orders ->
                orderAdapter.submitList(orders)
                
                // 显示/隐藏空状态
                if (orders.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.recyclerViewOrders.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.recyclerViewOrders.visibility = View.VISIBLE
                }
            }
        }
        
        // 加载状态
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // TODO: 可以添加ProgressBar显示加载状态
        }
        
        // UI事件
        viewModel.uiEvent.observe(viewLifecycleOwner) { event ->
            when (event) {
                is OrderListViewModel.UiEvent.ShowError -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
                is OrderListViewModel.UiEvent.ShowSuccess -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                }
                is OrderListViewModel.UiEvent.NavigateToDetail -> {
                    // TODO: 导航到订单详情页面
                    Toast.makeText(
                        requireContext(),
                        "订单详情功能开发中",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(orderId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除订单")
            .setMessage("确定要删除这个订单吗?")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteOrder(orderId)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_CHAT_ID = "chat_id"
        
        /**
         * 创建新实例(显示所有订单)
         */
        fun newInstance(): OrderListFragment {
            return OrderListFragment()
        }
        
        /**
         * 创建新实例(筛选特定联系人的订单)
         */
        fun newInstance(chatId: String): OrderListFragment {
            return OrderListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_ID, chatId)
                }
            }
        }
    }
}