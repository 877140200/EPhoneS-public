package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile

/**
 * 商品生成AI提示词构造器
 * 
 * 负责构建用于AI生成商品的提示词
 */
object ShoppingPromptBuilder {
    
    /**
     * 构建商品生成提示词
     * 
     * @param profile 角色人设
     * @param existingCategories 已有分类列表
     * @param categoryCount 要生成的分类数量
     * @param productCountPerCategory 每个分类的商品数量
     * @param apiUrl API地址
     * @param model 模型名称
     * @return AI请求对象
     */
    fun buildShoppingGenerationPrompt(
        profile: PersonProfile,
        existingCategories: List<String>,
        categoryCount: Int,
        productCountPerCategory: Int,
        apiUrl: String,
        model: String,
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList(),
        chatHistory: List<ChatMessage> = emptyList(),
        userProfile: UserProfile? = null
    ): AiPromptRequest {
        // 使用统一的组件构建器
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(appointments)
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(generalMemories)
        
        // 构建对话历史摘要
        val chatHistorySummary = if (chatHistory.isNotEmpty() && userProfile != null) {
            PromptComponentBuilder.buildSimplifiedHistorySummary(
                chatHistory.takeLast(150),
                profile,
                userProfile
            )
        } else {
            ""
        }
        
        val systemPrompt = """
你是一个虚拟商城的商品策划专家。

角色信息:
- 姓名: ${profile.realName}
- 人设: ${profile.persona}

# 角色的记忆
${appointmentsContent}${generalMemoriesContent}
${if (chatHistorySummary.isNotEmpty()) "- **最近的对话摘要**:\n$chatHistorySummary\n\n" else ""}

任务:
为这个角色设计一个专属的虚拟商城,生成 $categoryCount 个商品分类,每个分类包含 $productCountPerCategory 个商品。

要求:
1. 商品要符合角色的性格和喜好
2. 每个商品必须包含2-4个款式(颜色、规格等)
3. 价格要合理,符合商品属性
4. 图片提示词要详细,用于AI生成图片

返回格式(纯JSON,不要任何额外文字):
{
  "categories": [
    {
      "name": "分类名称",
      "products": [
        {
          "name": "商品名称",
          "price": 99.99,
          "description": "商品描述",
          "imagePrompt": "英文图片生成提示词",
          "variations": [
            {
              "name": "款式名称(如:红色/大号)",
              "price": 99.99,
              "imagePrompt": "英文图片生成提示词"
            }
          ]
        }
      ]
    }
  ]
}
        """.trimIndent()
        
        val temperature = 0.8f // 创造性商品生成使用较高温度
        
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessagePayload(
                    role = "system",
                    content = systemPrompt
                ),
                ChatMessagePayload(
                    role = "user",
                    content = "请为${profile.realName}生成商品数据"
                )
            ),
            temperature = temperature,
        )
        
        val fullUrl = "$apiUrl/v1/chat/completions"
        
        return AiPromptRequest(
            request = request,
            url = fullUrl,
            displayPromptJson = com.google.gson.Gson().toJson(request),
            timestamp = System.currentTimeMillis(),
            contactName = profile.realName,
            activityType = "商品生成"
        )
    }
}