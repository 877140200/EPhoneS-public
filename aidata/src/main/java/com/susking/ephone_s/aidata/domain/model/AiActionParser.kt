package com.susking.ephone_s.aidata.domain.model

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * AiAction 解析器
 * 负责解析 AI 返回的 JSON 响应并转换为 AiAction 列表
 */
object AiActionParser {
    
    private val gson = Gson()
    private val actionGson = GsonBuilder()
        .registerTypeAdapter(AiAction::class.java, AiActionDeserializer())
        .create()
    
    /**
     * 将 AI 返回的 JSON 字符串解析为 AiAction 列表
     * @param jsonResponse AI 返回的原始 JSON 字符串
     * @return 解析后的 AiAction 列表
     */
    fun parseAiActions(jsonResponse: String?): List<AiAction> {
        if (jsonResponse.isNullOrBlank()) return emptyList()

        Log.d("AiActionParser", "parseAiActions收到: ${jsonResponse.take(200)}")

        // 将响应转换为 JSON 数组字符串
        val jsonArrayString = parseToJsonArray(jsonResponse)

        Log.d("AiActionParser", "parseToJsonArray返回: ${jsonArrayString.take(200)}")

        return try {
            val actionListType = object : TypeToken<List<AiAction>>() {}.type
            val parsedElement = JsonParser.parseString(jsonArrayString)
            
            if (parsedElement.isJsonArray) {
                actionGson.fromJson(parsedElement, actionListType) ?: emptyList()
            } else {
                Log.w("AiActionParser", "解析结果不是JSON数组: $jsonArrayString")
                listOf(AiAction.Text(content = jsonResponse))
            }
        } catch (e: Exception) {
            Log.w("AiActionParser", "解析AiAction失败: ${e.message}")
            listOf(AiAction.Text(content = jsonResponse))
        }
    }
    
    /**
     * 解析混合内容（文本 + JSON 指令）
     * @param context Android Context
     * @param rawContent AI 返回的原始 content 字符串
     * @return 解析后的 ParsedAiResponse 对象
     */
    fun parseMixedContent(context: Context, rawContent: String?): ParsedAiResponse {
        if (rawContent.isNullOrBlank()) {
            return ParsedAiResponse("", null)
        }
        
        val parsed = parseMixedContentInternal(rawContent)
        
        val action = if (parsed.actionJson != null) {
            try {
                actionGson.fromJson(parsed.actionJson, AiAction::class.java)?.takeIf {
                    it !is AiAction.Unknown && it.type.isNotBlank()
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        
        return ParsedAiResponse(parsed.text, action)
    }
    
    /**
     * 将AI返回的JSON字符串解析为行动列表（JSON数组）
     * @param jsonResponse AI返回的原始JSON字符串
     * @return 解析后的JSON数组字符串，如果解析失败则返回包含原始文本的JSON数组
     */
    private fun parseToJsonArray(jsonResponse: String?): String {
        if (jsonResponse.isNullOrBlank()) return "[]"
        
        // 【诊断日志】记录原始响应
        Log.d("AiActionParser", "【诊断】原始响应前200字符: ${jsonResponse.take(200)}")
        
        var contentString = jsonResponse.trim()
        
        // 【新增】处理嵌套的完整API响应格式
        // 检查是否是包含choices结构的完整响应
        try {
            val parsedResponse = JsonParser.parseString(contentString)
            if (parsedResponse.isJsonObject) {
                val responseObject = parsedResponse.asJsonObject
                // 检查是否有choices数组
                if (responseObject.has("choices") && responseObject.get("choices").isJsonArray) {
                    val choicesArray = responseObject.getAsJsonArray("choices")
                    if (choicesArray.size() > 0) {
                        val firstChoice = choicesArray.get(0).asJsonObject
                        // 提取 choices[0].message.content
                        if (firstChoice.has("message")) {
                            val message = firstChoice.getAsJsonObject("message")
                            if (message.has("content")) {
                                val extractedContent = message.get("content").asString
                                Log.d("AiActionParser", "【诊断】从choices结构中提取到content: ${extractedContent.take(100)}")
                                contentString = extractedContent.trim()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 如果不是这种格式，继续使用原始内容
            Log.d("AiActionParser", "【诊断】不是嵌套的API响应格式，继续处理")
        }
        
        // 【新增】处理<think>标签：移除思考过程，只保留JSON数组
        if (contentString.contains("<think>") && contentString.contains("</think>")) {
            Log.d("AiActionParser", "【诊断】检测到<think>标签，将其移除")
            // 移除从<think>到</think>的所有内容
            contentString = contentString.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
            Log.d("AiActionParser", "【诊断】移除<think>后的内容前100字符: ${contentString.take(100)}")
        }
        
        // 智能处理Markdown代码块
        if (contentString.startsWith("```")) {
            contentString = contentString.substringAfter("```").substringBeforeLast("```").trim()
            // 移除可能的语言标识符（如 "json"）
            if (contentString.startsWith("json")) {
                contentString = contentString.substringAfter("json").trim()
            }
        }
        
        // 【新增】处理数组前面有额外文本的情况（如 "thought_chain\n[...]"）
        // 查找第一个 '[' 的位置
        val arrayStartIndex = contentString.indexOf('[')
        if (arrayStartIndex > 0) {
            // 如果 '[' 不在开头，检查前面是否只有非JSON字符
            val beforeArray = contentString.substring(0, arrayStartIndex).trim()
            // 如果前面的内容不是JSON对象（不以 '{' 开头），则从 '[' 开始截取
            if (beforeArray.isNotEmpty() && !beforeArray.startsWith('{')) {
                Log.d("AiActionParser", "【诊断】检测到数组前有额外文本: '$beforeArray'，将其移除")
                contentString = contentString.substring(arrayStartIndex)
            }
        }
        
        if (contentString.isBlank()) {
            Log.e("AiActionParser", "从AI响应中提取的JSON内容为空。原始内容: $jsonResponse")
            return """[{"type": "text", "content": ""}]"""
        }
        
        return try {
            // 【诊断日志】尝试解析JSON
            Log.d("AiActionParser", "【诊断】尝试解析的contentString前200字符: ${contentString.take(200)}")
            
            val parsedElement = JsonParser.parseString(contentString)
            
            when {
                // 情况1: 内容是JSON数组
                parsedElement.isJsonArray -> {
                    Log.d("AiActionParser", "【诊断】成功识别为JSON数组，包含${parsedElement.asJsonArray.size()}个元素")
                    contentString
                }
                
                // 情况2: 内容是JSON对象
                parsedElement.isJsonObject -> {
                    val jsonObject = parsedElement.asJsonObject
                    // 2a: 检查是否为包含 "actions" 的包装对象
                    if (jsonObject.has("actions") && jsonObject.get("actions").isJsonArray) {
                        Log.d("AiActionParser", "【诊断】识别为包含actions的包装对象")
                        jsonObject.get("actions").toString()
                    } else if (jsonObject.has("type")) {
                        // 2b: 单个action对象，包装成数组
                        Log.d("AiActionParser", "【诊断】识别为单个action对象，包装成数组")
                        "[$contentString]"
                    } else {
                        Log.w("AiActionParser", "无法识别的JSON对象格式: $contentString")
                        """[{"type": "text", "content": ""}]"""
                    }
                }
                else -> {
                    Log.w("AiActionParser", "响应不是JSON数组或对象: $contentString")
                    """[{"type": "text", "content": ""}]"""
                }
            }
        } catch (e: Exception) {
            Log.w("AiActionParser", "【诊断】响应不是有效的JSON，异常: ${e.message}")
            Log.w("AiActionParser", "【诊断】失败的contentString前500字符: ${contentString.take(500)}")
            """[{"type": "text", "content": ""}]"""
        }
    }
    
    /**
     * 从AI返回的、可能混合了文本和JSON指令的字符串中，分离出用户可见的文本和可执行的JSON
     * @param rawContent AI返回的原始content字符串
     * @return 返回一个ParsedMixedContent对象，包含处理后的文本和可能存在的JSON字符串
     */
    private fun parseMixedContentInternal(rawContent: String?): ParsedMixedContent {
        if (rawContent.isNullOrBlank()) {
            return ParsedMixedContent("", null)
        }
        
        // 过滤掉 timestamp，格式如：(Timestamp: 1765956761033)
        var cleanedContent = rawContent.replace(Regex("\\(Timestamp:\\s*\\d+\\)\\s*"), "").trim()
        
        // 增强的正则表达式，用于从字符串末尾提取可选的JSON指令（可能被Markdown代码块包裹）
        val pattern = "([\\s\\S]*?)(?:`?\\s*(\\{\\s*\"type\"[\\s\\S]*\\})\\s*`?)$".toRegex()
        val matchResult = pattern.find(cleanedContent.trim())
        
        if (matchResult != null) {
            val textPart = matchResult.groupValues[1].trim()
            val jsonPart = matchResult.groupValues[2].trim()
            
            if (jsonPart.isNotBlank()) {
                return try {
                    // 验证是否为有效 JSON
                    val parsedElement = JsonParser.parseString(jsonPart)
                    if (parsedElement.isJsonObject) {
                        val jsonObject = parsedElement.asJsonObject
                        if (jsonObject.has("type") && jsonObject.get("type").asString.isNotBlank()) {
                            ParsedMixedContent(textPart, jsonPart)
                        } else {
                            ParsedMixedContent(rawContent, null)
                        }
                    } else {
                        ParsedMixedContent(rawContent, null)
                    }
                } catch (e: Exception) {
                    ParsedMixedContent(rawContent, null)
                }
            }
            return ParsedMixedContent(textPart, null)
        }
        
        return ParsedMixedContent(cleanedContent, null)
    }
    
    /**
     * 用于封装从混合内容中解析出的文本和JSON
     */
    private data class ParsedMixedContent(
        val text: String,
        val actionJson: String?
    )
    
    /**
     * 用于封装从混合内容中解析出的文本和动作
     */
    data class ParsedAiResponse(
        val text: String,
        val action: AiAction?
    )
}