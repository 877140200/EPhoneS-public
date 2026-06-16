package com.susking.ephone_s.aidata.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 一个用于处理图片文件存储的帮助类。
 * 主要负责将 Base64 编码的图片数据保存到应用的内部存储中，并管理这些文件。
 */
object ImageFileHelper {

    private const val IMAGE_SUBDIR = "chat_images"

    /**
     * 将 Base64 编码的字符串解码并保存为图片文件。
     *
     * @param context 上下文，用于访问应用的文件目录。
     * @param base64String 要解码和保存的 Base64 字符串。
     * @return 如果保存成功，则返回新创建文件的绝对路径；如果失败，则返回 null。
     */
    fun saveImageFromBase64(context: Context, base64String: String): String? {
        // 检查字符串是否是有效的 Base64 格式
        if (!base64String.startsWith("data:image")) {
            // 如果不是我们期望的格式，可能它本身就是一个路径或者无效数据，直接返回
            return if (File(base64String).exists()) base64String else null
        }

        return try {
            // 从 Base64 字符串中分离出编码类型和数据本身
            val parts = base64String.split(",")
            if (parts.size != 2) return null

            val imageBytes = Base64.decode(parts[1], Base64.DEFAULT)
            
            // 在单元测试环境中，Base64.decode可能返回null
            if (imageBytes == null || imageBytes.isEmpty()) {
                return null
            }

            // 获取用于存放图片的目录
            val imageDir = File(context.filesDir, IMAGE_SUBDIR)
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            // 创建一个唯一的文件名
            val imageFile = File(imageDir, "${UUID.randomUUID()}.jpg")

            // 将解码后的字节写入文件
            FileOutputStream(imageFile).use { outputStream ->
                outputStream.write(imageBytes)
            }

            // 返回新文件的绝对路径
            imageFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: IllegalArgumentException) {
            // Base64 解码失败
            e.printStackTrace()
            null
        } catch (e: NullPointerException) {
            // 处理Base64.decode返回null的情况（主要在单元测试环境中）
            e.printStackTrace()
            null
        }
    }

    /**
     * 从给定的URI复制图片文件到指定目录。
     *
     * @param context 上下文，用于访问ContentResolver。
     * @param sourceUri 要复制的图片文件的URI。
     * @param destinationDir 目标目录。
     * @return 如果复制成功，则返回新的File对象；如果失败，则返回null。
     */
    fun copyImageToDirectory(context: Context, sourceUri: Uri, destinationDir: File): File? {
        return try {
            // 确保目标目录存在
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            // 从URI获取输入流
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null

            // 创建一个唯一的文件名，保留原始扩展名（如果可能）
            val fileExtension = context.contentResolver.getType(sourceUri)?.substringAfterLast('/') ?: "jpg"
            val destinationFile = File(destinationDir, "${UUID.randomUUID()}.$fileExtension")

            // 复制文件
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.use {
                    it.copyTo(outputStream)
                }
            }
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从给定的URI复制图片文件到指定目录,使用自定义文件名。
     *
     * @param context 上下文,用于访问ContentResolver。
     * @param sourceUri 要复制的图片文件的URI。
     * @param destinationDir 目标目录。
     * @param customFileName 自定义文件名(不含扩展名)。
     * @return 如果复制成功,则返回新的File对象;如果失败,则返回null。
     */
    fun copyImageWithCustomName(context: Context, sourceUri: Uri, destinationDir: File, customFileName: String): File? {
        return try {
            // 确保目标目录存在
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            // 从URI获取输入流
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null

            // 获取文件扩展名
            val fileExtension = context.contentResolver.getType(sourceUri)?.substringAfterLast('/') ?: "jpg"
            val destinationFile = File(destinationDir, "$customFileName.$fileExtension")

            // 复制文件
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.use {
                    it.copyTo(outputStream)
                }
            }
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 复制文件并返回相对于目标目录的路径。
     * @param context 上下文
     * @param uriString 源文件的URI字符串
     * @param destinationDir 目标目录
     * @return 相对路径字符串，如 "images/filename.jpg"，如果失败则返回null
     */
    fun copyUriToRelativePath(context: Context, uriString: String?, destinationDir: File): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = parseUriSafely(uriString)
            val copiedFile = copyImageToDirectory(context, uri, destinationDir)
            copiedFile?.relativeTo(destinationDir.parentFile!!)?.path
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 复制文件并返回相对于目标目录的路径,使用自定义文件名。
     * @param context 上下文
     * @param uriString 源文件的URI字符串
     * @param destinationDir 目标目录
     * @param customFileName 自定义文件名(不含扩展名)
     * @return 相对路径字符串，如 "images/filename.jpg"，如果失败则返回null
     */
    fun copyUriToRelativePathWithCustomName(context: Context, uriString: String?, destinationDir: File, customFileName: String): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = parseUriSafely(uriString)
            val copiedFile = copyImageWithCustomName(context, uri, destinationDir, customFileName)
            copiedFile?.relativeTo(destinationDir.parentFile!!)?.path
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 安全地解析URI字符串,能够正确处理文件路径和URI格式。
     * - 如果是绝对路径,使用Uri.fromFile()
     * - 如果是URI格式(content://或file://),使用Uri.parse()
     * @param uriString 要解析的URI字符串
     * @return 解析后的Uri对象
     */
    private fun parseUriSafely(uriString: String): Uri {
        return when {
            // 如果是绝对路径
            uriString.startsWith("/") -> Uri.fromFile(File(uriString))
            // 如果是content://或file://等URI格式
            uriString.contains("://") -> Uri.parse(uriString)
            // 其他情况,尝试作为文件路径处理
            else -> {
                val file = File(uriString)
                if (file.exists()) Uri.fromFile(file) else Uri.parse(uriString)
            }
        }
    }

    /**
     * 根据提供的文件路径删除文件。
     *
     * @param filePath 要删除的文件的路径。
     * @return 如果文件被成功删除，则返回 true；否则返回 false。
     */
    fun deleteImage(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }
}