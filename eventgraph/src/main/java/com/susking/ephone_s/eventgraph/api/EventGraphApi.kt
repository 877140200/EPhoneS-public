package com.susking.ephone_s.eventgraph.api

import androidx.fragment.app.Fragment
import com.susking.ephone_s.eventgraph.ui.EventGraphFragment

/**
 * 事件图谱模块入口。
 */
object EventGraphApi {
    fun createEventGraphFragment(): Fragment {
        return EventGraphFragment.newInstance()
    }
}
