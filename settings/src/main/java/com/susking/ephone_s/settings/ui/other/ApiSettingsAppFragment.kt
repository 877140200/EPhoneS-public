package com.susking.ephone_s.settings.ui.other

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.desktop.data.DesktopDuplicateRepairResult
import com.susking.ephone_s.desktop.data.DesktopRepository
import com.susking.ephone_s.settings.databinding.FragmentApiSettingsAppBinding
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.ImportOutcome
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult
import com.susking.ephone_s.aidata.util.BackupSnapshotHelper
import com.susking.ephone_s.settings.databinding.DialogConflictResolutionBinding
import com.susking.ephone_s.settings.databinding.DialogImportResultBinding
import com.susking.ephone_s.settings.ui.main.ApiSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ApiSettingsAppFragment : Fragment() {

    private var _binding: FragmentApiSettingsAppBinding? = null
    private val binding get() = _binding!!

    // 使用 Hilt 自动注入 ViewModel
    private val viewModel: ApiSettingsViewModel by activityViewModels()

    @Inject
    lateinit var desktopRepository: DesktopRepository
    
    private var progressDialog: ProgressDialog? = null
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                prepareImportFromFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiSettingsAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.backgroundServiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 通过 ViewModel 更新状态
            if (viewModel.settingsState.value?.isBackgroundServiceEnabled != isChecked) {
                viewModel.updateBackgroundServiceEnabled(isChecked)
            }
        }

        binding.chatLoadCountInput.addTextChangedListener {
            val count = it.toString().toIntOrNull() ?: 0
            if (viewModel.settingsState.value?.chatListLoadCount != count) {
                viewModel.updateChatListLoadCount(count)
            }
        }

        binding.initialLoadCountInput.addTextChangedListener {
            val count = it.toString().toIntOrNull() ?: 0
            if (viewModel.settingsState.value?.chatInitialLoadCount != count) {
                viewModel.updateChatInitialLoadCount(count)
            }
        }

        // 导出数据按钮
        binding.buttonExportData.setOnClickListener {
            exportCompleteAppData()
        }

        // 导入数据按钮
        binding.buttonImportData.setOnClickListener {
            selectImportFile()
        }
        
        binding.buttonDataCheckAndRepair.setOnClickListener {
            showDataCheckAndRepairDialog()
        }
        
        setupStorageButtons()
        binding.buttonClearTtsCache.setOnClickListener {
            showClearTtsCacheDialog()
        }

        loadStorageUsageStats()

        // 功能一览按钮
        binding.buttonFeatureOverview.setOnClickListener {
            com.susking.ephone_s.settings.api.SettingsApi.openFeatureOverviewActivity(requireContext())
        }

        // 关于按钮
        binding.buttonAbout.setOnClickListener {
            com.susking.ephone_s.settings.api.SettingsApi.openAboutActivity(requireContext())
        }

        // 权限管理按钮
        binding.buttonPermission.setOnClickListener {
            com.susking.ephone_s.settings.api.SettingsApi.openPermissionActivity(requireContext())
        }
    }

    /**
     * 显示数据检查与修复确认弹窗。
     * 当前只修复桌面应用入口重复问题，不会扫描或修改其他数据。
     */
    private fun showDataCheckAndRepairDialog(): Unit {
        AlertDialog.Builder(requireContext())
            .setTitle("数据检查与修复")
            .setMessage("将检查桌面和 Dock 中是否存在重复应用入口。\n\n为保证数据安全，本次只会移除重复的桌面入口，不会修改聊天记录、联系人、相册、世界书、设置等其他数据。")
            .setPositiveButton("开始修复") { _, _ ->
                repairDuplicateDesktopAppEntries()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行桌面重复入口修复。
     * 该操作在 IO 线程中访问桌面 DataStore，并只在发现重复入口时写回修复后的桌面布局。
     */
    private fun repairDuplicateDesktopAppEntries(): Unit {
        lifecycleScope.launch {
            try {
                showProgressDialog("正在检查桌面数据...")
                val result: DesktopDuplicateRepairResult = withContext(Dispatchers.IO) {
                    desktopRepository.repairDuplicateAppEntries()
                }
                hideProgressDialog()
                showDataRepairResultDialog(result)
            } catch (e: Exception) {
                hideProgressDialog()
                Log.e("ApiSettingsAppFragment", "桌面重复入口修复失败", e)
                showErrorDialog("修复失败：${e.message}")
            }
        }
    }

    /**
     * 展示桌面重复入口修复结果。
     */
    private fun showDataRepairResultDialog(result: DesktopDuplicateRepairResult): Unit {
        val message: String = if (result.hasChanges) {
            val removedNames: String = result.removedAppNames.joinToString("、")
            "检查完成：共检查 ${result.checkedEntryCount} 个桌面入口，已安全移除 ${result.removedEntryCount} 个重复入口。\n\n涉及应用：$removedNames\n\n未修改其他数据。"
        } else {
            "检查完成：共检查 ${result.checkedEntryCount} 个桌面入口，未发现重复应用入口。\n\n未修改任何数据。"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("修复完成")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.settingsState.observe(viewLifecycleOwner) { state ->
            if (state == null) return@observe

            // 完整地同步所有UI组件的状态
            if (binding.languageSpinner.text.toString() != state.language) {
                binding.languageSpinner.setText(state.language, false)
            }

            if (binding.chatLoadCountInput.text.toString() != state.chatListLoadCount.toString()) {
                binding.chatLoadCountInput.setText(state.chatListLoadCount.toString())
            }

            if (binding.initialLoadCountInput.text.toString() != state.chatInitialLoadCount.toString()) {
                binding.initialLoadCountInput.setText(state.chatInitialLoadCount.toString())
            }

            if (binding.backgroundServiceSwitch.isChecked != state.isBackgroundServiceEnabled) {
                binding.backgroundServiceSwitch.isChecked = state.isBackgroundServiceEnabled
            }
        }
    }

    fun getLanguageSetting(): String {
        return binding.languageSpinner.text.toString()
    }

    private fun showClearTtsCacheDialog(): Unit {
        AlertDialog.Builder(requireContext())
            .setTitle("清理未引用语音缓存")
            .setMessage("将只删除 voice_messages 目录中没有被聊天记录引用的语音缓存文件。仍绑定在语音气泡上的文件会保留。")
            .setPositiveButton("开始清理") { _, _ ->
                clearUnreferencedTtsCache()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearUnreferencedTtsCache(): Unit {
        lifecycleScope.launch {
            val result: CacheCleanResult = withContext(Dispatchers.IO) {
                executeClearUnreferencedTtsCache()
            }
            if (_binding == null) return@launch
            Toast.makeText(
                requireContext(),
                "已清理 ${result.deletedCount} 个语音缓存，释放 ${StorageUsageStats.formatBytes(result.deletedBytes)}。",
                Toast.LENGTH_LONG
            ).show()
            loadStorageUsageStats()
        }
    }

    private fun executeClearUnreferencedTtsCache(): CacheCleanResult {
        val voiceDirectory = java.io.File(requireContext().filesDir, VOICE_MESSAGES_DIRECTORY_NAME)
        if (!voiceDirectory.exists() || !voiceDirectory.isDirectory) {
            return CacheCleanResult(deletedCount = 0, deletedBytes = 0L)
        }
        val referencedPaths: Set<String> = queryStringListFromDatabase(
            databaseName = AIDATA_DATABASE_NAME,
            sql = "SELECT voiceAudioPath AS path FROM chat_messages WHERE voiceAudioPath IS NOT NULL AND voiceAudioPath != ''"
        ).mapNotNull { path: String ->
            normalizeStoragePath(path)
        }.toSet()

        var deletedCount = 0
        var deletedBytes = 0L
        voiceDirectory.listFiles()?.forEach { file: java.io.File ->
            if (!file.isFile) return@forEach
            val normalizedPath: String = file.absolutePath
            if (normalizedPath in referencedPaths) return@forEach
            val fileBytes: Long = file.length()
            if (file.delete()) {
                deletedCount++
                deletedBytes += fileBytes
            }
        }
        return CacheCleanResult(deletedCount = deletedCount, deletedBytes = deletedBytes)
    }

    private fun normalizeStoragePath(path: String): String? {
        val trimmedPath: String = path.trim()
        if (trimmedPath.isBlank()) return null
        if (trimmedPath.startsWith("voice_messages/")) {
            return java.io.File(requireContext().filesDir, trimmedPath).absolutePath
        }
        return java.io.File(trimmedPath).absolutePath
    }

    /**
     * 给每个存储条目框绑定点击刷新行为。
     */
    private fun setupStorageButtons(): Unit {
        val refreshListener: View.OnClickListener = View.OnClickListener {
            loadStorageUsageStats()
        }
        binding.buttonStorageAvatar.setOnClickListener(refreshListener)
        binding.buttonStorageChatImage.setOnClickListener(refreshListener)
        binding.buttonStorageAlbum.setOnClickListener(refreshListener)
        binding.buttonStorageChatRecord.setOnClickListener(refreshListener)
        binding.buttonStorageVector.setOnClickListener(refreshListener)
        binding.buttonStorageOther.setOnClickListener(refreshListener)
        binding.buttonStorageTotal.setOnClickListener(refreshListener)
    }

    /**
     * 刷新存储空间占用统计。
     * 这里仅做读取统计，不会改动任何文件或数据库内容。
     */
    private fun loadStorageUsageStats(): Unit {
        showStorageUsageLoading()
        lifecycleScope.launch {
            val stats: StorageUsageStats = withContext(Dispatchers.IO) {
                calculateStorageUsageStats()
            }
            if (_binding == null) return@launch
            bindStorageUsageStats(stats)
        }
    }

    /**
     * 显示统计中的占位状态。
     */
    private fun showStorageUsageLoading(): Unit {
        binding.buttonStorageAvatar.text = "头像：正在统计..."
        binding.buttonStorageChatImage.text = "聊天图片：正在统计..."
        binding.buttonStorageAlbum.text = "相册：正在统计..."
        binding.buttonStorageChatRecord.text = "聊天记录：正在统计..."
        binding.buttonStorageVector.text = "向量数据：正在统计..."
        binding.buttonStorageOther.text = "其他：正在统计..."
        binding.buttonStorageTotal.text = "总计：正在统计..."
    }

    /**
     * 将统计结果分别渲染到每个独立条目框。
     */
    private fun bindStorageUsageStats(stats: StorageUsageStats): Unit {
        binding.buttonStorageAvatar.text = "头像：${StorageUsageStats.formatBytes(stats.avatarBytes)}"
        binding.buttonStorageChatImage.text = "聊天图片：${StorageUsageStats.formatBytes(stats.chatImageBytes)}"
        binding.buttonStorageAlbum.text = "相册：${StorageUsageStats.formatBytes(stats.albumBytes)}"
        binding.buttonStorageChatRecord.text = "聊天记录：${StorageUsageStats.formatBytes(stats.chatRecordBytes)}"
        binding.buttonStorageVector.text = "向量数据：${StorageUsageStats.formatBytes(stats.vectorBytes)}"
        binding.buttonStorageOther.text = "其他：${StorageUsageStats.formatBytes(stats.otherBytes)}"
        binding.buttonStorageTotal.text = "总计：${StorageUsageStats.formatBytes(stats.totalBytes)}"
    }

    /**
     * 统计头像、聊天图片、相册、聊天记录、向量数据和其他数据的空间占用。
     * 文件类资源按实际文件大小统计；聊天记录和向量数据按数据库字段内容长度估算。
     */
    private fun calculateStorageUsageStats(): StorageUsageStats {
        val countedPaths: MutableSet<String> = mutableSetOf()
        val avatarBytes: Long = calculateReferencedFileBytes(
            countedPaths = countedPaths,
            sql = """
                SELECT avatarUri AS path FROM person_profiles
                UNION ALL SELECT backgroundUri AS path FROM person_profiles
                UNION ALL SELECT chatBackgroundUri AS path FROM person_profiles
            """.trimIndent()
        )
        val chatImageBytes: Long = calculateReferencedFileBytes(
            countedPaths = countedPaths,
            sql = """
                SELECT imageUrl AS path FROM chat_messages
                UNION ALL SELECT giftImageUrl AS path FROM chat_messages
            """.trimIndent()
        )
        val albumBytes: Long = calculateAlbumPhotoBytes(countedPaths = countedPaths)
        val chatRecordBytes: Long = queryLongFromAiDataDatabase(
            sql = """
                SELECT IFNULL(SUM(
                    LENGTH(IFNULL(id, '')) +
                    LENGTH(IFNULL(type, '')) +
                    LENGTH(IFNULL(contactId, '')) +
                    LENGTH(IFNULL(content, '')) +
                    LENGTH(IFNULL(imageDescription, '')) +
                    LENGTH(IFNULL(stickerName, '')) +
                    LENGTH(IFNULL(productInfo, '')) +
                    LENGTH(IFNULL(status, '')) +
                    LENGTH(IFNULL(greeting, '')) +
                    LENGTH(IFNULL(senderName, '')) +
                    LENGTH(IFNULL(recipientName, '')) +
                    LENGTH(IFNULL(notes, '')) +
                    LENGTH(IFNULL(recalledContent, ''))
                ), 0) FROM chat_messages
            """.trimIndent()
        )
        val vectorBytes: Long = queryLongFromAiDataDatabase(
            sql = """
                SELECT IFNULL(SUM(
                    LENGTH(IFNULL(id, '')) +
                    LENGTH(IFNULL(memoryId, '')) +
                    LENGTH(IFNULL(indexedObjectType, '')) +
                    LENGTH(IFNULL(indexedObjectId, '')) +
                    LENGTH(IFNULL(contactId, '')) +
                    LENGTH(IFNULL(embeddingBlob, X'')) +
                    LENGTH(IFNULL(embeddingHash, '')) +
                    LENGTH(IFNULL(modelName, '')) +
                    LENGTH(IFNULL(modelVersion, ''))
                ), 0) FROM memory_embeddings
            """.trimIndent()
        )
        val categorizedBytes: Long = avatarBytes + chatImageBytes + albumBytes + chatRecordBytes + vectorBytes
        val totalBytes: Long = calculateDirectoryBytes(requireContext().filesDir) + calculateDirectoryBytes(requireContext().cacheDir) + calculateDatabaseDirectoryBytes()
        val otherBytes: Long = (totalBytes - categorizedBytes).coerceAtLeast(0L)

        return StorageUsageStats(
            avatarBytes = avatarBytes,
            chatImageBytes = chatImageBytes,
            albumBytes = albumBytes,
            chatRecordBytes = chatRecordBytes,
            vectorBytes = vectorBytes,
            otherBytes = otherBytes,
            totalBytes = totalBytes
        )
    }

    /**
     * 根据数据库中保存的路径统计文件大小，并通过集合避免同一文件被重复计算。
     */
    private fun calculateReferencedFileBytes(
        countedPaths: MutableSet<String>,
        sql: String,
        databaseName: String = AIDATA_DATABASE_NAME
    ): Long {
        var totalBytes: Long = 0L
        queryStringListFromDatabase(databaseName = databaseName, sql = sql).forEach { path: String ->
            val normalizedPath: String = path.trim()
            if (normalizedPath.isBlank() || normalizedPath.startsWith("http")) {
                return@forEach
            }
            if (!countedPaths.add(normalizedPath)) {
                return@forEach
            }
            totalBytes += if (normalizedPath.startsWith("data:image")) {
                calculateBase64ImageBytes(normalizedPath)
            } else {
                calculatePathBytes(normalizedPath)
            }
        }
        return totalBytes
    }

    /**
     * 统计相册数据库中记录的照片文件大小。
     */
    private fun calculateAlbumPhotoBytes(countedPaths: MutableSet<String>): Long {
        return calculateReferencedFileBytes(
            countedPaths = countedPaths,
            databaseName = ALBUM_DATABASE_NAME,
            sql = "SELECT uri AS path FROM photos"
        )
    }

    /**
     * 估算内联 Base64 图片的解码后字节数，用于旧聊天图片直接存入数据库的情况。
     */
    private fun calculateBase64ImageBytes(base64Image: String): Long {
        val payload: String = base64Image.substringAfter(',', missingDelimiterValue = "")
        if (payload.isBlank()) return 0L
        val paddingBytes: Int = payload.takeLast(BASE64_PADDING_LOOKUP_LENGTH).count { char: Char -> char == '=' }
        return ((payload.length * BASE64_DECODE_NUMERATOR) / BASE64_DECODE_DENOMINATOR - paddingBytes).coerceAtLeast(0).toLong()
    }

    /**
     * 查询指定数据库中的字符串列表，用于读取图片或文件路径字段。
     */
    private fun queryStringListFromDatabase(databaseName: String, sql: String): List<String> {
        return try {
            openReadableDatabase(databaseName = databaseName)?.use { database: SQLiteDatabase ->
                database.rawQuery(sql, null).use { cursor: Cursor ->
                    val result: MutableList<String> = mutableListOf()
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.let { value: String -> result.add(value) }
                    }
                    result
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w("ApiSettingsAppFragment", "读取图片路径统计失败", e)
            emptyList()
        }
    }

    /**
     * 查询 aidata 数据库中的单个 Long 值，用于估算文本记录和向量二进制数据大小。
     */
    private fun queryLongFromAiDataDatabase(sql: String): Long {
        return try {
            openReadableAiDataDatabase()?.use { database: SQLiteDatabase ->
                database.rawQuery(sql, null).use { cursor: Cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
            } ?: 0L
        } catch (e: Exception) {
            Log.w("ApiSettingsAppFragment", "读取数据库空间统计失败", e)
            0L
        }
    }

    /**
     * 以只读方式打开 aidata 数据库，避免统计过程影响现有业务写入逻辑。
     */
    private fun openReadableAiDataDatabase(): SQLiteDatabase? {
        return openReadableDatabase(databaseName = AIDATA_DATABASE_NAME)
    }

    /**
     * 以只读方式打开指定数据库，避免统计过程影响现有业务写入逻辑。
     */
    private fun openReadableDatabase(databaseName: String): SQLiteDatabase? {
        val databaseFile: File = requireContext().getDatabasePath(databaseName)
        if (!databaseFile.exists()) return null
        return SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * 解析应用内文件路径或 URI，并返回能读取到的文件大小。
     */
    private fun calculatePathBytes(path: String): Long {
        val filePath: String = if (path.startsWith("file://")) {
            Uri.parse(path).path.orEmpty()
        } else {
            path
        }
        val file: File = File(filePath)
        if (file.exists()) return calculateFileBytes(file)

        return try {
            requireContext().contentResolver.openAssetFileDescriptor(Uri.parse(path), "r")?.use { descriptor ->
                descriptor.length.coerceAtLeast(0L)
            } ?: 0L
        } catch (e: Exception) {
            Log.w("ApiSettingsAppFragment", "无法读取文件大小: $path", e)
            0L
        }
    }

    /**
     * 递归统计目录或普通文件大小。
     */
    private fun calculateFileBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { child: File -> calculateFileBytes(child) } ?: 0L
    }

    /**
     * 统计应用内部文件目录大小。
     */
    private fun calculateDirectoryBytes(directory: File?): Long {
        return directory?.let { file: File -> calculateFileBytes(file) } ?: 0L
    }

    /**
     * 统计数据库目录中所有数据库及 WAL/SHM 辅助文件大小。
     */
    private fun calculateDatabaseDirectoryBytes(): Long {
        val databaseFile: File = requireContext().getDatabasePath(AIDATA_DATABASE_NAME)
        val databaseDirectory: File = databaseFile.parentFile ?: return 0L
        return calculateFileBytes(databaseDirectory)
    }

    /**
     * 存储空间统计结果。
     */
    private data class CacheCleanResult(
        val deletedCount: Int,
        val deletedBytes: Long
    )

    private data class StorageUsageStats(
        val avatarBytes: Long,
        val chatImageBytes: Long,
        val albumBytes: Long,
        val chatRecordBytes: Long,
        val vectorBytes: Long,
        val otherBytes: Long,
        val totalBytes: Long
    ) {
        companion object {
            fun formatBytes(bytes: Long): String {
                val kilobyte: Double = 1024.0
                val megabyte: Double = kilobyte * 1024.0
                val gigabyte: Double = megabyte * 1024.0
                return when {
                    bytes >= gigabyte -> "%.2f GB".format(Locale.getDefault(), bytes / gigabyte)
                    bytes >= megabyte -> "%.2f MB".format(Locale.getDefault(), bytes / megabyte)
                    bytes >= kilobyte -> "%.2f KB".format(Locale.getDefault(), bytes / kilobyte)
                    else -> "$bytes B"
                }
            }
        }
    }

    /**
     * aidata 主数据库名称，与数据层 Room 构建名称保持一致。
     */
    private companion object {
        private const val VOICE_MESSAGES_DIRECTORY_NAME: String = "voice_messages"
        private const val AIDATA_DATABASE_NAME: String = "aidata_database"
        private const val ALBUM_DATABASE_NAME: String = "album_database"
        private const val BASE64_PADDING_LOOKUP_LENGTH: Int = 2
        private const val BASE64_DECODE_NUMERATOR: Int = 3
        private const val BASE64_DECODE_DENOMINATOR: Int = 4
    }

    /**
     * 导出完整应用数据
     */
    private fun exportCompleteAppData() {
        AlertDialog.Builder(requireContext())
            .setTitle("导出完整应用数据")
            .setMessage("此操作将导出所有应用数据（包括QQ、世界书、相册、CPhone、桌面布局等），可能需要一些时间，是否继续？")
            .setPositiveButton("确定") { _, _ ->
                performExport()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行导出操作
     */
    private fun performExport() {
        lifecycleScope.launch {
            try {
                // 显示进度对话框
                showProgressDialog("正在导出数据...")
                
                // 通过AiDataApi获取ExportCompleteAppDataUseCase
                val exportUseCase = AiDataApi.getExportCompleteAppDataUseCase()
                
                // 执行导出
                val result = exportUseCase()
                
                // 隐藏进度对话框
                hideProgressDialog()
                
                // 显示结果
                result.onSuccess { message ->
                    showSuccessDialog(message)
                }.onFailure { error ->
                    showErrorDialog("导出失败：${error.message}")
                }
                
            } catch (e: Exception) {
                hideProgressDialog()
                showErrorDialog("导出异常：${e.message}")
                Log.e("ApiSettingsAppFragment", "导出失败", e)
            }
        }
    }
    
    /**
     * 选择导入文件
     */
    private fun selectImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        filePickerLauncher.launch(intent)
    }
    
    /**
     * 准备导入：显示预览信息
     */
    private fun prepareImportFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgressDialog("正在分析导入文件...")
                
                // 通过AiDataApi获取ImportCompleteAppDataUseCase
                val importUseCase = AiDataApi.getImportCompleteAppDataUseCase()
                
                // 准备导入
                val result = importUseCase.prepareImport(uri)
                
                hideProgressDialog()
                
                result.onSuccess { preview ->
                    showImportPreviewDialog(preview, uri)
                }.onFailure { error ->
                    showErrorDialog("分析文件失败：${error.message}")
                }
                
            } catch (e: Exception) {
                hideProgressDialog()
                showErrorDialog("分析文件异常：${e.message}")
                Log.e("ApiSettingsAppFragment", "准备导入失败", e)
            }
        }
    }
    
    /**
     * 显示导入预览对话框 - 支持导入模式选择
     */
    private fun showImportPreviewDialog(
        preview: com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase.ImportPreview,
        uri: Uri
    ) {
        // 显示导入模式选择对话框
        showImportModeSelectionDialog(preview, uri)
    }
    
    /**
     * 显示导入模式选择对话框
     */
    private fun showImportModeSelectionDialog(
        preview: com.susking.ephone_s.aidata.domain.use_case.ImportCompleteAppDataUseCase.ImportPreview,
        uri: Uri
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportDate = dateFormat.format(Date(preview.exportTimestamp))
        
        // 构建标题和消息
        val title = "完整应用数据导入"
        val stats = preview.conflictStats
        val message = buildString {
            appendLine("导出版本：${preview.exportVersion}")
            appendLine("导出时间：$exportDate")
            appendLine()
            appendLine("数据统计：")
            appendLine("• 联系人：${preview.contactCount}个")
            if (stats.newContacts > 0 || stats.conflictingContacts > 0) {
                appendLine("  (新增: ${stats.newContacts} | 冲突: ${stats.conflictingContacts})")
            }
            appendLine("• 消息：${preview.messageCount}条")
            appendLine("• 世界书：${preview.worldBookCount}个")
            if (stats.newWorldBooks > 0 || stats.conflictingWorldBooks > 0) {
                appendLine("  (新增: ${stats.newWorldBooks} | 冲突: ${stats.conflictingWorldBooks})")
            }
            appendLine("• 相册：${preview.albumCount}张")
            if (preview.cphoneDataCount > 0) {
                appendLine("• CPhone：${preview.cphoneDataCount}项")
            }
            if (preview.hasDesktopLayout) {
                append("• 桌面布局：有")
            }
        }
        
        // 使用自定义布局
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            com.susking.ephone_s.settings.R.layout.dialog_import_mode_selection,
            null
        )
        val tvMessage = dialogView.findViewById<TextView>(com.susking.ephone_s.settings.R.id.tvMessage)
        val radioGroup = dialogView.findViewById<RadioGroup>(com.susking.ephone_s.settings.R.id.radioGroupModes)
        
        tvMessage.text = message
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确认导入") { _: android.content.DialogInterface, _: Int ->
                when (radioGroup.checkedRadioButtonId) {
                    com.susking.ephone_s.settings.R.id.radioSmartMerge -> {
                        // 增量导入(智能合并) - 使用交互式导入
                        performInteractiveImport(uri)
                    }
                    com.susking.ephone_s.settings.R.id.radioOverwrite -> {
                        // 覆盖导入
                        performImport(uri, com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE)
                    }
                    else -> {
                        // 默认使用智能合并
                        performInteractiveImport(uri)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("说明") { _: android.content.DialogInterface, _: Int ->
                showImportModeDescription {
                    // 说明对话框关闭后，重新显示导入模式选择对话框
                    showImportModeSelectionDialog(preview, uri)
                }
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示导入模式说明对话框
     */
    private fun showImportModeDescription(onDismiss: (() -> Unit)? = null) {
        val description = """
            【增量导入(智能合并)】推荐
            选择文件后直接开始导入,遇到冲突时会询问您:
            • 联系人资料:逐字段对比,每个字段冲突都会询问您保留哪个
            • 聊天消息/记忆等:整体对比,冲突时询问您保留哪条
            • 相同数据:自动跳过,不重复导入
            • 新数据:自动添加,不会丢失
            • 导入完成后显示详细统计:新增几条、冲突几条
            
            适用场景:
            ✓ 想精确控制每个冲突的处理方式
            ✓ 需要合并多个数据源
            ✓ 不确定要保留哪些数据
            
            【覆盖导入】
            完全清空现有数据,导入全部新数据。
            注意:此操作会删除所有现有数据,不可撤销!
            
            适用场景:
            ✓ 想完全替换所有数据
            ✓ 确定导入的数据是最新最全的
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("导入模式说明")
            .setMessage(description)
            .setPositiveButton("我知道了") { _, _ ->
                onDismiss?.invoke()
            }
            .show()
    }
    
    /**
     * 执行导入操作 - 支持导入模式
     */
    private fun performImport(
        uri: Uri,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode =
            com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE
    ) {
        lifecycleScope.launch {
            try {
                showProgressDialog("正在导入数据，请稍候...")
                
                // 通过AiDataApi获取ImportCompleteAppDataUseCase
                val importUseCase = AiDataApi.getImportCompleteAppDataUseCase()
                
                // 执行导入(传入导入模式)
                val outcome = importUseCase.executeImport(uri, mode)

                hideProgressDialog()

                when (outcome) {
                    is ImportOutcome.Success -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("导入成功")
                            .setMessage("${outcome.data}\n\n建议重启应用以确保数据正常加载。")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                    is ImportOutcome.Failed -> {
                        showErrorDialog("导入失败：${outcome.error.message}")
                    }
                    is ImportOutcome.RolledBack -> {
                        showRolledBackDialogAndRestart(outcome)
                    }
                }

            } catch (e: Exception) {
                hideProgressDialog()
                showErrorDialog("导入异常：${e.message}")
                Log.e("ApiSettingsAppFragment", "执行导入失败", e)
            }
        }
    }

    /**
     * 显示「导入失败、数据已回滚」提示框，用户确认后重启 App。
     *
     * 数据已用快照恢复到导入前状态，但内存中仍持有旧的数据库连接 / 缓存，必须重启进程才能读到
     * 恢复后的数据。重启前应阻止用户进行其他操作，故用不可取消的弹框承接这一空档。
     */
    private fun showRolledBackDialogAndRestart(outcome: ImportOutcome.RolledBack) {
        val message: String = if (outcome.restoredCleanly) {
            "导入过程中出错，您的数据已恢复到导入前的状态，未发生丢失。\n\n" +
                "点击确定将重启应用以完成恢复。\n\n失败原因：${outcome.error.message}"
        } else {
            "导入过程中出错，正在尝试恢复数据时也遇到了问题，数据可能不完整。\n\n" +
                "点击确定将重启应用，请重启后检查数据。\n\n失败原因：${outcome.error.message}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("导入失败，已回滚")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("确定并重启") { _, _ ->
                BackupSnapshotHelper(requireContext().applicationContext).restartApp()
            }
            .show()
    }
    
    /**
     * 执行交互式增量导入(智能合并模式)
     */
    private fun performInteractiveImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgressDialog("正在导入数据，请稍候...")
                
                // 通过AiDataApi获取ImportCompleteAppDataUseCase
                val importUseCase = AiDataApi.getImportCompleteAppDataUseCase()
                
                // 执行交互式导入
                val result = importUseCase.executeInteractiveImport(uri) { conflictItem ->
                    // 隐藏进度对话框,显示冲突解决对话框
                    hideProgressDialog()
                    val resolution = showConflictResolutionDialogSuspend(conflictItem)
                    // 用户做出选择后,重新显示进度对话框
                    showProgressDialog("正在导入数据，请稍候...")
                    resolution
                }
                
                hideProgressDialog()

                when (val outcome = result) {
                    is ImportOutcome.Success -> {
                        showImportResultDialog(outcome.data)
                    }
                    is ImportOutcome.Failed -> {
                        showErrorDialog("导入失败：${outcome.error.message}")
                    }
                    is ImportOutcome.RolledBack -> {
                        showRolledBackDialogAndRestart(outcome)
                    }
                }

            } catch (e: Exception) {
                hideProgressDialog()
                showErrorDialog("导入异常：${e.message}")
                Log.e("ApiSettingsAppFragment", "交互式导入失败", e)
            }
        }
    }
    
    /**
     * 显示冲突解决对话框(挂起函数)
     * @return 用户的选择
     */
    private suspend fun showConflictResolutionDialogSuspend(
        conflictItem: ConflictItem
    ): ConflictResolution = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        // 检查是否为"有现有数据,无导入数据"的情况
        val hasExisting: Boolean
        val hasImport: Boolean
        
        when (conflictItem) {
            is ConflictItem.PersonProfileFieldConflict -> {
                hasExisting = conflictItem.existingValue != null
                hasImport = conflictItem.importValue != null
            }
            is ConflictItem.ChatMessageConflict -> {
                hasExisting = conflictItem.existingMessage.isNotEmpty()
                hasImport = conflictItem.importMessage.isNotEmpty()
            }
            is ConflictItem.LongTermMemoryConflict -> {
                hasExisting = conflictItem.existingMemory.isNotEmpty()
                hasImport = conflictItem.importMemory.isNotEmpty()
            }
            is ConflictItem.JottingConflict -> {
                hasExisting = conflictItem.existingJotting.isNotEmpty()
                hasImport = conflictItem.importJotting.isNotEmpty()
            }
            is ConflictItem.HeartbeatConflict -> {
                hasExisting = conflictItem.existingHeartbeat.isNotEmpty()
                hasImport = conflictItem.importHeartbeat.isNotEmpty()
            }
            is ConflictItem.WorldBookConflict -> {
                hasExisting = conflictItem.existingWorldBook.isNotEmpty()
                hasImport = conflictItem.importWorldBook.isNotEmpty()
            }
            is ConflictItem.WorldBookEntryConflict -> {
                hasExisting = conflictItem.existingEntry.isNotEmpty()
                hasImport = conflictItem.importEntry.isNotEmpty()
            }
            is ConflictItem.FavoriteMessageConflict -> {
                hasExisting = conflictItem.existingFavorite.isNotEmpty()
                hasImport = conflictItem.importFavorite.isNotEmpty()
            }
            is ConflictItem.FeedConflict -> {
                hasExisting = conflictItem.existingFeed.isNotEmpty()
                hasImport = conflictItem.importFeed.isNotEmpty()
            }
        }
        
        // 如果"有现有数据,无导入数据",直接保留现有数据,不展示对话框
        if (hasExisting && !hasImport) {
            continuation.resume(ConflictResolution.KEEP_EXISTING, null)
            return@suspendCancellableCoroutine
        }
        
        requireActivity().runOnUiThread {
            val dialogBinding = DialogConflictResolutionBinding.inflate(layoutInflater)
            
            // 设置冲突信息
            when (conflictItem) {
                is ConflictItem.PersonProfileFieldConflict -> {
                    dialogBinding.tvConflictTitle.text = "联系人资料字段冲突"
                    dialogBinding.tvConflictId.text = "字段: ${conflictItem.fieldName}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName} (${conflictItem.contactId})"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatValue(conflictItem.existingValue)
                    dialogBinding.tvImportData.text = formatValue(conflictItem.importValue)
                }
                is ConflictItem.ChatMessageConflict -> {
                    dialogBinding.tvConflictTitle.text = "聊天消息冲突"
                    dialogBinding.tvConflictId.text = "消息ID: ${conflictItem.messageId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingMessage)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importMessage)
                }
                is ConflictItem.LongTermMemoryConflict -> {
                    dialogBinding.tvConflictTitle.text = "长期记忆冲突"
                    dialogBinding.tvConflictId.text = "记忆ID: ${conflictItem.memoryId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingMemory)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importMemory)
                }
                is ConflictItem.JottingConflict -> {
                    dialogBinding.tvConflictTitle.text = "随笔冲突"
                    dialogBinding.tvConflictId.text = "随笔ID: ${conflictItem.jottingId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingJotting)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importJotting)
                }
                is ConflictItem.HeartbeatConflict -> {
                    dialogBinding.tvConflictTitle.text = "心声冲突"
                    dialogBinding.tvConflictId.text = "心声ID: ${conflictItem.heartbeatId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingHeartbeat)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importHeartbeat)
                }
                is ConflictItem.WorldBookConflict -> {
                    dialogBinding.tvConflictTitle.text = "世界书冲突"
                    dialogBinding.tvConflictId.text = "世界书ID: ${conflictItem.worldBookId}"
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingWorldBook)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importWorldBook)
                }
                is ConflictItem.WorldBookEntryConflict -> {
                    dialogBinding.tvConflictTitle.text = "世界书条目冲突"
                    dialogBinding.tvConflictId.text = "条目ID: ${conflictItem.entryId}"
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingEntry)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importEntry)
                }
                is ConflictItem.FavoriteMessageConflict -> {
                    dialogBinding.tvConflictTitle.text = "收藏消息冲突"
                    dialogBinding.tvConflictId.text = "消息ID: ${conflictItem.messageId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingFavorite)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importFavorite)
                }
                is ConflictItem.FeedConflict -> {
                    dialogBinding.tvConflictTitle.text = "动态冲突"
                    dialogBinding.tvConflictId.text = "动态ID: ${conflictItem.feedId}"
                    dialogBinding.tvContactInfo.text = "联系人: ${conflictItem.contactRealName}"
                    dialogBinding.tvContactInfo.visibility = View.VISIBLE
                    dialogBinding.tvTimestamp.text = "时间: ${formatTimestamp(conflictItem.timestamp)}"
                    dialogBinding.tvTimestamp.visibility = View.VISIBLE
                    dialogBinding.tvExistingData.text = formatMap(conflictItem.existingFeed)
                    dialogBinding.tvImportData.text = formatMap(conflictItem.importFeed)
                }
            }
            
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogBinding.root)
                .setPositiveButton("使用导入数据") { _, _ ->
                    continuation.resume(ConflictResolution.USE_IMPORT, null)
                }
                .setNegativeButton("保留现有数据") { _, _ ->
                    continuation.resume(ConflictResolution.KEEP_EXISTING, null)
                }
                .setCancelable(false)
                .create()
            
            continuation.invokeOnCancellation {
                dialog.dismiss()
            }
            
            dialog.show()
            
            // 动态调整ScrollView高度，使其平分剩余空间
            dialog.window?.let { window ->
                dialogBinding.root.post {
                    val displayMetrics = resources.displayMetrics
                    val screenHeight = displayMetrics.heightPixels
                    val maxDialogHeight = (screenHeight * 0.8).toInt() // 对话框最大高度为屏幕的80%
                    
                    // 测量固定元素的高度
                    var fixedHeight = 0
                    
                    // 标题
                    dialogBinding.tvConflictTitle.measure(
                        View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED
                    )
                    fixedHeight += dialogBinding.tvConflictTitle.measuredHeight
                    
                    // ID信息
                    dialogBinding.tvConflictId.measure(
                        View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED
                    )
                    fixedHeight += dialogBinding.tvConflictId.measuredHeight
                    
                    // 联系人信息（如果可见）
                    if (dialogBinding.tvContactInfo.visibility == View.VISIBLE) {
                        dialogBinding.tvContactInfo.measure(
                            View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.UNSPECIFIED
                        )
                        fixedHeight += dialogBinding.tvContactInfo.measuredHeight
                    }
                    
                    // 时间戳（如果可见）
                    if (dialogBinding.tvTimestamp.visibility == View.VISIBLE) {
                        dialogBinding.tvTimestamp.measure(
                            View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.UNSPECIFIED
                        )
                        fixedHeight += dialogBinding.tvTimestamp.measuredHeight
                    }
                    
                    // "现有数据:"标签
                    val tvExistingLabel = dialogBinding.root.findViewById<TextView>(
                        com.susking.ephone_s.settings.R.id.tvExistingData
                    )?.parent?.let { parent ->
                        (parent as? ViewGroup)?.getChildAt(0) as? TextView
                    }
                    tvExistingLabel?.measure(
                        View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED
                    )
                    fixedHeight += tvExistingLabel?.measuredHeight ?: 0
                    
                    // "导入数据:"标签
                    val tvImportLabel = dialogBinding.root.findViewById<TextView>(
                        com.susking.ephone_s.settings.R.id.tvImportData
                    )?.parent?.let { parent ->
                        (parent as? ViewGroup)?.getChildAt(0) as? TextView
                    }
                    tvImportLabel?.measure(
                        View.MeasureSpec.makeMeasureSpec(dialogBinding.root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED
                    )
                    fixedHeight += tvImportLabel?.measuredHeight ?: 0
                    
                    // 加上内边距和边距
                    val padding = dialogBinding.root.paddingTop + dialogBinding.root.paddingBottom
                    fixedHeight += padding + 200 // 额外空间用于按钮和其他边距
                    
                    // 计算可用于ScrollView的高度
                    val availableHeight = maxDialogHeight - fixedHeight
                    
                    // 每个ScrollView分配一半高度
                    if (availableHeight > 100) { // 确保有足够的空间
                        val scrollViewHeight = availableHeight / 2
                        
                        val scrollViewExisting = dialogBinding.root.findViewById<android.widget.ScrollView>(
                            com.susking.ephone_s.settings.R.id.scrollViewExistingData
                        )
                        val scrollViewImport = dialogBinding.root.findViewById<android.widget.ScrollView>(
                            com.susking.ephone_s.settings.R.id.scrollViewImportData
                        )
                        
                        scrollViewExisting?.layoutParams = (scrollViewExisting?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                            height = scrollViewHeight
                        }
                        
                        scrollViewImport?.layoutParams = (scrollViewImport?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                            height = scrollViewHeight
                        }
                        
                        dialogBinding.root.requestLayout()
                    }
                }
            }
        }
    }
    
    /**
     * 显示导入结果对话框
     */
    private fun showImportResultDialog(result: ImportResult) {
        val dialogBinding = DialogImportResultBinding.inflate(layoutInflater)
        
        // 设置统计结果
        dialogBinding.tvImportStats.text = result.formatReport()
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            // 建议重启应用
            AlertDialog.Builder(requireContext())
                .setTitle("提示")
                .setMessage("建议重启应用以确保数据正常加载。")
                .setPositiveButton("确定", null)
                .show()
        }
        
        dialog.show()
    }
    
    /**
     * 格式化值用于显示
     */
    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "(空)"
            is String -> if (value.isEmpty()) "(空字符串)" else value
            is List<*> -> value.joinToString(", ")
            else -> value.toString()
        }
    }
    
    /**
     * 格式化Map用于显示
     */
    private fun formatMap(map: Map<String, Any?>): String {
        return map.entries.joinToString("\n") { (key, value) ->
            "$key: ${formatValue(value)}"
        }
    }
    
    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 显示进度对话框
     */
    private fun showProgressDialog(message: String) {
        progressDialog = ProgressDialog(requireContext()).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }
    
    /**
     * 隐藏进度对话框
     */
    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    /**
     * 显示成功对话框
     */
    private fun showSuccessDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("导出成功")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("导出失败")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideProgressDialog()
        _binding = null
    }
}