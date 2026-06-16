package com.susking.ephone_s.settings.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayoutMediator
import com.susking.ephone_s.settings.databinding.FragmentApiSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Settings 主界面 Fragment
 * 使用 Hilt 进行依赖注入
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentApiSettingsBinding? = null
    private val binding get() = _binding!!

    // 使用 Hilt 自动注入 ViewModel
    private val viewModel: ApiSettingsViewModel by viewModels()
    
    private lateinit var pagerAdapter: ApiSettingsPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 【新增】处理窗口边衬，避免UI与系统栏重叠
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 将顶部和底部的 insets 应用为 padding
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViewPager()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // 【关键修复】在返回前，强制保存所有设置
            viewModel.saveSettings()
            // 直接从 FragmentManager 弹出返回栈，不再依赖 NavController
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupViewPager() {
        pagerAdapter = ApiSettingsPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2 // 强制预加载所有页面

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "API 设置"
                1 -> "功能设置"
                2 -> "应用设置"
                else -> null
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}