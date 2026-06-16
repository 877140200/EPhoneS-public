package com.susking.ephone_s.clouddreams.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.clouddreams.R
import java.io.File
import kotlin.concurrent.thread

class ChatActivity : AppCompatActivity(), OnMessageEditedListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var backButton: ImageView
    private lateinit var menuButton: ImageView
    private lateinit var chatTitleTextView: TextView
    private lateinit var filePath: String
    private lateinit var messages: MutableList<ChatMessage>
    private lateinit var adapter: MainActivity.ChatAdapter
    private lateinit var regexRuleManager: RegexRuleManager
    // 该聊天记录首行声明的正则 id 列表（null=外部旧文件无 metadata，回退全部全局规则）
    private var appliedRegexIds: List<String>? = null
    // 添加一个标志位来跟踪是否需要刷新消息
    private var needRefreshMessages = false

    // 添加自动阅读相关变量
    private var isAutoReading = false
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private lateinit var autoScrollRunnable: Runnable
    private var autoScrollSpeed = 50 // 默认滚动速度提高到80 (1-100)
    private var scrollAmountPerFrame = 1 // 默认每帧滚动像素数提高到8

    private lateinit var regexDropdownLauncher: ActivityResultLauncher<Intent>

    private var startTime: Long = 0
    private var timerHandler: Handler? = null
    private val timerRunnable = object : Runnable {
        override fun run() {
            // 计算已浏览时间（秒）
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000

            // 更新剩余时间提示
            supportActionBar?.subtitle = "再浏览${30 - elapsedSeconds}秒计入统计"

            // 检查是否达到1分钟
            if (elapsedSeconds >= 30) {
                // 增加浏览计数
                val prefs = getSharedPreferences("file_view_counts", MODE_PRIVATE)
                val currentCount = prefs.getInt(filePath, 0)
                prefs.edit { putInt(filePath, currentCount + 1) }

                // 停止计时
                timerHandler?.removeCallbacks(this)
            } else {
                // 继续计时
                timerHandler?.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c_content_container)

        // 初始化视图
        recyclerView = findViewById(R.id.recyclerView)
        backButton = findViewById(R.id.backButton)
        menuButton = findViewById(R.id.menuButton)
        chatTitleTextView = findViewById(R.id.chatTitleTextView)
        regexRuleManager = RegexRuleManager(this)

        // 设置布局管理器
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 获取传递过来的文件路径
        filePath = intent.getStringExtra("file_path") ?: ""
        val fileName = intent.getStringExtra("file_name") ?: "聊天记录"

        // 设置标题
        chatTitleTextView.text = fileName.replace(".jsonl", "")

        thread {
            // 后台线程执行耗时操作
            appliedRegexIds = JsonFileHelper.readMetadata(filePath)?.appliedRegexIds
            messages = parseJsonContentStreaming(filePath)

            // 在主线程更新UI
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val chatRecordsDir = File(getExternalFilesDir(null), "chat_records")
                    adapter = MainActivity.ChatAdapter(
                        this@ChatActivity,
                        messages,
                        this,
                        regexRuleManager,
                        appliedRegexIds,
                        chatRecordsDir
                    )
                    recyclerView.adapter = adapter
                }
            }
        }

        // 返回按钮点击事件
        backButton.setOnClickListener {
            JsonFileHelper.saveMessagesToFile(filePath, messages)
            finish()
        }

        // 设置菜单按钮点击事件
        menuButton.setOnClickListener {
            showPopupMenu()
        }

        timerHandler = Handler(Looper.getMainLooper())  // 初始化计时器
        initAutoScroll()  // 初始化自动阅读功能

        // 注册 ActivityResultLauncher
        regexDropdownLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val needRefresh = result.data?.getBooleanExtra("need_refresh", false) ?: false
                if (needRefresh) {
                    needRefreshMessages = true
                }
            }
        }
    }

    override fun onMessageEdited(updatedMessage: ChatMessage, position: Int) {
        // 立即保存到文件
        JsonFileHelper.saveMessagesToFile(filePath, messages)
    }

    //解析 JSON 内容的方法
    @SuppressLint("NotifyDataSetChanged")
    private fun parseJsonContentStreaming(filePath: String): MutableList<ChatMessage> {
        return JsonFileHelper.parseJsonContentStreaming(filePath, regexRuleManager)
    }

    override fun onResume() {
        super.onResume()
        // 检查是否需要刷新消息
        if (needRefreshMessages) {
            refreshMessages()
            needRefreshMessages = false
        }

        // 开始计时
        startTime = System.currentTimeMillis()
        timerHandler?.postDelayed(timerRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        // 停止计时
        timerHandler?.removeCallbacks(timerRunnable)

        // 停止自动阅读
        stopAutoReading()

        // 保存消息到文件
        JsonFileHelper.saveMessagesToFile(filePath, messages)
    }

    // 添加刷新消息的方法
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshMessages() {
        // 重新应用正则规则到所有消息（按该记录声明的 id 列表，与初次渲染一致）
        for (i in messages.indices) {
            val msg = messages[i]
            val processedMessage = regexRuleManager.processMessage(msg.message, msg.isUser, appliedRegexIds)
            if (processedMessage != msg.message) {
                // 创建新的ChatMessage对象，并处理代码块内的换行符
                messages[i] = msg.copy(message = processedMessage)
            }
        }
        adapter.notifyDataSetChanged()
    }

    // 添加显示弹出菜单的方法
    private fun showPopupMenu() {
        val popup = PopupMenu(this, menuButton)
        popup.menuInflater.inflate(R.menu.chat_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_regex -> {
                    regex()
                    true
                }
                R.id.menu_export -> {
                    exportChat()
                    true
                }
                R.id.menu_rename -> {
                    renameChat()
                    true
                }
                R.id.menu_bookmark -> {
                    showBookmarkDialog()
                    true
                }
                R.id.menu_auto_read -> {
                    toggleAutoReading()
                    true
                }
                R.id.menu_auto_read_settings -> {
                    showAutoReadSettings()
                    true
                }
                else -> false
            }
        }

        val autoReadItem = popup.menu.findItem(R.id.menu_auto_read)
        if (isAutoReading) {
            autoReadItem.title = "停止自动阅读"
        } else {
            autoReadItem.title = "开始自动阅读哈基米哈基米"
        }
        popup.show()
    }

    private fun regex() {
        // 打开只读正则查看器：传入当前聊天记录声明的正则 id 列表
        val intent = Intent(this, RegexDropdownActivity::class.java)
        intent.putExtra("read_only_mode", true)
        intent.putStringArrayListExtra("applied_regex_ids", ArrayList(appliedRegexIds ?: emptyList()))
        regexDropdownLauncher.launch(intent)
    }

    /**
     * 导出当前聊天记录为 .jsonl 文件。
     *
     * 文件本身已是 SillyTavern 原生格式（保存对话时即按原生格式落盘，编辑也保真不瘦身），
     * 故先 [JsonFileHelper.saveMessagesToFile] 落盘最新内容，再经 FileProvider 以 ACTION_SEND 导出。
     * 导出的文件可被酒馆重新导入。
     */
    private fun exportChat() {
        try {
            // 落盘最新内容（含编辑），保证导出的是当前状态
            if (::messages.isInitialized) {
                JsonFileHelper.saveMessagesToFile(filePath, messages)
            }
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在，无法导出", Toast.LENGTH_SHORT).show()
                return
            }
            // clouddreams 是库模块，运行时 applicationId 即宿主 app，authority 与 app manifest 一致
            val authority = "${applicationContext.packageName}.provider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "导出聊天记录"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    // 添加重命名功能
    private fun renameChat() {
        val currentFile = File(filePath)
        val currentName = currentFile.name.replace(".jsonl", "")

        val inputEditText = EditText(this).apply {
            setText(currentName)
        }

        AlertDialog.Builder(this)
            .setTitle("重命名聊天记录")
            .setView(inputEditText)
            .setPositiveButton("确定") { _, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(currentFile.parent, "$newName.jsonl")
                    if (currentFile.renameTo(newFile)) {
                        filePath = newFile.absolutePath
                        chatTitleTextView.text = newFile.name
                        Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 添加书签功能
    private fun showBookmarkDialog() {
        val inputEditText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "输入消息位置 (1-${messages.size})"
        }

        AlertDialog.Builder(this)
            .setTitle("跳转到指定消息")
            .setView(inputEditText)
            .setPositiveButton("跳转") { _, _ ->
                val positionStr = inputEditText.text.toString()
                if (positionStr.isNotEmpty()) {
                    val position = positionStr.toInt() - 1 // 转换为0-based索引
                    if (position in 0 until messages.size) {
                        recyclerView.scrollToPosition(position)
                        // 高亮显示该消息
                        (recyclerView.layoutManager as LinearLayoutManager)
                            .scrollToPositionWithOffset(position, 0)
                    } else {
                        Toast.makeText(this, "无效的位置", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 初始化自动滚动
    private fun initAutoScroll() {
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (!isAutoReading) return

                if (!recyclerView.canScrollVertically(1)) {
                    stopAutoReading()
                    Toast.makeText(this@ChatActivity, "已到达底部，自动阅读已停止", Toast.LENGTH_SHORT).show()
                    return
                }

                // 计算滚动间隔和每帧滚动像素
                val interval = 1
                // 使用scrollBy替代smoothScrollBy减少频闪
                recyclerView.scrollBy(0, scrollAmountPerFrame)
                autoScrollHandler.postDelayed(this, interval.toLong())
            }
        }
    }

    // 根据速度计算每帧滚动像素
    private fun calculateScrollAmountFromSpeed(speed: Int): Int {
        // 速度范围: 1-100，转换为每帧滚动像素:
        return (speed / 10).coerceIn(1, 10)
    }

    // 切换自动阅读状态
    private fun toggleAutoReading() {
        if (isAutoReading) {
            stopAutoReading()
        } else {
            startAutoReading()
        }
    }

    // 开始自动阅读
    private fun startAutoReading() {
        isAutoReading = true
        scrollAmountPerFrame = calculateScrollAmountFromSpeed(autoScrollSpeed)
        if (::adapter.isInitialized && adapter.itemCount > 0) {
            val interval = 1
            autoScrollHandler.postDelayed(autoScrollRunnable, interval.toLong())
            Toast.makeText(this, "自动阅读已开启", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请等待消息加载完成", Toast.LENGTH_SHORT).show()
            isAutoReading = false
        }
    }

    // 停止自动阅读
    private fun stopAutoReading() {
        isAutoReading = false
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        Toast.makeText(this, "自动阅读已停止", Toast.LENGTH_SHORT).show()
    }

    // 显示自动阅读设置对话框
    @SuppressLint("SetTextI18n")
    private fun showAutoReadSettings() {
        val dialogView = layoutInflater.inflate(R.layout.oo_auto_read, null)
        val speedSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.speedSeekBar)
        val speedValue = dialogView.findViewById<TextView>(R.id.speedValue)

        // 设置当前速度
        speedSeekBar.progress = autoScrollSpeed - 1
        speedValue.text = "${autoScrollSpeed}级"

        // 速度变化监听
        speedSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val speedLevel = progress + 1
                speedValue.text = "${speedLevel}级"
                autoScrollSpeed = speedLevel
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("自动阅读设置")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                // 保存设置
                if (isAutoReading) {
                    // 如果正在自动阅读，重新启动以应用新设置
                    stopAutoReading()
                    startAutoReading()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}