package com.susking.ephone_s.settings.ui.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.susking.ephone_s.settings.ui.other.ApiSettingsApiFragment
import com.susking.ephone_s.settings.ui.other.ApiSettingsAppFragment
import com.susking.ephone_s.settings.ui.other.ApiSettingsFeaturesFragment

class ApiSettingsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ApiSettingsApiFragment()
            1 -> ApiSettingsFeaturesFragment()
            2 -> ApiSettingsAppFragment()
            else -> throw IllegalStateException("Invalid adapter position $position")
        }
    }
}