package com.susking.ephone_s.schedule.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 课程表页面系统栏避让工具。
 * 所有课程表 Fragment 都通过这里统一处理状态栏和导航栏内边距，避免内容被系统栏遮挡。
 */
object ScheduleSystemBarHelper {

    fun applySystemBarPadding(rootView: View): Unit {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
