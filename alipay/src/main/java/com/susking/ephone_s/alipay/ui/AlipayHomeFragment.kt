package com.susking.ephone_s.alipay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.susking.ephone_s.alipay.databinding.FragmentAlipayHomeBinding
import kotlinx.coroutines.launch

/**
 * 支付宝首页Fragment
 * 显示钱包余额和账单列表
 */
class AlipayHomeFragment : Fragment() {
    
    private var _binding: FragmentAlipayHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: AlipayViewModel
    private lateinit var billAdapter: BillAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlipayHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[AlipayViewModel::class.java]
        
        setupUI()
        observeViewModel()
    }
    
    /**
     * 设置UI
     */
    private fun setupUI() {
        // 设置RecyclerView
        billAdapter = BillAdapter()
        binding.recyclerViewBills.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = billAdapter
        }
        
        // 设置刷新监听
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }
    }
    
    /**
     * 观察ViewModel数据变化
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察钱包信息
            viewModel.walletInfo.collect { wallet ->
                wallet?.let {
                    binding.textBalance.text = "¥ ${it.getFormattedBalance()}"
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察账单列表
            viewModel.billList.collect { bills ->
                billAdapter.submitList(bills)
                
                // 显示空状态
                if (bills.isEmpty()) {
                    binding.textEmptyState.visibility = View.VISIBLE
                    binding.recyclerViewBills.visibility = View.GONE
                } else {
                    binding.textEmptyState.visibility = View.GONE
                    binding.recyclerViewBills.visibility = View.VISIBLE
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察加载状态
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            // 观察错误消息
            viewModel.errorMessage.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}