package com.susking.ephone_s.settings.ui.permission

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.settings.databinding.ActivityPermissionBinding

/**
 * 权限管理页面
 *
 * 用途：集中管理小手机来电相关的三项关键权限，方便用户在拒绝授权后随时回来重新开启。
 * 三项权限：
 *  1. 通知权限（POST_NOTIFICATIONS）：用于后台来电时弹出通知。
 *  2. 全屏通知权限（USE_FULL_SCREEN_INTENT）：用于锁屏/后台时直接拉起全屏来电界面（Android 14+ 需用户手动授权）。
 *  3. 悬浮窗权限（SYSTEM_ALERT_WINDOW）：用于通话最小化为系统级悬浮窗。
 *
 * 每项权限展示当前授权状态，并提供「去授权」按钮跳转到对应的系统设置页。
 * onResume 时刷新所有状态，确保用户从系统设置返回后状态显示同步更新。
 */
class PermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面（含从系统设置返回）都刷新权限状态显示
        refreshAllPermissionStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 通知权限：跳转到本应用的通知设置页
        binding.buttonNotification.setOnClickListener {
            openNotificationSettings()
        }

        // 全屏通知权限：Android 14+ 跳转专用授权页，低版本回退到应用详情页
        binding.buttonFullScreen.setOnClickListener {
            openFullScreenIntentSettings()
        }

        // 悬浮窗权限：跳转到悬浮窗授权页
        binding.buttonOverlay.setOnClickListener {
            openOverlaySettings()
        }
    }

    /**
     * 刷新三项权限的状态显示
     */
    private fun refreshAllPermissionStatus() {
        updateStatusText(
            isGranted = hasNotificationPermission(),
            statusViewSetter = { text, color ->
                binding.textNotificationStatus.text = text
                binding.textNotificationStatus.setTextColor(color)
            }
        )
        updateStatusText(
            isGranted = hasFullScreenIntentPermission(),
            statusViewSetter = { text, color ->
                binding.textFullScreenStatus.text = text
                binding.textFullScreenStatus.setTextColor(color)
            }
        )
        updateStatusText(
            isGranted = hasOverlayPermission(),
            statusViewSetter = { text, color ->
                binding.textOverlayStatus.text = text
                binding.textOverlayStatus.setTextColor(color)
            }
        )
    }

    /**
     * 根据授权状态统一设置状态文本与颜色
     *
     * @param isGranted 是否已授权
     * @param statusViewSetter 接收（文本, 颜色）并应用到对应状态 TextView 的回调
     */
    private fun updateStatusText(
        isGranted: Boolean,
        statusViewSetter: (text: String, color: Int) -> Unit
    ) {
        if (isGranted) {
            statusViewSetter("已开启", COLOR_GRANTED)
        } else {
            statusViewSetter("未开启", COLOR_DENIED)
        }
    }

    // region 权限状态检查

    /**
     * 检查通知权限是否已开启
     */
    private fun hasNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    /**
     * 检查全屏通知权限是否已开启
     * Android 14（UPSIDE_DOWN_CAKE）起需用户显式授权；低版本默认拥有该能力。
     */
    private fun hasFullScreenIntentPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.canUseFullScreenIntent()
    }

    /**
     * 检查悬浮窗权限是否已开启
     */
    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    // endregion

    // region 跳转系统设置

    /**
     * 跳转到本应用的通知设置页
     */
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivitySafely(intent, fallbackToAppDetails = true)
    }

    /**
     * 跳转到全屏通知权限授权页
     * Android 14+ 有专用页面；低版本回退到应用详情页。
     */
    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:$packageName")
            )
            startActivitySafely(intent, fallbackToAppDetails = true)
        } else {
            openAppDetailsSettings()
        }
    }

    /**
     * 跳转到悬浮窗权限授权页
     */
    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivitySafely(intent, fallbackToAppDetails = true)
    }

    /**
     * 跳转到应用详情页（作为各类授权页跳转失败时的兜底）
     */
    private fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivitySafely(intent, fallbackToAppDetails = false)
    }

    /**
     * 安全启动 Activity，捕获设备无对应设置页的异常
     *
     * @param intent 目标意图
     * @param fallbackToAppDetails 启动失败时是否回退到应用详情页
     */
    private fun startActivitySafely(intent: Intent, fallbackToAppDetails: Boolean) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (fallbackToAppDetails) {
                openAppDetailsSettings()
            }
        }
    }

    // endregion

    companion object {
        // 已授权状态文本颜色（绿色）
        private const val COLOR_GRANTED: Int = 0xFF2E7D32.toInt()

        // 未授权状态文本颜色（红色）
        private const val COLOR_DENIED: Int = 0xFFC62828.toInt()
    }
}
