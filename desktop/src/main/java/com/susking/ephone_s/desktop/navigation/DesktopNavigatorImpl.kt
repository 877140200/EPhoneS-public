package com.susking.ephone_s.desktop.navigation

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.susking.ephone_s.album.ui.AlbumFragment
import com.susking.ephone_s.clouddreams.api.CloudDreamsApi
import com.susking.ephone_s.cphone.api.CPhoneApi
import com.susking.ephone_s.desktop.api.DesktopNavigator
import com.susking.ephone_s.eventgraph.api.EventGraphApi
import com.susking.ephone_s.desktop.api.FragmentProvider
import com.susking.ephone_s.tavern.api.TavernApi
import com.susking.ephone_s.health.api.HealthApi
import javax.inject.Inject

/**
 * Desktop 导航实现类
 * 负责处理桌面图标点击后的页面跳转逻辑
 *
 * 注意: QQ、世界书、主题、设置等功能目前在 app 模块的 features 包下,
 * 它们还没有被模块化。通过 FragmentProvider 接口从 app 模块获取这些 Fragment。
 */
class DesktopNavigatorImpl @Inject constructor(
    private val fragmentManager: FragmentManager,
    private val mainContainerId: Int,
    private val fragmentProvider: FragmentProvider
) : DesktopNavigator {

    override fun launchQq() {
        val fragment = fragmentProvider.createQqFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchWorldBook() {
        val fragment = fragmentProvider.createWorldBookFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchAlbum() {
        // Album 已经模块化,可以直接使用
        fragmentManager.beginTransaction()
            .replace(mainContainerId, AlbumFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    override fun launchTheme() {
        val fragment = fragmentProvider.createThemeFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchSettings() {
        val fragment = fragmentProvider.createSettingsFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchCloudDreams(context: Context) {
        // CloudDreams 已经模块化,通过 API 启动
        CloudDreamsApi.launchCloudDreams(context)
    }

    override fun launchCPhone() {
        // CPhone 已经模块化,通过 API 获取 Fragment
        fragmentManager.beginTransaction()
            .replace(mainContainerId, CPhoneApi.createCPhoneFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun launchPreset() {
        val fragment = fragmentProvider.createPresetFragment()
        if (fragment != null) {
            fragmentManager.beginTransaction()
                .replace(mainContainerId, fragment)
                .addToBackStack(null)
                .commit()
        } else {
            android.util.Log.w("DesktopNavigator", "Preset feature not implemented yet")
        }
    }

    override fun launchShopping() {
        val fragment = fragmentProvider.createShoppingFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    override fun launchAlipay() {
        val fragment = fragmentProvider.createAlipayFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchEventGraph() {
        fragmentManager.beginTransaction()
            .replace(mainContainerId, EventGraphApi.createEventGraphFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun launchSchedule() {
        val fragment = fragmentProvider.createScheduleFragment()
        fragmentManager.beginTransaction()
            .replace(mainContainerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun launchTavern() {
        // Tavern 已模块化,通过 API 获取 Fragment
        fragmentManager.beginTransaction()
            .replace(mainContainerId, TavernApi.createTavernFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun launchHealth() {
        // Health 已模块化,通过 API 获取 Fragment
        fragmentManager.beginTransaction()
            .replace(mainContainerId, HealthApi.createHealthFragment())
            .addToBackStack(null)
            .commit()
    }
}