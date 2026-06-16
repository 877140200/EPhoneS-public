package com.susking.ephone_s.album.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.susking.ephone_s.album.R
import com.susking.ephone_s.album.api.AlbumNavigator
import com.susking.ephone_s.album.databinding.FragmentAlbumBinding
import com.susking.ephone_s.album.domain.model.Photo

class AlbumFragment : Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupToolbar()
    }

    private fun setupToolbar() {
        binding.albumToolbar.setOnMenuItemClickListener { menuItem ->
            val pagerAdapter = binding.viewPager.adapter as FragmentStateAdapter
            val currentFragment = childFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}")
            if (currentFragment is AllPhotosFragment) {
                when (menuItem.itemId) {
                    R.id.action_select_all -> {
                        currentFragment.selectAllPhotos()
                        true
                    }
                    R.id.action_clear_selection -> {
                        currentFragment.clearPhotoSelection()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }


    private fun setupViewPager() {
        // 创建一个适配器，用于向ViewPager2提供Fragment页面
        val pagerAdapter = object : FragmentStateAdapter(this) {
            private val fragments = listOf(AllPhotosFragment(), AlbumListFragment())
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        binding.viewPager.adapter = pagerAdapter

        // 使用TabLayoutMediator将TabLayout与ViewPager2连接起来
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "照片"
                1 -> "相册"
                else -> ""
            }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.albumToolbar.title = when (position) {
                    0 -> "照片"
                    1 -> "相册"
                    else -> ""
                }
                binding.albumToolbar.menu.clear() // 切换页面时清除菜单
            }
        })
    }

    fun updateToolbarForSelection(inSelectionMode: Boolean, selectionCount: Int) {
        binding.tabLayout.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        binding.viewPager.isUserInputEnabled = !inSelectionMode

        if (inSelectionMode) {
            val title = if (selectionCount > 0) "已选择 $selectionCount 项" else "选择项目"
            binding.albumToolbar.title = title
            binding.albumToolbar.menu.clear()
            binding.albumToolbar.inflateMenu(R.menu.menu_photo_selection)
        } else {
            binding.albumToolbar.title = when (binding.viewPager.currentItem) {
                0 -> "照片"
                1 -> "相册"
                else -> ""
            }
            binding.albumToolbar.menu.clear()
        }
    }

    fun navigateToPhotoViewer(photos: List<Photo>, position: Int) {
        val navigator = requireContext().applicationContext as? AlbumNavigator
        navigator?.navigateToPhotoViewer(parentFragmentManager, ArrayList(photos), position)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AlbumFragment()
    }
}
