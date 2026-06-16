package com.susking.ephone_s.qq.ui.qzone

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import com.susking.ephone_s.core.R
import com.susking.ephone_s.core.ui.BaseActivity
import com.susking.ephone_s.qq.databinding.ActivityCreateFeedBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFeedActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateFeedBinding
    private val viewModel: CreateFeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()


        viewModel.feedCreationResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                Log.e("CreateFeedActivity", "发布动态失败", it) // 在日志中打印完整的错误信息
                Toast.makeText(this, "发布失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24) // 使用关闭图标代替返回箭头
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_create_feed, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // 处理关闭按钮的点击事件
                true
            }
            R.id.action_publish -> {
                publishFeed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun publishFeed() {
        val content = binding.feedContentEditText.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.createFeed(content)
    }
}