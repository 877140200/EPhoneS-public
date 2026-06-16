package com.susking.ephone_s.settings.ui.about

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.settings.api.SettingsApi
import com.susking.ephone_s.settings.databinding.ActivityAboutBinding
import com.susking.ephone_s.settings.ui.legal.TextViewerActivity

/**
 * 关于页面
 * 显示应用信息、版本号,以及隐私政策和开源许可证的入口
 */
class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置沉浸式状态栏

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "关于"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        // 设置版本信息
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            
            binding.tvVersion.text = "版本 ${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            binding.tvVersion.text = "版本 1.0"
        }
    }

    private fun setupClickListeners() {
        // 隐私政策
        binding.itemPrivacyPolicy.setOnClickListener {
            openTextViewer(
                title = "隐私政策",
                assetFile = TextViewerActivity.PRIVACY_POLICY
            )
        }

        // 开源许可证
        binding.itemOpenSourceLicenses.setOnClickListener {
            openTextViewer(
                title = "开源许可证",
                assetFile = TextViewerActivity.OPEN_SOURCE_LICENSES
            )
        }

        // 检查更新：用户主动点击，使用非静默模式，全程给出反馈
        binding.itemCheckUpdate.setOnClickListener {
            SettingsApi.checkForUpdate(activity = this, isSilent = false)
        }

        // 意见反馈：打开纯文字反馈页
        binding.itemFeedback.setOnClickListener {
            SettingsApi.openFeedback(context = this)
        }
    }

    private fun openTextViewer(title: String, assetFile: String) {
        val intent = Intent(this, TextViewerActivity::class.java).apply {
            putExtra(TextViewerActivity.EXTRA_TITLE, title)
            putExtra(TextViewerActivity.EXTRA_ASSET_FILE, assetFile)
        }
        startActivity(intent)
    }
}