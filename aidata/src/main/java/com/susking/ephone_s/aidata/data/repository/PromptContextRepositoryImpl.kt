package com.susking.ephone_s.aidata.data.repository

import android.util.Log
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PromptContext
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallQuery
import com.susking.ephone_s.aidata.domain.service.MemoryRecallService
import com.susking.ephone_s.aidata.domain.repository.PromptContextRepository
import com.susking.ephone_s.aidata.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * 提示词上下文 Repository 实现
 * 这是 aidata 模块最核心的实现
 *
 * 设计原则：
 * 1. 无状态 - 不缓存任何数据
 * 2. 所有数据来自其他 Repository
 * 3. 并发安全 - 每次调用都重新获取最新数据
 */
class PromptContextRepositoryImpl(
    private val database: AiDataDatabase,
    private val personProfileRepository: PersonProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryRecallService: MemoryRecallService? = null
) : PromptContextRepository {
    
    private val chatDao = database.chatMessageDao()
    private val worldBookDao = database.worldBookDao()
    private val worldBookEntryDao = database.worldBookEntryDao()
    private val longTermMemoryDao = database.longTermMemoryDao()
    private val stickerDao = database.stickerDao()
    private val feedDao = database.feedDao()
    
    override suspend fun buildPromptContext(
        contactId: String,
        userProfile: UserProfile,
        isPropel: Boolean,
        lastCallFailureReason: String?,
        enableNovelAi: Boolean
    ): PromptContext {
        // 1. 从 PersonProfileRepository 获取角色设定
        val personProfile = personProfileRepository.getPersonProfileById(contactId)
            ?: throw IllegalArgumentException("Contact not found: $contactId")
        
        // 2. 获取聊天记录（限制数量避免过长）
        val chatHistory = chatDao.getMessagesForContactNonFlow(contactId)
            .map { it.chatMessage.toDomainModel() }
            .takeLast(personProfile.shortTermMemoryLimit)
        
        // 3. 获取世界观提示词（根据绑定配置和模式过滤）
        val worldBookPrompts = getFilteredWorldBookPrompts(personProfile)
        
        // 4. 获取长期记忆
        val memoryQuery = chatHistory.takeLast(RECENT_MESSAGE_LIMIT)
            .joinToString(separator = "\n") { message -> message.content.orEmpty() }
        val memoryRecallContext = getMemoryRecallContext(contactId, memoryQuery.ifBlank { personProfile.realName })
        logMemoryRecallContext(contactId, memoryRecallContext)
        val longTermMemories = memoryRecallContext?.toCompatibleMemories()?.takeIf { memories: List<LongTermMemory> -> memories.isNotEmpty() }
            ?: getPromptMemories(contactId, memoryQuery.ifBlank { personProfile.realName })
        
        // 5. 获取可用表情包
        val availableStickers = stickerDao.getAllStickers().first()
        
        // 6. 获取最近动态
        val recentFeeds = feedDao.getAllFeeds().first()
        
        return PromptContext(
            personProfile = personProfile,
            userProfile = userProfile,
            chatHistory = chatHistory,
            worldBookPrompts = worldBookPrompts,
            longTermMemories = longTermMemories,
            memoryRecallContext = memoryRecallContext,
            availableStickers = availableStickers,
            recentFeeds = recentFeeds,
            isPropel = isPropel,
            lastCallFailureReason = lastCallFailureReason,
            enableNovelAi = enableNovelAi
        )
    }
    
    private suspend fun getMemoryRecallContext(contactId: String, query: String): MemoryRecallContext? {
        val recallService: MemoryRecallService = memoryRecallService ?: return null
        return runCatching {
            recallService.recallMemoryContext(MemoryRecallQuery(contactId = contactId, currentMessage = query, topK = PROMPT_MEMORY_LIMIT))
        }.getOrNull()
    }

    private fun logMemoryRecallContext(contactId: String, context: MemoryRecallContext?): Unit {
        if (context == null) {
            Log.d(TAG, "查手机提示词结构化记忆召回为空 contactId=$contactId")
            return
        }
        Log.d(
            TAG,
            "查手机提示词结构化记忆召回 contactId=$contactId, relatedEvents=${context.relevantEvents.size}, activeFacts=${context.activeFacts.size}, pendingCommitments=${context.pendingCommitments.size}, relationshipTimelines=${context.relationshipTimelines.size}, timelineSummaries=${context.timelineSummaries.size}, estimatedTokens=${context.estimatedTokenCount}"
        )
    }

    private suspend fun getPromptMemories(contactId: String, query: String): List<LongTermMemory> {
        val recallService: MemoryRecallService = memoryRecallService ?: return emptyList()
        return runCatching {
            recallService.recallMemories(query, contactId, PROMPT_MEMORY_LIMIT)
                .map { result: MemoryRecallService.RecallResult -> result.memory }
        }.getOrDefault(emptyList())
    }

    /**
     * 根据PersonProfile的绑定配置获取过滤后的世界书提示词
     * 规则：
     * 1. 根据offlineModeEnabled判断使用线上还是线下模式的配置
     * 2. 全局世界书优先于局部世界书
     * 3. 当世界书同时在全局和局部时，只使用全局的，过滤掉局部的
     * 4. 只有绑定了的世界书才会被启用
     */
    private suspend fun getFilteredWorldBookPrompts(personProfile: com.susking.ephone_s.aidata.domain.model.PersonProfile): List<String> {
        // 根据模式选择对应的绑定列表
        val globalWorldBookIds = if (personProfile.offlineModeEnabled) {
            personProfile.offlineGlobalWorldBooks
        } else {
            personProfile.onlineGlobalWorldBooks
        }
        
        val localWorldBookIds = if (personProfile.offlineModeEnabled) {
            personProfile.offlineLocalWorldBooks
        } else {
            personProfile.onlineLocalWorldBooks
        }
        
        // 合并ID列表，全局优先（去重，全局的会覆盖局部的）
        val enabledWorldBookIds = (globalWorldBookIds + localWorldBookIds).distinct()
        
        // 如果没有绑定任何世界书，返回空列表
        if (enabledWorldBookIds.isEmpty()) {
            return emptyList()
        }
        
        // 获取所有绑定的世界书的所有启用条目
        val allPrompts = mutableListOf<String>()
        for (worldBookId in enabledWorldBookIds) {
            val entries = worldBookEntryDao.getEntriesForWorldBook(worldBookId).first()
            // 只获取启用的条目
            val enabledEntries = entries.filter { it.isEnabled }
            allPrompts.addAll(enabledEntries.map { it.content })
        }
        
        return allPrompts
    }

    private companion object {
        private const val TAG: String = "PromptContextRepositoryImpl"
        private const val PROMPT_MEMORY_LIMIT: Int = 50
        private const val RECENT_MESSAGE_LIMIT: Int = 8
    }
}