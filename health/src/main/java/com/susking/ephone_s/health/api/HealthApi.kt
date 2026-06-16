package com.susking.ephone_s.health.api

import androidx.fragment.app.Fragment
import com.susking.ephone_s.health.ui.HealthFragment

/**
 * 健康模块入口。
 *
 * 对外只暴露 Fragment 工厂，桌面导航通过本入口获取健康界面，
 * 与 [com.susking.ephone_s.tavern.api.TavernApi] 等模块保持一致的解耦方式。
 */
object HealthApi {
    fun createHealthFragment(): Fragment = HealthFragment.newInstance()
}
