package com.susking.ephone_s.clouddreams.ui


// 这就像定义一个“聊天记录”的模板，每条记录都有谁、说了什么、时间
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.susking.ephone_s.clouddreams.R
import com.susking.ephone_s.core.ui.BaseActivity
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(), // 添加唯一ID
    val name: String,
    val isUser: Boolean,
    val message: String,
    val sendDate: String,
    val model: String,
    var processedMessage: String,
    // 原始 jsonl 整行 JSON（保真：保存/导出时回写以保留 swipes/extra 等酒馆原生字段）。
    // 外部导入的旧文件可能为空串，此时保存回退为最小字段集。
    val rawJson: String = "",
    // 头像本地相对路径（avatars/x.png），相对于 chat_records 目录；无头像为 null。
    val avatarPath: String? = null
) : Serializable

// 接口定义
interface OnMessageEditedListener {
    fun onMessageEdited(updatedMessage: ChatMessage, position: Int)
}

class MainActivity : BaseActivity() {

    private lateinit var filesListView: ListView
    private val chatFiles = mutableListOf<File>()

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private val pinnedFiles = mutableSetOf<String>()

    private lateinit var regexRuleManager: RegexRuleManager

    // 使用 SharedPreferences 存储浏览计数
    private val viewCountPrefs by lazy { getSharedPreferences("file_view_counts", MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted: Boolean ->
        if (isGranted) {
            openFilePicker()
        } else {
            Toast.makeText(this, "需要存储权限才能导入文件", Toast.LENGTH_SHORT).show()
        }
    }


    inner class FileListAdapter(private val files: List<File>) : BaseAdapter() {
        override fun getCount(): Int = files.size
        override fun getItem(position: Int): File = files[position]
        override fun getItemId(position: Int): Long = position.toLong()

        @SuppressLint("StringFormatMatches", "SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.b_each_chat, parent, false)

            val file = files[position]

            // 设置文件信息 - 移除文件扩展名
            val fileNameWithoutExtension = file.name.replace(".jsonl", "")

            val fileNameTextView = view.findViewById<TextView>(R.id.fileNameTextView)
            val fileInfoTextView = view.findViewById<TextView>(R.id.fileInfoTextView)
            val pinnedIcon = view.findViewById<ImageView>(R.id.pinnedIcon)
            val topButton = view.findViewById<Button>(R.id.topButton)
            val deleteButton = view.findViewById<Button>(R.id.deleteButton)
            val swipeLayout = view.findViewById<com.chauthai.swipereveallayout.SwipeRevealLayout>(R.id.swipeLayout)


            // 获取文件大小
            val fileSize = file.length()
            val fileSizeText = when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1048576 -> "${fileSize / 1024} KB"
                else -> "${fileSize / 1048576} MB"
            }
            // 获取浏览次数
            val viewCount = viewCountPrefs.getInt(file.absolutePath, 0)
            val viewCountTextView = view.findViewById<TextView>(R.id.viewCountTextView)
            viewCountTextView.text = "浏览${viewCount}次"

            // 格式化日期（使用系统默认区域设置）
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val lastModified = dateFormat.format(Date(file.lastModified()))

            // 设置文件信息
            fileNameTextView.text = fileNameWithoutExtension
            fileInfoTextView.text ="$fileSizeText · $lastModified"

            // 显示置顶状态
            val isPinned = pinnedFiles.contains(file.absolutePath)
            pinnedIcon.visibility = if (isPinned) View.VISIBLE else View.GONE
            topButton.text = if (isPinned) "取消置顶" else "置顶"

            // 设置置顶按钮点击事件
            topButton.setOnClickListener {
                if (pinnedFiles.contains(file.absolutePath)) {
                    pinnedFiles.remove(file.absolutePath)
                } else {
                    pinnedFiles.add(file.absolutePath)
                }
                savePinnedFiles()
                loadFileList() // 刷新列表
                swipeLayout.close(true) // 关闭滑动面板
            }

            // 设置删除按钮点击事件
            deleteButton.setOnClickListener {
                if (file.delete()) {
                    Toast.makeText(this@MainActivity, "已删除: ${file.name}", Toast.LENGTH_SHORT).show()
                    loadFileList() // 刷新列表
                } else {
                    Toast.makeText(this@MainActivity, "删除失败: ${file.name}", Toast.LENGTH_SHORT).show()
                }
                swipeLayout.close(true) // 关闭滑动面板
            }

            // 设置主内容点击事件
            view.findViewById<View>(R.id.mainContent).setOnClickListener {
                loadChatFile(file)
            }

            return view
        }
    }

    // 添加保存和加载置顶文件的方法
    private fun savePinnedFiles() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit { putStringSet("pinned_files", pinnedFiles) }
    }

    private fun loadPinnedFiles() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        pinnedFiles.addAll(prefs.getStringSet("pinned_files", emptySet()) ?: emptySet())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.b_memory_list)
        regexRuleManager = RegexRuleManager(this)
        loadPinnedFiles() // 加载置顶文件列表

        // 初始化文件选择器
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    readJsonFile(uri)
                }
            }
        }

        // 1. 找到界面上的按钮和”黑板”
        val importButton = findViewById<ImageView>(R.id.importButton)
        val regexButton = findViewById<ImageView>(R.id.regexButton)
        filesListView = findViewById<ListView>(R.id.filesListView)
        filesListView.adapter = FileListAdapter(emptyList())


        // 加载已保存的文件列表
        loadFileList()

        // 6. 给按钮设置点击事件（先弹出一个提示，表示点击有效）
        importButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openFilePicker()
            } else {
                // 使用新的Launcher请求权限
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 正则管理按钮：打开全局正则编辑器（非只读）
        regexButton.setOnClickListener {
            val intent = Intent(this, RegexDropdownActivity::class.java)
            startActivity(intent)
        }

    }
    // 添加处理权限请求结果的方法
    // 权限请求的Launcher


    // 添加打开文件选择器的方法
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/jsonl",
                "text/plain",
                "application/octet-stream"
            ))
        }
        filePickerLauncher.launch(intent)
    }

    // 这个类负责把聊天数据放到 RecyclerView 里
    class ChatAdapter(
        context: Context,
        private val messages: MutableList<ChatMessage>,
        private val editListener: OnMessageEditedListener? = null,
        private val regexRuleManager: RegexRuleManager? = null, // 添加RegexRuleManager参数
        // 该聊天记录声明的正则 id 列表：编辑后重新处理时按此应用，保证与渲染一致；null 回退全部全局规则
        private val appliedRegexIds: List<String>? = null,
        // chat_records 目录，用于把头像相对路径解析为绝对文件路径
        private val chatRecordsDir: File? = null
    ) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

        init {
            MarkdownRenderer.initialize(context)
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val sender: TextView = view.findViewById(R.id.senderTextView)
            val avatar: ImageView = view.findViewById(R.id.avatarImageView)
            val message: TextView = view.findViewById(R.id.messageTextView)
            val editText: EditText = view.findViewById(R.id.editMessageText)
            val time: TextView = view.findViewById(R.id.timeTextView)
            val editIcon: ImageView = view.findViewById(R.id.editIcon)
            val doneIcon: ImageView = view.findViewById(R.id.doneIcon)
            val rightIcon: ImageView = view.findViewById(R.id.rightIcon)
        }

        override fun getItemCount(): Int = messages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.c_content_item_message, parent, false)
            return ViewHolder(view)
        }

        private val renderedPositions = mutableSetOf<Int>()

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val chatMessage = messages[position]
            holder.sender.text = chatMessage.name

            // 加载本地头像：有图则显示图片盖住名字方块，无图则隐藏图片露出名字方块
            bindAvatar(holder, chatMessage.avatarPath)

            // 性能优化：只有未渲染过的位置才重新渲染
            if (!renderedPositions.contains(position)) {
                MarkdownRenderer.renderHtmlWithMarkdown(holder.message, chatMessage.processedMessage)
                renderedPositions.add(position)
            }

            holder.editText.setText(chatMessage.message)
            holder.time.text = chatMessage.sendDate

            // 根据model字段设置rightIcon的图片
            val model = chatMessage.model.lowercase(Locale.ROOT)

            when {
                model.contains("gemini") -> holder.rightIcon.setImageResource(R.drawable.gemini)
                model.contains("deepseek") -> holder.rightIcon.setImageResource(R.drawable.deepseek)
                model.contains("openai") -> holder.rightIcon.setImageResource(R.drawable.openai)
                else -> holder.rightIcon.setImageResource(R.drawable.model_empty) // 设置一个默认图标
            }

    // 如果是用户消息，隐藏rightIcon
    if (chatMessage.isUser) {
        holder.rightIcon.visibility = View.GONE
    } else {
        holder.rightIcon.visibility = View.VISIBLE

        // 添加点击事件，显示model信息
        holder.rightIcon.setOnClickListener {
            // 显示model信息的Toast
            Toast.makeText(
                holder.itemView.context,
                "模型: ${chatMessage.model.ifEmpty { "未知" }}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

            // 设置编辑按钮点击事件
            holder.editIcon.setOnClickListener {
                enableMessageEditing(holder, position)
            }

            // 设置完成按钮点击事件
            holder.doneIcon.setOnClickListener {
                finishMessageEditing(holder, position)
                // 清除渲染缓存，因为内容已更改
                renderedPositions.remove(position)
            }

            // 设置编辑框完成编辑事件
            holder.editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    finishMessageEditing(holder, position)
                    true
                } else {
                    false
                }
            }

            // 设置编辑框失去焦点事件
            holder.editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    finishMessageEditing(holder, position)
                }
            }
        }

        private fun enableMessageEditing(holder: ViewHolder, position: Int) {
            // 隐藏正常消息文本，显示编辑框
            holder.message.visibility = View.GONE
            holder.editText.visibility = View.VISIBLE

            // 切换图标：隐藏编辑图标，显示完成图标
            holder.editIcon.visibility = View.GONE
            holder.doneIcon.visibility = View.VISIBLE

            // 设置编辑框文本
            holder.editText.setText(messages[position].message)

            // 请求焦点并显示键盘
            holder.editText.requestFocus()
            val inputMethodManager = holder.itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(holder.editText, InputMethodManager.SHOW_IMPLICIT)
        }

        private fun finishMessageEditing(holder: ViewHolder, position: Int) {
            val newText = holder.editText.text.toString()
            val originalText = messages[position].message

            if (newText != originalText) {
                // 应用正则规则到新文本（按该记录声明的 id 列表，与渲染保持一致）
                val processedMessage = regexRuleManager?.processMessage(
                    newText,
                    messages[position].isUser,
                    appliedRegexIds
                ) ?: newText

                // 更新数据 - 创建新的ChatMessage对象
                val updatedMessage = messages[position].copy(message = processedMessage)
                messages[position] = updatedMessage

                // 重新渲染Markdown
                MarkdownRenderer.renderHtmlWithMarkdown(holder.message, processedMessage)

                Toast.makeText(holder.itemView.context, "消息已更新", Toast.LENGTH_SHORT).show()

                // 通知监听器消息已编辑
                editListener?.onMessageEdited(updatedMessage, position)
            }

            // 隐藏编辑框，显示正常消息文本
            holder.editText.visibility = View.GONE
            holder.message.visibility = View.VISIBLE

            // 切换图标：显示编辑图标，隐藏完成图标
            holder.editIcon.visibility = View.VISIBLE
            holder.doneIcon.visibility = View.GONE

            // 隐藏键盘
            val inputMethodManager = holder.itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(holder.editText.windowToken, 0)
        }

        /**
         * 绑定头像：把相对路径（avatars/x.png）解析为 chat_records 下的绝对文件并加载。
         * 文件存在则显示图片（盖住名字方块），否则隐藏图片露出名字方块（兜底）。
         */
        private fun bindAvatar(holder: ViewHolder, avatarPath: String?) {
            val dir = chatRecordsDir
            if (avatarPath.isNullOrBlank() || dir == null) {
                holder.avatar.visibility = View.GONE
                return
            }
            val avatarFile = File(dir, avatarPath)
            if (!avatarFile.exists()) {
                holder.avatar.visibility = View.GONE
                return
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath)
            if (bitmap == null) {
                holder.avatar.visibility = View.GONE
            } else {
                holder.avatar.setImageBitmap(bitmap)
                holder.avatar.visibility = View.VISIBLE
            }
        }
    }

    // 添加加载文件列表的方法
    private fun loadFileList() {
        chatFiles.clear()

        // 使用外部存储目录
        val externalDir = getExternalFilesDir(null)
        val chatDir = File(externalDir, "chat_records")
        Log.d("LoadFileList", "加载文件列表，目录: ${chatDir.absolutePath}")

        if (!chatDir.exists()) {
            filesListView.adapter = FileListAdapter(emptyList())
            return
        }
        val files = chatDir.listFiles { file ->
            file.isFile && file.name.endsWith(".jsonl", ignoreCase = true)
        }?.toList() ?: emptyList()

        // 按置顶状态和修改时间排序
        val sortedFiles = files.sortedWith(compareBy(
            { !pinnedFiles.contains(it.absolutePath) }, // 置顶文件在前
            { -it.lastModified() } // 最新修改的文件在前
        ))

        chatFiles.addAll(sortedFiles)
        filesListView.adapter = FileListAdapter(chatFiles)
        filesListView.isEnabled = chatFiles.isNotEmpty()

        Log.d("LoadFileList", "找到 ${files.size} 个文件")
    }

    // 修改读取JSON文件的方法，添加保存功能
    private fun readJsonFile(uri: Uri) {
        try {
            var fileName = ""
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
            // Check if file has .json extension
            if (!fileName.endsWith(".jsonl", ignoreCase = true)) {
                Toast.makeText(this, "只能导入.jsonl格式的文件", Toast.LENGTH_SHORT).show()
                return
            }

            // 使用工具类读取内容
            val content = JsonFileHelper.readContentFromUri(this, uri)
            if (content.isEmpty()) {
                Toast.makeText(this, "文件内容为空", Toast.LENGTH_SHORT).show()
                return
            }

            // 使用工具类验证内容
            if (!JsonFileHelper.validateJsonContent(content)) {
                Toast.makeText(
                    this,
                    "文件格式错误: 包含无效的JSON格式",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // 保存文件到应用目录
            val savedFile = JsonFileHelper.saveChatFile(this, uri, content)

            // 启动 ChatActivity 并传递文件路径而不是内容
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("file_path", savedFile.absolutePath) // 传递文件路径而不是内容
                putExtra("file_name", savedFile.name)
            }
            startActivity(intent)

            // 更新文件列表
            loadFileList()

            Toast.makeText(this, "文件已保存: ${savedFile.name}", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // 添加加载聊天文件的方法
    private fun loadChatFile(file: File) {
        try {
            Log.d("LoadChatFile", "尝试加载文件: ${file.absolutePath}")

            // 更严格的文件检查
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在: ${file.name}", Toast.LENGTH_SHORT).show()
                Log.e("LoadChatFile", "文件不存在: ${file.absolutePath}")
                // 刷新文件列表
                loadFileList()
                return
            }

            if (!file.isFile) {
                Toast.makeText(this, "不是有效文件: ${file.name}", Toast.LENGTH_SHORT).show()
                return
            }

            if (!file.canRead()) {
                Toast.makeText(this, "没有读取权限: ${file.name}", Toast.LENGTH_SHORT).show()
                return
            }

            // 直接将 JSON 内容传递给 ChatActivity
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("file_path", file.absolutePath)
                putExtra("file_name", file.name)
            }
            startActivity(intent)

        } catch (e: SecurityException) {
            Log.e("LoadChatFile", "安全异常", e)
            Toast.makeText(this, "没有文件访问权限", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("LoadChatFile", "加载文件失败", e)
            Toast.makeText(this, "加载文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}