package com.susking.ephone_s.qq.domain.use_case.ai

import android.content.Context
import com.google.gson.Gson
import com.susking.ephone_s.aidata.domain.model.AiActionParser
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.repository.ChatRepository
import com.susking.ephone_s.aidata.domain.repository.HeartbeatRepository
import com.susking.ephone_s.aidata.domain.repository.JottingRepository
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.brain.service.ActionExecutor
import com.susking.ephone_s.qq.domain.model.OfflineVersionData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 切换AI响应版本的UseCase
 * 从版本历史中选择一个版本并重新渲染消息
 */
class SwitchResponseVersionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val heartbeatRepository: HeartbeatRepository,
    private val jottingRepository: JottingRepository,
    private val actionExecutor: ActionExecutor,
    private val gson: Gson
) {
    suspend operator fun invoke(
        messageId: String,
        contactId: String,
        newIndex: Int
    ): Result<Unit> {
        return try {
            val chatHistory = chatRepository.getMessagesForContact(contactId).first()

            // 找到持有版本历史的消息
            val originalMessage = chatHistory.find { it.id == messageId }
                ?: return Result.failure(Exception("找不到原始消息"))

            val turnId = originalMessage.aiTurnId
                ?: return Result.failure(Exception("消息不属于AI轮次"))

            val versions = originalMessage.aiResponseVersions
            if (newIndex < 0 || newIndex >= versions.size) {
                return Result.failure(Exception("无效的版本索引"))
            }

            // 清除旧版本的附属数据
            heartbeatRepository.deleteHeartbeatsByAiTurnId(turnId)
            jottingRepository.deleteJottingsByAiTurnId(turnId)

            // 获取目标版本JSON
            val targetJson = versions[newIndex]
            val originalTimestamp = chatHistory.find { it.aiTurnId == turnId }?.timestamp

            // 根据JSON格式判断版本类型并重新生成消息
            val newMessages = parseVersionJson(targetJson, contactId, turnId, originalTimestamp)

            if (newMessages.isEmpty()) {
                return Result.failure(Exception("解析版本失败"))
            }

            // 将版本历史附加到最后一条消息，并保持同一轮多气泡的时间戳递增。
            val orderedMessages: List<ChatMessage> = assignSequentialTimestamps(newMessages, originalTimestamp)
            val lastMessage = orderedMessages.last()
            val otherMessages = orderedMessages.dropLast(1)
            val lastWithHistory = lastMessage.copy(
                aiResponseVersions = versions,
                displayedResponseIndex = newIndex
            )

            val finalMessages = otherMessages.map { message: ChatMessage ->
                message.copy(aiTurnId = turnId)
            } + lastWithHistory.copy(aiTurnId = turnId)

            // 替换数据库中的消息
            val messagesToDelete = chatHistory.filter { it.aiTurnId == turnId }
            chatRepository.deleteMessages(messagesToDelete)
            finalMessages.forEach { chatRepository.insertMessage(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseVersionJson(
        json: String,
        contactId: String,
        turnId: String,
        timestamp: Long?
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val ts = timestamp ?: System.currentTimeMillis()

        when {
            json.trim().startsWith("{") -> {
                // 离线模式JSON对象
                try {
                    val versionData = gson.fromJson(json, OfflineVersionData::class.java)
                    if (!versionData.textContent.isNullOrBlank()) {
                        messages.add(ChatMessage(
                            contactId = contactId,
                            type = "offline_text",
                            role = "assistant",
                            content = versionData.textContent,
                            timestamp = ts
                        ))
                    }
                    if (!versionData.imageUrl.isNullOrBlank()) {
                        messages.add(ChatMessage(
                            contactId = contactId,
                            type = "naiimag",
                            role = "assistant",
                            imageUrl = versionData.imageUrl,
                            timestamp = ts
                        ))
                    }
                } catch (e: Exception) {
                    messages.add(ChatMessage(
                        contactId = contactId,
                        type = "error",
                        role = "assistant",
                        content = "[版本数据损坏]",
                        timestamp = ts
                    ))
                }
            }
            json.trim().startsWith("[") -> {
                // 在线模式JSON数组
                val actions = AiActionParser.parseAiActions(json)
                val executionResult = actionExecutor.executeActions(
                    context, actions, contactId, turnId, timestamp
                )
                messages.addAll(executionResult.messages)
            }
            else -> {
                // 格式未知,直接显示原文
                messages.add(ChatMessage(
                    contactId = contactId,
                    type = "text",
                    role = "assistant",
                    content = json,
                    timestamp = ts
                ))
            }
        }

        return messages
    }

    private fun assignSequentialTimestamps(
        messages: List<ChatMessage>,
        originalTimestamp: Long?
    ): List<ChatMessage> {
        if (messages.isEmpty()) {
            return emptyList()
        }
        val baseTimestamp: Long = originalTimestamp ?: messages.minOf { it.timestamp }
        return messages.mapIndexed { index: Int, message: ChatMessage ->
            message.copy(timestamp = baseTimestamp + index.toLong())
        }
    }
}
