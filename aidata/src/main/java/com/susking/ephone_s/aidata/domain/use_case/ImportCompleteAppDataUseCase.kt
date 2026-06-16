package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntity
import com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity
import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.domain.model.CPhoneData
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictItem
import com.susking.ephone_s.aidata.domain.model.import_export.ConflictResolution
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.domain.model.import_export.ImportOutcome
import com.susking.ephone_s.aidata.domain.model.import_export.ImportResult
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.aidata.util.BackupSnapshotHelper
import com.susking.ephone_s.aidata.util.ImportPathProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * 完整应用数据导入UseCase
 * 
 * 导入完整应用数据，包括：
 * 1. QQ数据
 * 2. 世界书数据
 * 3. 相册数据
 * 4. CPhone数据
 * 5. 桌面布局数据
 * 6. 主题偏好数据
 * 7. SharedPreferences设置
 */
class ImportCompleteAppDataUseCase @Inject constructor(
    private val context: Context,
    private val dataImportExportRepository: DataImportExportRepository,
    private val worldBookRepository: WorldBookRepository,
    private val worldBookEntryRepository: WorldBookEntryRepository,
    private val shoppingDatabase: ShoppingDatabase,
    private val cphoneRepository: CPhoneRepository,
    private val settingsRepository: SettingsRepository,
    private val albumDataProvider: AlbumDataProvider?,
    private val desktopDataProvider: DesktopDataProvider?
) {
    
    private val gson: Gson = GsonBuilder().create()
    private val pathProcessor = ImportPathProcessor(context)
    private val snapshotHelper = BackupSnapshotHelper(context)

    companion object {
        private const val TAG = "ImportCompleteAppData"
        private const val THEME_PREFERENCES_NAME = "theme_prefs"
        private const val KEY_CURRENT_THEME_SNAPSHOT = "current_theme_snapshot"
        private const val KEY_CUSTOM_THEMES_JSON = "custom_themes_json"
        private const val KEY_PINNED_THEME_IDS_JSON = "pinned_theme_ids_json"
        private const val THEME_RESOURCES_DIR = "themes/resources"
        private const val THEME_RESOURCE_URI_PREFIX = "theme_resource://"
        // 主题导入完成广播：通知 app 模块的 ThemeRepository 单例重新读盘刷新内存状态。
        // 用广播而非直接依赖，是因为 aidata 模块不能反向依赖 app 的 ThemeRepository。
        private const val ACTION_THEME_RELOAD = "com.susking.ephone_s.ACTION_THEME_RELOAD"
    }
    
    /**
     * 冲突统计信息
     */
    data class ConflictStats(
        val newContacts: Int = 0,
        val conflictingContacts: Int = 0,
        val newWorldBooks: Int = 0,
        val conflictingWorldBooks: Int = 0
    )
    
    /**
     * 数据导入预览信息
     */
    data class ImportPreview(
        val exportVersion: String,
        val exportTimestamp: Long,
        val exportType: String,
        val modules: List<String>,
        val contactCount: Int,
        val messageCount: Int,
        val worldBookCount: Int,
        val albumCount: Int,
        val cphoneDataCount: Int,
        val hasDesktopLayout: Boolean,
        val conflictStats: ConflictStats = ConflictStats(),
        val mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode = com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE
    )
    
    /**
     * 准备导入：解析ZIP并返回预览信息
     */
    suspend fun prepareImport(uri: Uri): Result<ImportPreview> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "complete_import_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()
            
            // 解压ZIP文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                unzipFile(inputStream, tempDir)
            } ?: return@withContext Result.failure(Exception("无法打开文件"))
            
            // 读取manifest.json
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("无效的备份文件：缺少manifest.json"))
            }
            
            val manifest = gson.fromJson(
                manifestFile.readText(),
                object : TypeToken<Map<String, Any>>() {}.type
            ) as Map<String, Any>
            
            // 验证导出类型
            val exportType = manifest["exportType"] as? String
            if (exportType != "COMPLETE_APP_DATA") {
                return@withContext Result.failure(Exception("不支持的备份类型：$exportType"))
            }
            
            // 统计数据
            var contactCount = 0
            var messageCount = 0
            var worldBookCount = 0
            var albumCount = 0
            var cphoneDataCount = 0
            var hasDesktopLayout = false
            
            // 获取现有联系人ID列表和世界书ID列表，用于计算冲突
            val existingContactIds = dataImportExportRepository.getAllContactIds()
            val existingWorldBookIds = worldBookRepository.getAllWorldBookIds()
            
            var newContacts = 0
            var conflictingContacts = 0
            var newWorldBooks = 0
            var conflictingWorldBooks = 0
            
            // 读取QQ数据统计
            val qqDataFile = File(tempDir, "qq/data.json")
            if (qqDataFile.exists()) {
                val qqData = gson.fromJson(
                    qqDataFile.readText(),
                    ExportData::class.java
                )
                
                qqData.contacts?.let { contacts ->
                    contactCount = contacts.size
                    contacts.forEach { contact ->
                        if (existingContactIds.contains(contact.id)) {
                            conflictingContacts++
                        } else {
                            newContacts++
                        }
                    }
                }
                
                qqData.chatMessages?.let { messageCount = it.size }
            }
            
            // 读取世界书数据统计
            val worldbookDataFile = File(tempDir, "worldbook/data.json")
            if (worldbookDataFile.exists()) {
                val worldbookData = gson.fromJson(
                    worldbookDataFile.readText(),
                    object : TypeToken<Map<String, Any>>() {}.type
                ) as Map<String, Any>
                
                (worldbookData["worldBooks"] as? List<*>)?.let { worldBooksList ->
                    worldBookCount = worldBooksList.size
                    val worldBooksJson = gson.toJson(worldBooksList)
                    val worldBooks = gson.fromJson(worldBooksJson, object : TypeToken<List<WorldBookEntity>>() {}.type) as List<WorldBookEntity>
                    worldBooks.forEach { worldBook ->
                        if (existingWorldBookIds.contains(worldBook.worldBookId)) {
                            conflictingWorldBooks++
                        } else {
                            newWorldBooks++
                        }
                    }
                }
            }
            
            // 读取相册数据统计
            val albumDataFile = File(tempDir, "album/data.json")
            if (albumDataFile.exists()) {
                val albumData = gson.fromJson(
                    albumDataFile.readText(),
                    object : TypeToken<Map<String, Any>>() {}.type
                ) as Map<String, Any>
                
                (albumData["albums"] as? List<*>)?.let { albumCount = it.size }
            }
            
            // 读取CPhone数据统计
            val cphoneDataFile = File(tempDir, "cphone/data.json")
            if (cphoneDataFile.exists()) {
                val cphoneData = gson.fromJson(
                    cphoneDataFile.readText(),
                    object : TypeToken<Map<String, Any>>() {}.type
                ) as Map<String, Any>
                
                (cphoneData["cphoneData"] as? Map<*, *>)?.let { cphoneDataCount = it.size }
            }
            
            // 检查桌面布局
            val desktopLayoutFile = File(tempDir, "desktop/layout.json")
            hasDesktopLayout = desktopLayoutFile.exists()
            
            @Suppress("UNCHECKED_CAST")
            val modules = (manifest["modules"] as? List<Map<String, String>>)?.map { it["name"] ?: "" } ?: emptyList()
            
            val conflictStats = ConflictStats(
                newContacts = newContacts,
                conflictingContacts = conflictingContacts,
                newWorldBooks = newWorldBooks,
                conflictingWorldBooks = conflictingWorldBooks
            )
            
            val preview = ImportPreview(
                exportVersion = manifest["exportVersion"] as? String ?: "unknown",
                exportTimestamp = (manifest["exportTimestamp"] as? Double)?.toLong() ?: 0L,
                exportType = exportType,
                modules = modules,
                contactCount = contactCount,
                messageCount = messageCount,
                worldBookCount = worldBookCount,
                albumCount = albumCount,
                cphoneDataCount = cphoneDataCount,
                hasDesktopLayout = hasDesktopLayout,
                conflictStats = conflictStats
            )
            
            Result.success(preview)
            
        } catch (e: Exception) {
            Log.e(TAG, "准备导入失败", e)
            Result.failure(e)
        } finally {
            // 不删除临时目录，等执行导入时使用
        }
    }
    
    /**
     * 执行导入
     * @param uri ZIP文件URI
     * @param mode 导入模式
     */
    suspend fun executeImport(
        uri: Uri,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode = com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE
    ): ImportOutcome<String> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "complete_import_${System.currentTimeMillis()}")

        // 1. 解压 ZIP（此阶段失败时尚未触碰用户数据，返回 Failed 而非 RolledBack）
        try {
            tempDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                unzipFile(inputStream, tempDir)
            } ?: run {
                tempDir.deleteRecursively()
                return@withContext ImportOutcome.Failed(Exception("无法打开文件"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解压备份文件失败", e)
            tempDir.deleteRecursively()
            return@withContext ImportOutcome.Failed(e)
        }

        // 2. 创建回滚快照（此阶段失败同样未触碰用户数据，返回 Failed）
        val snapshotDir: File = try {
            snapshotHelper.createSnapshot()
        } catch (e: Exception) {
            Log.e(TAG, "创建回滚快照失败，已中止导入以保护现有数据", e)
            tempDir.deleteRecursively()
            return@withContext ImportOutcome.Failed(
                Exception("无法创建数据备份，已中止导入以保护现有数据：${e.message}", e)
            )
        }

        // 3. 执行导入；任一环节失败则用快照回滚
        try {
            Log.d(TAG, "开始导入完整应用数据,模式: ${mode.name}")

            importQQData(tempDir, mode)
            importWorldBookData(tempDir)
            importShoppingData(tempDir)
            importAlbumData(tempDir)
            importCPhoneData(tempDir)
            importDesktopData(tempDir)
            importThemeData(tempDir)
            importChatRecordsData(tempDir)

            val modeDesc = when (mode) {
                com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.OVERWRITE -> "覆盖"
                com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_KEEP_EXISTING -> "增量(保留现有)"
                com.susking.ephone_s.aidata.domain.model.import_export.ImportMode.INCREMENTAL_PREFER_IMPORT -> "增量(优先导入)"
            }
            Log.d(TAG, "完整应用数据${modeDesc}导入完成")
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.Success("完整应用数据${modeDesc}导入成功！")

        } catch (e: Exception) {
            Log.e(TAG, "导入失败，开始回滚到导入前状态", e)
            val restoredCleanly: Boolean = snapshotHelper.restoreSnapshot(snapshotDir)
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.RolledBack(error = e, restoredCleanly = restoredCleanly)
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * 执行交互式增量导入
     * 遇到冲突时通过回调询问用户如何处理
     * @param uri ZIP文件URI
     * @param onConflict 冲突回调函数,返回用户的选择
     * @return 导入结果统计
     */
    suspend fun executeInteractiveImport(
        uri: Uri,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportOutcome<ImportResult> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "complete_import_${System.currentTimeMillis()}")

        // 1. 解压 ZIP（尚未触碰用户数据）
        try {
            tempDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                unzipFile(inputStream, tempDir)
            } ?: run {
                tempDir.deleteRecursively()
                return@withContext ImportOutcome.Failed(Exception("无法打开文件"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解压备份文件失败", e)
            tempDir.deleteRecursively()
            return@withContext ImportOutcome.Failed(e)
        }

        // 2. 创建回滚快照（尚未触碰用户数据）
        val snapshotDir: File = try {
            snapshotHelper.createSnapshot()
        } catch (e: Exception) {
            Log.e(TAG, "创建回滚快照失败，已中止导入以保护现有数据", e)
            tempDir.deleteRecursively()
            return@withContext ImportOutcome.Failed(
                Exception("无法创建数据备份，已中止导入以保护现有数据：${e.message}", e)
            )
        }

        // 3. 执行交互式导入；失败则回滚
        try {
            Log.d(TAG, "开始交互式增量导入")
            val qqResult = importQQDataInteractive(tempDir, onConflict)
            Log.d(TAG, "交互式增量导入完成")
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.Success(qqResult)

        } catch (e: Exception) {
            Log.e(TAG, "交互式导入失败，开始回滚到导入前状态", e)
            val restoredCleanly: Boolean = snapshotHelper.restoreSnapshot(snapshotDir)
            snapshotHelper.discardSnapshot(snapshotDir)
            ImportOutcome.RolledBack(error = e, restoredCleanly = restoredCleanly)
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * 交互式导入QQ数据
     */
    private suspend fun importQQDataInteractive(
        tempDir: File,
        onConflict: suspend (ConflictItem) -> ConflictResolution
    ): ImportResult {
        Log.d(TAG, "开始交互式导入QQ数据")
        
        val qqDataFile = File(tempDir, "qq/data.json")
        if (!qqDataFile.exists()) {
            Log.w(TAG, "QQ数据文件不存在，返回空结果")
            return ImportResult()
        }
        
        try {
            // 读取QQ data.json
            val qqDataJson = qqDataFile.readText()
            val qqData = gson.fromJson(
                qqDataJson,
                ExportData::class.java
            )
            
            // 处理图片路径：将相对路径转换为绝对路径
            val qqDir = File(tempDir, "qq")
            val processedData = pathProcessor.processAllDataPaths(qqData, qqDir)
            
            // 使用交互式导入
            val result = dataImportExportRepository.importAllDataInteractive(
                data = processedData,
                onConflict = onConflict
            )
            
            // 导入SharedPreferences
            val settingsFile = File(tempDir, "qq/settings.json")
            if (settingsFile.exists()) {
                val settings = gson.fromJson(
                    settingsFile.readText(),
                    object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                ) as Map<String, Map<String, Any>>
                
                settingsRepository.importMultipleSharedPreferences(settings)
            }
            
            Log.d(TAG, "QQ数据交互式导入完成")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "QQ数据交互式导入失败", e)
            throw e
        }
    }
    
    /**
     * 解压ZIP文件
     */
    private fun unzipFile(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                
                // 安全检查：防止路径遍历攻击
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip路径在目标目录之外: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    
    /**
     * 导入QQ数据
     * @param tempDir 临时目录
     * @param mode 导入模式
     */
    private suspend fun importQQData(
        tempDir: File,
        mode: com.susking.ephone_s.aidata.domain.model.import_export.ImportMode
    ) {
        Log.d(TAG, "开始导入QQ数据,模式: ${mode.name}")
        
        val qqDataFile = File(tempDir, "qq/data.json")
        if (!qqDataFile.exists()) {
            Log.w(TAG, "QQ数据文件不存在，跳过")
            return
        }
        
        try {
            // 读取QQ data.json
            val qqDataJson = qqDataFile.readText()
            val qqData = gson.fromJson(
                qqDataJson,
                ExportData::class.java
            )
            
            // 处理图片路径：将相对路径转换为绝对路径
            val qqDir = File(tempDir, "qq")
            val processedData = pathProcessor.processAllDataPaths(qqData, qqDir)
            
            // 根据导入模式选择不同的导入方法
            if (mode.isIncremental()) {
                dataImportExportRepository.importAllDataIncremental(processedData, mode)
            } else {
                dataImportExportRepository.importAllData(processedData)
            }
            
            // 导入SharedPreferences
            val settingsFile = File(tempDir, "qq/settings.json")
            if (settingsFile.exists()) {
                val settings = gson.fromJson(
                    settingsFile.readText(),
                    object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                ) as Map<String, Map<String, Any>>
                
                settingsRepository.importMultipleSharedPreferences(settings)
            }
            
            Log.d(TAG, "QQ数据导入完成")
        } catch (e: Exception) {
            Log.e(TAG, "QQ数据导入失败", e)
            throw e
        }
    }
    
    /**
     * 导入世界书数据
     */
    private suspend fun importWorldBookData(tempDir: File) {
        Log.d(TAG, "开始导入世界书数据")
        
        val worldbookDataFile = File(tempDir, "worldbook/data.json")
        if (!worldbookDataFile.exists()) {
            Log.w(TAG, "世界书数据文件不存在，跳过")
            return
        }
        
        val worldbookData = gson.fromJson(
            worldbookDataFile.readText(),
            object : TypeToken<Map<String, Any>>() {}.type
        ) as Map<String, Any>
        
        // 导入世界书
        val worldBooksJson = gson.toJson(worldbookData["worldBooks"])
        val worldBooks = gson.fromJson(worldBooksJson, object : TypeToken<List<WorldBookEntity>>() {}.type) as List<WorldBookEntity>
        
        // 导入世界书条目
        val entriesJson = gson.toJson(worldbookData["worldBookEntries"])
        val entries = gson.fromJson(entriesJson, object : TypeToken<List<WorldBookEntryEntity>>() {}.type) as List<WorldBookEntryEntity>
        
        // 保存到数据库
        // 使用insert方法，因为这是导入新数据
        worldBooks.forEach { worldBookRepository.insertWorldBook(it) }
        entries.forEach { worldBookEntryRepository.insertEntry(it) }
        
        // 导入SharedPreferences
        val settingsFile = File(tempDir, "worldbook/settings.json")
        if (settingsFile.exists()) {
            val settings = gson.fromJson(
                settingsFile.readText(),
                object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            ) as Map<String, Map<String, Any>>
            
            if (settings.isNotEmpty()) {
                settingsRepository.importMultipleSharedPreferences(settings)
            }
        }
        
        Log.d(TAG, "世界书数据导入完成")
    }

    /**
     * 导入购物应用数据
     */
    private suspend fun importShoppingData(tempDir: File) {
        Log.d(TAG, "开始导入购物数据")

        val shoppingDataFile = File(tempDir, "shopping/data.json")
        if (!shoppingDataFile.exists()) {
            Log.w(TAG, "购物数据文件不存在，跳过")
            return
        }

        val shoppingData = gson.fromJson(
            shoppingDataFile.readText(),
            object : TypeToken<Map<String, Any>>() {}.type
        ) as Map<String, Any>

        // 导入分类
        val categoriesJson = gson.toJson(shoppingData["shoppingCategories"])
        val categories = gson.fromJson(categoriesJson, object : TypeToken<List<com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity>>() {}.type)
            as? List<com.susking.ephone_s.aidata.data.local.entity.ShoppingCategoryEntity> ?: emptyList()

        // 导入商品
        val productsJson = gson.toJson(shoppingData["shoppingProducts"])
        val products = gson.fromJson(productsJson, object : TypeToken<List<com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity>>() {}.type)
            as? List<com.susking.ephone_s.aidata.data.local.entity.ShoppingProductEntity> ?: emptyList()

        // 导入购物车
        val cartItemsJson = gson.toJson(shoppingData["shoppingCartItems"])
        val cartItems = gson.fromJson(cartItemsJson, object : TypeToken<List<com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity>>() {}.type)
            as? List<com.susking.ephone_s.aidata.data.local.entity.ShoppingCartItemEntity> ?: emptyList()

        // 导入订单
        val ordersJson = gson.toJson(shoppingData["shoppingOrders"])
        val orders = gson.fromJson(ordersJson, object : TypeToken<List<com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity>>() {}.type)
            as? List<com.susking.ephone_s.aidata.data.local.entity.ShoppingOrderEntity> ?: emptyList()

        // 导入授权账号
        val accountsJson = gson.toJson(shoppingData["shoppingAuthorizedAccounts"])
        val accounts = gson.fromJson(accountsJson, object : TypeToken<List<com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity>>() {}.type)
            as? List<com.susking.ephone_s.aidata.data.local.entity.ShoppingAuthorizedAccountEntity> ?: emptyList()

        // 保存到数据库(批量插入)
        if (categories.isNotEmpty()) shoppingDatabase.shoppingCategoryDao().insertCategories(categories)
        if (products.isNotEmpty()) shoppingDatabase.shoppingProductDao().insertProducts(products)
        if (cartItems.isNotEmpty()) shoppingDatabase.shoppingCartDao().insertCartItems(cartItems)
        if (orders.isNotEmpty()) shoppingDatabase.shoppingOrderDao().insertOrders(orders)
        if (accounts.isNotEmpty()) shoppingDatabase.shoppingAuthorizedAccountDao().insertAuthorizedAccounts(accounts)

        // 导入SharedPreferences
        val settingsFile = File(tempDir, "shopping/settings.json")
        if (settingsFile.exists()) {
            val settings = gson.fromJson(
                settingsFile.readText(),
                object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            ) as Map<String, Map<String, Any>>

            if (settings.isNotEmpty()) {
                settingsRepository.importMultipleSharedPreferences(settings)
            }
        }

        Log.d(TAG, "购物数据导入完成: ${categories.size}分类, ${products.size}商品, ${cartItems.size}购物车, ${orders.size}订单, ${accounts.size}授权账号")
    }

    /**
     * 导入相册数据
     */
    private suspend fun importAlbumData(tempDir: File) {
        Log.d(TAG, "开始导入相册数据")
        
        val albumDataFile = File(tempDir, "album/data.json")
        if (!albumDataFile.exists()) {
            Log.w(TAG, "相册数据文件不存在，跳过")
            return
        }
        
        if (albumDataProvider != null) {
            albumDataProvider.importAlbumData(tempDir)
            
            // 导入SharedPreferences
            val settingsFile = File(tempDir, "album/settings.json")
            if (settingsFile.exists()) {
                val settings = gson.fromJson(
                    settingsFile.readText(),
                    object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                ) as Map<String, Map<String, Any>>
                
                if (settings.isNotEmpty()) {
                    settingsRepository.importMultipleSharedPreferences(settings)
                }
            }
        } else {
            Log.w(TAG, "AlbumDataProvider未实现，跳过相册数据导入")
        }
        
        Log.d(TAG, "相册数据导入完成")
    }
    
    /**
     * 导入CPhone数据
     */
    private suspend fun importCPhoneData(tempDir: File) {
        Log.d(TAG, "开始导入CPhone数据")
        
        val cphoneDataFile = File(tempDir, "cphone/data.json")
        if (!cphoneDataFile.exists()) {
            Log.w(TAG, "CPhone数据文件不存在，跳过")
            return
        }
        
        val cphoneData = gson.fromJson(
            cphoneDataFile.readText(),
            object : TypeToken<Map<String, Any>>() {}.type
        ) as Map<String, Any>
        
        val imagesDir = File(tempDir, "cphone/images")
        
        // 处理图片路径
        fun processImagePath(relativePath: String?): String? {
            if (relativePath == null) return null
            if (relativePath.startsWith("http")) return relativePath
            
            val sourceFile = File(tempDir, "cphone/$relativePath")
            if (!sourceFile.exists()) return null
            
            // 复制到应用存储
            val destDir = File(context.filesDir, "images").apply { mkdirs() }
            val destFile = File(destDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            return destFile.absolutePath
        }
        
        // 导入CPhone数据
        val cphoneDataMapJson = gson.toJson(cphoneData["cphoneData"])
        val cphoneDataMap = gson.fromJson(
            cphoneDataMapJson,
            object : TypeToken<Map<String, CPhoneData>>() {}.type
        ) as Map<String, CPhoneData>
        
        // 处理每个联系人的CPhone数据
        cphoneDataMap.forEach { (contactId, data) ->
            // 处理相册照片
            val processedAlbumPhotos = data.albumPhotos.map { photo ->
                photo.copy(imageUrl = processImagePath(photo.imageUrl))
            }
            
            // 处理淘宝商品
            val processedTaobaoData = data.taobaoData?.let { taobaoData ->
                val processedPurchases = taobaoData.purchases.map { purchase ->
                    purchase.copy(imageUrl = processImagePath(purchase.imageUrl))
                }
                taobaoData.copy(purchases = processedPurchases)
            }
            
            // 处理地图足迹
            val processedAmapFootprints = data.amapFootprints.map { footprint ->
                footprint.copy(imageUrl = processImagePath(footprint.imageUrl))
            }
            
            // 处理App使用记录
            val processedAppUsageRecords = data.appUsageRecords.map { record ->
                record.copy(imageUrl = processImagePath(record.imageUrl))
            }
            
            // 处理音乐封面
            val processedMusicTracks = data.musicTracks.map { track ->
                track.coverUrl = processImagePath(track.coverUrl)
                track
            }
            
            val processedData = data.copy(
                albumPhotos = processedAlbumPhotos,
                taobaoData = processedTaobaoData,
                amapFootprints = processedAmapFootprints,
                appUsageRecords = processedAppUsageRecords,
                musicTracks = processedMusicTracks
            )
            
            // 保存到数据库
            cphoneRepository.saveCPhoneData(processedData)
        }
        
        // 导入SharedPreferences
        val settingsFile = File(tempDir, "cphone/settings.json")
        if (settingsFile.exists()) {
            val settings = gson.fromJson(
                settingsFile.readText(),
                object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            ) as Map<String, Map<String, Any>>
            
            if (settings.isNotEmpty()) {
                settingsRepository.importMultipleSharedPreferences(settings)
            }
        }
        
        Log.d(TAG, "CPhone数据导入完成")
    }
    
    /**
     * 导入桌面布局数据
     */
    private suspend fun importDesktopData(tempDir: File) {
        Log.d(TAG, "开始导入桌面布局数据")
        
        val desktopLayoutFile = File(tempDir, "desktop/layout.json")
        if (!desktopLayoutFile.exists()) {
            Log.w(TAG, "桌面布局数据文件不存在，跳过")
            return
        }
        
        if (desktopDataProvider != null) {
            desktopDataProvider.importDesktopLayout(tempDir)
            
            // 导入SharedPreferences
            val settingsFile = File(tempDir, "desktop/settings.json")
            if (settingsFile.exists()) {
                val settings = gson.fromJson(
                    settingsFile.readText(),
                    object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                ) as Map<String, Map<String, Any>>
                
                if (settings.isNotEmpty()) {
                    settingsRepository.importMultipleSharedPreferences(settings)
                }
            }
        } else {
            Log.w(TAG, "DesktopDataProvider未实现，跳过桌面布局导入")
        }
        
        Log.d(TAG, "桌面布局数据导入完成")
    }
    
    /**
     * 导入主题偏好数据和用户自定义主题资源。
     *
     * 主题偏好目前只写入SharedPreferences，不操作数据库表。
     * 导入时会先把theme/resources复制到应用私有目录，再把theme_resource标记恢复为稳定的file URI。
     */
    private fun importThemeData(tempDir: File) {
        Log.d(TAG, "开始导入主题偏好数据")

        val settingsFile = File(tempDir, "theme/settings.json")
        if (!settingsFile.exists()) {
            Log.w(TAG, "主题偏好数据文件不存在，跳过")
            return
        }

        val resourceUriMap: Map<String, String> = importThemeResources(tempDir)
        val settings = gson.fromJson(
            settingsFile.readText(),
            object : TypeToken<Map<String, Map<String, Any>>>() {}.type
        ) as Map<String, Map<String, Any>>
        val processedSettings: Map<String, Map<String, Any?>> = processThemeSettingsForImport(settings, resourceUriMap)

        if (processedSettings.isNotEmpty()) {
            settingsRepository.importMultipleSharedPreferences(processedSettings)
        }

        // 主题偏好直接写入 SharedPreferences，绕过了 app 模块的 ThemeRepository 单例内存缓存。
        // 发送重载广播，通知主题仓库重新读盘刷新内存状态，使导入的主题立即生效，无需重启进程。
        context.sendBroadcast(Intent(ACTION_THEME_RELOAD).setPackage(context.packageName))

        Log.d(TAG, "主题偏好数据导入完成")
    }

    private fun importThemeResources(tempDir: File): Map<String, String> {
        val sourceResourcesDir = File(tempDir, "theme/resources")
        if (!sourceResourcesDir.exists()) return emptyMap()
        val targetResourcesDir = File(context.filesDir, THEME_RESOURCES_DIR).apply { mkdirs() }
        // 为本次导入生成唯一批次前缀，避免导入文件名与本机已有自定义主题资源同名时互相覆盖。
        val importBatchPrefix: String = "import_${System.currentTimeMillis()}_"
        val uriMap = mutableMapOf<String, String>()
        sourceResourcesDir.listFiles()?.forEach { sourceFile ->
            if (!sourceFile.isFile) return@forEach
            val targetFileName: String = "$importBatchPrefix${sourceFile.name}"
            val targetFile = File(targetResourcesDir, targetFileName)
            sourceFile.copyTo(targetFile, overwrite = false)
            // 可迁移标记仍以导出时的原始文件名为 key，value 指向重命名后的本机文件，保证 JSON 中 token 替换正确。
            uriMap["$THEME_RESOURCE_URI_PREFIX${sourceFile.name}"] = android.net.Uri.fromFile(targetFile).toString()
        }
        return uriMap
    }

    private fun processThemeSettingsForImport(
        settings: Map<String, Map<String, Any>>,
        resourceUriMap: Map<String, String>
    ): Map<String, Map<String, Any?>> {
        return settings.mapValues { entry ->
            if (entry.key != THEME_PREFERENCES_NAME) return@mapValues entry.value
            entry.value.mapValues { preferenceEntry ->
                val value: Any? = preferenceEntry.value
                if (value is String && isThemeJsonPreference(preferenceEntry.key)) {
                    rewriteThemeResourceUrisForImport(value, resourceUriMap)
                } else {
                    value
                }
            }
        }
    }

    private fun isThemeJsonPreference(key: String): Boolean {
        return key == KEY_CURRENT_THEME_SNAPSHOT || key == KEY_CUSTOM_THEMES_JSON
    }

    private fun rewriteThemeResourceUrisForImport(themeJson: String, resourceUriMap: Map<String, String>): String {
        var processedJson: String = themeJson
        resourceUriMap.forEach { (portableUri, stableUri) ->
            processedJson = processedJson.replace(portableUri, stableUri)
        }
        return processedJson
    }

    /**
     * 导入酒馆记录（chat_records）数据。
     *
     * 递归复制备份包中的 chat_records 整个目录（含 .jsonl 和 avatars/）到
     * `getExternalFilesDir(null)/chat_records`，覆盖现有文件（同名文件被备份包覆盖）。
     * 正则本体 `regex_global_rules` prefs 已被 `importMultipleSharedPreferences` 恢复。
     */
    private fun importChatRecordsData(tempDir: File) {
        Log.d(TAG, "开始导入酒馆记录数据")

        val sourceChatRecords = File(tempDir, "chat_records")
        if (!sourceChatRecords.exists() || !sourceChatRecords.isDirectory) {
            Log.w(TAG, "备份包中无酒馆记录数据，跳过")
            return
        }

        val targetChatRecords = File(context.getExternalFilesDir(null), "chat_records")
        targetChatRecords.mkdirs()

        // 递归复制整个 chat_records 目录，覆盖现有文件
        sourceChatRecords.copyRecursively(targetChatRecords, overwrite = true)

        val fileCount = targetChatRecords.walkTopDown().count { it.isFile }
        Log.d(TAG, "酒馆记录数据导入完成：$fileCount 个文件")
    }

}