package com.susking.ephone_s.desktop

import androidx.fragment.app.Fragment
import com.susking.ephone_s.alipay.AlipayFragmentProviderImpl
import com.susking.ephone_s.desktop.api.FragmentProvider
import com.susking.ephone_s.features.theme.ui.ThemeFragment
import com.susking.ephone_s.features.worldbook.ui.list.WorldBookFragment
import com.susking.ephone_s.qq.ui.QqMainFragment
import com.susking.ephone_s.settings.api.SettingsApi
import com.susking.ephone_s.schedule.ui.ScheduleFragment
import com.susking.ephone_s.shopping.ui.container.ShoppingContainerFragment
import javax.inject.Inject

/**
 * Fragment 提供者实现
 * 负责创建 app 模块中未模块化的 Fragment,供 desktop 模块使用
 */
class FragmentProviderImpl @Inject constructor() : FragmentProvider {
    
    override fun createQqFragment(): Fragment {
        return QqMainFragment.newInstance()
    }
    
    override fun createWorldBookFragment(): Fragment {
        return WorldBookFragment.newInstance()
    }
    
    override fun createThemeFragment(): Fragment {
        return ThemeFragment.newInstance()
    }
    
    override fun createSettingsFragment(): Fragment {
        return SettingsApi.createSettingsFragment()
    }
    
    override fun createPresetFragment(): Fragment? {
        // TODO: 预设功能尚未实现
        return null
    }
    
    override fun createShoppingFragment(): Fragment {
        return ShoppingContainerFragment.newInstance()
    }
    
    override fun createAlipayFragment(): Fragment {
        return AlipayFragmentProviderImpl().createAlipayFragment()
    }

    override fun createScheduleFragment(): Fragment {
        return ScheduleFragment.newInstance()
    }
}