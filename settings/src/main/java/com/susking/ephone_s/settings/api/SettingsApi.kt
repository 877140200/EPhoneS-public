package com.susking.ephone_s.settings.api

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.settings.feedback.FeedbackActivity
import com.susking.ephone_s.settings.update.UpdateManager

/**
 * Settings 模块对外 API
 * 其他模块通过此接口访问 settings 功能
 */
object SettingsApi {
    
    /**
     * 创建设置界面 Fragment
     * @return 设置主界面 Fragment
     */
    fun createSettingsFragment(): Fragment {
        return com.susking.ephone_s.settings.ui.main.SettingsFragment()
    }
    
    /**
     * 创建 NovelAI 设置对话框
     * @return NovelAI 设置对话框 DialogFragment
     */
    fun createNovelAiSettingsDialog(): DialogFragment {
        return com.susking.ephone_s.settings.ui.novelai.NovelAiSettingsDialogFragment()
    }
    
    /**
     * 创建角色 NovelAI 设置对话框
     * @param contact 角色Profile对象
     * @return 角色 NovelAI 设置对话框 DialogFragment
     */
    fun createCharacterNaiSettingsDialog(contact: PersonProfile): DialogFragment {
        return com.susking.ephone_s.settings.ui.novelai.CharacterNaiSettingsDialogFragment.newInstance(contact)
    }
    
    /**
     * 创建 NovelAI 测试生成对话框
     * @return NovelAI 测试生成对话框 DialogFragment
     */
    fun createNovelAiTestGenDialog(): DialogFragment {
        return com.susking.ephone_s.settings.ui.novelai.NovelAiTestGenDialogFragment()
    }
    
    /**
     * 打开功能一览页面
     * @param context Context
     */
    fun openFeatureOverviewActivity(context: Context) {
        val intent = Intent(context, com.susking.ephone_s.settings.ui.features.FeatureOverviewActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * 打开关于页面
     * @param context Context
     */
    fun openAboutActivity(context: Context) {
        val intent = Intent(context, com.susking.ephone_s.settings.ui.about.AboutActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * 打开权限管理页面
     * 集中管理来电相关的通知、全屏通知、悬浮窗三项权限
     * @param context Context
     */
    fun openPermissionActivity(context: Context) {
        val intent = Intent(context, com.susking.ephone_s.settings.ui.permission.PermissionActivity::class.java)
        context.startActivity(intent)
    }
    
    /**
     * 打开隐私政策页面
     * @param context Context
     */
    fun openPrivacyPolicy(context: Context) {
        val intent = Intent(context, com.susking.ephone_s.settings.ui.legal.TextViewerActivity::class.java).apply {
            putExtra(com.susking.ephone_s.settings.ui.legal.TextViewerActivity.EXTRA_TITLE, "隐私政策")
            putExtra(com.susking.ephone_s.settings.ui.legal.TextViewerActivity.EXTRA_ASSET_FILE,
                com.susking.ephone_s.settings.ui.legal.TextViewerActivity.PRIVACY_POLICY)
        }
        context.startActivity(intent)
    }
    
    /**
     * 打开开源许可证页面
     * @param context Context
     */
    fun openOpenSourceLicenses(context: Context) {
        val intent = Intent(context, com.susking.ephone_s.settings.ui.legal.TextViewerActivity::class.java).apply {
            putExtra(com.susking.ephone_s.settings.ui.legal.TextViewerActivity.EXTRA_TITLE, "开源许可证")
            putExtra(com.susking.ephone_s.settings.ui.legal.TextViewerActivity.EXTRA_ASSET_FILE,
                com.susking.ephone_s.settings.ui.legal.TextViewerActivity.OPEN_SOURCE_LICENSES)
        }
        context.startActivity(intent)
    }

    /**
     * 发起应用更新检查。
     *
     * 内部串联版本检查、更新弹窗、安装权限引导与下载安装的完整流程。
     * 需要 [AppCompatActivity] 以使用其生命周期作用域与弹窗能力。
     *
     * @param activity 发起检查的宿主 Activity
     * @param isSilent 是否静默模式：true 仅在发现新版时弹窗（用于启动自动检查），
     *                 false 全程给出反馈（用于用户主动点击检查）
     */
    fun checkForUpdate(activity: AppCompatActivity, isSilent: Boolean) {
        UpdateManager(activity).checkForUpdate(isSilent)
    }

    /**
     * 打开意见反馈页面。
     * @param context Context
     */
    fun openFeedback(context: Context) {
        val intent = Intent(context, FeedbackActivity::class.java)
        context.startActivity(intent)
    }
}