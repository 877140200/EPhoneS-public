package com.susking.ephone_s.qq.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.susking.ephone_s.qq.databinding.FragmentQqNotificationsBinding

class QqNotificationsFragment : Fragment() {

    private var _binding: FragmentQqNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQqNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupTabs()
        setupToolbar()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    private fun setupViewPager() {
        binding.viewPager.adapter = NotificationsPagerAdapter(this)
    }

    private fun setupTabs() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }

    private fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "全部"
            1 -> "点赞"
            2 -> "评论和@"
            3 -> "转发"
            4 -> "其他"
            else -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class NotificationsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 5 // For "全部", "点赞", "评论和@", "转发", "其他"

        override fun createFragment(position: Int): Fragment {
            // TODO: Return the appropriate fragment for each tab
            return Fragment() // Placeholder
        }
    }
}