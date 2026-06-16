package com.susking.ephone_s.settings.ui.legal

import android.os.Bundle
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.settings.databinding.ActivityTextViewerBinding
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 通用文本查看Activity
 * 用于显示隐私政策、许可证等文本文档
 */
class TextViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityTextViewerBinding

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ASSET_FILE = "extra_asset_file"

        // 预定义的文档类型
        const val PRIVACY_POLICY = "privacy_policy.txt"
        const val OPEN_SOURCE_LICENSES = "licenses/open_source_licenses.txt"
        const val APACHE_LICENSE = "licenses/apache_2.0_license.txt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityTextViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadContent()
    }

    private fun setupToolbar() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "文档查看"
        binding.toolbar.title = title
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadContent() {
        val assetFile = intent.getStringExtra(EXTRA_ASSET_FILE)
        
        if (assetFile.isNullOrEmpty()) {
            binding.textContent.text = "文档加载失败:未指定文件"
            return
        }

        try {
            val content = loadTextFromAssets(assetFile)
            binding.textContent.text = content
            
            // 滚动到顶部
            binding.scrollView.post {
                binding.scrollView.scrollTo(0, 0)
            }
        } catch (e: Exception) {
            binding.textContent.text = "文档加载失败: ${e.message}"
        }
    }

    private fun loadTextFromAssets(fileName: String): String {
        return try {
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val content = reader.use { it.readText() }
            content
        } catch (e: Exception) {
            throw Exception("无法加载文件: $fileName", e)
        }
    }
}