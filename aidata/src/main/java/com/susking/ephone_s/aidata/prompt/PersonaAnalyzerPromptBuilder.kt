package com.susking.ephone_s.aidata.prompt

import com.susking.ephone_s.aidata.data.local.entity.AppointmentEntity
import com.susking.ephone_s.aidata.data.local.entity.GeneralMemoryEntity
import com.susking.ephone_s.aidata.domain.model.ChatMessage
import com.susking.ephone_s.aidata.domain.model.LongTermMemory
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile

/**
 * 人设分析提示词构造器
 * 用于让AI自动分析角色人设,并推断出合适的时间感知配置
 */
object PersonaAnalyzerPromptBuilder {

    /**
     * 构建人设分析提示词
     * AI将根据人设描述自动推断:
     * 1. 作息时间(bedtime, wakeTime, isNightOwl)
     * 2. 是否需要睡觉(needsSleep)
     * 3. 多久算"很久没联系"(longTimeNoContactThreshold)
     * 4. 回复紧迫性(responseUrgencyLevel: 1=慢热 2=正常 3=秒回)
     */
    fun buildAnalyzePrompt(
        personaDescription: String,
        longTermMemories: List<LongTermMemory> = emptyList(),
        appointments: List<AppointmentEntity> = emptyList(),
        generalMemories: List<GeneralMemoryEntity> = emptyList(),
        chatHistory: List<ChatMessage> = emptyList(),
        personProfile: PersonProfile? = null,
        userProfile: UserProfile? = null
    ): String {
        // 使用统一的组件构建器
        val longTermMemoryContent = PromptComponentBuilder.buildLongTermMemorySection(longTermMemories)
        val appointmentsContent = PromptComponentBuilder.buildAppointmentsSection(appointments)
        val generalMemoriesContent = PromptComponentBuilder.buildGeneralMemoriesSection(generalMemories)
        
        // 构建对话历史摘要
        val chatHistorySummary = if (chatHistory.isNotEmpty() && personProfile != null && userProfile != null) {
            PromptComponentBuilder.buildSimplifiedHistorySummary(
                chatHistory.take(150),
                personProfile,
                userProfile
            )
        } else {
            ""
        }
        
        return """
# 核心任务
你是一个专业的角色分析师。你的任务是根据用户提供的"角色人设描述",分析出这个角色的时间感知特征。

# 输出格式铁律
你的回复【必须且只能】是以下JSON格式,不要有任何其他内容:
```json
{
  "sleepSchedule": {
    "bedtime": 23,
    "wakeTime": 7,
    "isNightOwl": false
  },
  "timeSensitivityConfig": {
    "needsSleep": true,
    "longTimeNoContactThreshold": 24,
    "responseUrgencyLevel": 2
  }
}
```

# 分析指南

## 1. sleepSchedule (作息时间表)
- **bedtime**: 就寝时间(0-23小时制)
  * 学生/上班族: 22-23点
  * 夜猫子/程序员: 1-3点
  * 早睡型: 20-21点
  
- **wakeTime**: 起床时间(0-23小时制)
  * 学生/上班族: 6-7点
  * 夜猫子: 10-12点
  * 早起型: 5-6点
  
- **isNightOwl**: 是否是夜猫子(true/false)
  * 程序员、夜班工作者、创作者、游戏玩家 → true
  * 正常作息、学生、早睡早起型 → false

## 2. timeSensitivityConfig (时间敏感度配置)

### 2.1 needsSleep (是否需要睡觉)
- **false**: 机器人、AI、神仙、鬼怪、不死生物、能量体
- **true**: 所有正常人类、动物、需要休息的生物

### 2.2 longTimeNoContactThreshold (多少小时算"很久没联系")
根据角色身份和关系类型判断:
- **12小时**: 
  * 学生情侣、闺蜜、死党
  * 粘人型、依赖型角色
  * 需要频繁联系的关系
  
- **24小时** (默认): 
  * 普通朋友、同学、同事
  * 正常社交关系
  
- **48小时**: 
  * 独立型、冷淡型角色
  * 工作狂、忙碌的人
  * 慢热型、不善社交的人
  
- **72小时及以上**: 
  * 高冷型、傲娇型
  * 社恐、隐士、独来独往
  * 非常忙碌或不重视社交的人

### 2.3 responseUrgencyLevel (回复紧迫性)
- **1 (慢热型)**: 
  * 社恐、内向、慢热
  * 高冷、傲娇、矜持
  * 思考型、谨慎型
  * 不善表达、害羞
  
- **2 (正常型)**: 
  * 普通人的回复速度
  * 不快不慢,自然适中
  
- **3 (秒回型)**: 
  * 外向、热情、开朗
  * 粘人、依赖型
  * 话痨、社交达人
  * 非常在乎对方的人

# 分析示例

## 示例1: 学生情侣
人设: "一个高中生,性格活泼开朗,是你的女朋友,平时很粘你,喜欢秒回消息。"
分析结果:
```json
{
  "sleepSchedule": {
    "bedtime": 23,
    "wakeTime": 6,
    "isNightOwl": false
  },
  "timeSensitivityConfig": {
    "needsSleep": true,
    "longTimeNoContactThreshold": 12,
    "responseUrgencyLevel": 3
  }
}
```
理由: 学生早睡早起,粘人性格12小时就算久,活泼开朗所以秒回。

## 示例2: 程序员朋友
人设: "一个程序员,经常熬夜写代码,性格比较内向慢热,不太主动联系别人。"
分析结果:
```json
{
  "sleepSchedule": {
    "bedtime": 2,
    "wakeTime": 11,
    "isNightOwl": true
  },
  "timeSensitivityConfig": {
    "needsSleep": true,
    "longTimeNoContactThreshold": 48,
    "responseUrgencyLevel": 1
  }
}
```
理由: 程序员夜猫子,内向慢热48小时才算久,不主动所以慢热型。

## 示例3: AI助手
人设: "一个AI智能助手,没有生理需求,24小时在线,随时响应。"
分析结果:
```json
{
  "sleepSchedule": {
    "bedtime": 23,
    "wakeTime": 7,
    "isNightOwl": false
  },
  "timeSensitivityConfig": {
    "needsSleep": false,
    "longTimeNoContactThreshold": 24,
    "responseUrgencyLevel": 3
  }
}
```
理由: AI不需要睡觉(needsSleep=false),作息时间在这种情况下无意义,快速响应。

# 现在开始分析

请根据以下人设描述,输出JSON格式的分析结果:

**角色人设描述**:
$personaDescription

# 记忆资料 (你必须严格遵守的事实)
${longTermMemoryContent}${appointmentsContent}${generalMemoriesContent}

${if (chatHistorySummary.isNotEmpty()) "- **最近的对话摘要**:\n$chatHistorySummary\n" else ""}
请严格按照上述JSON格式输出,不要添加任何解释或其他内容。
        """.trimIndent()
    }
}