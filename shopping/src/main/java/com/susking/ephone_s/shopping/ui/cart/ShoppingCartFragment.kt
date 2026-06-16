package com.susking.ephone_s.shopping.ui.cart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.susking.ephone_s.core.util.EventBus
import com.susking.ephone_s.core.util.ShowPaymentDialogEvent
import com.susking.ephone_s.shopping.databinding.FragmentShoppingCartBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 购物车Fragment
 * 
 * 展示购物车中的商品,支持修改数量、删除和结算
 */
@AndroidEntryPoint
class ShoppingCartFragment : Fragment() {
    
    private var _binding: FragmentShoppingCartBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ShoppingCartViewModel by viewModels()
    
    private lateinit var cartAdapter: ShoppingCartAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingCartBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }
    
    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "购物车"
            
            // 清空购物车
            inflateMenu(com.susking.ephone_s.shopping.R.menu.menu_shopping_cart)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    com.susking.ephone_s.shopping.R.id.action_clear_cart -> {
                        showClearCartDialog()
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        cartAdapter = ShoppingCartAdapter(
            onQuantityChanged = { cartItem, newQuantity ->
                viewModel.updateQuantity(cartItem.id, newQuantity)
            },
            onRemoveClick = { cartItem ->
                viewModel.removeItem(cartItem.id)
            }
        )
        
        binding.recyclerViewCart.apply {
            adapter = cartAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }
    
    /**
     * 设置按钮
     */
    private fun setupButtons() {
        // 结算按钮
        binding.buttonCheckout.setOnClickListener {
            viewModel.checkout()
        }
    }
    
    /**
     * 观察ViewModel状态
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察购物车商品列表
                launch {
                    viewModel.cartItems.collect { items ->
                        cartAdapter.submitList(items)
                        
                        // 显示或隐藏空状态
                        if (items.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.layoutCart.visibility = View.GONE
                        } else {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.layoutCart.visibility = View.VISIBLE
                        }
                    }
                }
                
                // 观察总金额
                launch {
                    viewModel.totalAmount.collect { total ->
                        binding.textViewTotalAmount.text = String.format("¥%.2f", total)
                    }
                }
                
                // 观察错误消息
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            viewModel.clearErrorMessage()
                        }
                    }
                }
                
                // 观察成功消息
                launch {
                    viewModel.successMessage.collect { message ->
                        message?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.clearSuccessMessage()
                        }
                    }
                }
                
                // 观察支付对话框显示事件
                launch {
                    viewModel.showPaymentDialog.collect { shouldShow ->
                        if (shouldShow) {
                            showPaymentConfirmationDialog()
                            viewModel.resetPaymentDialogState()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 显示支付确认对话框
     * 通过EventBus发送事件,由App模块监听并显示
     */
    private fun showPaymentConfirmationDialog() {
        EventBus.post(
            ShowPaymentDialogEvent(
                orderAmount = viewModel.totalAmount.value,
                onConfirm = {
                    viewModel.confirmPayment()
                }
            )
        )
    }
    
    /**
     * 显示清空购物车对话框
     */
    private fun showClearCartDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清空购物车")
            .setMessage("确定要清空购物车吗?")
            .setPositiveButton("确定") { _, _ ->
                viewModel.clearCart()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = ShoppingCartFragment()
    }
}