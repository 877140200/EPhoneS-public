package com.susking.ephone_s.aidata.util

import android.content.Context
import android.util.Log
import com.susking.ephone_s.aidata.domain.model.import_export.EPhoneSChat
import com.susking.ephone_s.aidata.domain.model.import_export.ExportData
import java.io.File

/**
 * 导入路径处理器
 * 
 * 负责处理导入数据中的图片路径，将ZIP中的图片复制到应用存储
 */
class ImportPathProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImportPathProcessor"
    }
    
    /**
     * 替换字符串末尾的指定后缀(忽略大小写)
     */
    private fun String.replaceLast(oldSuffix: String, newSuffix: String, ignoreCase: Boolean = false): String {
        val lastIndex = this.lastIndexOf(oldSuffix, ignoreCase = ignoreCase)
        return if (lastIndex != -1 && lastIndex == this.length - oldSuffix.length) {
            this.substring(0, lastIndex) + newSuffix
        } else {
            this
        }
    }
    
    /**
     * 处理全量数据中的所有图片路径
     * @param rawExportData 原始导出数据
     * @param tempDir 临时解压目录
     * @return 处理后的导出数据，图片路径已更新为应用存储路径
     */
    fun processAllDataPaths(rawExportData: ExportData, tempDir: File): ExportData {
        Log.d(TAG, "processAllDataPaths: 开始处理路径,tempDir=${tempDir.absolutePath}")
        Log.d(TAG, "processAllDataPaths: tempDir文件列表=${tempDir.listFiles()?.joinToString { it.name }}")
        
        // 列出images文件夹中的所有文件
        val imagesDir = File(tempDir, "images")
        if (imagesDir.exists() && imagesDir.isDirectory) {
            val imageFiles = imagesDir.listFiles()
            Log.d(TAG, "processAllDataPaths: images目录中的文件数量=${imageFiles?.size ?: 0}")
            imageFiles?.take(5)?.forEach {
                Log.d(TAG, "processAllDataPaths: images文件=${it.name}")
            }
        } else {
            Log.w(TAG, "processAllDataPaths: images目录不存在或不是目录")
        }
        
        fun processVoiceAudioPath(pathString: String?): String? {
            if (pathString.isNullOrBlank()) {
                return null
            }
            val fileName: String = if (pathString.startsWith("/")) {
                File(pathString).name
            } else {
                File(pathString).name
            }
            val sourceFile: File = File(tempDir, "voice_messages/$fileName")
            if (!sourceFile.exists() || !sourceFile.isFile) {
                return pathString.takeIf { File(it).exists() }
            }
            return copyFileToAppStorage(sourceFile, "voice_messages")
        }

        fun processUri(pathString: String?): String? {
            if (pathString.isNullOrBlank()) {
                Log.d(TAG, "processUri: 路径为空")
                return null
            }
            Log.d(TAG, "processUri: 处理路径=$pathString")
            
            // 如果是绝对路径,提取文件名并在ZIP中查找
            val fileName = if (pathString.startsWith("/")) {
                File(pathString).name
            } else {
                pathString
            }
            
            // 尝试在images目录中查找,同时尝试.jpg和.jpeg扩展名
            var sourceFile = File(tempDir, "images/$fileName")
            Log.d(TAG, "processUri: 查找文件=${sourceFile.absolutePath}, exists=${sourceFile.exists()}")
            
            if (!sourceFile.exists()) {
                // 尝试替换扩展名 .jpeg <-> .jpg
                val alternateFileName = when {
                    fileName.endsWith(".jpeg", ignoreCase = true) -> fileName.replaceLast(".jpeg", ".jpg", ignoreCase = true)
                    fileName.endsWith(".jpg", ignoreCase = true) -> fileName.replaceLast(".jpg", ".jpeg", ignoreCase = true)
                    else -> null
                }
                
                if (alternateFileName != null) {
                    val alternateFile = File(tempDir, "images/$alternateFileName")
                    Log.d(TAG, "processUri: 尝试替代扩展名=${alternateFile.absolutePath}, exists=${alternateFile.exists()}")
                    if (alternateFile.exists()) {
                        sourceFile = alternateFile
                    } else {
                        // 列出images目录中包含相同UUID的所有文件
                        val baseFileName = fileName.substringBeforeLast(".")
                        val matchingFiles = imagesDir.listFiles()?.filter { it.name.startsWith(baseFileName) }
                        Log.d(TAG, "processUri: 查找UUID=$baseFileName 的匹配文件: ${matchingFiles?.joinToString { it.name } ?: "无"}")
                    }
                }
            }
            
            if (!sourceFile.exists()) {
                // 如果images目录没有,尝试直接使用原路径
                val fallbackFile = File(tempDir, fileName)
                Log.d(TAG, "processUri: 尝试备用路径=${fallbackFile.absolutePath}, exists=${fallbackFile.exists()}")
                if (fallbackFile.exists()) {
                    val result = copyFileToAppStorage(fallbackFile, "images")
                    Log.d(TAG, "processUri: 复制结果=$result")
                    return result
                }
            }
            
            val result = copyFileToAppStorage(sourceFile, "images")
            Log.d(TAG, "processUri: 复制结果=$result")
            return result
        }

        return rawExportData.copy(
            contacts = rawExportData.contacts?.map { contact ->
                // 为旧数据补充默认的 timeSensitivityConfig
                val contactWithDefaults = if (contact.timeSensitivityConfig == null) {
                    contact.copy(
                        timeSensitivityConfig = com.susking.ephone_s.aidata.domain.model.TimeSensitivityConfig()
                    )
                } else {
                    contact
                }
                
                contactWithDefaults.copy(
                    avatarUri = processUri(contactWithDefaults.avatarUri),
                    backgroundUri = processUri(contactWithDefaults.backgroundUri),
                    chatBackgroundUri = processUri(contactWithDefaults.chatBackgroundUri),
                    selectedPhotos = contactWithDefaults.selectedPhotos?.mapNotNull { url -> processUri(url) } ?: emptyList()
                )
            } ?: emptyList(),
            chatMessages = rawExportData.chatMessages?.map { message ->
                message.copy(
                    imageUrl = processUri(message.imageUrl),
                    voiceAudioPath = processVoiceAudioPath(message.voiceAudioPath)
                )
            } ?: emptyList(),
            feeds = rawExportData.feeds?.map { feed -> feed.copy(imageUrls = feed.imageUrls?.mapNotNull { url -> processUri(url) } ?: emptyList()) } ?: emptyList(),
            userProfile = rawExportData.userProfile?.let { profile ->
                profile.copy(
                    avatarUri = processUri(profile.avatarUri),
                    backgroundUri = processUri(profile.backgroundUri),
                    feedsHeaderBackgroundUri = processUri(profile.feedsHeaderBackgroundUri)
                )
            },
            favoriteMessages = rawExportData.favoriteMessages?.map { msg ->
                msg.copy(
                    senderAvatar = processUri(msg.senderAvatar),
                    imageUrl = if (msg.imageUrl?.startsWith("favorites/") == true) {
                        copyFileToAppStorage(File(tempDir, msg.imageUrl), "favorites")
                    } else {
                        processUri(msg.imageUrl)
                    }
                )
            } ?: emptyList()
        )
    }
    
    /**
     * 处理单个聊天记录中的图片路径
     * @param rawEphoneSChat 原始聊天记录
     * @param tempDir 临时解压目录
     * @return 处理后的聊天记录，图片路径已更新为应用存储路径
     */
    fun processSingleChatPaths(rawEphoneSChat: EPhoneSChat, tempDir: File): EPhoneSChat {
        // 防御性检查: 确保 chatData 不为 null
        val chatData = rawEphoneSChat.chatData
            ?: throw IllegalArgumentException("chatData 字段为空,可能是数据格式不正确或JSON解析失败")
        
        val settings = chatData.settings
            ?: throw IllegalArgumentException("settings 字段为空,可能是数据格式不正确")
        
        fun processVoiceAudioPath(pathString: String?): String? {
            if (pathString.isNullOrBlank()) {
                return null
            }
            val fileName: String = File(pathString).name
            val sourceFile: File = File(tempDir, "voice_messages/$fileName")
            if (!sourceFile.exists() || !sourceFile.isFile) {
                return pathString.takeIf { File(it).exists() }
            }
            return copyFileToAppStorage(sourceFile, "voice_messages")
        }

        fun processUri(pathString: String?): String? {
            if (pathString.isNullOrBlank()) return null
            
            // 如果是绝对路径,提取文件名
            val fileName = if (pathString.startsWith("/")) {
                File(pathString).name
            } else {
                pathString
            }
            
            // 尝试在images目录中查找,同时尝试.jpg和.jpeg扩展名
            var sourceFile = File(tempDir, "images/$fileName")
            if (!sourceFile.exists()) {
                // 尝试替换扩展名 .jpeg <-> .jpg
                val alternateFileName = when {
                    fileName.endsWith(".jpeg", ignoreCase = true) -> fileName.replaceLast(".jpeg", ".jpg", ignoreCase = true)
                    fileName.endsWith(".jpg", ignoreCase = true) -> fileName.replaceLast(".jpg", ".jpeg", ignoreCase = true)
                    else -> null
                }
                
                if (alternateFileName != null) {
                    val alternateFile = File(tempDir, "images/$alternateFileName")
                    if (alternateFile.exists()) {
                        sourceFile = alternateFile
                    }
                }
            }
            
            if (!sourceFile.exists()) {
                // 如果images目录没有,尝试直接使用原路径
                val fallbackFile = File(tempDir, fileName)
                if (fallbackFile.exists()) {
                    return copyFileToAppStorage(fallbackFile, "images")
                }
            }
            
            return copyFileToAppStorage(sourceFile, "images")
        }
        
        val processedSettings = settings.copy(
            aiAvatar = processUri(settings.aiAvatar) ?: "",
            background = processUri(settings.background),
            chatBackground = processUri(settings.chatBackground),
            selectedPhotos = settings.selectedPhotos?.mapNotNull { url -> processUri(url) }
        )
        val processedHistory = chatData.history.map { msg ->
            msg.copy(
                imageUrl = processUri(msg.imageUrl),
                voiceAudioPath = processVoiceAudioPath(msg.voiceAudioPath)
            )
        }
        val processedChatData = chatData.copy(
            settings = processedSettings,
            history = processedHistory
        )
        return rawEphoneSChat.copy(chatData = processedChatData)
    }
    
    /**
     * 将文件复制到应用存储目录
     * @param sourceFile 源文件
     * @param subDir 子目录名称（如"images"、"favorites"）
     * @return 目标文件的绝对路径，失败返回null
     */
    private fun copyFileToAppStorage(sourceFile: File, subDir: String): String? {
        Log.d(TAG, "copyFileToAppStorage: 源文件=${sourceFile.absolutePath}, 目标子目录=$subDir")
        if (!sourceFile.exists()) {
            Log.w(TAG, "copyFileToAppStorage: 源文件不存在")
            return null
        }
        val destDir = File(context.filesDir, subDir).apply { mkdirs() }
        val destFile = File(destDir, sourceFile.name)
        Log.d(TAG, "copyFileToAppStorage: 目标文件=${destFile.absolutePath}")
        try {
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "copyFileToAppStorage: 复制成功")
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copyFileToAppStorage: 复制失败", e)
            return null
        }
    }
}