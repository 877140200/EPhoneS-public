package com.susking.ephone_s.core.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 应用中所有 Activity 的基类，用于处理通用逻辑，例如设置全面屏。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 将内容区域扩展到系统栏（状态栏和导航栏）
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
