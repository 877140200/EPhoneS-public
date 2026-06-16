package com.susking.ephone_s.aidata.domain.use_case

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.susking.ephone_s.aidata.domain.service.MemoryFactGraphExtractionService
import com.susking.ephone_s.aidata.prompt.AiPromptService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SummarizeCallTranscriptUseCase @Inject constructor(
    private val aiPromptService: AiPromptService,
    private val aiRequestService: com.susking.ephone_s.aidata.api.AiRequestService,
    @ApplicationContext private val context: Context,
    private val memoryFactGraphExtractionService: MemoryFactGraphExtractionService
) {
    private val gson = Gson()

    suspend operator fun invoke(
        contactId: String,
        transcript: String,
        lastMessageTimestamp: Long,
        hangupTimestamp: Long,
        isUserHangup: Boolean,
        videoCallId: Long? = null  // 新增：视频通话记录ID
    ) {
        try {
            // 1. 使用 AiPromptService 构建请求
            val promptRequest = aiPromptService.buildCallSummaryPrompt(
                contactId = contactId,
                transcript = transcript,
                lastMessageTimestamp = lastMessageTimestamp,
                hangupTimestamp = hangupTimestamp,
                isUserHangup = isUserHangup
            )

            // 2. 调用AI服务获取视频通话结构化抽取结果
            val extractionJson: String? = aiRequestService.getChatCompletion(context, promptRequest)

            // 3. 校验必须存在视频通话总结性结构化事件后，再保存结构化事件、节点和关系。
            if (extractionJson != null && hasRequiredVideoCallSummaryEvent(extractionJson)) {
                val result = memoryFactGraphExtractionService.saveVideoCallFactGraphResponse(
                    contactId = contactId,
                    videoCallId = videoCallId,
                    transcript = transcript,
                    response = extractionJson
                )
                result.onSuccess { extractionResult: MemoryFactGraphExtractionService.ExtractionResult ->
                    Log.i(TAG, "视频通话结构化记忆保存成功: events=${extractionResult.eventCount}, nodes=${extractionResult.nodeCount}, relations=${extractionResult.relationCount}")
                }.onFailure { exception: Throwable ->
                    Log.e(TAG, "视频通话结构化记忆保存失败", exception)
                }
            } else {
                Log.e(TAG, "视频通话结构化抽取结果为空，或缺少必需的视频通话总结性结构化事件，已跳过写入")
            }
        } catch (e: Exception) {
            Log.e(TAG, "抽取视频通话结构化记忆时发生错误", e)
        }
    }

    private fun hasRequiredVideoCallSummaryEvent(json: String): Boolean {
        return runCatching {
            val root: JsonObject = gson.fromJson(json, JsonObject::class.java)
            val events = root.getAsJsonArray("events") ?: return@runCatching false
            if (events.size() <= 0) return@runCatching false
            events.any { element ->
                if (!element.isJsonObject) return@any false
                val event: JsonObject = element.asJsonObject
                val title: String = event.get("title")?.asString ?: ""
                val content: String = event.get("content")?.asString ?: ""
                title.startsWith(REQUIRED_VIDEO_CALL_SUMMARY_TITLE_PREFIX) &&
                    REQUIRED_VIDEO_CALL_SUMMARY_CONTENT_MARKS.all { mark: String -> content.contains(mark) }
            }
        }.getOrElse { exception: Throwable ->
            Log.e(TAG, "解析视频通话结构化抽取JSON失败", exception)
            false
        }
    }

    private companion object {
        private const val TAG: String = "VideoCallMemoryExtraction"
        private const val REQUIRED_VIDEO_CALL_SUMMARY_TITLE_PREFIX: String = "视频通话-通话总结："
        private val REQUIRED_VIDEO_CALL_SUMMARY_CONTENT_MARKS: List<String> = listOf(
            "通话开始时间",
            "通话结束时间",
            "持续时长",
            "大致内容",
            "大致情绪变化"
        )
    }
}