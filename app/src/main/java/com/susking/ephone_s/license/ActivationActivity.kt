package com.susking.ephone_s.license

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.MainActivity
import com.susking.ephone_s.core.license.DeviceFingerprint
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.databinding.ActivityActivationBinding
import kotlinx.coroutines.launch

/**
 * 激活界面。
 *
 * 未激活的设备启动 app 时由 [MainActivity] 闸门跳转至此。用户输入一人一码的激活码，
 * app 自动携带设备指纹提交到 Worker 验证，通过后保存本地激活状态并进入主界面。
 *
 * 体验要点：
 *  - 设备指纹由 app 自动携带，用户只需输入激活码这“一步”。
 *  - 免费 workers.dev 国内访问不稳，网络失败时给出友好提示并允许直接重试。
 */
class ActivationActivity : BaseActivity() {

    private lateinit var binding: ActivityActivationBinding

    private val licenseManager: LicenseManager by lazy { LicenseManager(this) }
    private val remoteService: LicenseRemoteService by lazy { LicenseRemoteService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已激活设备不应再进入此页（防止用户手动绕回），直接放行到主界面。
        if (licenseManager.isActivated()) {
            navigateToMain()
            return
        }

        binding = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnActivate.setOnClickListener {
            val code: String = binding.etActivationCode.text?.toString()?.trim().orEmpty()
            if (code.isEmpty()) {
                showError("请输入激活码")
                return@setOnClickListener
            }
            executeActivation(code)
        }
    }

    /**
     * 发起激活验证流程。
     * @param code 用户输入的激活码
     */
    private fun executeActivation(code: String) {
        setLoading(true)
        clearError()
        lifecycleScope.launch {
            val fingerprint: String = DeviceFingerprint.getFingerprint(this@ActivationActivity)
            val result: ActivationResult = remoteService.activate(code, fingerprint)
            setLoading(false)
            handleResult(code, result)
        }
    }

    /**
     * 处理激活结果。
     * @param code 本次提交的激活码，成功时留存
     * @param result 验证结果
     */
    private fun handleResult(code: String, result: ActivationResult) {
        when (result) {
            is ActivationResult.Success -> {
                licenseManager.saveActivated(code)
                navigateToMain()
            }
            is ActivationResult.Rejected -> showError(result.message)
            is ActivationResult.NetworkError -> showError("${result.message}\n请检查网络后重试")
        }
    }

    /**
     * 切换加载状态，加载中禁用按钮与输入框避免重复提交。
     * @param isLoading 是否处于请求中
     */
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnActivate.isEnabled = !isLoading
        binding.etActivationCode.isEnabled = !isLoading
        binding.btnActivate.text = if (isLoading) "验证中…" else "激活"
    }

    /**
     * 显示错误提示。
     * @param message 错误文案
     */
    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    /** 清除错误提示。 */
    private fun clearError() {
        binding.tvError.visibility = View.GONE
    }

    /** 激活成功后进入主界面，并结束自身避免返回栈回到激活页。 */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
