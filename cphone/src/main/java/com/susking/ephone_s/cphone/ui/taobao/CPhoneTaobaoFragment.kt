package com.susking.ephone_s.cphone.ui.taobao

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.susking.ephone_s.cphone.R
import com.susking.ephone_s.cphone.databinding.FragmentCphoneTaobaoBinding
import com.susking.ephone_s.aidata.domain.model.TaobaoPurchase
import com.susking.ephone_s.cphone.ui.CPhoneAppViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * CPhone淘宝App主界面
 * 显示购物订单列表和总花费统计
 */
@AndroidEntryPoint
class CPhoneTaobaoFragment : Fragment() {

    private var _binding: FragmentCphoneTaobaoBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CPhoneAppViewModel by viewModels()

    private lateinit var adapter: CPhoneTaobaoAdapter
    private var contactId: String = ""
    private val orderList = mutableListOf<TaobaoPurchase>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contactId = it.getString(ARG_CONTACT_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCphoneTaobaoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppBar()
        setupRecyclerView()
        observeData()
        observeRefreshState()
        loadOrders()
    }

    /**
     * 设置顶部导航栏
     */
    private fun setupAppBar() {
        // 获取Toolbar
        val toolbar = binding.root.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.toolbar
        )
        
        toolbar?.apply {
            title = "淘宝"
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            
            // 刷新按钮
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)?.setOnClickListener {
                refreshOrders()
            }
            
            // 生成图片按钮（只在淘宝页面显示）
            binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_generate_images)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    generateImages()
                }
            }
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CPhoneTaobaoAdapter(
            onOrderClick = { order ->
                // TODO: 点击订单后可以显示订单详情
                // 当前暂不实现详情页
            },
            onOrderEdit = { order ->
                handleEditOrder(order)
            },
            onOrderDelete = { order ->
                handleDeleteOrder(order)
            }
        )

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CPhoneTaobaoFragment.adapter
        }
    }

    /**
     * 观察淘宝订单数据
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getTaobaoData(contactId).collect { taobaoOrders ->
                orderList.clear()
                orderList.addAll(taobaoOrders)
                
                if (orderList.isEmpty()) {
                    showEmptyState()
                    updateTotalSpent(0.0)
                } else {
                    showContent()
                    adapter.submitList(orderList.toList())
                    updateTotalSpent(calculateTotalSpent())
                }
            }
        }
    }
    
    /**
     * 观察刷新状态
     */
    private fun observeRefreshState() {
        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CPhoneAppViewModel.RefreshState.Idle -> {
                    // 空闲状态
                }
                is CPhoneAppViewModel.RefreshState.Loading -> {
                    showLoading()
                }
                is CPhoneAppViewModel.RefreshState.Success -> {
                    if (state.appType == "taobao") {
                        Snackbar.make(binding.root, "购物记录刷新成功", Snackbar.LENGTH_SHORT).show()
                        viewModel.resetRefreshState()
                    }
                }
                is CPhoneAppViewModel.RefreshState.Error -> {
                    if (state.appType == "taobao") {
                        showContent()
                        Snackbar.make(
                            binding.root,
                            "刷新失败: ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                        viewModel.resetRefreshState()
                    }
                }
            }
        }
    }
    
    /**
     * 加载订单数据
     */
    private fun loadOrders() {
        // 数据通过observeData自动加载
    }

    /**
     * 刷新订单
     * 调用AI接口生成10-20条购物记录
     */
    private fun refreshOrders() {
        viewModel.refreshAppData(contactId, "taobao")
    }
    
    /**
     * 生成商品图片
     * 为所有没有图片的商品生成图片
     */
    private fun generateImages() {
        viewModel.generateTaobaoImages(contactId)
        Snackbar.make(
            binding.root,
            "已开始生成商品图片，请在大脑中查看进度",
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * 计算总花费
     */
    private fun calculateTotalSpent(): Double {
        return orderList.sumOf { it.price }
    }

    /**
     * 更新总花费显示
     */
    private fun updateTotalSpent(totalSpent: Double) {
        binding.tvTotalSpent.text = "¥${String.format("%.2f", totalSpent)}"
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.apply {
            rvOrders.visibility = View.GONE
            progressBar.visibility = View.GONE
            cardTotalSpent.visibility = View.GONE
            
            // 显示空状态布局
            val emptyStateView = root.findViewById<View>(R.id.empty_state)
            emptyStateView?.visibility = View.VISIBLE
            
            // 设置空状态文字
            root.findViewById<TextView>(R.id.tv_empty_message)?.text = 
                "暂无购物记录\n点击刷新生成AI购物记录"
        }
    }

    /**
     * 显示内容
     */
    private fun showContent() {
        binding.apply {
            rvOrders.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            cardTotalSpent.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }

    /**
     * 显示加载中
     */
    private fun showLoading() {
        binding.apply {
            rvOrders.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            cardTotalSpent.visibility = View.VISIBLE
            
            // 隐藏空状态布局
            root.findViewById<View>(R.id.empty_state)?.visibility = View.GONE
        }
    }
    
    /**
     * 处理编辑订单
     */
    private fun handleEditOrder(order: TaobaoPurchase) {
        // TODO: 实现编辑功能
        // 当前暂时显示提示信息
        Snackbar.make(
            binding.root,
            "编辑功能待实现：${order.itemName}",
            Snackbar.LENGTH_SHORT
        ).show()
    }
    
    /**
     * 处理删除订单
     */
    private fun handleDeleteOrder(order: TaobaoPurchase) {
        // 显示确认对话框
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除订单「${order.itemName}」吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteOrder(order)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行删除订单
     */
    private fun deleteOrder(order: TaobaoPurchase) {
        viewModel.deleteTaobaoOrder(contactId, order.id)
        
        Snackbar.make(
            binding.root,
            "已删除：${order.itemName}",
            Snackbar.LENGTH_SHORT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "contact_id"

        fun newInstance(contactId: String) = CPhoneTaobaoFragment().apply {
            arguments = bundleOf(ARG_CONTACT_ID to contactId)
        }
    }
}