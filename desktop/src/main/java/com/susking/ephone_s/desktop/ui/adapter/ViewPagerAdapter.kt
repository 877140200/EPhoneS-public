package com.susking.ephone_s.desktop.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.susking.ephone_s.desktop.ui.DesktopFragment

private const val NUM_PAGES = 2 // We now have two desktops

class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = NUM_PAGES

    override fun createFragment(position: Int): Fragment {
        // Create the corresponding DesktopFragment instance based on the position
        return DesktopFragment.newInstance(position)
    }
}