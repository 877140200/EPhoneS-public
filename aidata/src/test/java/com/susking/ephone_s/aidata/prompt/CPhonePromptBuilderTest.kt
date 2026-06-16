package com.susking.ephone_s.aidata.prompt

import com.google.common.truth.Truth.assertThat
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.PromptContext
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.model.memory.MemoryEventStatus
import com.susking.ephone_s.aidata.domain.model.memory.MemoryIndexedObjectType
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallContext
import com.susking.ephone_s.aidata.domain.model.memory.MemoryRecallItem
import org.junit.Test

class CPhonePromptBuilderTest {
    private val builder: CPhonePromptBuilder = CPhonePromptBuilder()

    @Test
    fun buildAlbumPrompt_whenMemoryRecallContextExists_shouldUseStructuredSections(): Unit {
        val inputContext: PromptContext = createPromptContext(
            memoryRecallContext = createMemoryRecallContext(),
            longTermMemories = listOf(createLongTermMemory("legacy-memory", "旧原子事件不应该优先出现"))
        )
        val actualPrompt: String = builder.buildAlbumPrompt(inputContext)
        assertThat(actualPrompt).contains("# 本轮相关结构化事件（关于小北）")
        assertThat(actualPrompt).contains("小北答应周末一起看电影")
        assertThat(actualPrompt).contains("# 当前有效事实")
        assertThat(actualPrompt).contains("小北喜欢甜口咖啡")
        assertThat(actualPrompt).contains("# 待完成承诺与约定")
        assertThat(actualPrompt).contains("角色需要提醒小北带伞")
        assertThat(actualPrompt).contains("# 时间线摘要")
        assertThat(actualPrompt).contains("最近一周两个人关系更亲密")
        assertThat(actualPrompt).doesNotContain("旧原子事件不应该优先出现")
    }

    @Test
    fun buildAlbumPrompt_whenMemoryRecallContextIsEmpty_shouldNotFallbackToLongTermMemories(): Unit {
        val inputContext: PromptContext = createPromptContext(
            memoryRecallContext = MemoryRecallContext(),
            longTermMemories = listOf(createLongTermMemory("legacy-memory", "旧原子事件不应该降级出现"))
        )
        val actualPrompt: String = builder.buildAlbumPrompt(inputContext)
        assertThat(actualPrompt).doesNotContain("# 原子事件")
        assertThat(actualPrompt).doesNotContain("旧原子事件不应该降级出现")
    }

    @Test
    fun buildQQPrompt_whenMemoryRecallContextExists_shouldUseStructuredSections(): Unit {
        val inputContext: PromptContext = createPromptContext(
            memoryRecallContext = createMemoryRecallContext(),
            longTermMemories = listOf(createLongTermMemory("legacy-memory", "QQ旧原子事件不应该优先出现"))
        )
        val actualPrompt: String = builder.buildQQPrompt(inputContext)
        assertThat(actualPrompt).contains("# 本轮相关结构化事件（关于小北）")
        assertThat(actualPrompt).contains("小北答应周末一起看电影")
        assertThat(actualPrompt).doesNotContain("QQ旧原子事件不应该优先出现")
    }

    private fun createPromptContext(
        memoryRecallContext: MemoryRecallContext?,
        longTermMemories: List<LongTermMemory>
    ): PromptContext {
        return PromptContext(
            personProfile = createPersonProfile(),
            userProfile = createUserProfile(),
            chatHistory = listOf(createChatMessage()),
            worldBookPrompts = emptyList(),
            longTermMemories = longTermMemories,
            memoryRecallContext = memoryRecallContext,
            availableStickers = emptyList(),
            recentFeeds = emptyList()
        )
    }

    private fun createMemoryRecallContext(): MemoryRecallContext {
        return MemoryRecallContext(
            relevantEvents = listOf(
                createRecallItem(
                    objectType = MemoryIndexedObjectType.EVENT,
                    objectId = "event-1",
                    text = "小北答应周末一起看电影",
                    status = MemoryEventStatus.ACTIVE
                )
            ),
            activeFacts = listOf(
                createRecallItem(
                    objectType = MemoryIndexedObjectType.FACT,
                    objectId = "fact-1",
                    text = "小北喜欢甜口咖啡",
                    status = MemoryEventStatus.ACTIVE
                )
            ),
            pendingCommitments = listOf(
                createRecallItem(
                    objectType = MemoryIndexedObjectType.COMMITMENT,
                    objectId = "commitment-1",
                    text = "角色需要提醒小北带伞",
                    status = MemoryEventStatus.PENDING
                )
            ),
            timelineSummaries = listOf(
                createRecallItem(
                    objectType = MemoryIndexedObjectType.SUMMARY,
                    objectId = "summary-1",
                    text = "最近一周两个人关系更亲密"
                )
            ),
            estimatedTokenCount = 128
        )
    }

    private fun createRecallItem(
        objectType: MemoryIndexedObjectType,
        objectId: String,
        text: String,
        status: MemoryEventStatus? = null
    ): MemoryRecallItem {
        return MemoryRecallItem(
            objectType = objectType,
            objectId = objectId,
            title = objectId,
            text = text,
            status = status,
            finalScore = 0.9f,
            confidenceScore = 0.8f
        )
    }

    private fun createLongTermMemory(id: String, memoryText: String): LongTermMemory {
        return LongTermMemory(
            id = id,
            contactId = CONTACT_ID,
            memoryText = memoryText,
            timestamp = NOW_TIMESTAMP
        )
    }

    private fun createPersonProfile(): PersonProfile {
        return PersonProfile(
            id = CONTACT_ID,
            remarkName = "小明",
            realName = "小明",
            persona = "温柔可靠的联系人",
            shortTermMemoryLimit = 20
        )
    }

    private fun createUserProfile(): UserProfile {
        return UserProfile(
            id = USER_ID,
            nickname = "小北",
            signature = "保持可爱",
            avatarUri = null,
            backgroundUri = null,
            persona = "正在测试小手机的用户"
        )
    }

    private fun createChatMessage(): ChatMessage {
        return ChatMessage(
            id = "message-1",
            contactId = CONTACT_ID,
            content = "今天想一起出去玩",
            timestamp = NOW_TIMESTAMP,
            role = "user"
        )
    }

    private companion object {
        private const val CONTACT_ID: String = "contact-1"
        private const val USER_ID: String = "user-1"
        private const val NOW_TIMESTAMP: Long = 1_700_000_000_000L
    }
}
