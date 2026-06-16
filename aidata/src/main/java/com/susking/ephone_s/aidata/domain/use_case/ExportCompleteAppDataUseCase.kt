
package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.susking.ephone_s.aidata.domain.provider.AlbumDataProvider
import com.susking.ephone_s.aidata.domain.provider.DesktopDataProvider
import com.susking.ephone_s.aidata.data.local.ShoppingDatabase
import com.susking.ephone_s.aidata.domain.repository.CPhoneRepository
import com.susking.ephone_s.aidata.domain.repository.DataImportExportRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookEntryRepository
import com.susking.ephone_s.aidata.domain.repository.WorldBookRepository
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import com.susking.ephone_s.aidata.util.ImageFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * 完整应用数据导出UseCase
 * 
 * 导出所有应用数据，包括：
 * 1. QQ数据（复用ExportDataUseCase）
 * 2. 世界书数据
 * 3. 相册数据
 * 4. CPhone数据
 * 5. 桌面布局数据
 * 6. 主题偏好数据
 *
 * 导出结构：
 * - qq/data.json + images/ + favorites/ + settings.json
 * - worldbook/data.json + settings.json
 * - album/data.json + photos/ + settings.json
 * - cphone/data.json + images/ + settings.json
 * - desktop/layout.json + settings.json
 * - theme/settings.json
 * - manifest.json
 */
class ExportCompleteAppDataUseCase @Inject constructor(
    private val context: Context,
    private val exportDataUseCase: ExportDataUseCase,
    private val personProfileRepository: PersonProfileRepository,
    private val dataImportExportRepository: DataImportExportRepository,
    private val worldBookRepository: WorldBookRepository,
    private val worldBookEntryRepository: WorldBookEntryRepository,
    private val shoppingDatabase: ShoppingDatabase,
    private val cphoneRepository: CPhoneRepository,
    private val settingsRepository: SettingsRepository,
    private val albumDataProvider: AlbumDataProvider?,
    private val desktopDataProvider: DesktopDataProvider?
) {
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    companion object {
        private const val TAG = "ExportCompleteAppData"
        private const val THEME_PREFERENCES_NAME = "theme_prefs"
        private const val KEY_CURRENT_THEME_SNAPSHOT = "current_theme_snapshot"
        private const val KEY_CUSTOM_THEMES_JSON = "custom_themes_json"
        private const val KEY_PINNED_THEME_IDS_JSON = "pinned_theme_ids_json"
        private const val THEME_RESOURCES_DIR = "themes/resources"
        private const val THEME_RESOURCE_URI_PREFIX = "theme_resource://"
    }
    
    /**
     * 执行完整应用数据导出
     */
    suspend operator fun invoke(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始导出完整应用数据")
            
            // 创建临时目录
            val tempDir = File(context.cacheDir, "complete_export_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // 创建模块目录
                val qqDir = File(tempDir, "qq").apply { mkdirs() }
                val worldbookDir = File(tempDir, "worldbook").apply { mkdirs() }
                val shoppingDir = File(tempDir, "shopping").apply { mkdirs() }
                val albumDir = File(tempDir, "album").apply { mkdirs() }
                val cphoneDir = File(tempDir, "cphone").apply { mkdirs() }
                val desktopDir = File(tempDir, "desktop").apply { mkdirs() }
                val themeDir = File(tempDir, "theme").apply { mkdirs() }
                val chatRecordsDir = File(tempDir, "chat_records").apply { mkdirs() }

                // 并发导出各模块数据
                coroutineScope {
                    val jobs = listOf(
                        async { exportQQData(qqDir) },
                        async { exportWorldBookData(worldbookDir) },
                        async { exportShoppingData(shoppingDir) },
                        async { exportAlbumData(albumDir) },
                        async { exportCPhoneData(cphoneDir) },
                        async { exportDesktopData(desktopDir) },
                        async { exportThemeData(themeDir) },
                        async { exportChatRecordsData(chatRecordsDir) }
                    )

                    jobs.awaitAll()
                }
                
                // 生成manifest.json
                generateManifest(tempDir)
                
                // 打包成ZIP文件
                val zipFile = createZipFile(tempDir)
                
                Log.d(TAG, "导出完成: ${zipFile.absolutePath}")
                Result.success("完整应用数据已成功导出到 '下载' 文件夹下的 ${zipFile.name} 文件中。")
                
            } finally {
                // 清理临时目录
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "导出失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出QQ数据 - 直接使用旧的ExportDataUseCase
     */
    private suspend fun exportQQData(qqDir: File) {
        Log.d(TAG, "开始导出QQ数据 - 使用ExportDataUseCase")
        
        // 获取所有联系人和好友分组
        val contacts = personProfileRepository.getPersonProfiles()
        val friendGroups = dataImportExportRepository.getFriendGroups()
        
        // 创建临时目录用于旧UseCase导出
        val tempQQDir = File(context.cacheDir, "qq_export_temp_${System.currentTimeMillis()}").apply { mkdirs() }
        
        try {
            // 使用旧的ExportDataUseCase导出到ZIP
            val result = exportDataUseCase.invoke("zip", contacts, friendGroups)
            
            if (result.isSuccess) {
                // 找到导出的ZIP文件
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val zipFiles = downloadsDir.listFiles { file ->
                    file.name.startsWith("EPhoneS_All_Export_") && file.name.endsWith(".zip")
                }?.sortedByDescending { it.lastModified() }
                
                val latestZip = zipFiles?.firstOrNull()
                if (latestZip != null) {
                    // 解压ZIP到qqDir
                    java.util.zip.ZipInputStream(latestZip.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val file = File(qqDir, entry.name)
                            if (!file.canonicalPath.startsWith(qqDir.canonicalPath)) {
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
                    
                    // 删除临时ZIP文件
                    latestZip.delete()
                }
            }
            
            // 将语音消息的本地录音复制到完整备份包中，并把导出数据里的绝对路径改成相对路径。
            processQQVoiceAudioFiles(qqDir)
            
            // 导出QQ相关的SharedPreferences
            exportQQSettings(qqDir)
            
            Log.d(TAG, "QQ数据导出完成")
        } finally {
            tempQQDir.deleteRecursively()
        }
    }
    
    private fun processQQVoiceAudioFiles(qqDir: File) {
        val dataFile: File = File(qqDir, "data.json")
        if (!dataFile.exists()) {
            return
        }
        val voiceMessagesDir: File = File(qqDir, "voice_messages").apply { mkdirs() }
        val exportData: ExportData = gson.fromJson(dataFile.readText(), ExportData::class.java)
        val processedMessages = exportData.chatMessages?.map { message ->
            val voiceAudioPath: String = message.voiceAudioPath ?: return@map message
            if (voiceAudioPath.startsWith("voice_messages/")) {
                return@map message
            }
            val sourceFile: File = File(voiceAudioPath)
            if (!sourceFile.exists() || !sourceFile.isFile) {
                return@map message
            }
            val targetFile: File = createUniqueBackupFile(voiceMessagesDir, sourceFile.name)
            sourceFile.copyTo(targetFile, overwrite = true)
            message.copy(voiceAudioPath = "voice_messages/${targetFile.name}")
        } ?: emptyList()
        dataFile.writeText(gson.toJson(exportData.copy(chatMessages = processedMessages)))
    }
    
    private fun createUniqueBackupFile(directory: File, originalFileName: String): File {
        val safeFileName: String = originalFileName.ifBlank { "voice_${System.currentTimeMillis()}.wav" }
        val baseName: String = safeFileName.substringBeforeLast('.', safeFileName)
        val extension: String = safeFileName.substringAfterLast('.', missingDelimiterValue = "")
        var candidateFile = File(directory, safeFileName)
        var index = 1
        while (candidateFile.exists()) {
            val indexedFileName: String = if (extension.isBlank()) {
                "${baseName}_$index"
            } else {
                "${baseName}_$index.$extension"
            }
            candidateFile = File(directory, indexedFileName)
            index++
        }
        return candidateFile
    }
    
    /**
     * 导出世界书数据
     */
    private suspend fun exportWorldBookData(worldbookDir: File) {
        Log.d(TAG, "开始导出世界书数据")
        
        val worldBooks = worldBookRepository.getAllWorldBooks().first()
        
        // 获取所有世界书的条目
        val allEntries = mutableListOf<com.susking.ephone_s.aidata.data.local.entity.WorldBookEntryEntity>()
        for (worldBook in worldBooks) {
            val entries = worldBookEntryRepository.getEntriesForWorldBook(worldBook.worldBookId).first()
            allEntries.addAll(entries)
        }
        
        val data = mapOf(
            "worldBooks" to worldBooks,
            "worldBookEntries" to allEntries
        )
        
        File(worldbookDir, "data.json").writeText(gson.toJson(data))
        
        // 导出世界书相关的SharedPreferences（如果有）
        exportWorldBookSettings(worldbookDir)
        
        Log.d(TAG, "世界书数据导出完成")
    }

    /**
     * 导出购物应用数据
     */
    private suspend fun exportShoppingData(shoppingDir: File) {
        Log.d(TAG, "开始导出购物数据")

        val categories = shoppingDatabase.shoppingCategoryDao().getAllCategoriesList()
        val products = shoppingDatabase.shoppingProductDao().getAllProductsList()
        val cartItems = shoppingDatabase.shoppingCartDao().getAllCartItemsList()
        val orders = shoppingDatabase.shoppingOrderDao().getAllOrdersList()
        val authorizedAccounts = shoppingDatabase.shoppingAuthorizedAccountDao().getAllAuthorizedAccountsList()

        val data = mapOf(
            "shoppingCategories" to categories,
            "shoppingProducts" to products,
            "shoppingCartItems" to cartItems,
            "shoppingOrders" to orders,
            "shoppingAuthorizedAccounts" to authorizedAccounts
        )

        File(shoppingDir, "data.json").writeText(gson.toJson(data))

        // 导出购物相关的SharedPreferences（如果有）
        exportShoppingSettings(shoppingDir)

        Log.d(TAG, "购物数据导出完成: ${categories.size}分类, ${products.size}商品, ${cartItems.size}购物车, ${orders.size}订单, ${authorizedAccounts.size}授权账号")
    }

    /**
     * 导出相册数据
     * 注意：由于album模块在aidata模块中无法直接访问，
     * 这里需要通过接口或者在app模块中实现
     */
    private suspend fun exportAlbumData(albumDir: File) {
        Log.d(TAG, "开始导出相册数据")
        
        val photosDir = File(albumDir, "photos").apply { mkdirs() }
        
        if (albumDataProvider != null) {
            // 通过Provider获取相册数据
            val dataJson = albumDataProvider.exportAlbumData(albumDir, photosDir)
            File(albumDir, "data.json").writeText(dataJson)
            
            // 获取相册相关的SharedPreferences
            val settings = albumDataProvider.getAlbumSettings()
            if (settings.isNotEmpty()) {
                File(albumDir, "settings.json").writeText(gson.toJson(settings))
            } else {
                File(albumDir, "settings.json").writeText(gson.toJson(mapOf<String, Any?>()))
            }
        } else {
            // Provider未实现，创建空数据
            val data = mapOf(
                "albums" to emptyList<Any>(),
                "photos" to emptyList<Any>(),
                "note" to "相册数据Provider未实现"
            )
            File(albumDir, "data.json").writeText(gson.toJson(data))
            File(albumDir, "settings.json").writeText(gson.toJson(mapOf<String, Any?>()))
        }
        
        Log.d(TAG, "相册数据导出完成")
    }
    
    /**
     * 导出CPhone数据
     */
    private suspend fun exportCPhoneData(cphoneDir: File) {
        Log.d(TAG, "开始导出CPhone数据")
        
        val imagesDir = File(cphoneDir, "images").apply { mkdirs() }
        
        // 获取所有联系人的CPhone数据
        val contacts = personProfileRepository.getPersonProfiles()
        val allCPhoneData = contacts.mapNotNull { contact ->
            cphoneRepository.getCPhoneDataSuspend(contact.id)?.let { cphoneData ->
                // 处理CPhone数据中的图片
                val processedData = processCPhoneImages(cphoneData, imagesDir)
                contact.id to processedData
            }
        }.toMap()
        
        val data = mapOf(
            "cphoneData" to allCPhoneData
        )
        
        File(cphoneDir, "data.json").writeText(gson.toJson(data))
        
        // 导出CPhone相关的SharedPreferences（如果有）
        exportCPhoneSettings(cphoneDir)
        
        Log.d(TAG, "CPhone数据导出完成")
    }
    
    /**
     * 处理CPhone数据中的图片
     */
    private fun processCPhoneImages(
        cphoneData: com.susking.ephone_s.aidata.domain.model.CPhoneData,
        imagesDir: File
    ): com.susking.ephone_s.aidata.domain.model.CPhoneData {
        // 处理相册照片
        val processedAlbumPhotos = cphoneData.albumPhotos.map { photo ->
            photo.imageUrl?.let { imageUrl ->
                if (!imageUrl.startsWith("http")) {
                    val imageFile = File(imageUrl)
                    if (imageFile.exists()) {
                        ImageFileHelper.copyImageToDirectory(context, android.net.Uri.fromFile(imageFile), imagesDir)?.let { file ->
                            return@map photo.copy(imageUrl = "images/${file.name}")
                        }
                    }
                }
            }
            photo
        }
        
        // 处理淘宝商品图片
        val processedTaobaoData = cphoneData.taobaoData?.let { taobaoData ->
            val processedPurchases = taobaoData.purchases.map { purchase ->
                purchase.imageUrl?.let { imageUrl ->
                    if (!imageUrl.startsWith("http")) {
                        val imageFile = File(imageUrl)
                        if (imageFile.exists()) {
                            ImageFileHelper.copyImageToDirectory(context, android.net.Uri.fromFile(imageFile), imagesDir)?.let { file ->
                                return@map purchase.copy(imageUrl = "images/${file.name}")
                            }
                        }
                    }
                }
                purchase
            }
            taobaoData.copy(purchases = processedPurchases)
        }
        
        // 处理高德地图足迹图片
        val processedAmapFootprints = cphoneData.amapFootprints.map { footprint ->
            footprint.imageUrl?.let { imageUrl ->
                if (!imageUrl.startsWith("http")) {
                    val imageFile = File(imageUrl)
                    if (imageFile.exists()) {
                        ImageFileHelper.copyImageToDirectory(context, android.net.Uri.fromFile(imageFile), imagesDir)?.let { file ->
                            return@map footprint.copy(imageUrl = "images/${file.name}")
                        }
                    }
                }
            }
            footprint
        }
        
        // 处理App图标
        val processedAppUsageRecords = cphoneData.appUsageRecords.map { record ->
            record.imageUrl?.let { imageUrl ->
                if (!imageUrl.startsWith("http")) {
                    val imageFile = File(imageUrl)
                    if (imageFile.exists()) {
                        ImageFileHelper.copyImageToDirectory(context, android.net.Uri.fromFile(imageFile), imagesDir)?.let { file ->
                            return@map record.copy(imageUrl = "images/${file.name}")
                        }
                    }
                }
            }
            record
        }
        
        // 处理音乐封面
        val processedMusicTracks = cphoneData.musicTracks.map { track ->
            track.coverUrl?.let { coverUrl ->
                if (!coverUrl.startsWith("http")) {
                    val imageFile = File(coverUrl)
                    if (imageFile.exists()) {
                        ImageFileHelper.copyImageToDirectory(context, android.net.Uri.fromFile(imageFile), imagesDir)?.let { file ->
                            track.coverUrl = "images/${file.name}"
                        }
                    }
                }
            }
            track
        }
        
        return cphoneData.copy(
            albumPhotos = processedAlbumPhotos,
            taobaoData = processedTaobaoData,
            amapFootprints = processedAmapFootprints,
            appUsageRecords = processedAppUsageRecords,
            musicTracks = processedMusicTracks
        )
    }
    
    /**
     * 导出桌面布局数据
     * 注意：由于desktop模块在aidata模块中无法直接访问，
     * 这里需要通过接口或者在app模块中实现
     */
    private suspend fun exportDesktopData(desktopDir: File) {
        Log.d(TAG, "开始导出桌面布局数据")
        
        if (desktopDataProvider != null) {
            // 通过Provider获取桌面布局数据
            val layoutJson = desktopDataProvider.exportDesktopLayout(desktopDir)
            File(desktopDir, "layout.json").writeText(layoutJson)
            
            // 获取桌面相关的SharedPreferences
            val settings = desktopDataProvider.getDesktopSettings()
            if (settings.isNotEmpty()) {
                File(desktopDir, "settings.json").writeText(gson.toJson(settings))
            } else {
                File(desktopDir, "settings.json").writeText(gson.toJson(mapOf<String, Any?>()))
            }
        } else {
            // Provider未实现，创建空数据
            val data = mapOf(
                "pages" to emptyList<Any>(),
                "dock" to emptyList<Any>(),
                "note" to "桌面布局数据Provider未实现"
            )
            File(desktopDir, "layout.json").writeText(gson.toJson(data))
            File(desktopDir, "settings.json").writeText(gson.toJson(mapOf<String, Any?>()))
        }
        
        Log.d(TAG, "桌面布局数据导出完成")
    }
    
    /**
     * 导出主题相关的SharedPreferences和用户自定义主题资源。
     *
     * 当前完整主题系统将所选主题ID、主题快照、自定义主题JSON、置顶顺序和主题数据结构版本保存到theme_prefs。
     * 自定义壁纸、应用图标和Brain图标会被复制到theme/resources目录，JSON中的私有文件URI会被重写为可迁移的theme_resource标记。
     */
    private fun exportThemeData(themeDir: File) {
        Log.d(TAG, "开始导出主题偏好数据")

        val resourcesDir = File(themeDir, "resources").apply { mkdirs() }
        val themePrefsNames = listOf(
            THEME_PREFERENCES_NAME
        )
        val settings = settingsRepository.getMultipleSharedPreferences(themePrefsNames)
        val processedSettings = processThemeSettingsForExport(settings, resourcesDir)
        File(themeDir, "settings.json").writeText(gson.toJson(processedSettings))

        val customThemesJson: String? = processedSettings[THEME_PREFERENCES_NAME]?.get(KEY_CUSTOM_THEMES_JSON) as? String
        if (!customThemesJson.isNullOrBlank()) {
            File(themeDir, "custom_themes.json").writeText(customThemesJson)
        }

        Log.d(TAG, "主题偏好数据导出完成")
    }

    private fun processThemeSettingsForExport(
        settings: Map<String, Map<String, Any?>>,
        resourcesDir: File
    ): Map<String, Map<String, Any?>> {
        return settings.mapValues { entry ->
            if (entry.key != THEME_PREFERENCES_NAME) return@mapValues entry.value
            entry.value.mapValues { preferenceEntry ->
                val value: Any? = preferenceEntry.value
                if (value is String && isThemeJsonPreference(preferenceEntry.key)) {
                    rewriteThemeResourceUrisForExport(value, resourcesDir)
                } else {
                    value
                }
            }
        }
    }

    private fun isThemeJsonPreference(key: String): Boolean {
        return key == KEY_CURRENT_THEME_SNAPSHOT || key == KEY_CUSTOM_THEMES_JSON
    }

    @Suppress("unused")
    private fun isThemeOrderPreference(key: String): Boolean {
        return key == KEY_PINNED_THEME_IDS_JSON
    }

    private fun rewriteThemeResourceUrisForExport(themeJson: String, resourcesDir: File): String {
        val appThemeResourcesDir = File(context.filesDir, THEME_RESOURCES_DIR)
        if (!appThemeResourcesDir.exists()) return themeJson
        var processedJson: String = themeJson
        appThemeResourcesDir.listFiles()?.forEach { resourceFile ->
            if (!resourceFile.isFile) return@forEach
            val targetFile = File(resourcesDir, resourceFile.name)
            resourceFile.copyTo(targetFile, overwrite = true)
            val originalUri: String = android.net.Uri.fromFile(resourceFile).toString()
            val portableUri: String = "$THEME_RESOURCE_URI_PREFIX${resourceFile.name}"
            processedJson = processedJson.replace(originalUri, portableUri)
        }
        return processedJson
    }
    
    /**
     * 导出QQ相关的SharedPreferences
     */
    private fun exportQQSettings(qqDir: File) {
        val qqPrefsNames = listOf(
            "ephone_api_settings",
            "novelai_generation_settings",
            "person_profile_prefs",
            "qq_groups",
            "wallet_prefs",
            "image_generation_manager"
        )
        
        val settings = settingsRepository.getMultipleSharedPreferences(qqPrefsNames)
        File(qqDir, "settings.json").writeText(gson.toJson(settings))
    }
    
    /**
     * 导出世界书相关的SharedPreferences
     */
    private fun exportWorldBookSettings(worldbookDir: File) {
        // 世界书目前可能没有独立的SharedPreferences
        val settings = mapOf<String, Any?>()
        File(worldbookDir, "settings.json").writeText(gson.toJson(settings))
    }
    
    /**
     * 导出CPhone相关的SharedPreferences
     */
    private fun exportCPhoneSettings(cphoneDir: File) {
        // 导出CPhone联系人级自动日记设置，确保完整备份能恢复每个联系人的开关与生成窗口记录。
        val cphonePrefsNames = listOf(
            "cphone_auto_diary_schedule"
        )
        val settings = settingsRepository.getMultipleSharedPreferences(cphonePrefsNames)
        File(cphoneDir, "settings.json").writeText(gson.toJson(settings))
    }

    /**
     * 导出购物应用相关的SharedPreferences
     */
    private fun exportShoppingSettings(shoppingDir: File) {
        // 导出购物相关的SharedPreferences（当前购物模块可能没有单独的prefs，预留接口）
        val shoppingPrefsNames = listOf<String>()  // 空列表,购物数据全在数据库
        if (shoppingPrefsNames.isNotEmpty()) {
            val settings = settingsRepository.getMultipleSharedPreferences(shoppingPrefsNames)
            File(shoppingDir, "settings.json").writeText(gson.toJson(settings))
        }
    }

    /**
     * 导出酒馆记录（chat_records）数据。
     *
     * clouddreams 的聊天记录文件（.jsonl）与头像图片（avatars/）都在外部文件目录
     * `getExternalFilesDir(null)/chat_records`，递归复制整个目录到备份包。
     * 正则本体存于 `regex_global_rules` prefs，已被 `getAllSharedPreferences` 自动覆盖。
     */
    private fun exportChatRecordsData(chatRecordsDir: File) {
        Log.d(TAG, "开始导出酒馆记录数据")

        val sourceChatRecords = File(context.getExternalFilesDir(null), "chat_records")
        if (!sourceChatRecords.exists() || !sourceChatRecords.isDirectory) {
            // chat_records 不存在：写空 settings，标记无数据
            File(chatRecordsDir, "settings.json").writeText(gson.toJson(mapOf("note" to "无聊天记录数据")))
            Log.d(TAG, "酒馆记录目录不存在，跳过")
            return
        }

        // 递归复制整个 chat_records 目录（含 .jsonl 和 avatars/ 子目录）
        sourceChatRecords.copyRecursively(chatRecordsDir, overwrite = true)

        // 导出 clouddreams 相关的 SharedPreferences（regex_global_rules 已被 getAllSharedPreferences 自动覆盖，此处标记即可）
        val settings = mapOf("note" to "正则全局库 regex_global_rules 已随 getAllSharedPreferences 导出")
        File(chatRecordsDir, "settings.json").writeText(gson.toJson(settings))

        val fileCount = chatRecordsDir.walkTopDown().count { it.isFile }
        Log.d(TAG, "酒馆记录数据导出完成：$fileCount 个文件")
    }

    /**
     * 生成manifest.json
     */
    private fun generateManifest(tempDir: File) {
        val manifest = mapOf(
            "exportVersion" to "2.0.0",
            "exportTimestamp" to System.currentTimeMillis(),
            "exportType" to "COMPLETE_APP_DATA",
            "appVersion" to "1.0.0",
            "modules" to listOf(
                mapOf("name" to "qq", "dataFile" to "qq/data.json"),
                mapOf("name" to "worldbook", "dataFile" to "worldbook/data.json"),
                mapOf("name" to "album", "dataFile" to "album/data.json"),
                mapOf("name" to "cphone", "dataFile" to "cphone/data.json"),
                mapOf("name" to "desktop", "dataFile" to "desktop/layout.json"),
                mapOf("name" to "theme", "dataFile" to "theme/settings.json", "resourcesDir" to "theme/resources"),
                mapOf("name" to "chat_records", "dataDir" to "chat_records", "note" to "酒馆记录：.jsonl 文件与 avatars 头像目录")
            )
        )

        File(tempDir, "manifest.json").writeText(gson.toJson(manifest))
    }
    
    /**
     * 创建ZIP文件
     */
    private fun createZipFile(tempDir: File): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFileName = "EPhoneS_Complete_Export_$timestamp.zip"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val zipFile = File(downloadsDir, zipFileName)
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            tempDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryPath = file.relativeTo(tempDir).path
                    val zipEntry = ZipEntry(entryPath)
                    zos.putNextEntry(zipEntry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        
        return zipFile
    }
    
}