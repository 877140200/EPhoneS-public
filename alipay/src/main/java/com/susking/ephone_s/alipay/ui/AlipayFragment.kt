package com.susking.ephone_s.alipay.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.susking.ephone_s.alipay.R
import com.susking.ephone_s.alipay.databinding.FragmentAlipayBinding

/**
 * 支付宝主界面Fragment
 * 管理底部导航栏和子Fragment切换
 */
class AlipayFragment : Fragment() {
    
    private var _binding: FragmentAlipayBinding? = null
    private val binding get() = _binding!!
    
    private var currentFragment: Fragment? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlipayBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupBottomNavigation()
        
        // 默认显示首页
        if (savedInstanceState == null) {
            showHomeFragment()
        }
    }
    
    /**
     * 设置窗口边距适配
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
     * 设置底部导航栏
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeFragment()
                    binding.toolbar.title = "支付宝"
                    true
                }
                R.id.nav_work -> {
                    showWorkClockFragment()
                    binding.toolbar.title = "上班打卡"
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 显示首页Fragment
     */
    private fun showHomeFragment() {
        val fragment = AlipayHomeFragment()
        switchFragment(fragment)
    }
    
    /**
     * 显示上班打卡Fragment
     */
    private fun showWorkClockFragment() {
        val fragment = WorkClockFragment()
        switchFragment(fragment)
    }
    
    /**
     * 切换Fragment
     */
    private fun switchFragment(fragment: Fragment) {
        if (currentFragment === fragment) return
        
        val transaction = childFragmentManager.beginTransaction()
        
        // 隐藏当前Fragment
        currentFragment?.let {
            transaction.hide(it)
        }
        
        // 显示或添加新Fragment
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragment_container, fragment)
        }
        
        transaction.commit()
        currentFragment = fragment
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}