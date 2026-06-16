package com.susking.ephone_s.clouddreams.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.susking.ephone_s.core.util.ChatRecordMetadataKeys
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonFileHelper {

    /**
     * 一条 jsonl 首行的元数据解析结果。
     *
     * @property userName 用户名（ST user_name）
     * @property characterName 角色名（ST character_name）
     * @property userAvatar 用户头像相对路径（avatars/x.png），可空
     * @property characterAvatar 角色头像相对路径，可空
     * @property appliedRegexIds 应用的正则 id 列表；null 表示该文件无 metadata 行（外部旧文件）
     */
    data class ChatMetadata(
        val userName: String?,
        val characterName: String?,
        val userAvatar: String?,
        val characterAvatar: String?,
        val appliedRegexIds: List<String>?
    )

    /**
     * 判断一行 JSON 是否为元数据行：同时含 user_name 与 character_name 即视为 metadata。
     * （ST 原生 metadata 行与我方保存格式均满足；普通消息行有 mes 字段、无这两个键。）
     */
    private fun isMetadataLine(obj: JSONObject): Boolean {
        return obj.has(ChatRecordMetadataKeys.KEY_USER_NAME) &&
            obj.has(ChatRecordMetadataKeys.KEY_CHARACTER_NAME)
    }

    /** 把元数据 JSON 行解析为 [ChatMetadata]。 */
    private fun parseMetadata(obj: JSONObject): ChatMetadata {
        val ids: List<String>? = obj.optJSONArray(ChatRecordMetadataKeys.KEY_APPLIED_REGEX_IDS)?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
        }
        return ChatMetadata(
            userName = obj.optString(ChatRecordMetadataKeys.KEY_USER_NAME).ifBlank { null },
            characterName = obj.optString(ChatRecordMetadataKeys.KEY_CHARACTER_NAME).ifBlank { null },
            userAvatar = obj.optString(ChatRecordMetadataKeys.KEY_USER_AVATAR).ifBlank { null },
            characterAvatar = obj.optString(ChatRecordMetadataKeys.KEY_CHARACTER_AVATAR).ifBlank { null },
            appliedRegexIds = ids
        )
    }

    /** 仅读取文件首行的元数据；无 metadata 行返回 null。 */
    fun readMetadata(filePath: String): ChatMetadata? {
        if (filePath.isBlank()) return null
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isEmpty()) continue
                    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
                    val obj = JSONObject(trimmed)
                    return if (isMetadataLine(obj)) parseMetadata(obj) else null
                }
            }
            null
        } catch (e: Exception) {
            Log.w("JSON_META", "读取元数据失败", e)
            null
        }
    }

    // 验证 JSON 文件内容
    fun validateJsonContent(content: String): Boolean {
        val lines = content.split("\n")
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                try {
                    JSONObject(trimmedLine)
                } catch (e: Exception) {
                    Log.w("JSON_VALIDATE", "无效的JSON行 ${index + 1}: ${line.take(50)}...", e)
                    return false
                }
            }
        }
        return true
    }

    // 流式解析 JSON 内容（用于大文件）
    fun parseJsonContentStreaming(filePath: String, regexRuleManager: RegexRuleManager): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        if (filePath.isBlank()) {
            return messages
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                return messages
            }

            // 先读元数据：拿到正则 id 列表与双方头像路径
            var metadata: ChatMetadata? = null

            // 使用缓冲读取器逐行读取
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                var lineNumber = 0

                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    if (line.isNullOrBlank()) continue

                    try {
                        // 更严格的 JSON 检测
                        val trimmedLine = line!!.trim()
                        if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                            val jsonObject = JSONObject(trimmedLine)

                            // 元数据行：解析后跳过（不作为消息）
                            if (isMetadataLine(jsonObject)) {
                                metadata = parseMetadata(jsonObject)
                                continue
                            }

                            // 确保必要字段存在
                            if (jsonObject.has("mes")) {
                                val name = jsonObject.optString("name", "未知")
                                val isUser = jsonObject.optBoolean("is_user", false)
                                val message = jsonObject.optString("mes", "")
                                val sendDate = jsonObject.optString("send_date", "未知时间")

                                val model = if (jsonObject.has("extra")) {
                                    val extra = jsonObject.optJSONObject("extra")
                                    extra?.optString("model", "") ?: jsonObject.optString("model", "")
                                } else {
                                    jsonObject.optString("model", "") // 尝试直接从根获取
                                }

                                // 按消息归属绑定头像路径
                                val avatarPath: String? = if (isUser) {
                                    metadata?.userAvatar
                                } else {
                                    metadata?.characterAvatar
                                }

                                messages.add(ChatMessage(
                                    name = name,
                                    isUser = isUser,
                                    message = message,
                                    processedMessage = regexRuleManager.processMessage(
                                        message, isUser, metadata?.appliedRegexIds
                                    ),
                                    sendDate = sendDate,
                                    model = model,
                                    rawJson = trimmedLine,
                                    avatarPath = avatarPath
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("JSON_PARSE", "跳过无效JSON行 $lineNumber: ${line?.take(50)}...", e)
                        // 继续处理其他行而不是中断
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "解析JSON内容失败", e)
        }

        return messages
    }

    // 从 URI 读取内容
    fun readContentFromUri(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                val buffer = CharArray(8192) // 8KB缓冲区
                var charsRead: Int

                // 逐块读取文件内容
                while (reader.read(buffer).also { charsRead = it } != -1) {
                    stringBuilder.appendRange(buffer, 0, charsRead)
                }

                stringBuilder.toString()
            } ?: ""
        } catch (e: Exception) {
            Log.e("URI_READ", "从URI读取内容失败", e)
            ""
        }
    }
    // 保存聊天文件到应用目录
    fun saveChatFile(context: Context, originalUri: Uri, content: String): File {
        // 创建聊天记录目录
        val externalDir = context.getExternalFilesDir(null)
        val chatDir = File(externalDir, "chat_records")

        if (!chatDir.exists()) {
            val created = chatDir.mkdirs()
            Log.d("SaveFile", "创建目录: $created, 路径: ${chatDir.absolutePath}")
        }

        // 生成文件名
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        var fileName = "chat_$timestamp.json"

        // 尝试使用原始文件名
        try {
            context.contentResolver.query(originalUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        val originalName = cursor.getString(displayNameIndex)
                        if (originalName != null && originalName.isNotEmpty()) {
                            fileName = originalName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SaveFile", "获取原始文件名失败", e)
        }

        // 确保文件名唯一
        var counter = 1
        var baseName = fileName
        if (baseName.contains(".")) {
            val dotIndex = baseName.lastIndexOf(".")
            baseName = baseName.substring(0, dotIndex)
            val extension = fileName.substring(dotIndex)

            while (File(chatDir, fileName).exists()) {
                fileName = "${baseName}_$counter$extension"
                counter++
            }
        } else {
            while (File(chatDir, fileName).exists()) {
                fileName = "${baseName}_$counter"
                counter++
            }
        }

        // 保存文件 - 使用流式写入
        val file = File(chatDir, fileName)
        try {
            FileOutputStream(file).use { fos ->
                // 使用缓冲写入提高效率
                val writer = BufferedWriter(OutputStreamWriter(fos))
                writer.write(content)
                writer.close() // 确保关闭写入器
            }
            Log.d("SaveFile", "文件保存成功: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("SaveFile", "文件保存失败", e)
            throw e
        }

        return file
    }

    /**
     * 将消息列表保存到文件（防瘦身）。
     *
     * 保真策略：
     * - 保留原文件首行的元数据行（messages 不含它，故从原文件读回）。
     * - 每条消息基于其 [ChatMessage.rawJson] 整行回写，只更新 `mes` 字段，
     *   其余原生字段（swipes/extra/force_avatar 等）原样保留，杜绝「编辑即瘦身」。
     * - rawJson 为空（外部导入的旧文件无原始行）时，回退写最小字段集。
     */
    fun saveMessagesToFile(filePath: String, messages: List<ChatMessage>) {
        try {
            val file = File(filePath)

            val builder = StringBuilder()

            // 1) 保留原文件的元数据行（若有）
            val metadataLine: String? = readRawMetadataLine(file)
            if (metadataLine != null) {
                builder.append(metadataLine)
            }

            // 2) 逐条回写消息：基于 rawJson 只改 mes，无 rawJson 则回退最小字段集
            for (message in messages) {
                if (builder.isNotEmpty()) builder.append('\n')
                val line: String = if (message.rawJson.isNotBlank()) {
                    try {
                        JSONObject(message.rawJson).apply {
                            put("mes", message.message)
                        }.toString()
                    } catch (e: Exception) {
                        buildMinimalLine(message)
                    }
                } else {
                    buildMinimalLine(message)
                }
                builder.append(line)
            }

            file.writeText(builder.toString())
            Log.d("SAVE_FILE", "消息已保存到文件: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("SAVE_FILE", "保存文件失败", e)
            throw e
        }
    }

    /** 读取原文件首行（若为元数据行则原样返回该行字符串），否则返回 null。 */
    private fun readRawMetadataLine(file: File): String? {
        if (!file.exists()) return null
        return try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isEmpty()) continue
                    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
                    val obj = JSONObject(trimmed)
                    return if (isMetadataLine(obj)) trimmed else null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 无原始行时的最小字段集（兼容外部导入旧文件）。 */
    private fun buildMinimalLine(message: ChatMessage): String {
        return JSONObject().apply {
            put("model", message.model)
            put("name", message.name)
            put("is_user", message.isUser)
            put("mes", message.message)
            put("send_date", message.sendDate)
        }.toString()
    }

}