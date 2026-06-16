package com.susking.ephone_s.shopping.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.susking.ephone_s.shopping.R
import com.susking.ephone_s.shopping.databinding.FragmentShoppingContainerBinding
import com.susking.ephone_s.shopping.ui.cart.ShoppingCartFragment
import com.susking.ephone_s.shopping.ui.home.HomeTabFragment
import com.susking.ephone_s.shopping.ui.message.MessageTabFragment
import com.susking.ephone_s.shopping.ui.order.OrderListFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * 商城容器Fragment
 * 
 * 管理底部导航栏和4个Tab页面：
 * - 首页
 * - 消息
 * - 购物车
 * - 我的淘宝
 */
@AndroidEntryPoint
class ShoppingContainerFragment : Fragment() {
    
    private var _binding: FragmentShoppingContainerBinding? = null
    private val binding get() = _binding!!
    
    // 缓存Fragment实例以保持状态
    private var homeTabFragment: HomeTabFragment? = null
    private var messageTabFragment: MessageTabFragment? = null
    private var cartFragment: ShoppingCartFragment? = null
    private var orderFragment: OrderListFragment? = null
    
    private var currentFragment: Fragment? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingContainerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBottomNavigation()
        
        // 默认显示首页
        if (savedInstanceState == null) {
            showHomeTab()
        }
    }
    
    /**
     * 设置底部导航栏
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeTab()
                    true
                }
                R.id.nav_message -> {
                    showMessageTab()
                    true
                }
                R.id.nav_cart -> {
                    showCartTab()
                    true
                }
                R.id.nav_orders -> {
                    showOrdersTab()
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 显示首页Tab
     */
    private fun showHomeTab() {
        if (homeTabFragment == null) {
            homeTabFragment = HomeTabFragment.newInstance()
        }
        switchFragment(homeTabFragment!!)
    }
    
    /**
     * 显示消息Tab
     */
    private fun showMessageTab() {
        if (messageTabFragment == null) {
            messageTabFragment = MessageTabFragment.newInstance()
        }
        switchFragment(messageTabFragment!!)
    }
    
    /**
     * 显示购物车Tab
     */
    private fun showCartTab() {
        if (cartFragment == null) {
            cartFragment = ShoppingCartFragment.newInstance()
        }
        switchFragment(cartFragment!!)
    }
    
    /**
     * 显示订单Tab
     */
    private fun showOrdersTab() {
        if (orderFragment == null) {
            orderFragment = OrderListFragment.newInstance()
        }
        switchFragment(orderFragment!!)
    }
    
    /**
     * 切换Fragment
     */
    private fun switchFragment(fragment: Fragment) {
        if (fragment == currentFragment) {
            return
        }
        
        val transaction = childFragmentManager.beginTransaction()
        
        // 隐藏当前Fragment
        currentFragment?.let {
            transaction.hide(it)
        }
        
        // 显示或添加新Fragment
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragmentContainer, fragment)
        }
        
        transaction.commit()
        currentFragment = fragment
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = ShoppingContainerFragment()
    }
}