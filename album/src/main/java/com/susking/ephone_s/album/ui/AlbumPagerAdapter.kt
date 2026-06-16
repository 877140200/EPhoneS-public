package com.susking.ephone_s.album.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class AlbumPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AllPhotosFragment.newInstance()
            1 -> AlbumListFragment.newInstance()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
