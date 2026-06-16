package com.susking.ephone_s.aidata.service

import android.content.Context
import android.util.Log
import com.susking.ephone_s.aidata.api.AiDataApi
import com.susking.ephone_s.aidata.data.local.dao.ScheduledGreetingDao
import com.susking.ephone_s.aidata.data.local.dao.ChatMessageDao
import com.susking.ephone_s.aidata.data.local.entity.ScheduledGreetingEntity
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import com.susking.ephone_s.aidata.prompt.AiPromptRequest
import com.susking.ephone_s.aidata.prompt.ChatCompletionRequest
import com.susking.ephone_s.aidata.prompt.ChatMessagePayload
import com.susking.ephone_s.aidata.prompt.OnlinePromptBuilder
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 预发送祝福服务
 * 负责检查、生成、存储和管理节日祝福
 */
class ScheduledGreetingService(
    private val scheduledGreetingDao: ScheduledGreetingDao,
    private val chatMessageDao: ChatMessageDao,
    private val personProfileRepository: PersonProfileRepository
) {
    
    companion object {
        private const val TAG = "ScheduledGreetingService"
    }
    
    /**
     * 检查是否需要为联系人预生成祝福
     * @param contactId 联系人ID
     * @param messageCount 消息数量
     * @return 是否需要生成祝福（返回节日信息）
     */
    /**
     * 检查并预生成祝福
     * @param contactId 联系人ID
     * @param messageCount 消息数量
     */
    suspend fun checkAndPrepareGreeting(
        contactId: String,
        messageCount: Int
    ) {
        try {
            Log.i(TAG, "========== 开始检查节日祝福 ==========")
            Log.i(TAG, "联系人ID: $contactId")
            Log.i(TAG, "消息总数: $messageCount")
            
            // 检查是否有即将到来的节日
            val festivalInfo = OnlinePromptBuilder.checkUpcomingFestival(contactId, messageCount)
            if (festivalInfo == null) {
                Log.d(TAG, "联系人 $contactId 无需生成节日祝福 (消息数<200 或 无即将到来的节日)")
                Log.i(TAG, "========== 检查结束 ==========\n")
                return
            }
            
            val (festivalName, festivalDate, greetingType) = festivalInfo
            Log.i(TAG, "✓ 检测到即将到来的节日: $festivalName")
            Log.i(TAG, "节日日期: ${festivalDate.get(Calendar.YEAR)}年${festivalDate.get(Calendar.MONTH)+1}月${festivalDate.get(Calendar.DAY_OF_MONTH)}日")
            Log.i(TAG, "祝福类型: $greetingType")
            
            // 检查是否已经有该节日的祝福
            val existingGreeting = scheduledGreetingDao.findExistingGreeting(
                contactId = contactId,
                greetingType = greetingType,
                year = festivalDate.get(Calendar.YEAR),
                month = festivalDate.get(Calendar.MONTH) + 1,
                day = festivalDate.get(Calendar.DAY_OF_MONTH)
            )
            
            if (existingGreeting != null) {
                Log.w(TAG, "⚠ 联系人 $contactId 已有 $festivalName 祝福 (ID: ${existingGreeting.id}, 状态: ${existingGreeting.status})")
                Log.i(TAG, "========== 检查结束 ==========\n")
                return
            }
            
            Log.i(TAG, "✓ 无重复祝福，开始生成新祝福")
            
            // 获取联系人和用户信息
            val contact = personProfileRepository.getPersonProfileById(contactId)
            if (contact == null) {
                Log.e(TAG, "✗ 找不到联系人 $contactId")
                Log.i(TAG, "========== 检查结束 ==========\n")
                return
            }
            Log.i(TAG, "✓ 联系人信息: ${contact.remarkName} (${contact.realName})")
            
            val userProfile = personProfileRepository.getUserProfile()
            Log.i(TAG, "✓ 用户信息: ${userProfile.nickname}")
            
            // 生成祝福提示词
            val prompt = OnlinePromptBuilder.buildFestivalGreetingPrompt(
                contact, userProfile, festivalName, festivalDate
            )
            
            Log.i(TAG, "---------- 祝福提示词 ----------")
            Log.i(TAG, prompt)
            Log.i(TAG, "--------------------------------")
            
            Log.i(TAG, "开始调用AI生成祝福...")
            
            // 调用AI生成祝福内容
            val greetingContent = requestAiGreeting(prompt)
            if (greetingContent == null) {
                Log.e(TAG, "✗ AI生成祝福失败")
                Log.i(TAG, "========== 检查结束 ==========\n")
                return
            }
            
            Log.i(TAG, "✓ AI生成祝福成功")
            Log.i(TAG, "---------- 祝福内容 ----------")
            Log.i(TAG, greetingContent)
            Log.i(TAG, "------------------------------")
            Log.i(TAG, "祝福长度: ${greetingContent.length} 字符")
            
            // 保存祝福
            val greetingId = saveGreeting(contactId, greetingType, greetingContent, festivalDate)
            
            Log.i(TAG, "✓ 祝福已保存 (ID: $greetingId)")
            Log.i(TAG, "发送时间: ${festivalDate.get(Calendar.YEAR)}年${festivalDate.get(Calendar.MONTH)+1}月${festivalDate.get(Calendar.DAY_OF_MONTH)}日 00:00:00")
            Log.i(TAG, "========== 预生成完成 ==========\n")
        } catch (e: Exception) {
            Log.e(TAG, "✗ 预生成祝福时出错", e)
            Log.i(TAG, "========== 检查结束（异常） ==========\n")
        }
    }
    
    /**
     * 请求AI生成祝福内容
     * @param prompt 祝福提示词
     * @return AI生成的祝福内容，失败返回null
     */
    private suspend fun requestAiGreeting(prompt: String): String? {
        return try {
            Log.d(TAG, "【AI请求】获取API配置...")
            val apiUrl = AiDataApi.getSettingsRepository().getMainApiUrl()
            val model = AiDataApi.getSettingsRepository().getMainModel()
            val temperature = AiDataApi.getSettingsRepository().getApiTemperature()
            
            Log.d(TAG, "【AI请求】API URL: $apiUrl")
            Log.d(TAG, "【AI请求】模型: $model")
            Log.d(TAG, "【AI请求】温度: $temperature")
            
            // 构建请求消息列表
            val messages = listOf(
                ChatMessagePayload(role = "user", content = prompt)
            )
            
            val requestBody = ChatCompletionRequest(
                model = model,
                messages = messages,
                temperature = temperature
            )
            
            val fullUrl = "$apiUrl/v1/chat/completions"
            val displayJson = GsonBuilder().setPrettyPrinting().create().toJson(requestBody)
            val promptRequest = AiPromptRequest(requestBody, fullUrl, displayJson, System.currentTimeMillis())
            
            Log.d(TAG, "【AI请求】完整URL: $fullUrl")
            Log.d(TAG, "【AI请求】请求体JSON:")
            Log.d(TAG, displayJson)
            
            // 从AiDataApi获取context
            val context = AiDataApi.getContext()
            Log.d(TAG, "【AI请求】Context已获取")
            
            Log.d(TAG, "【AI请求】开始调用AI服务...")
            val response = AiDataApi.getAiRequestService().getChatCompletion(context, promptRequest)?.trim()
            
            if (response != null) {
                Log.d(TAG, "【AI请求】✓ 响应成功，长度: ${response.length}")
            } else {
                Log.w(TAG, "【AI请求】⚠ 响应为null")
            }
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "【AI请求】✗ 请求失败", e)
            Log.e(TAG, "【AI请求】错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "【AI请求】错误消息: ${e.message}")
            null
        }
    }
    
    /**
     * 生成祝福提示词
     * @param contact 联系人信息
     * @param userProfile 用户信息
     * @param festivalName 节日名称
     * @param festivalDate 节日日期
     * @return 祝福提示词
     */
    /**
     * 保存AI生成的祝福
     * @param contactId 联系人ID
     * @param greetingType 祝福类型
     * @param greetingContent AI生成的祝福内容
     * @param festivalDate 节日日期
     * @return 保存的祝福ID
     */
    suspend fun saveGreeting(
        contactId: String,
        greetingType: String,
        greetingContent: String,
        festivalDate: Calendar
    ): Long {
        // 计算发送时间：节日当天的00:00:00
        val scheduledCalendar = festivalDate.clone() as Calendar
        scheduledCalendar.set(Calendar.HOUR_OF_DAY, 0)
        scheduledCalendar.set(Calendar.MINUTE, 0)
        scheduledCalendar.set(Calendar.SECOND, 0)
        scheduledCalendar.set(Calendar.MILLISECOND, 0)
        
        val greeting = ScheduledGreetingEntity(
            contactId = contactId,
            greetingType = greetingType,
            greetingContent = greetingContent,
            scheduledTime = scheduledCalendar.timeInMillis,
            festivalYear = festivalDate.get(Calendar.YEAR),
            festivalMonth = festivalDate.get(Calendar.MONTH) + 1,
            festivalDay = festivalDate.get(Calendar.DAY_OF_MONTH)
        )
        
        return scheduledGreetingDao.insertGreeting(greeting)
    }
    
    /**
     * 获取联系人的所有待发送祝福
     */
    fun getPendingGreetings(contactId: String): Flow<List<ScheduledGreetingEntity>> {
        return scheduledGreetingDao.getPendingGreetingsByContact(contactId)
    }
    
    /**
     * 获取联系人的所有祝福历史
     */
    fun getAllGreetings(contactId: String): Flow<List<ScheduledGreetingEntity>> {
        return scheduledGreetingDao.getAllGreetingsByContact(contactId)
    }
    
    /**
     * 取消待发送的祝福
     */
    suspend fun cancelGreeting(greetingId: Long) {
        scheduledGreetingDao.markAsCancelled(greetingId)
    }
    
    /**
     * 手动触发发送祝福（用于测试）
     */
    suspend fun sendGreetingNow(greetingId: Long): Boolean {
        val greeting = scheduledGreetingDao.getGreetingById(greetingId) ?: return false
        
        if (greeting.status != "pending") {
            return false
        }
        
        try {
            // 创建消息实体
            val message = com.susking.ephone_s.aidata.data.local.entity.ChatMessageEntity(
                contactId = greeting.contactId,
                type = "text",
                content = greeting.greetingContent,
                timestamp = System.currentTimeMillis(),
                role = "assistant"
            )
            
            // 插入消息到聊天记录
            chatMessageDao.insertMessage(message)
            
            // 标记祝福为已发送
            scheduledGreetingDao.markAsSent(greeting.id, System.currentTimeMillis())
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}