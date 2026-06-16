package com.susking.ephone_s.qq.ui.processtext

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.qq.databinding.ActivityProcessTextBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * 处理跨应用文本选择的Activity
 * 用户在其他应用选中文字后，可以通过系统菜单选择本应用，将文字添加到收藏
 */
@AndroidEntryPoint
class ProcessTextActivity : BaseActivity() {

    private lateinit var binding: ActivityProcessTextBinding
    private val viewModel: ProcessTextViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取选中的文本
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""

        setupUI(selectedText)
        observeViewModel()
    }

    private fun setupUI(selectedText: String) {
        binding.apply {
            // 设置初始文本
            editTextContent.setText(selectedText)

            // 尝试获取来源应用名称
            val callingPackage = callingActivity?.packageName
            val appName = getAppName(callingPackage)
            editTextSource.setText(appName)

            // 按钮点击事件
            buttonCancel.setOnClickListener { 
                setResult(Activity.RESULT_CANCELED)
                finish() 
            }
            buttonSave.setOnClickListener { saveToFavorites() }
        }
    }

    private fun saveToFavorites() {
        val text = binding.editTextContent.text.toString().trim()
        val source = binding.editTextSource.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(this, "文本内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveToFavorites(text, source)
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            binding.buttonSave.isEnabled = !state.isSaving

            when {
                state.saveSuccess -> {
                    Toast.makeText(this, "已保存到收藏", Toast.LENGTH_SHORT).show()
                    // 收藏功能不修改原文本，所以不返回EXTRA_PROCESS_TEXT
                    // 这样可以避免在外部编辑框中重复粘贴文本
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                state.errorMessage != null -> {
                    Toast.makeText(this, state.errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 根据包名获取应用名称
     */
    private fun getAppName(packageName: String?): String {
        if (packageName == null) return ""
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
}