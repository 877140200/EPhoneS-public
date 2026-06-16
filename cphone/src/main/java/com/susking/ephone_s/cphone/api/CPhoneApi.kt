package com.susking.ephone_s.cphone.api

import androidx.fragment.app.Fragment
import com.susking.ephone_s.cphone.ui.CPhoneMainFragment

/**
 * CPhone模块的公开API接口
 * 供app模块调用以启动CPhone功能
 */
object CPhoneApi {
    
    /**
     * 创建CPhone主界面Fragment
     * @return CPhone的主Fragment实例
     */
    fun createCPhoneFragment(): Fragment {
        // 直接创建Fragment实例
        return CPhoneMainFragment.newInstance()
    }
}