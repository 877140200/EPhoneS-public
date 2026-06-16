package com.susking.ephone_s.tavern.api

import androidx.fragment.app.Fragment
import com.susking.ephone_s.tavern.ui.TavernFragment

/**
 * 酒馆模块入口。
 *
 * 对外只暴露 Fragment 工厂，桌面导航通过本入口获取酒馆界面，
 * 与 [com.susking.ephone_s.eventgraph.api.EventGraphApi] 等模块保持一致的解耦方式。
 */
object TavernApi {
    fun createTavernFragment(): Fragment = TavernFragment.newInstance()
}
